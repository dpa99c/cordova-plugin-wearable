import Foundation
import WatchConnectivity

class WatchConnect: NSObject, WCSessionDelegate {
    static let shared = WatchConnect()  // Singleton instance
    var watchSession = WCSession.default

    // Callbacks to forward messages and events to the plugin implementation
    var messageCallback: (([String: Any]) -> Void)?
    var eventCallback: (([String: Any]) -> Void)?

    // Heartbeat tracking
    private var lastWatchHeartbeat: Date? = nil
    private var heartbeatTimer: DispatchSourceTimer? = nil
    private let connectivityStaleMs: TimeInterval = 4.0  // 4 seconds
    private let connectivityPollIntervalMs: TimeInterval = 1.0  // 1 second
    
    // Background queue for heartbeat operations to avoid blocking UI
    private let heartbeatQueue = DispatchQueue(label: "com.cordova.wearable.heartbeat", qos: .utility)
    
    // Connectivity state (independent of native WCSession state)
    private var isConnectivityEnabled: Bool = false
    
    public func getIsConnectivityEnabled() -> Bool {
        return isConnectivityEnabled
    }

    // Connectivity state tracking
    private var previousIsPaired: Bool? = nil
    private var previousIsWatchAppInstalled: Bool? = nil
    private var previousIsActivated: Bool? = nil
    private var previousIsReachable: Bool? = nil
    private var previousIsConnected: Bool? = nil

    override init() {
        super.init()
        if WCSession.isSupported() {
            watchSession = WCSession.default
            watchSession.delegate = self
            logSessionSnapshot(reason: "init")
        } else {
            Logger.error("WCSession is not supported on this device")
        }
    }

    public func activateSession() {
        if WCSession.isSupported() {
            watchSession = WCSession.default
            watchSession.activate()
            Logger.debug("WCSession activateSession called")
            logSessionSnapshot(reason: "activateSession")
        }
    }

    func sessionDidBecomeActive(_ session: WCSession) {
        Logger.debug("sessionDidBecomeActive")
        logSessionSnapshot(reason: "didBecomeActive")
        if let callback = eventCallback,
            let data = [Wearable.DataKey.event: "sessionDidBecomeActive"] as? [String: Any] {
            callback(data)
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {
        Logger.debug("sessionDidBecomeInactive")
        logSessionSnapshot(reason: "didBecomeInactive")
        if let callback = eventCallback,
            let data = [Wearable.DataKey.event: "sessionDidBecomeInactive"] as? [String: Any] {
            callback(data)
        }
    }

    func sessionDidDeactivate(_ session: WCSession) {
        Logger.debug("sessionDidDeactivate")
        logSessionSnapshot(reason: "didDeactivate")
        if let callback = eventCallback,
            let data = [Wearable.DataKey.event: "sessionDidDeactivate"] as? [String: Any] {
            callback(data)
        }
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        Logger.debug("sessionReachabilityDidChange \(session.isReachable)")
        logSessionSnapshot(reason: "reachabilityDidChange")
        if let callback = eventCallback,
            let data = [Wearable.DataKey.event: "sessionReachabilityDidChange", Wearable.DataKey.isReachable: session.isReachable] as? [String: Any] {
            callback(data)
        }
    }

    func sessionWatchStateDidChange(_ session: WCSession) {
        Logger.debug("sessionWatchStateDidChange")
        logSessionSnapshot(reason: "watchStateDidChange")
        if let callback = eventCallback,
            let data = [Wearable.DataKey.event: "sessionWatchStateDidChange"] as? [String: Any] {
            callback(data)
        }
    }

    public func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if error != nil {
            Logger.error("activationDidCompleteWith error", error)
        } else {
            Logger.debug("activationDidCompleteWith activationState:\(activationState)")
        }
        logSessionSnapshot(reason: "activationDidComplete")
    }

    /**
     Forward applicationContext updates to listeners via NotificationCenter.
     This allows a StateSync consumer to observe incoming ops/snapshots/acks
     without taking over the WCSession delegate.
     */
    public func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        Logger.debug("didReceiveApplicationContext keys: \(Array(applicationContext.keys))")
        // Only forward state sync messages if connectivity is enabled
        guard isConnectivityEnabled else {
            Logger.debug("Ignoring applicationContext because connectivity is disabled")
            return
        }
        NotificationCenter.default.post(name: Notification.Name("WearableApplicationContextReceived"), object: nil, userInfo: applicationContext)
    }

    /**
     Callback method to handle all received messages from the watch where the watch does not need a reply back. This will handle mssages sent via sendMessage method
     */
    public func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        // Messages are expected to carry a top-level `data` object.
        if let data = message[Wearable.DataKey.data] as? [String: Any] {
            Logger.verbose("didReceiveMessage payload keys: \(Array(data.keys))")
            // Filter internal heartbeat messages
            if let hb = data[Wearable.DataKey.heartbeat] as? String {
                Logger.debug("Received heartbeat message: \(hb)")
                // Only process watch heartbeats if connectivity is enabled
                if hb == Wearable.DataKey.watch && isConnectivityEnabled {
                    lastWatchHeartbeat = Date()
                }
                return
            }

            if handleStateSyncPayload(data) {
                return
            }

            // Otherwise forward to plugin listener
            if let callback = messageCallback {
                callback(data)
            }
        }
    }

    /**
     Callback method to handle all received userinfo from the watch  sent via transferUserInfo  method
     */
    public func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
        if let data = userInfo[Wearable.DataKey.data] as? [String: Any] {
            Logger.verbose("didReceiveUserInfo payload keys: \(Array(data.keys))")
            if let hb = data[Wearable.DataKey.heartbeat] as? String {
                Logger.debug("Received userInfo heartbeat: \(hb)")
                // Only process watch heartbeats if connectivity is enabled
                if hb == Wearable.DataKey.watch && isConnectivityEnabled {
                    lastWatchHeartbeat = Date()
                }
                return
            }
            if handleStateSyncPayload(data) {
                return
            }
            if let callback = messageCallback {
                callback(data)
            }
        }
    }

    /// Send a message to the watch. Prefer live sendMessage when reachable, otherwise fall back to transferUserInfo.
    /// This method is asynchronous and returns immediately - it does not block the calling thread.
    public func sendMessage(data: [String: Any]) {
        guard watchSession.isPaired else {
            Logger.verbose("Attempted to send message but no watch is paired")
            return
        }

        // Wrap payload under `data` key to match receive expectations
        let payload = [Wearable.DataKey.data: data]
        Logger.debug("sendMessage request keys: \(Array(data.keys)) reachable=\(watchSession.isReachable)")

        // Dispatch async to avoid blocking the calling thread
        heartbeatQueue.async { [weak self] in
            guard let self = self else { return }
            
            let success = RetryHelper.withRetry(
                tag: "WatchConnect",
                operation: "sendMessage",
                maxAttempts: RetryHelper.RetryConfig.maxAttempts
            ) { attempt -> Bool in
                if attempt > 1 {
                    Logger.debug("Retry attempt \(attempt) for sendMessage")
                }
                
                if self.watchSession.isReachable {
                    var sendSucceeded = false
                    let semaphore = DispatchSemaphore(value: 0)
                    
                    self.watchSession.sendMessage(payload, replyHandler: { _ in
                        sendSucceeded = true
                        semaphore.signal()
                    }) { error in
                        Logger.warn("sendMessage error on attempt \(attempt): \(error.localizedDescription)")
                        semaphore.signal()
                    }
                    
                    // Wait for send to complete (with timeout)
                    // Safe to use semaphore here since we're on a background queue
                    _ = semaphore.wait(timeout: .now() + 5.0)
                    
                    if sendSucceeded {
                        return true
                    } else if attempt < RetryHelper.RetryConfig.maxAttempts {
                        // Will retry
                        return false
                    } else {
                        // Last attempt failed, fall back to transferUserInfo
                        Logger.verbose("sendMessage failed after retries, falling back to transferUserInfo")
                        self.watchSession.transferUserInfo(payload)
                        self.logSessionSnapshot(reason: "sendMessageFailedFallback")
                        return true
                    }
                } else {
                    Logger.verbose("sendMessage falling back to transferUserInfo; reachable=false")
                    self.watchSession.transferUserInfo(payload)
                    return true
                }
            }
            
            if !success {
                Logger.error("Failed to send message after all retries")
            }
        }
    }

    /// Start sending periodic heartbeat messages to the watch (every 1s). Safe to call multiple times.
    public func startHeartbeat() {
        if(self.heartbeatTimer != nil) {
            Logger.verbose("Heartbeat already running")
            return
        }
        isConnectivityEnabled = true
        Logger.debug("Connectivity enabled")
        
        // Create a dispatch timer that runs on the background queue
        let timer = DispatchSource.makeTimerSource(queue: heartbeatQueue)
        timer.schedule(deadline: .now(), repeating: connectivityPollIntervalMs)
        timer.setEventHandler { [weak self] in
            guard let self = self else { return }
            
            // Send heartbeat message to watch
            let hb = [Wearable.DataKey.heartbeat: Wearable.DataKey.phone]
            self.sendMessage(data: hb)

            // Check connectivity state
            let isPaired = self.isPaired()
            let isWatchAppInstalled = self.isWatchAppInstalled()
            let isActivated = self.isActivated()
            let isReachable = self.isReachable()
            let isConnected = self.isConnected()

            self.dispatchIfChanged(key: Wearable.PluginEvent.isPairedChanged, prev: self.previousIsPaired, now: isPaired)
            self.dispatchIfChanged(key: Wearable.PluginEvent.isWatchAppInstalledChanged, prev: self.previousIsWatchAppInstalled, now: isWatchAppInstalled)
            self.dispatchIfChanged(key: Wearable.PluginEvent.isActivatedChanged, prev: self.previousIsActivated, now: isActivated)
            self.dispatchIfChanged(key: Wearable.PluginEvent.isReachableChanged, prev: self.previousIsReachable, now: isReachable)
            self.dispatchIfChanged(key: Wearable.PluginEvent.isConnectedChanged, prev: self.previousIsConnected, now: isConnected)

            self.previousIsPaired = isPaired
            self.previousIsWatchAppInstalled = isWatchAppInstalled
            self.previousIsActivated = isActivated
            self.previousIsReachable = isReachable
            self.previousIsConnected = isConnected
        }
        
        heartbeatTimer = timer
        timer.resume()
        Logger.debug("Heartbeat started")
    }

    /// Stop sending heartbeat messages.
    public func stopHeartbeat() {
        isConnectivityEnabled = false
        lastWatchHeartbeat = nil
        Logger.debug("Connectivity disabled")
        
        if let timer = heartbeatTimer {
            timer.cancel()
            heartbeatTimer = nil
            Logger.debug("Heartbeat stopped")
        }
        
        // Dispatch disconnected event on main thread (for UI updates)
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if let callback = self.eventCallback,
               let data = [Wearable.DataKey.event: Wearable.PluginEvent.isConnectedChanged, Wearable.DataKey.value: false] as? [String: Any] {
                callback(data)
            }
        }
    }

    public func registerMessageListener(callback: @escaping ([String: Any]) -> Void) {
        messageCallback = callback
    }

    public func registerEventListener(callback: @escaping ([String: Any]) -> Void) {
        eventCallback = callback
    }

    // MARK: - Support / Pairing / Connection helpers
    public func isActivated() -> Bool {
        guard WCSession.isSupported() else { return false }
        return watchSession.activationState == .activated
    }

    public func isPaired() -> Bool {
        guard WCSession.isSupported() else { return false }
        return watchSession.isPaired
    }

    public func isWatchAppInstalled() -> Bool {
        guard WCSession.isSupported() else { return false }
        return watchSession.isWatchAppInstalled
    }

    public func isReachable() -> Bool {
        guard WCSession.isSupported() else { return false }
        return watchSession.isReachable
    }

    public func isSupported() -> Bool {
        return WCSession.isSupported()
    }

    public func isConnected() -> Bool {
        guard isConnectivityEnabled else { return false }
        guard WCSession.isSupported() else { return false }
        guard self.isPaired() else { return false }
        guard self.isWatchAppInstalled() else { return false }
        guard self.isActivated() else { return false }
        guard self.isReachable() else { return false }

        if let last = lastWatchHeartbeat, Date().timeIntervalSince(last) <= connectivityStaleMs {
            return true
        }

        return false
    }

    // Send event if value has changed
    private func dispatchIfChanged(key: String, prev: Bool?, now: Bool) {
        if prev != now {
            if let callback = eventCallback,
               let data = [Wearable.DataKey.event: key, Wearable.DataKey.value: now] as? [String: Any] {
                callback(data)
            }
        }
    }

    private func logSessionSnapshot(reason: String) {
        guard WCSession.isSupported() else { return }
        let activated = self.isActivated()
        let paired = self.isPaired()
        let watchInstalled = self.isWatchAppInstalled()
        let reachable = self.isReachable()
        let connected = self.isConnected()
        let pending = watchSession.hasContentPending
        Logger.debug("WCSession snapshot [\(reason)]: activated=\(activated) paired=\(paired) reachable=\(reachable) watchInstalled=\(watchInstalled) connected=\(connected) pending=\(pending)")
    }

    private func handleStateSyncPayload(_ data: [String: Any]) -> Bool {
        guard let type = data[Wearable.DataKey.type] as? String else {
            return false
        }

        switch type {
        case "stateOp", "stateAck", "stateSnapshot":
            NotificationCenter.default.post(name: Notification.Name("WearableStateSyncMessage"), object: nil, userInfo: data)
            Logger.verbose("State sync payload handled: type=\(type)")
            return true
        default:
            return false
        }
    }
}
