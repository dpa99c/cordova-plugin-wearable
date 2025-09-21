package cordova.plugin.wearable

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

/**
 * StateStore persists per-path entries with LWW semantics and provides materialized view.
 */
class StateStore(private val context: Context) {

    companion object {
        private const val TAG = "StateStore"
        private const val PREFS_NAME = "wearable_state_store"
        private const val KEY_REPLICA_ID = "replicaId"
        private const val KEY_EPOCH = "epoch"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_PEER_SEEN = "peer_seen"
        private const val PHONE_REPLICA_ID = "phone"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    /**
     * Retrieve the replica identifier used for local versioning, migrating older UUID-based IDs if present.
     *
     * @return Stable replica identifier for the phone.
     */
    fun getReplicaId(): String {
        val oldId = prefs.getString(KEY_REPLICA_ID, null)
        // Check if we're migrating from old UUID-based replica ID
        if (oldId != null && oldId != PHONE_REPLICA_ID) {
            Logger.info(TAG, "StateStore migrating from old replicaId=$oldId to phone, clearing state")
            // Clear old state to avoid version conflicts
            prefs.edit()
                .remove(KEY_ENTRIES)
                .remove(KEY_PEER_SEEN)
                .putString(KEY_REPLICA_ID, PHONE_REPLICA_ID)
                .apply()
            return PHONE_REPLICA_ID
        }
        // Use fixed replica ID for phone (not a UUID)
        if (oldId == null) {
            prefs.edit().putString(KEY_REPLICA_ID, PHONE_REPLICA_ID).apply()
        }
        return PHONE_REPLICA_ID
    }

    @Synchronized
    /**
     * Get the current epoch used for snapshot versioning.
     *
     * @return Current epoch counter.
     */
    fun getEpoch(): Int {
        return prefs.getInt(KEY_EPOCH, 1)
    }

    @Synchronized
    /**
     * Increment the local epoch counter.
     */
    fun bumpEpoch() {
        val e = getEpoch() + 1
        prefs.edit().putInt(KEY_EPOCH, e).apply()
    }

    @Synchronized
    /**
     * Load persisted entries from SharedPreferences.
     *
     * @return Mutable map of path -> entry.
     */
    private fun loadEntries(): MutableMap<String, StateEntry> {
        val map = mutableMapOf<String, StateEntry>()
        val json = prefs.getString(KEY_ENTRIES, null) ?: return map
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val entry = StateEntry.fromJson(obj)
                map[entry.path] = entry
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse entries JSON", e)
        }
        return map
    }

    @Synchronized
    /**
     * Persist the given entry map to SharedPreferences.
     */
    private fun saveEntries(map: Map<String, StateEntry>) {
        val arr = JSONArray()
        map.values.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    @Synchronized
    /**
     * Compute the next local version for the provided path.
     *
     * @param path JSON pointer path.
     * @return New logical version.
     */
    fun nextLocalVersion(path: String): Version {
        val ts = System.currentTimeMillis()  // milliseconds since epoch
        return Version(ts, getReplicaId())
    }

    @Synchronized
    /**
     * Retrieve a single entry for the provided path.
     */
    fun getEntry(path: String): StateEntry? = loadEntries()[path]

    @Synchronized
    /**
     * Retrieve all entries currently stored.
     */
    fun getAllEntries(): List<StateEntry> = loadEntries().values.toList()

    @Synchronized
    /**
     * Apply (merge) a new state entry based on version ordering.
     *
     * @param newEntry Entry to merge.
     * @return True if the entry was applied.
     */
    fun applyEntry(newEntry: StateEntry): Boolean {
        val entries = loadEntries()
        val normalized = if (newEntry.path.startsWith("/")) newEntry.path.substring(1) else newEntry.path
        // Coalesce duplicate keys that differ only by leading slash
        val altWithSlash = "/$normalized"
        val existing = entries[normalized] ?: entries[altWithSlash]
        val shouldApply = existing == null || newEntry.version > existing.version
        if (shouldApply) {
            // Store under normalized key
            entries.remove(altWithSlash)
            entries[normalized] = newEntry.copy(path = normalized)
            saveEntries(entries)
            return true
        }
        return false
    }

    @Synchronized
    /**
     * Materialize the stored state into a nested JSON object.
     */
    fun materialize(): JSONObject {
        val root = JSONObject()
        val entries = getAllEntries().sortedBy { it.path.length } // apply parents before children where helpful
        for (e in entries) {
            if (!e.tombstone) {
                try {
                    JsonPointer.apply(root, e.path, e.value)
                } catch (ex: Exception) {
                    Logger.error(TAG, "Failed to apply path ${e.path}", ex)
                }
            }
        }
        return root
    }

    // Acknowledgements: record that peer has seen a given version for path
    @Synchronized
    /**
     * Record that a peer has seen a specific version for the provided path.
     */
    fun recordPeerSeen(peerReplicaId: String, path: String, version: Version) {
        val all = JSONObject(prefs.getString(KEY_PEER_SEEN, "{}") ?: "{}")
        val peer = if (all.has(peerReplicaId)) all.getJSONObject(peerReplicaId) else JSONObject()
        val normalized = if (path.startsWith("/")) path.substring(1) else path
        peer.put(normalized, version.toJson())
        all.put(peerReplicaId, peer)
        prefs.edit().putString(KEY_PEER_SEEN, all.toString()).apply()
    }

    @Synchronized
    /**
     * Check whether a peer has acknowledged at least the provided version for the path.
     */
    fun hasPeerSeen(peerReplicaId: String, path: String, version: Version): Boolean {
        val all = JSONObject(prefs.getString(KEY_PEER_SEEN, "{}") ?: "{}")
        if (!all.has(peerReplicaId)) return false
        val peer = all.getJSONObject(peerReplicaId)
        val normalized = if (path.startsWith("/")) path.substring(1) else path
        val verObj = peer.optJSONObject(normalized) ?: peer.optJSONObject(path) ?: return false
        val seen = Version.fromJson(verObj)
        return seen >= version
    }

    // Garbage collect tombstones acknowledged by peer
    @Synchronized
    /**
     * Remove tombstones acknowledged by the given peer to keep storage compact.
     */
    fun gcAcknowledgedTombstones(peerReplicaId: String) {
        val entries = loadEntries()
        val toRemove = mutableListOf<String>()
        for ((path, entry) in entries) {
            if (entry.tombstone && hasPeerSeen(peerReplicaId, path, entry.version)) {
                toRemove.add(path)
            }
        }
        if (toRemove.isNotEmpty()) {
            Logger.info(TAG, "gcAcknowledgedTombstones - removing tombstones for peer $peerReplicaId: $toRemove")
            toRemove.forEach { entries.remove(it) }
            saveEntries(entries)
        }
    }
}

/** Minimal JSON Pointer helper */
object JsonPointer {
    /**
     * Apply the provided value at the JSON pointer within the root object.
     *
     * @param root Mutable JSON object to update.
     * @param pointer JSON pointer path (`/alpha`, `alpha`, etc.).
     * @param value Value to assign; null removes the node.
     */
    fun apply(root: JSONObject, pointer: String, value: Any?) {
        // Accept either '/path' or 'path' and normalize to a JSON Pointer starting with '/'
        val normalized = if (pointer.startsWith("/")) pointer else "/${pointer.trimStart('/') }"
        val tokens = normalized.substring(1).split('/').map { it.replace("~1", "/").replace("~0", "~") }
        var cur = root
        for (i in 0 until tokens.size) {
            val key = tokens[i]
            val isLast = i == tokens.size - 1
            if (isLast) {
                if (value == null) {
                    cur.remove(key)
                } else {
                    cur.put(key, value)
                }
            } else {
                if (!cur.has(key) || cur.isNull(key) || cur.optJSONObject(key) == null) {
                    cur.put(key, JSONObject())
                }
                cur = cur.getJSONObject(key)
            }
        }
    }
}
