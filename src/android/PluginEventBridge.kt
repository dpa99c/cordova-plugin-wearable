package cordova.plugin.wearable

import org.json.JSONObject

/**
 * Bridge for plugin-generated events (connectivity, pairing, reachability, etc.).
 * Keeps a separate channel from user messages to avoid mixing concerns.
 */
object PluginEventBridge {
    private const val TAG = "PluginEventBridge"

    var callback: ((data: JSONObject) -> Unit)? = null

    private val queue = mutableListOf<JSONObject>()
    private const val MAX_QUEUE = 50

    /**
     * Register the plugin event callback and replay any queued events.
     *
     * @param cb Callback invoked for queued and future events.
     */
    fun register(cb: ((data: JSONObject) -> Unit)) {
        Logger.info(TAG, "Event callback registered")
        callback = cb
        if (queue.isNotEmpty()) {
            val copy = ArrayList(queue)
            queue.clear()
            copy.forEach { evt ->
                try { cb(evt) } catch (e: Exception) { Logger.error(TAG, "Error dispatching queued event", e) }
            }
        }
    }

    /**
     * Dispatch an event to the registered callback, queueing when unavailable.
     *
     * @param evt Event payload to forward.
     */
    fun dispatch(evt: JSONObject) {
        val cb = callback
        if (cb != null) {
            try { cb(evt) } catch (e: Exception) { Logger.error(TAG, "Error dispatching event", e) }
        } else {
            if (queue.size < MAX_QUEUE) {
                queue.add(evt)
            } else {
                Logger.warn(TAG, "Event queue full; dropping event: $evt")
            }
        }
    }
}
