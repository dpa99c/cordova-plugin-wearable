import Foundation

/// RetryHelper provides exponential backoff retry logic for transient failures.
class RetryHelper {
    
    struct RetryConfig {
        static let maxAttempts = 3
        static let initialDelayMs: TimeInterval = 0.5  // 500ms
        static let maxDelayMs: TimeInterval = 5.0      // 5000ms
        static let backoffMultiplier: Double = 2.0
    }
    
    /**
     Execute an operation with exponential backoff retry logic.
     - Parameters:
       - tag: Log tag for this operation
       - operation: Operation name for logging
       - maxAttempts: Maximum number of attempts (default from RetryConfig.maxAttempts)
       - block: Operation to execute. Should return true on success, false on retryable failure. Throw exception for non-retryable failures.
     - Returns: true if operation succeeded within maxAttempts, false otherwise
     */
    static func withRetry(
        tag: String,
        operation: String,
        maxAttempts: Int = RetryConfig.maxAttempts,
        block: (Int) throws -> Bool
    ) -> Bool {
        var attempt = 1
        var lastError: Error? = nil
        
        while attempt <= maxAttempts {
            do {
                let success = try block(attempt)
                if success {
                    if attempt > 1 {
                        Logger.info("\(tag): \(operation) succeeded on attempt \(attempt)")
                    }
                    return true
                }
                // Operation returned false, indicating retryable failure
            } catch {
                lastError = error
                Logger.warn("\(tag): \(operation) failed on attempt \(attempt): \(error.localizedDescription)")
            }
            
            if attempt < maxAttempts {
                let delayMs = calculateBackoffDelay(attempt: attempt)
                Logger.debug("\(tag): Retrying \(operation) after \(Int(delayMs * 1000))ms (attempt \(attempt + 1)/\(maxAttempts))")
                Thread.sleep(forTimeInterval: delayMs)
            }
            attempt += 1
        }
        
        if let error = lastError {
            Logger.error("\(tag): \(operation) failed after \(maxAttempts) attempts", error)
        } else {
            Logger.error("\(tag): \(operation) failed after \(maxAttempts) attempts with no specific error")
        }
        return false
    }
    
    private static func calculateBackoffDelay(attempt: Int) -> TimeInterval {
        let exponentialDelay = RetryConfig.initialDelayMs * pow(RetryConfig.backoffMultiplier, Double(attempt - 1))
        return min(exponentialDelay, RetryConfig.maxDelayMs)
    }
}
