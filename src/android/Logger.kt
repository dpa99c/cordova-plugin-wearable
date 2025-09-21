package cordova.plugin.wearable

import android.util.Log

/**
 * Simple logging wrapper that respects the plugin's `enableDebugLogging` flag.
 * Each method mirrors the Android `Log` levels and no-ops when logging is disabled.
 */
class Logger {
    companion object {
        /** Log an error message with optional throwable. */
        fun error(tag: String, message: String, throwable: Throwable? = null) {
            if(!Wearable.enableDebugLogging) {
                return
            }
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }

        /** Log a warning message. */
        fun warn(tag: String, message: String) {
            if(!Wearable.enableDebugLogging) {
                return
            }
            Log.w(tag, message)
        }

        /** Log an informational message. */
        fun info(tag: String, message: String) {
            if(!Wearable.enableDebugLogging) {
                return
            }
            Log.i(tag, message)
        }

        /** Log a debug message. */
        fun debug(tag: String, message: String) {
            if(!Wearable.enableDebugLogging) {
                return
            }
            Log.d(tag, message)
        }

        /** Log a verbose message. */
        fun verbose(tag: String, message: String) {
            if(!Wearable.enableDebugLogging) {
                return
            }
            Log.v(tag, message)
        }
    }
}