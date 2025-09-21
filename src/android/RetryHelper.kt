package cordova.plugin.wearable

import kotlin.math.min
import kotlin.math.pow

/**
 * RetryHelper provides exponential backoff retry logic for transient failures.
 */
object RetryHelper {

    /**
     * Execute an operation with exponential backoff retry logic.
     * @param tag Log tag for this operation
     * @param operation Operation name for logging
     * @param maxAttempts Maximum number of attempts (default from StateSyncSpec.Retry.MAX_ATTEMPTS)
     * @param block Operation to execute. Should return true on success, false on retryable failure. Throw exception for non-retryable failures.
     * @return true if operation succeeded within maxAttempts, false otherwise
     */
    fun withRetry(
        tag: String,
        operation: String,
        maxAttempts: Int = StateSyncSpec.Retry.MAX_ATTEMPTS,
        block: (attempt: Int) -> Boolean
    ): Boolean {
        var attempt = 1
        var lastException: Exception? = null

        while (attempt <= maxAttempts) {
            try {
                val success = block(attempt)
                if (success) {
                    if (attempt > 1) {
                        Logger.info(tag, "$operation succeeded on attempt $attempt")
                    }
                    return true
                }
                // Operation returned false, indicating retryable failure
            } catch (e: Exception) {
                lastException = e
                Logger.warn(tag, "$operation failed on attempt $attempt: ${e.message}")
            }

            if (attempt < maxAttempts) {
                val delayMs = calculateBackoffDelay(attempt)
                Logger.debug(tag, "Retrying $operation after ${delayMs}ms (attempt ${attempt + 1}/$maxAttempts)")
                Thread.sleep(delayMs)
            }
            attempt++
        }

        Logger.error(tag, "$operation failed after $maxAttempts attempts", lastException)
        return false
    }

    /**
     * Calculate the exponential backoff delay for the given attempt.
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = (StateSyncSpec.Retry.INITIAL_DELAY_MS *
            StateSyncSpec.Retry.BACKOFF_MULTIPLIER.pow(attempt - 1).toLong())
        return min(exponentialDelay, StateSyncSpec.Retry.MAX_DELAY_MS)
    }
}
