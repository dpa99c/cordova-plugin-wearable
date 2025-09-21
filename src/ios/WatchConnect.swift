import Foundation
import WatchConnectivity

class WatchConnect: NSObject, WCSessionDelegate {
    static let shared = WatchConnect()  // Singleton instance
    var watchSession = WCSession.default

    var listenerCallback: (((_ data: [String: Any]) -> Void)?

    override init() {
        super.init()
        if WCSession.isSupported() {
            watchSession = WCSession.default
            watchSession.delegate = self
            watchSession.activate()
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {
        Logger.trace("sessionDidBecomeInactive")
        if let callback = listenerCallback,
            let data = ["event": "sessionDidBecomeInactive"] as? [String: Any] {
            callback(data)
        }
    }

    func sessionDidDeactivate(_ session: WCSession) {
        Logger.trace("sessionDidDeactivate")
        if let callback = listenerCallback,
            let data = ["event": "sessionDidDeactivate"] as? [String: Any] {
            callback(data)
        }
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        Logger.trace("sessionReachabilityDidChange \(session.isReachable)")
        if let callback = listenerCallback,
            let data = ["event": "sessionReachabilityDidChange", "isReachable": session.isReachable] as? [String: Any] {
            callback(data)
        }
    }

    func sessionWatchStateDidChange(_ session: WCSession) {
        Logger.trace("sessionWatchStateDidChange")
        if let callback = listenerCallback,
            let data = ["event": "sessionWatchStateDidChange"] as? [String: Any] {
            callback(data)
        }
    }

    public func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if error != nil {
            Logger.error("activationDidCompleteWith error", error: error)
        } else {
            Logger.debug("activationDidCompleteWith activationState:\(activationState)")
        }
    }

    /**
     Callback method to handle all received messages from the watch where the watch does not need a reply back. This will handle mssages sent via sendMessage method
     */
    public func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        if let callback = listenerCallback,
            let data = message["data"] as? [String: Any] {
            callback(data)
        }
    }

    /**
     Callback method to handle all received userinfo from the watch  sent via transferUserInfo  method
     */
    public func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
        if let callback = listenerCallback,
            let data = userInfo["data"] as? [String: Any] {
            callback(data)
        }
    }

    public func sendMessage((data: [String: Any]) {
        guard watchSession.isPaired, watchSession.isWatchAppInstalled else {
            Logger.silly("Attempted to send message but no watch is paired or app is not installed")
            return
        }
        watchSession.transferUserInfo(data)
    }

    public func registerListener(callback: @escaping (((_ data: [String: Any]) -> Void)) {
        listenerCallback = callback
    }

}
