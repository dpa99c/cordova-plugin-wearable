/*
 Copyright (C) 2025 Dave Alden
 SPDX-License-Identifier: GPL-3.0-or-later

 This file is part of the cordova-plugin-wearable project and is
 licensed under the GNU General Public License v3.0 or later.
 See LICENSE at the repository root for full license text.
*/

package cordova.plugin.wearable

import androidx.appcompat.app.AppCompatActivity
import org.apache.cordova.CallbackContext
import org.json.JSONObject



class WearableImpl {

    companion object {
        private const val TAG = "WearableImpl"

        // watch connect object
        val watchConnect = WatchConnect()
        // bridge to allow the foreground service to forward events
        var listenerCallback: org.apache.cordova.CallbackContext? = null
        var eventCallback: org.apache.cordova.CallbackContext? = null

    }


    /**
     * Register the Cordova callback that should receive watch-to-phone messages.
     *
     * @param callback Cordova callback context from the JS layer.
     * @return `true` once the listener is registered.
     */
    fun registerMessageListener(callback: CallbackContext): Boolean {
        Logger.info(TAG, "Registering listener callback from Cordova")
        listenerCallback = callback
        // register bridge callback so the foreground service can forward events
        ListenerBridge.registerMessageListener(this::listenerCallback)
        return true
    }

    /**
     * Register the Cordova callback that should receive plugin event notifications (connectivity, pairing, etc.).
     *
     * @param callback Cordova callback context from the JS layer.
     * @return `true` once the listener is registered.
     */
    fun registerEventListener(callback: CallbackContext): Boolean {
        Logger.info(TAG, "Registering event callback from Cordova")
        eventCallback = callback
        PluginEventBridge.register(this::eventListenerCallback)
        return true
    }

    /**
     * Forward watch messages to the registered listener callback.
     *
     * @param data JSON payload from the Wear OS layer.
     */
    fun listenerCallback(data: JSONObject) {
        if (listenerCallback != null) {
            Wearable.sendPluginSuccess(listenerCallback!!, data, true)
        }
    }

    /**
     * Forward plugin events (connectivity, state changes) to the registered event callback.
     *
     * @param data JSON payload describing the event.
     */
    fun eventListenerCallback(data: JSONObject) {
        if (eventCallback != null) {
            Wearable.sendPluginSuccess(eventCallback!!, data, true)
        }
    }

    /**
     * Send a message payload to the watch.
     *
     * @param activity Host activity used to resolve Google Play Services clients.
     * @param path Wearable message path.
     * @param data Serialized payload.
     */
    fun sendMessageToWatch(activity: AppCompatActivity, path: String, data: String) {
        watchConnect.sendMessage(activity, path, data)
    }

    /**
     * Determine whether the device supports the Wearable APIs.
     *
     * @param activity Activity reference for context resolution.
     * @return `true` if Wearable APIs are available.
     */
    fun isSupported(activity: AppCompatActivity): Boolean {
        return watchConnect.isSupported(activity)
    }

    /**
     * Determine whether a Wear OS device is paired with the phone.
     *
     * @param activity Activity reference for context resolution.
     * @return `true` if a Wear OS node is paired.
     */
    fun isPaired(activity: AppCompatActivity): Boolean {
        return watchConnect.isPaired(activity)
    }

    /**
     * Determine whether a Wear OS device is currently connected/reachable.
     *
     * @param activity Activity reference for context resolution.
     * @return `true` if a Wear OS node is connected.
     */
    fun isConnected(activity: AppCompatActivity): Boolean {
        return watchConnect.isConnected(activity)
    }
}