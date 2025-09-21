package cordova.plugin.wearable

import org.json.JSONObject

/**
 * Bridge to allow the native foreground service to forward JSON events
 * into the plugin layer. Includes message queueing if no callback is registered.
 */
object ListenerBridge {
    private const val TAG = "ListenerBridge"
    // Separate callbacks for generic messages vs state events
    var messageCallback: ((data: JSONObject) -> Unit)? = null
    var stateCallback: ((data: JSONObject) -> Unit)? = null

    // Queues for messages/state events received before JS layer registers
    private val messageQueue = mutableListOf<JSONObject>()
    private val stateQueue = mutableListOf<JSONObject>()
    private const val MAX_QUEUE_SIZE = 20

    /**
     * Register a message listener callback and flush any queued messages.
     *
     * @param cb Callback invoked for each queued or future message event.
     */
    fun registerMessageListener(cb: ((data: JSONObject) -> Unit)) {
        Logger.info(TAG, "Message callback registered with ListenerBridge")
        messageCallback = cb

        if (messageQueue.isNotEmpty()) {
            Logger.info(TAG, "Flushing ${messageQueue.size} queued messages to newly registered message callback")
            val queueCopy = ArrayList(messageQueue)
            messageQueue.clear()
            queueCopy.forEach { msg ->
                try {
                    cb(msg)
                } catch (e: Exception) {
                    Logger.error(TAG, "Error dispatching queued message", e)
                }
            }
        }
    }

    /**
     * Register a state listener callback and flush any queued state events.
     *
     * @param cb Callback invoked for each queued or future state event.
     */
    fun registerStateListener(cb: ((data: JSONObject) -> Unit)) {
        Logger.info(TAG, "State callback registered with ListenerBridge")
        stateCallback = cb

        if (stateQueue.isNotEmpty()) {
            Logger.info(TAG, "Flushing ${stateQueue.size} queued state events to newly registered state callback")
            val queueCopy = ArrayList(stateQueue)
            stateQueue.clear()
            queueCopy.forEach { evt ->
                try {
                    cb(evt)
                } catch (e: Exception) {
                    Logger.error(TAG, "Error dispatching queued state event", e)
                }
            }
        }
    }

    /** Remove the current state listener and clear queued events. */
    fun unregisterStateListener() {
        Logger.info(TAG, "State callback unregistered from ListenerBridge")
        stateCallback = null
        stateQueue.clear()
    }

    /**
     * Dispatch an incoming event to the appropriate listener. Routes state events to the
     * state listener and other payloads to the message listener, queuing when listeners are absent.
     *
     * @param data Event payload from the native layer.
     */
    fun dispatch(data: JSONObject) {
        try {
            // Route state events explicitly where possible.
            // - If payload.event == 'stateChanged' -> state listener
            // - Otherwise if payload has 'type' -> treat as generic message
            // - Otherwise if payload has 'state' -> fallback to state listener
            val eventField = data.optString(StateSyncSpec.JsonKeys.EVENT, "")
            val hasType = data.has(StateSyncSpec.JsonKeys.TYPE)
            val hasState = data.has(StateSyncSpec.JsonKeys.STATE)

            if (eventField == "stateChanged" || (!hasType && hasState)) {
                val cb = stateCallback
                if (cb != null) {
                    Logger.info(TAG, "Dispatching state event to state callback: $data")
                    try { cb(data) } catch (e: Exception) { Logger.error(TAG, "Error dispatching state event", e) }
                } else {
                    if (stateQueue.size < MAX_QUEUE_SIZE) {
                        Logger.info(TAG, "No state callback registered, queueing state event: $data")
                        stateQueue.add(data)
                    } else {
                        Logger.warn(TAG, "State queue full, dropping state event: $data")
                    }
                }
            } else {
                val cb = messageCallback
                if (cb != null) {
                    Logger.info(TAG, "Dispatching message to message callback: $data")
                    try { cb(data) } catch (e: Exception) { Logger.error(TAG, "Error dispatching message", e) }
                } else {
                    if (messageQueue.size < MAX_QUEUE_SIZE) {
                        Logger.info(TAG, "No message callback registered, queueing message: $data")
                        messageQueue.add(data)
                    } else {
                        Logger.warn(TAG, "Message queue full, dropping message: $data")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Error routing dispatch", e)
        }
    }
}
