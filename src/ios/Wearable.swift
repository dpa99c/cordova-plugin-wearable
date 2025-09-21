import Foundation

@objc public class Wearable: NSObject {

    private let watchConnect = WatchConnect.shared

    private var listenerCallback: CDVInvokedUrlCommand?

    public func registerListener(callback: CDVInvokedUrlCommand) -> Bool {
        listenerCallback = callback
        watchConnect.registerListener(callback: listenerCallback)
        return true
    }

    public func listenerCallback(data: [String: Any]) {
        if let callback = listenerCallback {
            callback.resolve([
                "data": data
            ])
        }
    }

    public func sendDataToWatch(data: [String: Any]) {
        watchConnect.sendMessage(data: data)
    }
}
