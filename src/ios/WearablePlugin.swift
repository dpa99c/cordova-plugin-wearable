extension String: Error {}


@objc(WearablePlugin)
class WearablePlugin : CDVPlugin{

    static var enableDebugLogging: Bool = false

    private let implementation = Wearable()
    
    /**********************
     * Plugin API functions
     ************************/
    @objc(configure:)
    func configure(_ command: CDVInvokedUrlCommand) {
        let capability = command.arguments[0] as! String // unused on iOS
        let path = command.arguments[1] as! String // unused on iOS
        let enableDebugLogging = command.arguments[2] as! Bool

        WearablePlugin.enableDebugLogging = enableDebugLogging

        self.sendPluginSuccess(command: command, keepCallback: false)
    }

    @objc(registerListener:)
    func registerListener(_ command:CDVInvokedUrlCommand) {
        if implementation.registerListener(callback: command) == true {
            self.sendPluginResult(command: command, result: ["listening": true], keepCallback: true)
        } else {
            self.sendPluginResult(command: command, result: ["listening": false], keepCallback: false)
        }
    }

    @objc(sendDataToWatch:)
    func sendDataToWatch(_ command:CDVInvokedUrlCommand) {
        if  let data = command.arguments[0] as? [String: Any] {
            implementation.sendDataToWatch(type: type, data: data)
            self.sendPluginResult(command: command, result: ["delivered": true], keepCallback: false)
        } else {
            self.sendPluginResult(command: command, result: ["delivered": false], keepCallback: false)
        }
    }
    


    /**
     * Internal functions
     */
    
    func sendPluginNoResult(command: CDVInvokedUrlCommand) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
        pluginResult!.setKeepCallbackAs(true)
        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    func sendPluginSuccess(command: CDVInvokedUrlCommand, keepCallback:Bool) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        pluginResult!.setKeepCallbackAs(keepCallback)
        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    func sendPluginSuccess(command: CDVInvokedUrlCommand, result: String, keepCallback:Bool) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: result)
        pluginResult!.setKeepCallbackAs(keepCallback)
        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    func sendPluginSuccess(command: CDVInvokedUrlCommand, result: [String: Any], keepCallback:Bool) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: result)
        pluginResult!.setKeepCallbackAs(keepCallback)
        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    func sendPluginSuccess(command: CDVInvokedUrlCommand, result: [[String: Any]], keepCallback:Bool) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: result)
        pluginResult!.setKeepCallbackAs(keepCallback)
        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
    
    func sendPluginError(command: CDVInvokedUrlCommand, error:String, keepCallback:Bool) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error)
        pluginResult!.setKeepCallbackAs(keepCallback)
        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }
}
