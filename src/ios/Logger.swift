import os.log
import Foundation

public class Logger {
    private static var osLogger = nil as OSLog?

    /**************************
     * Log methods
     ***************************/
    static func error(_ message: String, _ args: CVarArg...) {
        osLog(message, level: .error, args)
    }
    static func warning(_ message: String, _ args: CVarArg...) {
        osLog(message, level: .fault, args)
    }
    static func info(_ message: String, _ args: CVarArg...) {
        osLog(message, level: .info, args)
    }
    static func debug(_ message: String, _ args: CVarArg...) {
        osLog(message, level: .debug, args)
    }
    static func verbose(_ message: String, _ args: CVarArg...) {
        osLog(message, level: .default, args)
    }

    /**************************
     * Internal methods
     ***************************/

    static func osLog(_ message: String, level: OSLogType = .default, _ args: CVarArg...) {
        if !WearablePlugin.enableDebugLogging {
            return
        }
        let formatted = String(format: message, arguments: args)

        if(Logger.osLogger == nil) {
            Logger.osLogger = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "WearablePlugin", category: "WearablePlugin")
        }

        os_log("%{public}@", log: Logger.osLogger, type: level, formatted)
    }
}
