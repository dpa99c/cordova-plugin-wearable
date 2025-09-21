package cordova.plugin.wearable

import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.gms.wearable.Wearable
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Primary Cordova entry point that brokers Wear OS connectivity calls between JS and native layers.
 */
class Wearable : CordovaPlugin() {

    val implementation = WearableImpl()
    private var stateStore: StateStore? = null
    private var stateSync: StateSync? = null

    companion object {
        private val TAG = Wearable::class.simpleName ?: "Wearable"
        private const val PERMISSION_FOREGROUND_CONNECTED_DEVICE = "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"
        private const val ERROR_NOT_SUPPORTED = "Wearable API is not supported on this device"
        private const val ERROR_NOT_PAIRED = "No paired WearOS device found"
        private const val ERROR_NOT_CONNECTED = "No connected WearOS device found"
        private const val ERROR_EMPTY_PATH = "Path is empty, please call configure first"
        private const val ERROR_EMPTY_CAPABILITY = "Capability is empty, please call configure first"
        private const val ERROR_EMPTY_DATA = "Data is empty"
        var capability: String = ""
        var path: String = ""
        var enableDebugLogging: Boolean = false
        // Foreground notification configuration (Android only)
        var notificationTitle: String = ""
        var notificationText: String = ""
        private const val REQUEST_FOREGROUND_CONNECTED_PERMS = 0xBEEF
        @Volatile var configuredOnce: Boolean = false

        enum class PluginEvent(val value: String) {
            IsConnectedChanged("isConnectedChanged"),
            IsPairedChanged("isPairedChanged"),
            IsReachableChanged("isReachableChanged"),
            StateChanged("stateChanged")
        }

        fun sendPluginSuccess(
            callbackContext: CallbackContext,
            keepCallback: Boolean = false
        ) {
            Logger.debug(TAG, "sendPluginSuccess: no data")
            val pluginResult = PluginResult(PluginResult.Status.OK)
            pluginResult.keepCallback = keepCallback
            callbackContext.sendPluginResult(pluginResult)
        }

        fun sendPluginSuccess(
            callbackContext: CallbackContext,
            result: JSONObject,
            keepCallback: Boolean = false
        ) {
            Logger.debug(TAG, "sendPluginSuccess: $result")
            val pluginResult = PluginResult(PluginResult.Status.OK, result)
            pluginResult.keepCallback = keepCallback
            callbackContext.sendPluginResult(pluginResult)
        }

        fun sendPluginError(
            callbackContext: CallbackContext,
            error: String,
            keepCallback: Boolean = false
        ) {
            Logger.error(TAG, "sendPluginError: $error")
            val pluginResult = PluginResult(PluginResult.Status.ERROR, error)
            pluginResult.keepCallback = keepCallback
            callbackContext.sendPluginResult(pluginResult)
        }
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        try {
            Logger.debug(TAG, "execute action: $action args: $args")
            when (action) {
                "configure" -> {
                    configure(callbackContext, args)
                }
                "startConnectivity" -> {
                    startConnectivity(callbackContext)
                }
                "stopConnectivity" -> {
                    stopConnectivity(callbackContext)
                }
                "registerMessageListener" -> {
                    registerMessageListener(callbackContext)
                }
                "registerEventListener" -> {
                    registerEventListener(callbackContext)
                }
                "isSupported" -> {
                    isSupported(callbackContext)
                }
                "isPaired" -> {
                    isPaired(callbackContext)
                }
                "isConnected" -> {
                    isConnected(callbackContext)
                }
                "sendMessageToWatch" -> {
                    sendMessageToWatch(callbackContext, args)
                }
                // Shared state actions
                "registerStateListener" -> registerStateListener(callbackContext)
                "unregisterStateListener" -> unregisterStateListener(callbackContext)
                "setState" -> setState(callbackContext, args)
                "removeState" -> removeState(callbackContext, args)
                "getState" -> getState(callbackContext, args)
                "hasState" -> hasState(callbackContext, args)
                "setStatePath" -> setStatePath(callbackContext, args)
                "removeStatePath" -> removeStatePath(callbackContext, args)
                "getAllState" -> getAllState(callbackContext)
                else -> {
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "Unknown error")
        }
        return false
    }

    /**********************
     * Plugin API functions
     ************************/

    private fun configure(
        callbackContext: CallbackContext,
        args: JSONArray
    ) {
        if (configuredOnce) {
            sendPluginError(callbackContext, "Wearable.configure() has already been called; multiple configuration is not supported")
            return
        }
        // Check device support before attempting configuration
        val activity = cordova.activity as androidx.appcompat.app.AppCompatActivity
        if (!implementation.isSupported(activity)) {
            sendPluginError(callbackContext, ERROR_NOT_SUPPORTED)
            return
        }
        // Expect a single options object
        if (args.length() == 0 || args.isNull(0)) {
            sendPluginError(callbackContext, "Missing options object for configure()")
            return
        }
        val opts = args.optJSONObject(0) ?: run {
            sendPluginError(callbackContext, "configure(options): options must be an object")
            return
        }

        // Android-required options
        val thisCapability = opts.optString("capability", "")
        if (thisCapability.isEmpty()) {
            sendPluginError(callbackContext, "configure(options): 'capability' is required on Android")
            return
        }
        capability = thisCapability

        val thisPath = opts.optString("path", "")
        if (thisPath.isEmpty()) {
            sendPluginError(callbackContext, "configure(options): 'path' is required on Android")
            return
        }
        path = thisPath

        // Cross-platform options
        if (opts.has("enableLogging")) {
            enableDebugLogging = opts.optBoolean("enableLogging", false)
        } else if (opts.has("enableDebugLogging")) {
            // Accept legacy key name if present in options
            enableDebugLogging = opts.optBoolean("enableDebugLogging", false)
        }
        // Android-only notification fields
        if (opts.has("notificationTitle")) notificationTitle = opts.optString("notificationTitle", "")
        if (opts.has("notificationText")) notificationText = opts.optString("notificationText", "")

        // Configuration only; connectivity is started via startConnectivity()

        configuredOnce = true
        // Initialize persistent StateStore early so registerStateListener can return
        // the materialized state immediately (even before startConnectivity is called).
        try {
            val appCtx = cordova.activity.applicationContext
            if (stateStore == null) stateStore = StateStore(appCtx)
            Logger.info(TAG, "Initialized StateStore during configure")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to initialize StateStore during configure", e)
        }

        sendPluginSuccess(callbackContext)
    }

    private fun startConnectivity(callbackContext: CallbackContext) {
        try {
            val act = cordova.activity as androidx.appcompat.app.AppCompatActivity

            if (!implementation.isSupported(act)) {
                sendPluginError(callbackContext, ERROR_NOT_SUPPORTED)
                return
            }

            Logger.info(TAG, "Starting connectivity with capability '$capability' and path '$path'")

            // Advertise the configured capability on the phone so the watch can discover this app.
            try {
                // Check existing capabilities first so we don't attempt to add duplicates
                Wearable.getCapabilityClient(act)
                    .getAllCapabilities(com.google.android.gms.wearable.CapabilityClient.FILTER_ALL)
                    .addOnSuccessListener { caps ->
                        val keys = caps.keys
                        Logger.info(TAG, "startConnectivity - getAllCapabilities keys: $keys")
                        if (keys.contains(capability)) {
                            Logger.info(TAG, "Capability $capability already present, skipping addLocalCapability")
                        } else {
                            Wearable.getCapabilityClient(act)
                                .addLocalCapability(capability)
                                .addOnSuccessListener { Logger.info(TAG, "Added local capability: ${capability}") }
                                .addOnFailureListener { ex ->
                                    if (ex is com.google.android.gms.common.api.ApiException && ex.statusCode == 4006) {
                                        Logger.info(TAG, "addLocalCapability returned DUPLICATE_CAPABILITY for $capability — ignoring")
                                    } else {
                                        Logger.error(TAG, "Failed to add local capability: ${ex.message}", ex)
                                    }
                                }
                        }
                    }
                    .addOnFailureListener { ex ->
                        Logger.error(TAG, "startConnectivity - getAllCapabilities failed: ${ex.message}", ex)
                    }
            } catch (e: Exception) {
                Logger.error(TAG, "Exception while advertising capability", e)
            }

            // Start foreground service to keep listeners active while backgrounded
            try {
                val intent = android.content.Intent(act.applicationContext, WearableForegroundService::class.java)
                intent.action = WearableForegroundService.ACTION_START

                // Check runtime permissions required by connectedDevice foreground services
                val hasForegroundConnected = ContextCompat.checkSelfPermission(act, PERMISSION_FOREGROUND_CONNECTED_DEVICE) == PackageManager.PERMISSION_GRANTED
                val hasBluetoothConnect = ContextCompat.checkSelfPermission(act, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

                if (!hasForegroundConnected || !hasBluetoothConnect) {
                    // Request the missing permissions; the plugin Activity will receive the callback
                    val missing = mutableListOf<String>()
                    if (!hasForegroundConnected) missing.add(PERMISSION_FOREGROUND_CONNECTED_DEVICE)
                    if (!hasBluetoothConnect) missing.add(Manifest.permission.BLUETOOTH_CONNECT)
                    Logger.info(TAG, "Requesting missing permissions: $missing")
                    ActivityCompat.requestPermissions(act, missing.toTypedArray(), REQUEST_FOREGROUND_CONNECTED_PERMS)
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        act.applicationContext.startForegroundService(intent)
                    } else {
                        act.applicationContext.startService(intent)
                    }
                    Logger.info(TAG, "Started WearableForegroundService")
                    // Service will init upon receiving ACTION_START
                }
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to start WearableForegroundService", e)
            }

            // Initialize state store/sync once path is known
            try {
                val appCtx = act.applicationContext
                if (stateStore == null) stateStore = StateStore(appCtx)
                if (stateSync == null) stateSync = StateSync(appCtx, stateStore!!)
                stateSync?.start()
                Logger.info(TAG, "StateSync started with base path: ${path}${StateSyncSpec.STATE_SEGMENT}")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to start StateSync", e)
            }

            sendPluginSuccess(callbackContext)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "startConnectivity error")
        }
    }

    private fun stopConnectivity(callbackContext: CallbackContext) {
        try {
            val act = cordova.activity as androidx.appcompat.app.AppCompatActivity

            Logger.info(TAG, "Stopping connectivity with capability '$capability' and path '$path'")

            // Remove advertised capability (best-effort)
            try {
                if (capability.isNotEmpty()) {
                    Wearable.getCapabilityClient(act)
                        .removeLocalCapability(capability)
                        .addOnSuccessListener { Logger.info(TAG, "Removed local capability: ${capability}") }
                        .addOnFailureListener { ex ->
                            // If capability was unknown (already removed), treat as informational — don't spam errors
                            if (ex is com.google.android.gms.common.api.ApiException && ex.statusCode == 4007) {
                                Logger.info(TAG, "removeLocalCapability returned UNKNOWN_CAPABILITY for $capability — treating as removed")
                            } else {
                                Logger.error(TAG, "Failed to remove local capability: ${ex.message}", ex)
                            }
                        }
                }
            } catch (e: Exception) {
                Logger.error(TAG, "Exception removing capability during stop", e)
            }

            // Stop foreground service if running
            try {
                val intent = android.content.Intent(act.applicationContext, WearableForegroundService::class.java)
                intent.action = WearableForegroundService.ACTION_STOP
                act.applicationContext.startService(intent)
                Logger.info(TAG, "Requested WearableForegroundService stop")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to stop WearableForegroundService", e)
            }

            // Stop state sync and prevent further state sends from the plugin layer
            try {
                stateSync?.stop()
                stateSync = null
                stateStore = null
            } catch (_: Exception) {}

            // If the foreground service instance isn't running (or stop failed silently), dispatch an explicit
            // isConnectedChanged=false event so JS listeners always observe the stopped connectivity state.
            try {
                val evt = JSONObject()
                evt.put("event", PluginEvent.IsConnectedChanged.value)
                evt.put("value", false)
                PluginEventBridge.dispatch(evt)
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to dispatch isConnectedChanged during stopConnectivity", e)
            }

            sendPluginSuccess(callbackContext)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "stopConnectivity error")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove advertised capability when the plugin is destroyed (best-effort)
        try {
            val activity = cordova.activity as androidx.appcompat.app.AppCompatActivity
            if (capability.isNotEmpty()) {
                Wearable.getCapabilityClient(activity)
                    .removeLocalCapability(capability)
                    .addOnSuccessListener {
                        Logger.info(TAG, "Removed local capability: ${capability}")
                    }
                    .addOnFailureListener { ex ->
                        if (ex is com.google.android.gms.common.api.ApiException && ex.statusCode == 4007) {
                            Logger.info(
                                TAG,
                                "removeLocalCapability returned UNKNOWN_CAPABILITY for $capability during onDestroy — ignoring"
                            )
                        } else {
                            Logger.error(
                                TAG,
                                "Failed to remove local capability: ${ex.message}",
                                ex
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Exception in onDestroy removing capability", e)
        }

        // Stop foreground service if running
        try {
            val activity = cordova.activity as androidx.appcompat.app.AppCompatActivity
            val intent = android.content.Intent(
                activity.applicationContext,
                WearableForegroundService::class.java
            )
            activity.applicationContext.stopService(intent)
            Logger.info(TAG, "Stopped WearableForegroundService")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to stop WearableForegroundService", e)
        }

        // Stop and clear state sync/store on plugin destroy
        try {
            stateSync?.stop()
            stateSync = null
            stateStore = null
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to stop/clear state sync on destroy", e)
        }
    }

    override fun onResume(multitasking: Boolean) {
        super.onResume(multitasking)
        try {
            Logger.debug(TAG, "onResume - requesting state sync snapshot")
            stateSync?.sendSnapshot()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to send snapshot on resume", e)
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_FOREGROUND_CONNECTED_PERMS) {
            var allGranted = true
            for (res in grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            try {
                val activity = cordova.activity as androidx.appcompat.app.AppCompatActivity
                if (allGranted) {
                    val intent = android.content.Intent(activity.applicationContext, WearableForegroundService::class.java)
                    intent.action = WearableForegroundService.ACTION_START
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        activity.applicationContext.startForegroundService(intent)
                    } else {
                        activity.applicationContext.startService(intent)
                    }
                    Logger.info(TAG, "Permissions granted; started WearableForegroundService")
                } else {
                    Logger.error(TAG, "Required permissions for connectedDevice foreground service were denied: ${permissions.joinToString()}")
                }
            } catch (e: Exception) {
                Logger.error(TAG, "Error handling permission result", e)
            }
        }
    }


    private fun registerMessageListener(
        callbackContext: CallbackContext
    ) {
        val activity = cordova.activity as androidx.appcompat.app.AppCompatActivity
        if (!implementation.isSupported(activity)) {
            sendPluginError(callbackContext, ERROR_NOT_SUPPORTED)
            return
        }
        implementation.registerMessageListener(callbackContext)
        sendPluginNoResult(callbackContext)
    }

    private fun registerEventListener(
        callbackContext: CallbackContext
    ) {
        val activity = cordova.activity as androidx.appcompat.app.AppCompatActivity
        if (!implementation.isSupported(activity)) {
            sendPluginError(callbackContext, ERROR_NOT_SUPPORTED)
            return
        }
        implementation.registerEventListener(callbackContext)
        sendPluginNoResult(callbackContext)
    }

    // =========================
    // Shared state exec handlers
    // =========================
    private fun registerStateListener(callbackContext: CallbackContext) {
        // Immediately send current materialized state and keep callback
        try {
            val payload = JSONObject()
            payload.put("event", PluginEvent.StateChanged.value)
            val state = stateStore?.materialize() ?: JSONObject()
            payload.put("state", state)
            Logger.info(TAG, "registerStateListener - sending initial materialized state: $payload")
            val pluginResult = PluginResult(PluginResult.Status.OK, payload)
            pluginResult.keepCallback = true
            callbackContext.sendPluginResult(pluginResult)

            // Also register the ListenerBridge to deliver subsequent updates specifically to state listener
            ListenerBridge.registerStateListener { data ->
                try {
                    val pr = PluginResult(PluginResult.Status.OK, data)
                    pr.keepCallback = true
                    callbackContext.sendPluginResult(pr)
                } catch (e: Exception) {
                    Logger.error(TAG, "Error forwarding state event", e)
                }
            }
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "registerStateListener error")
        }
    }

    private fun unregisterStateListener(callbackContext: CallbackContext) {
        try {
            ListenerBridge.unregisterStateListener()
            sendPluginSuccess(callbackContext)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "unregisterStateListener error")
        }
    }

    private fun setState(callbackContext: CallbackContext, args: JSONArray) {
        try {
            val key = args.getString(0)
            val value = args.opt(1)
            if (stateSync != null) {
                stateSync?.setPath("/${key}", value)
            } else {
                // No active StateSync (connectivity not started). Persist locally to StateStore
                try {
                    val path = "/${key}"
                    if (stateStore == null) stateStore = StateStore(cordova.activity.applicationContext)
                    val v = stateStore!!.nextLocalVersion(path)
                    val entry = StateEntry(path, false, v, value)
                    val changed = stateStore!!.applyEntry(entry)
                    if (changed) {
                        // Notify any registered listeners about the updated materialized state
                        try {
                            val payload = JSONObject()
                            payload.put("event", PluginEvent.StateChanged.value)
                            payload.put("state", stateStore!!.materialize())
                            ListenerBridge.dispatch(payload)
                        } catch (e: Exception) {
                            Logger.error(TAG, "Failed to dispatch local state change", e)
                        }
                    }
                } catch (e: Exception) {
                    Logger.error(TAG, "Failed to persist state locally", e)
                }
            }
            sendPluginSuccess(callbackContext)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "setState error")
        }
    }

    private fun removeState(callbackContext: CallbackContext, args: JSONArray) {
        try {
            val key = args.getString(0)
            if (stateSync != null) {
                stateSync?.removePath("/${key}")
            } else {
                // Persist tombstone locally
                try {
                    val path = "/${key}"
                    if (stateStore == null) stateStore = StateStore(cordova.activity.applicationContext)
                    val v = stateStore!!.nextLocalVersion(path)
                    val entry = StateEntry(path, true, v, null)
                    val changed = stateStore!!.applyEntry(entry)
                    if (changed) {
                        try {
                            val payload = JSONObject()
                            payload.put("event", PluginEvent.StateChanged.value)
                            payload.put("state", stateStore!!.materialize())
                            ListenerBridge.dispatch(payload)
                        } catch (e: Exception) {
                            Logger.error(TAG, "Failed to dispatch local tombstone change", e)
                        }
                    }
                } catch (e: Exception) {
                    Logger.error(TAG, "Failed to persist tombstone locally", e)
                }
            }
            sendPluginSuccess(callbackContext)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "removeState error")
        }
    }

    private fun getState(callbackContext: CallbackContext, args: JSONArray) {
        try {
            val key = args.getString(0)
            val mat = stateStore?.materialize() ?: JSONObject()
            val result = JSONObject()
            if (mat.has(key)) {
                result.put("exists", true)
                result.put("value", mat.get(key))
            } else {
                result.put("exists", false)
            }
            sendPluginSuccess(callbackContext, result)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "getState error")
        }
    }

    private fun hasState(callbackContext: CallbackContext, args: JSONArray) {
        try {
            val key = args.getString(0)
            val mat = stateStore?.materialize() ?: JSONObject()
            val result = JSONObject().put("has", mat.has(key))
            sendPluginSuccess(callbackContext, result)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "hasState error")
        }
    }

    private fun setStatePath(callbackContext: CallbackContext, args: JSONArray) {
        try {
            val pointer = args.getString(0)
            val value = args.opt(1)
            stateSync?.setPath(pointer, value)
            sendPluginSuccess(callbackContext)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "setStatePath error")
        }
    }

    private fun removeStatePath(callbackContext: CallbackContext, args: JSONArray) {
        try {
            val pointer = args.getString(0)
            stateSync?.removePath(pointer)
            sendPluginSuccess(callbackContext)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "removeStatePath error")
        }
    }

    private fun getAllState(callbackContext: CallbackContext) {
        try {
            val state = stateStore?.materialize() ?: JSONObject()
            sendPluginSuccess(callbackContext, state)
        } catch (e: Exception) {
            sendPluginError(callbackContext, e.message ?: "getAllState error")
        }
    }

    private fun isSupported(callbackContext: CallbackContext) {
        // run on background thread because WatchConnect uses Tasks.await
        Thread {
            try {
                val supported = implementation.isSupported(cordova.activity as androidx.appcompat.app.AppCompatActivity)
                val result = JSONObject()
                result.put("isSupported", supported)
                sendPluginSuccess(callbackContext, result)
            } catch (e: Exception) {
                sendPluginError(callbackContext, e.message ?: "Unknown error")
            }
        }.start()
    }

    private fun isPaired(callbackContext: CallbackContext) {
        Thread {
            try {
                val paired = implementation.isPaired(cordova.activity as androidx.appcompat.app.AppCompatActivity)
                val result = JSONObject()
                result.put("isPaired", paired)
                sendPluginSuccess(callbackContext, result)
            } catch (e: Exception) {
                sendPluginError(callbackContext, e.message ?: "Unknown error")
            }
        }.start()
    }


    private fun isConnected(callbackContext: CallbackContext) {
        Thread {
            try {
                val connected = implementation.isConnected(cordova.activity as androidx.appcompat.app.AppCompatActivity)
                val result = JSONObject()
                result.put("isConnected", connected)
                sendPluginSuccess(callbackContext, result)
            } catch (e: Exception) {
                sendPluginError(callbackContext, e.message ?: "Unknown error")
            }
        }.start()
    }

    private fun sendMessageToWatch(
        callbackContext: CallbackContext,
        args: JSONArray
    ) {
        val activity = cordova.activity as androidx.appcompat.app.AppCompatActivity

        if (!implementation.isSupported(activity)) {
            sendPluginError(callbackContext, ERROR_NOT_SUPPORTED)
            return
        }

        if (!implementation.isPaired(activity)) {
            sendPluginError(callbackContext, ERROR_NOT_PAIRED)
            return
        }

        if (!implementation.isConnected(activity)) {
            sendPluginError(callbackContext, ERROR_NOT_CONNECTED)
            return
        }

        // No reliable API exists to detect remote watch app installation on Wear OS.
        // We rely on connectivity/capability semantics instead.

        if(path.isEmpty()) {
            sendPluginError(callbackContext, ERROR_EMPTY_PATH)
            return
        }
        if(capability.isEmpty()) {
            sendPluginError(callbackContext, ERROR_EMPTY_CAPABILITY)
            return
        }

        if(args.isNull(0)) {
            sendPluginError(callbackContext, ERROR_EMPTY_DATA)
            return
        }
        val data = args.getJSONObject(0)


        implementation.sendMessageToWatch(cordova.activity as androidx.appcompat.app.AppCompatActivity, path, data.toString())
        sendPluginSuccess(callbackContext, false)
    }


    /********************
     * Internal functions
     *******************/
    private fun sendPluginNoResult(
        callbackContext: CallbackContext
    ) {
        // Keep callback registered so subsequent native events can be delivered asynchronously.
        val pluginResult = PluginResult(PluginResult.Status.NO_RESULT)
        pluginResult.keepCallback = true
        callbackContext.sendPluginResult(pluginResult)
    }
}
