/*
 Copyright (C) 2025 Dave Alden
 SPDX-License-Identifier: GPL-3.0-or-later

 This file is part of the cordova-plugin-wearable project and is
 licensed under the GNU General Public License v3.0 or later.
 See LICENSE at the repository root for full license text.
*/

var Wearable = {};

var SERVICE_ID = 'Wearable';

var ACTIONS = {
    CONFIGURE: 'configure',
    START_CONNECTIVITY: 'startConnectivity',
    STOP_CONNECTIVITY: 'stopConnectivity',
    REGISTER_MESSAGE_LISTENER: 'registerMessageListener',
    REGISTER_EVENT_LISTENER: 'registerEventListener',
    SEND_MESSAGE_TO_WATCH: 'sendMessageToWatch',
    IS_SUPPORTED: 'isSupported',
    IS_PAIRED: 'isPaired',
    IS_CONNECTED: 'isConnected',
    REGISTER_STATE_LISTENER: 'registerStateListener',
    UNREGISTER_STATE_LISTENER: 'unregisterStateListener',
    SET_STATE: 'setState',
    REMOVE_STATE: 'removeState',
    GET_STATE: 'getState',
    HAS_STATE: 'hasState',
    SET_STATE_PATH: 'setStatePath',
    REMOVE_STATE_PATH: 'removeStatePath',
    GET_ALL_STATE: 'getAllState'
};

function _execPromise(action, args, success, error) {
    // If a success callback is provided, use callback-style
    if (typeof success === 'function') {
        cordova.exec(success, error, SERVICE_ID, action, args || []);
        return;
    }

    // Otherwise return a Promise
    return new Promise(function(resolve, reject) {
        cordova.exec(function(res) { resolve(res); }, function(err) { reject(err); }, SERVICE_ID, action, args || []);
    });
}

/**
 * Configure the plugin using a single options object.
 *
 * Call this once at app startup before using capability-scoped APIs.
 *
 * Options:
 * - Android-only:
 *   - capability: string (required) — Capability name advertised by your Wear OS watch app
 *   - path: string (required) — Message path used for phone<->watch communication
 *   - notificationTitle?: string — Foreground service notification title
 *   - notificationText?: string — Foreground service notification text
 * - Cross-platform:
 *   - enableLogging?: boolean — Enable plugin debug logging
 *
 * @param {Object} options - Configuration options object (see above)
 * @param {function} [success] - Optional success callback (callback-style).
 * @param {function} [error] - Optional error callback (callback-style).
 * @returns {Promise|undefined} Promise when no callbacks are provided; otherwise undefined.
 */
Wearable.configure = function(options, success, error) {
    return _execPromise(ACTIONS.CONFIGURE, [options || {}], success, error);
};

/**
 * Start watch connectivity.
 * - Android: advertises capability, starts foreground service, and begins heartbeats/state sync
 * - iOS: initializes watch connectivity plumbing (future); currently a no-op that resolves
 */
Wearable.startConnectivity = function(success, error) {
    return _execPromise(ACTIONS.START_CONNECTIVITY, [], success, error);
};

/**
 * Stop watch connectivity.
 * - Android: removes capability, stops foreground service, and stops heartbeats/state sync
 * - iOS: currently a no-op that resolves
 */
Wearable.stopConnectivity = function(success, error) {
    return _execPromise(ACTIONS.STOP_CONNECTIVITY, [], success, error);
};

/**
 * Register a listener to receive messages from the watch app.
 *
 * Usage:
 * - Callback-style (recommended for listeners): Wearable.registerMessageListener(callback)
 * - Promise-style: await Wearable.registerMessageListener()  // registers and resolves when registration completes
 *
 * The listener will be called with a single object parameter containing event data.
 *
 * @param {function} [listener] - Optional listener callback that will receive events from the watch.
 * @param {function} [error] - Optional error callback for callback-style usage.
 * @returns {Promise|undefined} Promise if no callbacks are provided; otherwise undefined (or a resolved Promise when we register the listener).
 */
Wearable.registerMessageListener = function(listener, error) {
    // If listener provided, register it and return a resolved Promise (callback-style listener will receive events)
    if (typeof listener === 'function') {
        if (typeof error === 'function') {
            cordova.exec(listener, error, SERVICE_ID, ACTIONS.REGISTER_MESSAGE_LISTENER, []);
            return;
        }
        // Register listener and resolve immediately to indicate registration succeeded
        cordova.exec(listener, function(err){ console.error('registerMessageListener error', err); }, SERVICE_ID, ACTIONS.REGISTER_MESSAGE_LISTENER, []);
        return Promise.resolve({ listening: true });
    }

    // Fallback: attempt plugin-style registration and return promise
    return _execPromise(ACTIONS.REGISTER_MESSAGE_LISTENER, [], listener, error);
};

/**
 * Register a listener for plugin-generated events (pairing/connectivity/reachability changes, etc.).
 *
 * @param {function} [listener] - Optional event listener callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined (or resolved once registration succeeds).
 */
Wearable.registerEventListener = function(listener, error) {
    if (typeof listener === 'function') {
        if (typeof error === 'function') {
            cordova.exec(listener, error, SERVICE_ID, ACTIONS.REGISTER_EVENT_LISTENER, []);
            return;
        }
        cordova.exec(listener, function(err){ console.error('registerEventListener error', err); }, SERVICE_ID, ACTIONS.REGISTER_EVENT_LISTENER, []);
        return Promise.resolve({ listening: true });
    }
    return _execPromise(ACTIONS.REGISTER_EVENT_LISTENER, [], listener, error);
};

 /**
 * Sends data to the watch.
 *
 * Will reject or call the error callback if the device does not support the wearable
 * API or no paired/connected watch is available. On Wear OS there is no reliable phone-side
 * check for remote app installation; capability/message handshakes are used instead.
 *
 * @param {Object} data - JSON-serializable object to send to the watch.
 * @param {function} [success] - Optional success callback (callback-style).
 * @param {function} [error] - Optional error callback (callback-style).
 * @returns {Promise|undefined} Promise when no callbacks are provided; otherwise undefined.
 */
Wearable.sendMessageToWatch = function(data, success, error) {
    return _execPromise(ACTIONS.SEND_MESSAGE_TO_WATCH, [data], success, error);
};

/**
 * Check if the device supports the Wearable API.
 *
 * - Android: performs a capability-client call to verify the Wearable API is available on the device.
 * - iOS: returns WCSession.isSupported().
 *
 * Returns an object: { isSupported: boolean }
 *
 * @param {function} [success] - Optional success callback (callback-style).
 * @param {function} [error] - Optional error callback (callback-style).
 * @returns {Promise|undefined}
 */
Wearable.isSupported = function(success, error) {
    return _execPromise(ACTIONS.IS_SUPPORTED, [], success, error);
};

/**
 * Check if the device is paired with a watch.
 *
 * - Android: system-level pairing. Returns true if any connected/paired node exists (not scoped to capability).
 * - iOS: returns WCSession.isPaired.
 *
 * Returns an object: { isPaired: boolean }
 *
 * @param {function} [success] - Optional success callback (callback-style).
 * @param {function} [error] - Optional error callback (callback-style).
 * @returns {Promise|undefined}
 */
Wearable.isPaired = function(success, error) {
    return _execPromise(ACTIONS.IS_PAIRED, [], success, error);
};

/**
 * Check if the device has a currently connected/reachable watch.
 *
 * - Android: system-level reachable nodes. Returns true if any connected/reachable node exists (live messaging possible at the system level). Remote app installation cannot be determined reliably from the phone.
 * - iOS: returns WCSession.isReachable (live messaging possible).
 *
 * Returns an object: { isConnected: boolean }
 *
 * @param {function} [success] - Optional success callback (callback-style).
 * @param {function} [error] - Optional error callback (callback-style).
 * @returns {Promise|undefined}
 */
Wearable.isConnected = function(success, error) {
    return _execPromise(ACTIONS.IS_CONNECTED, [], success, error);
};


module.exports = Wearable;

// =========================
// Shared State JS API
// =========================

/**
 * Canonical cross-platform data contract (Android & iOS align on this shape):
 * - `getState(key)` resolves `{ exists: boolean, value?: any }` (value omitted when `exists === false`).
 * - `hasState(key)` resolves `{ has: boolean }`.
 * - `getAllState()` resolves a plain object mirroring the JSON tree; booleans remain booleans, numbers remain numbers, and `null` is preserved.
 * - `registerStateListener()` callbacks always receive `{ event: 'stateChanged', state: <object> }` with the fully materialized state.
 *
 * All payloads are JSON-serializable and identical across platforms once native implementations are aligned.
 */

/**
 * Register a listener to receive shared-state updates. The callback will be invoked:
 * - once immediately with `{ event: 'stateChanged', state: <object> }` representing the current state
 * - subsequently on every effective change (local or remote) after merge with the same payload shape
 *
 * @param {function} [listener] - Optional listener invoked with state change payloads.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined (or resolved once registration succeeds).
 */
Wearable.registerStateListener = function(listener, error) {
    if (typeof listener === 'function') {
        if (typeof error === 'function') {
            cordova.exec(listener, error, SERVICE_ID, ACTIONS.REGISTER_STATE_LISTENER, []);
            return;
        }
        cordova.exec(listener, function(err){ console.error('registerStateListener error', err); }, SERVICE_ID, ACTIONS.REGISTER_STATE_LISTENER, []);
        return Promise.resolve({ listening: true });
    }
    return _execPromise(ACTIONS.REGISTER_STATE_LISTENER, [], listener, error);
};

/**
 * Unregister the active shared state listener (no-op if none registered).
 *
 * @param {function} [success] - Optional success callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined.
 */
Wearable.unregisterStateListener = function(success, error) {
    return _execPromise(ACTIONS.UNREGISTER_STATE_LISTENER, [], success, error);
};

/**
 * Set a top-level state key to a JSON-serializable value (LWW per key).
 *
 * @param {string} key - Non-empty state key.
 * @param {*} value - JSON-serializable value to store.
 * @param {function} [success] - Optional success callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined.
 */
Wearable.setState = function(key, value, success, error) {
    if (typeof key !== 'string' || key.length === 0) {
        var msg = 'setState requires a non-empty string key';
        if (typeof success === 'function' || typeof error === 'function') {
            if (typeof error === 'function') error(msg);
            return;
        }
        return Promise.reject(new Error(msg));
    }
    return _execPromise(ACTIONS.SET_STATE, [key, value], success, error);
};

/**
 * Remove a top-level key (records tombstone).
 *
 * @param {string} key - Non-empty state key.
 * @param {function} [success] - Optional success callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined.
 */
Wearable.removeState = function(key, success, error) {
    if (typeof key !== 'string' || key.length === 0) {
        var msg = 'removeState requires a non-empty string key';
        if (typeof success === 'function' || typeof error === 'function') {
            if (typeof error === 'function') error(msg);
            return;
        }
        return Promise.reject(new Error(msg));
    }
    return _execPromise(ACTIONS.REMOVE_STATE, [key], success, error);
};

/**
 * Get a top-level key.
 *
 * Resolves with `{ exists: boolean, value?: any }` (value omitted when not present).
 *
 * @param {string} key - Non-empty state key.
 * @param {function} [success] - Optional success callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined.
 */
Wearable.getState = function(key, success, error) {
    if (typeof key !== 'string' || key.length === 0) {
        var msg = 'getState requires a non-empty string key';
        if (typeof success === 'function' || typeof error === 'function') {
            if (typeof error === 'function') error(msg);
            return;
        }
        return Promise.reject(new Error(msg));
    }
    return _execPromise(ACTIONS.GET_STATE, [key], success, error);
};

/**
 * Determine whether a top-level key exists.
 *
 * Resolves with `{ has: boolean }`.
 *
 * @param {string} key - Non-empty state key.
 * @param {function} [success] - Optional success callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined.
 */
Wearable.hasState = function(key, success, error) {
    if (typeof key !== 'string' || key.length === 0) {
        var msg = 'hasState requires a non-empty string key';
        if (typeof success === 'function' || typeof error === 'function') {
            if (typeof error === 'function') error(msg);
            return;
        }
        return Promise.reject(new Error(msg));
    }
    return _execPromise(ACTIONS.HAS_STATE, [key], success, error);
};

/**
 * Set a JSON Pointer path to a value (per-path LWW).
 *
 * @param {string} jsonPointer - JSON Pointer string beginning with `/`.
 * @param {*} value - JSON-serializable value to store.
 * @param {function} [success] - Optional success callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined.
 */
Wearable.setStatePath = function(jsonPointer, value, success, error) {
    if (typeof jsonPointer !== 'string' || jsonPointer.length === 0 || jsonPointer[0] !== '/') {
        var msg = 'setStatePath requires a JSON Pointer string beginning with /';
        if (typeof success === 'function' || typeof error === 'function') {
            if (typeof error === 'function') error(msg);
            return;
        }
        return Promise.reject(new Error(msg));
    }
    return _execPromise(ACTIONS.SET_STATE_PATH, [jsonPointer, value], success, error);
};

/**
 * Remove a JSON Pointer path (records tombstone).
 *
 * @param {string} jsonPointer - JSON Pointer string beginning with `/`.
 * @param {function} [success] - Optional success callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined.
 */
Wearable.removeStatePath = function(jsonPointer, success, error) {
    if (typeof jsonPointer !== 'string' || jsonPointer.length === 0 || jsonPointer[0] !== '/') {
        var msg = 'removeStatePath requires a JSON Pointer string beginning with /';
        if (typeof success === 'function' || typeof error === 'function') {
            if (typeof error === 'function') error(msg);
            return;
        }
        return Promise.reject(new Error(msg));
    }
    return _execPromise(ACTIONS.REMOVE_STATE_PATH, [jsonPointer], success, error);
};

/**
 * Return the full materialized shared state object (plain JSON with preserved booleans, numbers, null).
 *
 * @param {function} [success] - Optional success callback.
 * @param {function} [error] - Optional error callback.
 * @returns {Promise|undefined} Promise when callbacks are omitted; otherwise undefined.
 */
Wearable.getAllState = function(success, error) {
    return _execPromise(ACTIONS.GET_ALL_STATE, [], success, error);
};
