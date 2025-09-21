package cordova.plugin.wearable


import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject

class WearablePlugin : CordovaPlugin() {

    val implementation = Wearable()

    companion object {
        var capability: String = ""
        var path: String = ""
        var enableDebugLogging: Boolean = false

        fun sendPluginResult(
            callbackContext: CallbackContext,
            keepCallback: Boolean = false
        ) {
            val pluginResult = PluginResult(PluginResult.Status.OK)
            pluginResult.keepCallback = keepCallback
            callbackContext.sendPluginResult(pluginResult)
        }

        fun sendPluginResult(
            callbackContext: CallbackContext,
            result: Int,
            keepCallback: Boolean = false
        ) {
            val pluginResult = PluginResult(PluginResult.Status.OK, result)
            pluginResult.keepCallback = keepCallback
            callbackContext.sendPluginResult(pluginResult)
        }

        fun sendPluginResult(
            callbackContext: CallbackContext,
            result: JSONObject,
            keepCallback: Boolean = false
        ) {
            val pluginResult = PluginResult(PluginResult.Status.OK, result)
            pluginResult.keepCallback = keepCallback
            callbackContext.sendPluginResult(pluginResult)
        }

        fun sendPluginResult(
            callbackContext: CallbackContext,
            result: JSONArray,
            keepCallback: Boolean = false
        ) {
            val pluginResult = PluginResult(PluginResult.Status.OK, result)
            pluginResult.keepCallback = keepCallback
            callbackContext.sendPluginResult(pluginResult)
        }

        fun sendPluginError(
            callbackContext: CallbackContext,
            error: String,
            keepCallback: Boolean = false
        ) {
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
            when (action) {
                "configure" -> {
                    configure(callbackContext, args)
                }
                "registerListener" -> {
                    registerListener(callbackContext, args)
                }
                "sendDataToWatch" -> {
                    sendDataToWatch(callbackContext, args)
                }
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

    fun configure(
        callbackContext: CallbackContext,
        args: JSONArray
    ) {
        val capability = args.getString(0)
        if(capability.isEmpty()) {
            sendPluginError(callbackContext, "Capability is empty")
            return
        }
        WearablePlugin.capability = capability

        val path = args.getString(1)
        if(path.isEmpty()) {
            sendPluginError(callbackContext, "Path is empty")
            return
        }  
        WearablePlugin.path = path

        if(args.length() > 2) {
            WearablePlugin.enableDebugLogging = args.getBoolean(2)
        }

        sendPluginResult(callbackContext)
    }
    

    fun registerListener(
        callbackContext: CallbackContext,
        args: JSONArray
    ) {
        implementation.registerListener(callbackContext)
        sendEmptyPluginResult(callbackContext)
    }

    fun sendDataToWatch(
        callbackContext: CallbackContext,
        args: JSONArray
    ) {
        if(WearablePlugin.path.isEmpty()) {
            sendPluginError(callbackContext, "Path is empty, please call configure first")
            return
        }
        if(WearablePlugin.capability.isEmpty()) {
            sendPluginError(callbackContext, "Capability is empty, please call configure first")
            return
        }

        val data = args.getJSONObject(0)
        if(data.isEmpty()) {
            sendPluginError(callbackContext, "Data is empty")
            return
        }

        implementation.sendDataToWatch(cordova.activity as androidx.appcompat.app.AppCompatActivity, WearablePlugin.path, data.toString())
        sendEmptyPluginResult(callbackContext)
    }


    /********************
     * Internal functions
     *******************/
   fun sendEmptyPluginResult(
        callbackContext: CallbackContext
    ) {
        val pluginResult = PluginResult(PluginResult.Status.NO_RESULT)
        pluginResult.keepCallback = true
        callbackContext.sendPluginResult(pluginResult)
    }
}
