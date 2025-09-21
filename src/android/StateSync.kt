package cordova.plugin.wearable

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import android.os.Handler
import android.os.Looper
import android.net.Uri

class StateSync(private val context: Context, private val store: StateStore) : DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "StateSync"
        private const val ACK_FLUSH_DELAY_MS = 300L
        private const val PAYLOAD_KEY_JSON = "json"
        private const val EVENT_STATE_CHANGED = "stateChanged"
    }

    // Pending acknowledgements to batch and flush after a short debounce
    private val pendingAcks: MutableMap<String, Version> = mutableMapOf()
    private val handler = Handler(Looper.getMainLooper())
    private var ackFlushRunnable: Runnable? = null

    /** Base Wear OS Data Layer path for state sync payloads. */
    private fun basePath(): String = cordova.plugin.wearable.Wearable.path + StateSyncSpec.STATE_SEGMENT

    /**
     * Start observing Wear OS Data Layer events and publish the local snapshot.
     */
    fun start() {
    try { com.google.android.gms.wearable.Wearable.getDataClient(context).addListener(this) } catch (e: Exception) { Logger.error(TAG, "Failed to add DataClient listener", e) }
        bootstrapExistingDataItems()
        // Bi-directional handshake: publish our snapshot and request peer's snapshot
        try { sendSnapshot() } catch (e: Exception) { Logger.error(TAG, "Failed to publish initial snapshot on start", e) }
        try { requestPeerSnapshot() } catch (e: Exception) { Logger.error(TAG, "Failed to request peer snapshot on start", e) }
    }

    /** Stop observing Wear OS Data Layer events. */
    fun stop() {
    try { com.google.android.gms.wearable.Wearable.getDataClient(context).removeListener(this) } catch (e: Exception) { Logger.error(TAG, "Failed to remove DataClient listener", e) }
    }

    // Public API used by plugin bridge
    /**
     * Set a JSON pointer path to the provided value.
     *
     * @param path JSON pointer path (with or without leading slash).
     * @param value JSON-serializable value.
     */
    fun setPath(path: String, value: Any?) {
        // Normalize path: remove leading slash to match fromJson normalization
        val normalizedPath = if (path.startsWith("/")) path.substring(1) else path
        val v = store.nextLocalVersion(normalizedPath)
        val entry = StateEntry(normalizedPath, false, v, value)
        val changed = store.applyEntry(entry)
        if (changed) {
            sendOp(StateOp(StateSyncSpec.OpType.PUT, path, v, value, UUID.randomUUID().toString(), System.currentTimeMillis()))
            dispatchState()
        }
    }

    /**
     * Remove a JSON pointer path (records a tombstone).
     *
     * @param path JSON pointer path to remove.
     */
    fun removePath(path: String) {
        // Normalize path: remove leading slash to match fromJson normalization
        val normalizedPath = if (path.startsWith("/")) path.substring(1) else path
        val v = store.nextLocalVersion(normalizedPath)
        val entry = StateEntry(normalizedPath, true, v, null)
        val changed = store.applyEntry(entry)
        if (changed) {
            sendOp(StateOp(StateSyncSpec.OpType.DEL, path, v, null, UUID.randomUUID().toString(), System.currentTimeMillis()))
            dispatchState()
        }
    }

    /** Return the fully materialized shared state snapshot. */
    fun getMaterialized(): JSONObject = store.materialize()

    /** Notify the JS layer of the current materialized state. */
    private fun dispatchState() {
        try {
            val payload = JSONObject()
            payload.put(StateSyncSpec.JsonKeys.EVENT, EVENT_STATE_CHANGED)
            payload.put(StateSyncSpec.JsonKeys.STATE, store.materialize())
            ListenerBridge.dispatch(payload)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to dispatch state", e)
        }
    }

    /** Publish the entire state snapshot to the Wear OS Data Layer. */
    fun sendSnapshot() {
        RetryHelper.withRetry(
            tag = TAG,
            operation = "sendSnapshot",
            maxAttempts = StateSyncSpec.Retry.MAX_ATTEMPTS
        ) { attempt ->
            try {
                if (attempt > 1) {
                    Logger.debug(TAG, "Retry attempt $attempt for sendSnapshot")
                }
                val entries = JSONArray()
                store.getAllEntries().forEach { entries.put(it.toJson()) }
                val obj = JSONObject()
                obj.put(StateSyncSpec.JsonKeys.FROM_REPLICA, store.getReplicaId())
                obj.put(StateSyncSpec.JsonKeys.EPOCH, store.getEpoch())
                obj.put(StateSyncSpec.JsonKeys.ENTRIES, entries)

                val path = basePath() + StateSyncSpec.SNAPSHOT_SEGMENT
                putDataItem(path, obj)
                true
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to send snapshot on attempt $attempt", e)
                throw e
            }
        }
    }

    /**
     * Publish an individual state operation to the Wear OS Data Layer.
     *
     * @param op Operation to send.
     */
    private fun sendOp(op: StateOp) {
        val path = basePath() + StateSyncSpec.OP_SEGMENT + "/" + op.opId
        Logger.verbose(TAG, "sendOp: $path -> ${op.toJson()}")
        putDataItem(path, op.toJson())
    }

    /**
     * Send acknowledgements for the provided entries.
     *
     * @param seen Entries acknowledged by the phone.
     */
    fun sendAck(seen: List<StateEntry>) {
        try {
            val arr = JSONArray()
            for (e in seen) {
                val obj = JSONObject()
                obj.put(StateSyncSpec.JsonKeys.PATH, e.path)
                obj.put(StateSyncSpec.JsonKeys.VERSION, e.version.toJson())
                arr.put(obj)
            }
            val payload = JSONObject()
            payload.put(StateSyncSpec.JsonKeys.FROM_REPLICA, store.getReplicaId())
            payload.put(StateSyncSpec.JsonKeys.SEEN, arr)
            val path = basePath() + StateSyncSpec.ACK_SEGMENT
            putDataItem(path, payload)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to send ack", e)
        }
    }

    /**
     * Create or update a Wear OS Data Layer item with the provided payload.
     *
     * @param path Data Layer path.
     * @param obj JSON payload to serialize.
     */
    private fun putDataItem(path: String, obj: JSONObject) {
        RetryHelper.withRetry(
            tag = TAG,
            operation = "putDataItem",
            maxAttempts = StateSyncSpec.Retry.MAX_ATTEMPTS
        ) { attempt ->
            try {
                if (attempt > 1) {
                    Logger.debug(TAG, "Retry attempt $attempt for putDataItem: $path")
                }
                val req = PutDataMapRequest.create(path)
                req.dataMap.putString(PAYLOAD_KEY_JSON, obj.toString())
                val request = req.asPutDataRequest().setUrgent()
                com.google.android.gms.wearable.Wearable.getDataClient(context).putDataItem(request)
                Logger.verbose(TAG, "putDataItem: $path -> $obj")
                true
            } catch (e: Exception) {
                Logger.error(TAG, "putDataItem failed on attempt $attempt", e)
                throw e
            }
        }
    }

    /** Handle Wear OS Data Layer change events and route them through the state machinery. */
    override fun onDataChanged(events: DataEventBuffer) {
        try {
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                val path = item.uri.path ?: continue
                if (!path.startsWith(basePath())) continue
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                handleDataItem(path, dataMap)
            }
            // Ack flush will be handled via batching/enqueueAck. No immediate send here.
        } catch (e: Exception) {
            Logger.error(TAG, "onDataChanged error", e)
        }
    }

    /**
     * Parse and process a single Wear OS Data Layer item.
     *
     * @param path Item path.
     * @param dataMap Associated DataMap payload.
     */
    private fun handleDataItem(path: String, dataMap: DataMap) {
        try {
            val jsonStr = dataMap.getString(PAYLOAD_KEY_JSON)
            if (jsonStr == null) {
                Logger.warn(TAG, "handleDataItem: no json string in dataMap for path $path")
                return
            }
            val payload = JSONObject(jsonStr)
            Logger.verbose(TAG, "onDataChanged: $path -> $payload")
            when {
                path.contains(StateSyncSpec.OP_SEGMENT) -> {
                    val op = StateOp.fromJson(payload)
                    val normPath = if (op.path.startsWith("/")) op.path.substring(1) else op.path
                    val entry = if (op.type == StateSyncSpec.OpType.PUT)
                        StateEntry(normPath, false, op.version, op.value)
                    else StateEntry(normPath, true, op.version, null)
                    val changed = store.applyEntry(entry)
                    if (changed) {
                        dispatchState()
                    }
                    enqueueAck(entry)
                    Logger.verbose(TAG, "Received op: $op, applied: $changed, entry: $entry")
                }
                path.endsWith(StateSyncSpec.SNAPSHOT_SEGMENT) -> {
                    val entries = payload.getJSONArray(StateSyncSpec.JsonKeys.ENTRIES)
                    var changedAny = false
                    for (i in 0 until entries.length()) {
                        val entry = StateEntry.fromJson(entries.getJSONObject(i))
                        val changed = store.applyEntry(entry)
                        changedAny = changedAny || changed
                        enqueueAck(entry)
                        Logger.verbose(TAG, "Received snapshot entry: $entry, applied: $changed, entry: $entry")
                    }
                    if (changedAny) dispatchState()
                }
                path.endsWith(StateSyncSpec.ACK_SEGMENT) -> {
                    val fromReplica = payload.optString(StateSyncSpec.JsonKeys.FROM_REPLICA, "")
                    val seen = payload.optJSONArray(StateSyncSpec.JsonKeys.SEEN) ?: JSONArray()
                    for (i in 0 until seen.length()) {
                        val obj = seen.getJSONObject(i)
                        val p = obj.getString(StateSyncSpec.JsonKeys.PATH)
                        val v = Version.fromJson(obj.getJSONObject(StateSyncSpec.JsonKeys.VERSION))
                        if (fromReplica.isNotEmpty()) store.recordPeerSeen(fromReplica, p, v)
                        Logger.verbose(TAG, "Received ack from $fromReplica: $p @ $v")
                    }
                    if (fromReplica.isNotEmpty()) store.gcAcknowledgedTombstones(fromReplica)
                }
                path.contains("/request/") -> {
                    val type = payload.optString(StateSyncSpec.JsonKeys.TYPE, "")
                    if (type == StateSyncSpec.JsonKeys.REQUEST_SNAPSHOT) {
                        handleSnapshotRequest()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "handleDataItem error", e)
        }
    }

    /** Load existing Data Layer items on start so we reconcile with cached state. */
    private fun bootstrapExistingDataItems() {
        try {
            val client = com.google.android.gms.wearable.Wearable.getDataClient(context)
            val uri = Uri.Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .authority("*")
                .path(basePath())
                .build()
            client.getDataItems(uri, DataClient.FILTER_PREFIX)
                .addOnSuccessListener { buffer ->
                    try {
                        for (item in buffer) {
                            val path = item.uri.path ?: continue
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            handleDataItem(path, dataMap)
                        }
                    } finally {
                        buffer.release()
                    }
                }
                .addOnFailureListener { e ->
                    Logger.error(TAG, "Failed to bootstrap existing data items", e)
                }
        } catch (e: Exception) {
            Logger.error(TAG, "bootstrapExistingDataItems error", e)
        }
    }

    /**
     * Queue an acknowledgement for the provided entry and schedule a batched flush.
     *
     * @param entry Entry to acknowledge.
     */
    private fun enqueueAck(entry: StateEntry) {
        try {
            synchronized(pendingAcks) {
                val key = if (entry.path.startsWith("/")) entry.path else "/${entry.path.trimStart('/') }"
                val existing = pendingAcks[key]
                if (existing == null || entry.version > existing) {
                    pendingAcks[key] = entry.version
                }
                // debounce flush
                ackFlushRunnable?.let { handler.removeCallbacks(it) }
                ackFlushRunnable = Runnable { flushAcks() }
                handler.postDelayed(ackFlushRunnable!!, ACK_FLUSH_DELAY_MS)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "enqueueAck error", e)
        }
    }

    /** Flush any queued acknowledgements to the Wear OS Data Layer. */
    private fun flushAcks() {
        try {
            val toSend = mutableListOf<Pair<String, Version>>()
            synchronized(pendingAcks) {
                for ((p, v) in pendingAcks) toSend.add(Pair(p, v))
                pendingAcks.clear()
                ackFlushRunnable = null
            }
            if (toSend.isEmpty()) return
            val arr = JSONArray()
            for ((p, v) in toSend) {
                val obj = JSONObject()
                obj.put(StateSyncSpec.JsonKeys.PATH, p)
                obj.put(StateSyncSpec.JsonKeys.VERSION, v.toJson())
                arr.put(obj)
            }
            val payload = JSONObject()
            payload.put(StateSyncSpec.JsonKeys.FROM_REPLICA, store.getReplicaId())
            payload.put(StateSyncSpec.JsonKeys.SEEN, arr)
            val path = basePath() + StateSyncSpec.ACK_SEGMENT
            putDataItem(path, payload)
            Logger.verbose(TAG, "Sent ack for ${toSend.size} entries")
        } catch (e: Exception) {
            Logger.error(TAG, "flushAcks error", e)
        }
    }

    /** Request a snapshot from the peer to ensure convergence. */
    private fun requestPeerSnapshot() {
        try {
            val payload = JSONObject()
            payload.put(StateSyncSpec.JsonKeys.TYPE, StateSyncSpec.JsonKeys.REQUEST_SNAPSHOT)
            payload.put(StateSyncSpec.JsonKeys.FROM_REPLICA, store.getReplicaId())
            val path = basePath() + "/request/" + UUID.randomUUID().toString()
            putDataItem(path, payload)
            Logger.debug(TAG, "Requested peer snapshot")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to request peer snapshot", e)
        }
    }

    fun handleSnapshotRequest() {
        try {
            Logger.debug(TAG, "Received snapshot request, sending snapshot")
            sendSnapshot()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to handle snapshot request", e)
        }
    }
}
