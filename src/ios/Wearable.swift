extension String: Error {}


@objc(Wearable)
class Wearable: CDVPlugin {

    /** Cordova bridge that exposes the Wearable watchOS connectivity APIs to the JavaScript layer. */

    /************************
     * Static variables
     ************************/
    
    static var instance: Wearable? = nil

    static var enableDebugLogging: Bool = false

    struct ErrorMessage {
        static let unsupported = "WatchConnectivity is not supported on this device"
        static let notPaired = "No paired Apple Watch found"
        static let notConnected = "No connected Apple Watch found"
        static let invalidArguments = "Invalid arguments"
    }

    struct PluginEvent {
        static let isConnectedChanged = "isConnectedChanged"
        static let isPairedChanged = "isPairedChanged"
        static let isReachableChanged = "isReachableChanged"
        static let isWatchAppInstalledChanged = "isWatchAppInstalledChanged"
        static let isActivatedChanged = "isActivatedChanged"
        static let stateChanged = "stateChanged"
    }

    struct DataKey {
        static let isReachable = "isReachable"
        static let event = "event"
        static let state = "state"
        static let path = "path"
        static let tombstone = "tombstone"
        static let value = "value"
        static let data = "data"
        static let type = "type"
        static let version = "version"
        static let opId = "opId"
        static let op = "op"
        static let ts = "ts"
        static let snapshot = "snapshot"
        static let ack = "ack"
        static let peer = "peer"
        static let heartbeat = "heartbeat"
        static let watch = "watch"
        static let phone = "phone"

    }

    /************************
     * Static methods
     ************************/
    static func getInstance() -> Wearable? {
        return Wearable.instance
    }

    static func sendPluginNoResult(command: CDVInvokedUrlCommand) {
        let instance = Wearable.getInstance()
        guard let _ = instance else {
            Logger.warn("sendPluginNoResult: no plugin instance")
            return
        }
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
        // Retain callback to allow native layer to stream events as they arrive.
        pluginResult!.setKeepCallbackAs(true)
        instance!.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    static func sendPluginSuccess(command: CDVInvokedUrlCommand, keepCallback:Bool) {
        let instance = Wearable.getInstance()
        guard let _ = instance else {
            Logger.warn("sendPluginSuccess: no plugin instance")
            return
        }
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        pluginResult!.setKeepCallbackAs(keepCallback)
        instance!.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    static func sendPluginSuccess(command: CDVInvokedUrlCommand, result: String, keepCallback:Bool) {
        let instance = Wearable.getInstance()
        guard let _ = instance else {
            Logger.warn("sendPluginSuccess: no plugin instance")
            return
        }
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: result)
        pluginResult!.setKeepCallbackAs(keepCallback)
        instance!.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    static func sendPluginSuccess(command: CDVInvokedUrlCommand, result: [String: Any], keepCallback:Bool) {
        let instance = Wearable.getInstance()
        guard let _ = instance else {
            Logger.warn("sendPluginSuccess: no plugin instance")
            return
        }
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: result)
        pluginResult!.setKeepCallbackAs(keepCallback)
        instance!.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    static func sendPluginSuccess(command: CDVInvokedUrlCommand, result: [[String: Any]], keepCallback:Bool) {
        let instance = Wearable.getInstance()
        guard let _ = instance else {
            Logger.warn("sendPluginSuccess: no plugin instance")
            return
        }
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: result)
        pluginResult!.setKeepCallbackAs(keepCallback)
        instance!.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    static func sendPluginError(command: CDVInvokedUrlCommand, error:String, keepCallback:Bool) {
        let instance = Wearable.getInstance()
        guard let _ = instance else {
            Logger.warn("sendPluginSuccess: no plugin instance")
            return
        }
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error)
        pluginResult!.setKeepCallbackAs(keepCallback)
        instance!.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }

    /************************
     *Instance variables
     ************************/

    private let implementation = WearableImpl()

    /************************
     * Plugin lifecycle
     ************************/
    override func pluginInitialize() {
        super.pluginInitialize()
        Wearable.instance = self
        Logger.info("pluginInitialize")
    }
    
    override func dispose() {
        super.dispose()
        Logger.info("dispose")
        Wearable.instance = nil
    }
    
    /*****************************************
     * Plugin API functions (instance methods)
     *****************************************/
    @objc(configure:)
    func configure(_ command: CDVInvokedUrlCommand) {
        var enableDebugLogging = false
        if command.arguments.count > 0, let dict = command.arguments[0] as? [String: Any] {
            // Cross-platform option
            if let dbg = (dict["enableLogging"] as? Bool) ?? (dict["enableDebugLogging"] as? Bool) {
                enableDebugLogging = dbg
            }
            // iOS ignores capability/path and notification fields
        }

        // Ensure device supports WatchConnectivity before proceeding
        if !implementation.isSupported() {
            Wearable.sendPluginError(command: command, error: ErrorMessage.unsupported, keepCallback: false)
            return
        }

        Wearable.enableDebugLogging = enableDebugLogging

        Wearable.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(registerMessageListener:)
    func registerMessageListener(_ command:CDVInvokedUrlCommand) {
        if !implementation.isSupported() {
            Wearable.sendPluginError(command: command, error: ErrorMessage.unsupported, keepCallback: false)
            return
        }

        implementation.registerMessageListener(callback: command)
        Wearable.sendPluginNoResult(command: command)
    }

    @objc(registerEventListener:)
    func registerEventListener(_ command: CDVInvokedUrlCommand) {
        if !implementation.isSupported() {
            Wearable.sendPluginError(command: command, error: ErrorMessage.unsupported, keepCallback: false)
            return
        }

        implementation.registerEventListener(callback: command)
        Wearable.sendPluginNoResult(command: command)
    }

    @objc(registerStateListener:)
    func registerStateListener(_ command: CDVInvokedUrlCommand) {
        if !implementation.isSupported() {
            Wearable.sendPluginError(command: command, error: ErrorMessage.unsupported, keepCallback: false)
            return
        }

        implementation.registerStateListener(callback: command)
        Wearable.sendPluginNoResult(command: command)
    }

    @objc(unregisterStateListener:)
    func unregisterStateListener(_ command: CDVInvokedUrlCommand) {
        if !implementation.isSupported() {
            Wearable.sendPluginError(command: command, error: ErrorMessage.unsupported, keepCallback: false)
            return
        }

        implementation.unregisterStateListener()
        Wearable.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(getAllState:)
    func getAllState(_ command: CDVInvokedUrlCommand) {
        DispatchQueue.global(qos: .default).async {
            let snapshot = self.implementation.getAllState()
            Wearable.sendPluginSuccess(command: command, result: snapshot, keepCallback: false)
        }
    }

    @objc(setStatePath:)
    func setStatePath(_ command: CDVInvokedUrlCommand) {
        // Expect args: [ path: String, value: Any? ]
        guard command.arguments.count > 0, let path = command.arguments[0] as? String else {
            Wearable.sendPluginError(command: command, error: ErrorMessage.invalidArguments, keepCallback: false)
            return
        }
        var value: Any? = nil
        if command.arguments.count > 1 {
            value = command.arguments[1]
        }
        implementation.setStatePath(path: path, value: value)
        Wearable.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(removeStatePath:)
    func removeStatePath(_ command: CDVInvokedUrlCommand) {
        guard command.arguments.count > 0, let path = command.arguments[0] as? String else {
            Wearable.sendPluginError(command: command, error: ErrorMessage.invalidArguments, keepCallback: false)
            return
        }
        implementation.removeStatePath(path: path)
        Wearable.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(setState:)
    func setState(_ command: CDVInvokedUrlCommand) {
        // Expect args: [ path: String, value: Any? ]
        guard command.arguments.count > 0, let path = command.arguments[0] as? String else {
            Wearable.sendPluginError(command: command, error: ErrorMessage.invalidArguments, keepCallback: false)
            return
        }
        var value: Any? = nil
        if command.arguments.count > 1 {
            value = command.arguments[1]
        }
        implementation.setStatePath(path: path, value: value)
        Wearable.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(removeState:)
    func removeState(_ command: CDVInvokedUrlCommand) {
        guard command.arguments.count > 0, let path = command.arguments[0] as? String else {
            Wearable.sendPluginError(command: command, error: ErrorMessage.invalidArguments, keepCallback: false)
            return
        }
        implementation.removeState(path: path)
        Wearable.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(getState:)
    func getState(_ command: CDVInvokedUrlCommand) {
        guard command.arguments.count > 0, let path = command.arguments[0] as? String else {
            Wearable.sendPluginError(command: command, error: ErrorMessage.invalidArguments, keepCallback: false)
            return
        }
        DispatchQueue.global(qos: .default).async {
            let value = self.implementation.getState(path: path)
            var result: [String: Any] = [:]
            if let v = value {
                // Preserve null explicitly while reporting existence.
                result["exists"] = true
                result["value"] = v
            } else {
                result["exists"] = false
            }
            Wearable.sendPluginSuccess(command: command, result: result, keepCallback: false)
        }
    }

    @objc(hasState:)
    func hasState(_ command: CDVInvokedUrlCommand) {
        guard command.arguments.count > 0, let path = command.arguments[0] as? String else {
            Wearable.sendPluginError(command: command, error: ErrorMessage.invalidArguments, keepCallback: false)
            return
        }
        DispatchQueue.global(qos: .default).async {
            let exists = self.implementation.hasState(path: path)
            let result: [String: Any] = ["has": exists]
            Wearable.sendPluginSuccess(command: command, result: result, keepCallback: false)
        }
    }

    @objc(startConnectivity:)
    func startConnectivity(_ command: CDVInvokedUrlCommand) {
        implementation.startConnectivity()
        Wearable.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(stopConnectivity:)
    func stopConnectivity(_ command: CDVInvokedUrlCommand) {
        implementation.stopConnectivity()
        Wearable.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(isSupported:)
    func isSupported(_ command: CDVInvokedUrlCommand) {
        DispatchQueue.global(qos: .default).async {
            let supported = self.implementation.isSupported()
            Wearable.sendPluginSuccess(command: command, result: ["isSupported": supported], keepCallback: false)
        }
    }

    @objc(isPaired:)
    func isPaired(_ command: CDVInvokedUrlCommand) {
        DispatchQueue.global(qos: .default).async {
            let paired = self.implementation.isPaired()
            Wearable.sendPluginSuccess(command: command, result: ["isPaired": paired], keepCallback: false)
        }
    }

    @objc(isConnected:)
    func isConnected(_ command: CDVInvokedUrlCommand) {
        DispatchQueue.global(qos: .default).async {
            let connected = self.implementation.isConnected()
            Wearable.sendPluginSuccess(command: command, result: ["isConnected": connected], keepCallback: false)
        }
    }


    @objc(sendMessageToWatch:)
    func sendMessageToWatch(_ command:CDVInvokedUrlCommand) {
        // Check support/pair/connection before sending
        if !implementation.isSupported() {
            Wearable.sendPluginError(command: command, error: ErrorMessage.unsupported, keepCallback: false)
            return
        }

        if !implementation.isPaired() {
            Wearable.sendPluginError(command: command, error: ErrorMessage.notPaired, keepCallback: false)
            return
        }

        if !implementation.isConnected() {
            Wearable.sendPluginError(command: command, error: ErrorMessage.notConnected, keepCallback: false)
            return
        }

        guard command.arguments.count > 0 else {
            Wearable.sendPluginError(command: command, error: ErrorMessage.invalidArguments, keepCallback: false)
            return
        }

        if let data = command.arguments[0] as? [String: Any] {
            implementation.sendMessageToWatch(data: data)
            Wearable.sendPluginSuccess(command: command, result: ["delivered": true], keepCallback: false)
        } else {
            Wearable.sendPluginError(command: command, error: ErrorMessage.invalidArguments, keepCallback: false)
        }
    }
    


    /**********************
     * Internal functions
     **********************/
}
