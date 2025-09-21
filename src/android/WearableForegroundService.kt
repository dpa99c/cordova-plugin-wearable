package cordova.plugin.wearable

import android.app.Notification
import android.app.NotificationChannel
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.android.gms.wearable.*
import org.json.JSONObject
import com.google.android.gms.wearable.Wearable
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import com.google.android.gms.tasks.Tasks
import cordova.plugin.wearable.Wearable.Companion.PluginEvent

/**
 * Foreground service that registers Wearable listeners to receive messages even
 * when the Cordova WebView is backgrounded. It forwards messages to the plugin
 * listener callback if the Cordova layer is active.
 */
class WearableForegroundService : Service(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener, CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private val TAG = WearableForegroundService::class.simpleName ?: "WearableFGService"
        // Notification
        private const val CHANNEL_ID = "WearableForegroundChannel"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_TITLE_DEFAULT = "Wearable service"
        private const val NOTIFICATION_TEXT_DEFAULT = "Listening for wear messages"
        // Intent actions for explicit start/stop
        const val ACTION_START = "cordova.plugin.wearable.action.START_CONNECTIVITY"
        const val ACTION_STOP = "cordova.plugin.wearable.action.STOP_CONNECTIVITY"
        @Volatile
        var lastWatchHeartbeatAt: Long = 0L
        const val CONNECTIVITY_STALE_MS: Long = 4000 // Timeout for watch connectivity after which, if no heartbeat received, we consider disconnected
        @JvmStatic
        fun isWatchAppConnectedNow(): Boolean = lastWatchHeartbeatAt > 0 && (System.currentTimeMillis() - lastWatchHeartbeatAt) <= CONNECTIVITY_STALE_MS

        // Heartbeats
        private const val HEARTBEAT_WORK_NAME = "wearable_heartbeat"
        private const val HEARTBEAT_INTERVAL_MINUTES = 15L // WorkManager minimum periodic interval is 15 minutes
        private const val HEARTBEAT_TICK_SECONDS = 1L // 1 second heartbeat ticker while service is alive
        private const val AWAIT_NODES_TIMEOUT_SECS = 2L // 2 second timeout when querying nodes

        // Message path (should be configured via Wearable.configure())
        private const val DEFAULT_MESSAGE_PATH = "/data"

        // Message keys and values
        const val KEY_HEARTBEAT = "heartbeat"
        const val VALUE_HEARTBEAT_WATCH = "watch"
        const val VALUE_HEARTBEAT_PHONE = "phone"

        // Service init gate
        @Volatile private var initialized: Boolean = false
        @Volatile private var instance: WearableForegroundService? = null
        @Volatile private var pendingInit: Boolean = false

        /**
         * Initialize listeners and heartbeats after Wearable.configure() has set capability/path.
         * Safe to call multiple times; only the first successful call will take effect.
         */
        @JvmStatic
        fun initConfigured() {
            val inst = instance
            if (inst == null) {
                pendingInit = true
                Log.w(TAG, "initConfigured called before service onCreate; deferring until service starts")
                return
            }
            if (initialized) {
                Log.i(TAG, "ForegroundService already initialized; ignoring")
                return
            }
            val cap = cordova.plugin.wearable.Wearable.capability
            val p = cordova.plugin.wearable.Wearable.path
            if (cap.isEmpty() || p.isEmpty()) {
                Log.i(TAG, "initConfigured called but capability/path not set yet (cap='${'$'}cap', path='${'$'}p'); deferring")
                return
            }
            try {
                inst.registerListeners()
                inst.startHeartbeatSchedule()
                inst.startHeartbeatTicker()
                inst.createNotificationChannel()
                inst.startForeground(NOTIFICATION_ID, inst.buildNotification())
                initialized = true
                pendingInit = false
                Log.i(TAG, "ForegroundService initialized with capability='${cap}' path='${p}'")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ForegroundService", e)
            }
        }
    }

    // 3-second heartbeat ticker while the foreground service is alive
    private var heartbeatScheduler: ScheduledExecutorService? = null
    @Volatile private var lastIsPaired: Boolean? = null
    @Volatile private var lastIsConnected: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "WearableForegroundService created; waiting for ACTION_START to initialize")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand action=$action")
        when (action) {
            ACTION_START -> initConfigured()
            ACTION_STOP -> stopSelf()
            else -> Log.v(TAG, "No explicit action provided; service idle")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop heartbeat scheduling and ticker
        stopHeartbeatSchedule()
        stopHeartbeatTicker()

        // Reset last heartbeat so connectivity is considered stale
        try {
            lastWatchHeartbeatAt = 0L

            // Notify plugin listeners that connectivity has ended
            try {
                val connEvt = JSONObject().put("event", PluginEvent.IsConnectedChanged.value).put("value", false)
                PluginEventBridge.dispatch(connEvt)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dispatch isConnectedChanged=false", e)
            }

            try {
                val pairedEvt = JSONObject().put("event", PluginEvent.IsPairedChanged.value).put("value", false)
                PluginEventBridge.dispatch(pairedEvt)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dispatch isPairedChanged=false", e)
            }

            lastIsConnected = false
            lastIsPaired = false
        } catch (e: Exception) {
            Log.w(TAG, "Error while preparing disconnect events", e)
        }

        // Unregister OS listeners and clear init state
        try {
            unregisterListeners()
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering listeners during onDestroy", e)
        }
        initialized = false
        instance = null
        Log.i(TAG, "WearableForegroundService destroyed and listeners unregistered")
    }

    // WorkManager periodic heartbeat configuration
    private val heartbeatIntervalMinutes = HEARTBEAT_INTERVAL_MINUTES // WorkManager minimum periodic interval is 15 minutes

    private fun startHeartbeatSchedule() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<WearableHeartbeatWorker>(heartbeatIntervalMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                HEARTBEAT_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.i(TAG, "Enqueued WorkManager periodic heartbeat every ${heartbeatIntervalMinutes} minutes")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enqueue heartbeat worker", e)
        }
    }

    private fun stopHeartbeatSchedule() {
        try {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(HEARTBEAT_WORK_NAME)
            Log.i(TAG, "Canceled WorkManager periodic heartbeat")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel heartbeat worker", e)
        }
    }

    // High-frequency (3s) heartbeat while service is running
    private fun startHeartbeatTicker() {
        try {
            if (heartbeatScheduler != null && !heartbeatScheduler!!.isShutdown) return
            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor()
            heartbeatScheduler?.scheduleAtFixedRate({
                try {
                    sendHeartbeatTick()
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat tick failed", e)
                }
            }, 0, HEARTBEAT_TICK_SECONDS, TimeUnit.SECONDS)
            Log.i(TAG, "Started ${HEARTBEAT_TICK_SECONDS}s heartbeat ticker")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start heartbeat ticker", e)
        }
    }

    private fun stopHeartbeatTicker() {
        try {
            heartbeatScheduler?.shutdownNow()
            heartbeatScheduler = null
            Log.i(TAG, "Stopped heartbeat ticker")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop heartbeat ticker", e)
        }
    }

    private fun sendHeartbeatTick() {
        // Use blocking Tasks.await on a background scheduler thread with a short timeout
        try {
            val nodes = try {
                Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes, AWAIT_NODES_TIMEOUT_SECS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.v(TAG, "Heartbeat tick: no nodes or query failed (${e.message})")
                null
            }
            val pairedNow = !nodes.isNullOrEmpty()
            Log.v(TAG, "Heartbeat tick: pairedNow=$pairedNow nodes=${nodes?.map { it.id }}")
            val connectedNow = isWatchAppConnectedNow()
            Log.v(TAG, "Heartbeat tick: connectedNow=$connectedNow (lastWatchHeartbeatAt=$lastWatchHeartbeatAt)")

            // Emit events when pairing/connectivity changes or first observed
            fun dispatchIfChanged(key: String, prev: Boolean?, now: Boolean) {
                if (prev == null || prev != now) {
                    val evt = JSONObject()
                        .put("event", key)
                        .put("value", now)
                    Log.i(TAG, "Dispatching $key change -> $now")
                    PluginEventBridge.dispatch(evt)
                }
            }

            dispatchIfChanged(PluginEvent.IsPairedChanged.value, lastIsPaired, pairedNow)
            dispatchIfChanged(PluginEvent.IsConnectedChanged.value, lastIsConnected, connectedNow)
            lastIsPaired = pairedNow
            lastIsConnected = connectedNow

            if (nodes.isNullOrEmpty()) return
            val heartbeat = JSONObject().put(KEY_HEARTBEAT, VALUE_HEARTBEAT_PHONE).toString()
            for (n in nodes) {
                try {
                    Wearable.getMessageClient(applicationContext)
                        .sendMessage(n.id, configuredPath(), heartbeat.toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.v(TAG, "Heartbeat tick: failed to send to ${'$'}{n.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendHeartbeatTick error", e)
        }
    }

    private fun registerListeners() {
        try {
            Log.i(TAG, "Registering MessageClient, DataClient, and CapabilityClient listeners")
            Wearable.getMessageClient(applicationContext).addListener(this)
            Wearable.getDataClient(applicationContext).addListener(this)
            
            // Register for capability changes with the configured capability
            val capability = cordova.plugin.wearable.Wearable.capability
            Log.i(TAG, "Registering CapabilityClient listener for capability: $capability")
            Wearable.getCapabilityClient(applicationContext).addListener(this, capability)
            
            // Get capability info for diagnostics
            try {
                Log.i(TAG, "Checking capability nodes for: $capability")
                Wearable.getCapabilityClient(applicationContext)
                    .getCapability(capability, CapabilityClient.FILTER_ALL)
                    .addOnSuccessListener { capInfo ->
                        val nodes = capInfo.nodes
                        Log.i(TAG, "Capability nodes (FILTER_ALL): ${nodes.map { "${it.id}:${it.displayName}" }}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get capability info", e)
                    }
                
                // Also check reachable nodes
                Wearable.getCapabilityClient(applicationContext)
                    .getCapability(capability, CapabilityClient.FILTER_REACHABLE)
                    .addOnSuccessListener { capInfo ->
                        val nodes = capInfo.nodes
                        Log.i(TAG, "Capability nodes (FILTER_REACHABLE): ${nodes.map { "${it.id}:${it.displayName}" }}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking capability nodes", e)
            }
            
            // Check directly connected nodes)
            try {
                Wearable.getNodeClient(applicationContext).connectedNodes
                    .addOnSuccessListener { nodes ->
                        Log.i(TAG, "Connected nodes: ${nodes.map { "${it.id}:${it.displayName}" }}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get connected nodes", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connected nodes", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register wearable listeners", e)
        }
    }

    private fun unregisterListeners() {
        try {
            Wearable.getMessageClient(applicationContext).removeListener(this)
            Wearable.getDataClient(applicationContext).removeListener(this)
            Wearable.getCapabilityClient(applicationContext).removeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister wearable listeners", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Wearable", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Use NotificationCompat for forward- and backward-compatibility
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        val title = if (cordova.plugin.wearable.Wearable.notificationTitle.isNotEmpty()) cordova.plugin.wearable.Wearable.notificationTitle else NOTIFICATION_TITLE_DEFAULT
        val text = if (cordova.plugin.wearable.Wearable.notificationText.isNotEmpty()) cordova.plugin.wearable.Wearable.notificationText else NOTIFICATION_TEXT_DEFAULT
        builder.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }

    // MessageClient.OnMessageReceivedListener
    override fun onMessageReceived(messageEvent: MessageEvent) {
        try {
            val path = messageEvent.path
            val sourceId = messageEvent.sourceNodeId
            
            Log.i(TAG, "onMessageReceived: path=$path from node=$sourceId")
            
            if (messageEvent.data.isNotEmpty()) {
                val data = String(messageEvent.data, Charsets.UTF_8)
                Log.i(TAG, "Message data: $data")
                
                // Try parse JSON and handle message
                try {
                    val parsed = JSONObject(data)
                    // If this is a heartbeat message from the watch ({"heartbeat":"watch"})
                    try {
                        if (parsed.has(KEY_HEARTBEAT) && parsed.optString(KEY_HEARTBEAT) == VALUE_HEARTBEAT_WATCH) {
                            // Mark last time we saw a heartbeat from the watch app
                            lastWatchHeartbeatAt = System.currentTimeMillis()
                            Log.i(TAG, "Received heartbeat from node $sourceId at $lastWatchHeartbeatAt")
                            // We don't need to forward heartbeat messages to plugin layer
                        } else {
                            // Forward to plugin layer via ListenerBridge
                            Log.i(TAG, "Forwarding message to ListenerBridge: $parsed")
                            ListenerBridge.dispatch(parsed)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error handling connectivity request", e)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse message as JSON, treating as plain text", e)
                    // Not valid JSON: deliver as wrapped plain-text message
                    val wrapped = JSONObject()
                    wrapped.put("message", data)
                    Log.i(TAG, "Forwarding wrapped message to ListenerBridge: $wrapped")
                    ListenerBridge.dispatch(wrapped)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onMessageReceived error", e)
        }
    }

    // DataClient.OnDataChangedListener
    override fun onDataChanged(p0: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: ${p0.count} events")
        try {
            // Forward to StateSync if initialized
            val appCtx = applicationContext
            // Lazy-init a StateSync using same store as plugin
            val store = StateStore(appCtx)
            val sync = StateSync(appCtx, store)
            sync.onDataChanged(p0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process DataEvents in StateSync", e)
        }
    }

    // CapabilityClient.OnCapabilityChangedListener
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val nodes = capabilityInfo.nodes
        val nodeDetails = nodes.map { "${it.id}:${it.displayName}" }
        Log.i(TAG, "onCapabilityChanged: ${capabilityInfo.name} nodes=$nodeDetails")
        
        // Map capability changes to a reachability-style event similar to iOS
        val isReachable = nodes.isNotEmpty()
        val payload = JSONObject()
        payload.put("event", PluginEvent.IsReachableChanged.value)
        payload.put("capability", capabilityInfo.name)
        payload.put("isReachable", isReachable)
        val nodeIds = nodes.map { it.id }
        payload.put("nodes", nodeIds)
        
        Log.i(TAG, "Dispatching capability change event: $payload")
        // Route plugin-generated events via PluginEventBridge
        PluginEventBridge.dispatch(payload)
        
        // Do not send ad-hoc heartbeats here; periodic heartbeats are handled by WearableHeartbeatWorker.
    }

    private fun configuredPath(): String {
        val p = cordova.plugin.wearable.Wearable.path
        return if (p.isNotEmpty()) p else DEFAULT_MESSAGE_PATH
    }
}
