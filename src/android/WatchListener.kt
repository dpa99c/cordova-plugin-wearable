package cordova.plugin.wearable

import androidx.appcompat.app.AppCompatActivity
import com.getcapacitor.JSObject
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import org.json.JSONTokener


class WatchListener : WearableListenerService() {
    companion object {
        private val TAG = WatchListener::class.simpleName ?: "WatchListener"

        var listenerCallback: ((type: String, data: JSObject) -> Unit)? = null

        var appActivity: AppCompatActivity = AppCompatActivity()
    }

    fun setAppActivity(activity: AppCompatActivity) {
        appActivity = activity
    }


    fun registerListener(callback: ((type: String, data: JSObject) -> Unit)) {
        listenerCallback = callback
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        var data: JSObject? = null
        val path = messageEvent.path
        Logger.verbose(TAG, "onMessageReceived", hashMapOf(
            "path" to path,
            "data" to messageEvent.data.toString(Charsets.UTF_8),
        ))

        if (messageEvent.data.isNotEmpty()) {
            val messageData = JSONTokener(messageEvent.data.toString(Charsets.UTF_8)).nextValue() as JSONObject
            data = JSObject.fromJSONObject(messageData)
            listenerCallback?.let {
                it(path, data)
            }
        }
    }
}

