/*
 Copyright (C) 2025 Dave Alden
 SPDX-License-Identifier: GPL-3.0-or-later

 This file is part of the cordova-plugin-wearable project and is
 licensed under the GNU General Public License v3.0 or later.
 See LICENSE at the repository root for full license text.
*/

import Foundation
import UIKit

/** Concrete implementation that manages WatchConnectivity and shared state orchestration. */
@objc public class WearableImpl: NSObject {

    private let watchConnect = WatchConnect.shared
    private let store = StateStore()
    private lazy var stateSync: StateSync = {
        return StateSync(store: store)
    }()

    private let stateChangedNotification = Notification.Name("WearableStateChanged")

    private var appActiveObserver: NSObjectProtocol?

    private var messageListenerCommand: CDVInvokedUrlCommand?
    private var eventCallback: CDVInvokedUrlCommand?

    public func registerMessageListener(callback: CDVInvokedUrlCommand) {
        Logger.debug("registerMessageListener invoked")
        messageListenerCommand = callback
        watchConnect.registerMessageListener { [weak self] data in
            Logger.verbose("Forwarding message to JS listener: \(data)")
            self?.dispatchMessage(data: data)
        }
    }

    public func registerEventListener(callback: CDVInvokedUrlCommand) {
        Logger.debug("registerEventListener invoked")
        eventCallback = callback
        watchConnect.registerEventListener { [weak self] data in
            Logger.verbose("Dispatching event callback: \(data)")
            if let cb = self?.eventCallback {
                Wearable.sendPluginSuccess(command: cb, result: data, keepCallback: true)
            }
        }
    }

    private var stateCallback: CDVInvokedUrlCommand?
    private var stateObserver: NSObjectProtocol?

    public func registerStateListener(callback: CDVInvokedUrlCommand) {
        Logger.debug("registerStateListener invoked")
        stateCallback = callback
        // Send initial materialized state
        let snapshot = store.materialize()
        if let cb = stateCallback {
            Logger.debug("Emitting initial state payload with \(snapshot.count) keys")
            let payload: [String: Any] = [
                Wearable.DataKey.event: Wearable.PluginEvent.stateChanged,
                Wearable.DataKey.state: snapshot
            ]
            Wearable.sendPluginSuccess(command: cb, result: payload, keepCallback: true)
        }

        

        // Observe state changes from store (store posts userInfo as [String: Any])
        stateObserver = NotificationCenter.default.addObserver(forName: stateChangedNotification, object: nil, queue: OperationQueue.main) { [weak self] _ in
            guard let self = self, let callback = self.stateCallback else { return }

            let payload: [String: Any] = [
                Wearable.DataKey.event: Wearable.PluginEvent.stateChanged,
                Wearable.DataKey.state: self.store.materialize()
            ]
            Wearable.sendPluginSuccess(command: callback, result: payload, keepCallback: true)
        }
    }

    public func unregisterStateListener() {
        if stateCallback != nil {
            stateCallback = nil
        }
        if let obs = stateObserver {
            NotificationCenter.default.removeObserver(obs)
            stateObserver = nil
        }
        stateSync.stop()
    }

    public func getAllState() -> [String: Any] {
        return store.materialize()
    }

    public func setStatePath(path: String, value: Any?) {
        stateSync.setPath(path, value: value)
    }

    public func removeStatePath(path: String) {
        stateSync.setPath(path, value: nil)
    }

    public func removeState(path: String) {
        stateSync.setPath(path, value: nil)
    }

    public func getState(path: String) -> Any? {
        return store.getValueAtPath(path)
    }

    public func hasState(path: String) -> Bool {
        return store.hasPath(path)
    }

    public func listenerCallback(data: [String: Any]) {
        dispatchMessage(data: data)
    }

    public func sendMessageToWatch(data: [String: Any]) {
        Logger.debug("sendMessageToWatch called with payload keys: \(Array(data.keys))")
        watchConnect.sendMessage(data: data)
    }

    private func dispatchMessage(data: [String: Any]) {
        Logger.debug("dispatchMessage delivering payload: \(data)")
        if let callback = messageListenerCommand {
            Wearable.sendPluginSuccess(command: callback, result: data, keepCallback: true)
        }
    }

    public func startConnectivity() {
        Logger.info("startConnectivity invoked")
        watchConnect.activateSession()
        watchConnect.startHeartbeat()
        stateSync.start()

        // Observe app foregrounding and send a snapshot when app becomes active
        appActiveObserver = NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: OperationQueue.main) { [weak self] _ in
            guard let self = self else { return }
            Logger.debug("App became active - sending state snapshot")
            self.stateSync.sendSnapshot()
        }
    }

    public func stopConnectivity() {
        Logger.info("stopConnectivity invoked")
        watchConnect.stopHeartbeat()
        stateSync.stop()
        if let obs = appActiveObserver {
            NotificationCenter.default.removeObserver(obs)
            appActiveObserver = nil
        }
    }

    public func isSupported() -> Bool {
        return watchConnect.isSupported()
    }

    public func isPaired() -> Bool {
        return watchConnect.isPaired()
    }

    public func isConnected() -> Bool {
        return watchConnect.isConnected()
    }
}
