package cordova.plugin.wearable

import android.util.Log

class Logger {
    companion object {
        fun error(tag: String, message: String, throwable: Throwable? = null) {
            if(!WearablePlugin.enableDebugLogging) {
                return
            }
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }

        fun warn(tag: String, message: String) {
            if(!WearablePlugin.enableDebugLogging) {
                return
            }
            Log.w(tag, message)
        }

        fun info(tag: String, message: String) {
            if(!WearablePlugin.enableDebugLogging) {
                return
            }
            Log.i(tag, message)
        }

        fun debug(tag: String, message: String) {
            if(!WearablePlugin.enableDebugLogging) {
                return
            }
            Log.d(tag, message)
        }

        fun verbose(tag: String, message: String) {
            if(!WearablePlugin.enableDebugLogging) {
                return
            }
            Log.v(tag, message)
        }
    }
}