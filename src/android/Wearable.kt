package cordova.plugin.wearable

import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject



class Wearable {

    companion object {
        // watch connect object
        val watchConnect = WatchConnect()
        // watch listener object
        val watchListener = WatchListener()
        var listenerCallback: CallbackContext? = null

    }


    /**
     * Set up the routing for the watch request listner
     */
    fun registerListener(callback: CallbackContext): Boolean {
        listenerCallback = callback
        watchListener.registerListener(::listenerCallback)
        return true
    }

    /**
     * The update callback for the watch listener
     */
    fun listenerCallback(data: JSObject): Unit {
        if (listenerCallback != null) {
                val ret = JSObject()
                ret.put("data", data)
                listenerCallback?.resolve(ret)
            }
    }

    fun sendDataToWatch(activity: AppCompatActivity, path: String, data: String) {
        watchConnect.sendMessage(activity, path, data)
    }
}