import os.log
import Foundation

public class Logger {
    private static var osLogger = nil as OSLog?

    /**************************
     * Log methods
     ***************************/
    // Accept Any? arguments so callers can pass Errors and other objects.
    static func error(_ message: String, _ args: Any?...) {
        osLog(message, level: .error, args)
    }
    static func warn(_ message: String, _ args: Any?...) {
        osLog(message, level: .fault, args)
    }
    static func info(_ message: String, _ args: Any?...) {
        osLog(message, level: .info, args)
    }
    static func debug(_ message: String, _ args: Any?...) {
        osLog(message, level: .debug, args)
    }
    static func verbose(_ message: String, _ args: Any?...) {
        osLog(message, level: .default, args)
    }

    /**************************
     * Internal methods
     ***************************/

    static func osLog(_ message: String, level: OSLogType = .default, _ args: [Any?]) {
        if !Wearable.enableDebugLogging {
            return
        }

        // Convert arbitrary arguments (including Error) to strings for formatting.
        let stringArgs: [CVarArg] = args.map { arg in
            if let err = arg as? Error {
                return String(describing: err)
            } else if let optional = arg {
                return String(describing: optional)
            } else {
                return "nil"
            }
        }

        // Safely create formatted message. If format specifiers don't match provided
        // args, fall back to concatenating.
        let formatted: String
        do {
            formatted = try String(format: message, arguments: stringArgs)
        } catch {
            // If formatting fails, build a fallback string.
            var s = message
            if !stringArgs.isEmpty {
                s += " " + stringArgs.map { String(describing: $0) }.joined(separator: " ")
            }
            formatted = s
        }

        if(Logger.osLogger == nil) {
            Logger.osLogger = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "Wearable", category: "Wearable")
        }

        os_log("%{public}@", log: Logger.osLogger!, type: level, formatted)
    }
}
