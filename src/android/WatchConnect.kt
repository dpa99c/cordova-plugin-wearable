package cordova.plugin.wearable

import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlin.concurrent.thread

/**
 * Helper responsible for discovering Wear OS nodes and delivering messages/state updates.
 */
class WatchConnect {

    companion object {
        private const val TAG = "WatchConnect"
        private const val TASK_TIMEOUT_MS = 5_000L
    }

    /**
     * Send a message to the configured Wear OS capability, falling back to connected nodes when required.
     *
     * @param activity Host activity used to access Google Play Services clients.
     * @param path Wearable message path.
     * @param message UTF-8 encoded payload.
     */
    fun sendMessage(activity: AppCompatActivity, path: String, message: String) {
        thread {
            RetryHelper.withRetry(
                tag = TAG,
                operation = "sendMessage",
                maxAttempts = StateSyncSpec.Retry.MAX_ATTEMPTS
            ) { attempt ->
                try {
                    if (attempt > 1) {
                        Logger.debug(TAG, "Retry attempt $attempt for sendMessage")
                    }
                    Logger.info(TAG, "Querying capability: ${cordova.plugin.wearable.Wearable.capability}")
                    val capabilityInfo: CapabilityInfo? = awaitTaskBlocking(
                        Wearable.getCapabilityClient(activity)
                            .getCapability(cordova.plugin.wearable.Wearable.capability, CapabilityClient.FILTER_REACHABLE)
                    )
                    val nodes = capabilityInfo?.nodes ?: emptySet()
                    Logger.debug(TAG, "Capability nodes: ${nodes.map { it.id + ':' + it.displayName }}")
                    val bestNode = pickBestNode(nodes)
                    val watchId = bestNode?.id //  ID of the paired watch
                    if (watchId !== null) {
                        val watchName = bestNode.displayName
                        Logger.verbose(TAG, "Send message to watch (by capability): $watchName id: $watchId")
                        try {
                            Wearable.getMessageClient(activity)
                                .sendMessage(watchId, path, message.toByteArray(Charsets.UTF_8))
                            return@withRetry true
                        } catch (e: Exception) {
                            Logger.warn(TAG, "Failed to send to capability-selected node $watchId : ${e.message}")
                        }
                    }

                    // Fallback: try direct connected nodes
                    try {
                        val connectedNodes: List<Node> = awaitTaskBlocking(Wearable.getNodeClient(activity).connectedNodes) ?: emptyList()
                        if (connectedNodes.isNotEmpty()) {
                            Logger.verbose(TAG, "Fallback: sending to connected nodes: ${connectedNodes.map { it.id + ':' + it.displayName }}")
                            var sentToAny = false
                            for (n in connectedNodes) {
                                try {
                                    Wearable.getMessageClient(activity).sendMessage(n.id, path, message.toByteArray(Charsets.UTF_8))
                                    sentToAny = true
                                } catch (e: Exception) {
                                    Logger.warn(TAG, "Failed to send to connected node ${n.id}: ${e.message}")
                                }
                            }
                            if (sentToAny) return@withRetry true
                        } else {
                            Logger.warn(TAG, "No connected nodes available to send message")
                        }
                    } catch (e: Exception) {
                        Logger.error(TAG, "Error while attempting fallback send to connected nodes", e)
                    }
                    false
                } catch (e: Exception) {
                    // if message contains "ApiException" then the phone doesn't have a Wearable API installed
                    if (e.message?.contains("ApiException") == true) {
                        Logger.verbose(TAG, "ApiException in sendMessage: ${e.message}")
                        return@withRetry true  // Don't retry API exceptions
                    } else {
                        Logger.error(TAG, "Exception in sendMessage", e)
                        throw e
                    }
                }
            }
        }
    }

    /**
     * Return true if the Google Play Services Wearable API is available on this device.
     * This performs a lightweight capability query with FILTER_ALL; if the call throws an ApiException
     * we treat the API as not supported.
     */
    fun isSupported(activity: AppCompatActivity): Boolean {
        return try {
            // A capability request will throw if the wearable API isn't available; we don't care about nodes here,
            // only that the API call succeeds.
            awaitTaskBlocking(
                Wearable.getCapabilityClient(activity)
                    .getCapability(cordova.plugin.wearable.Wearable.capability, CapabilityClient.FILTER_ALL)
            )
            true
        } catch (e: Exception) {
            if (e.message?.contains("ApiException") == true) {
                Logger.verbose(TAG, "ApiException in isSupported: ${e.message}")
            } else {
                Logger.error(TAG, "Exception in isSupported", e)
            }
            false
        }
    }

    /**
     * Return true if the phone has at least one paired node for the configured capability.
     */
    fun isPaired(activity: AppCompatActivity): Boolean {
        return try {
            // Use NodeClient to get system connected/paired nodes (not capability-scoped)
            val nodesList: List<Node> = awaitTaskBlocking(Wearable.getNodeClient(activity).connectedNodes) ?: emptyList()
            nodesList.isNotEmpty()
        } catch (e: Exception) {
            if (e.message?.contains("ApiException") == true) {
                Logger.verbose(TAG, "ApiException in isPaired: ${e.message}")
            } else {
                Logger.error(TAG, "Exception in isPaired", e)
            }
            false
        }
    }

    /**
     * Return true if the plugin has received a recent connectivity probe from the watch app.
     * The foreground service updates the last-seen timestamp and considers it stale after a short timeout.
     */
    fun isConnected(activity: AppCompatActivity): Boolean {
        // Consider connected only if we've recently seen a connectivity request from the watch app
        return WearableForegroundService.isWatchAppConnectedNow()
    }

    /**
     * Blocking helper for non-coroutine contexts: waits for a Task<T> to complete with timeout.
     *
     * @param task Google Play Services task to await.
     * @param timeoutMs Timeout in milliseconds.
     * @return Task result or null if the task resolves with null.
     * @throws Exception When the task fails, is cancelled, or times out.
     */
    private fun <T> awaitTaskBlocking(task: Task<T>, timeoutMs: Long = TASK_TIMEOUT_MS): T? {
        val latch = CountDownLatch(1)
        var result: T? = null
        var error: Exception? = null
        task.addOnSuccessListener { res ->
            result = res
            latch.countDown()
        }.addOnFailureListener { ex ->
            error = ex as? Exception ?: Exception(ex)
            latch.countDown()
        }.addOnCanceledListener {
            latch.countDown()
        }
        try {
            val ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!ok) throw java.util.concurrent.TimeoutException("Task timed out after ${timeoutMs}ms")
            if (error != null) throw error as Exception
            return result
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Choose the most appropriate node from the capability or connected node set.
     * Prefers nearby nodes, otherwise falls back to the first entry.
     */
    private fun pickBestNode(nodes: Set<Node>): Node? {
        // Find a nearby node or pick one arbitrarily
        return nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
    }
}
