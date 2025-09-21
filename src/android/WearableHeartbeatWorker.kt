package cordova.plugin.wearable

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Worker that sends a small heartbeat to connected nodes when appropriate.
 * It checks for connected nodes and reachable capability nodes and only sends
 * heartbeats if at least one node is available (gating by reachability).
 */
class WearableHeartbeatWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val TAG = "WearableHeartbeatWorker"
    private val capability = cordova.plugin.wearable.Wearable.capability

    private fun configuredPath(): String {
        val configured = cordova.plugin.wearable.Wearable.path
        return if (configured.isNotEmpty()) configured else DEFAULT_MESSAGE_PATH
    }

    companion object {
        private const val DEFAULT_MESSAGE_PATH = "/data"
        private const val HEARTBEAT_TYPE_KEY = "type"
        private const val HEARTBEAT_TYPE_PHONE_READY = "phone_ready"
        private const val AWAIT_TIMEOUT_SECS = 3L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.v(TAG, "Heartbeat worker running - checking connected nodes")

            // First check directly connected nodes
            val connected = try {
                Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes, AWAIT_TIMEOUT_SECS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query connected nodes", e)
                null
            }

            if (connected != null && connected.isNotEmpty()) {
                val heartbeat = JSONObject().put(HEARTBEAT_TYPE_KEY, HEARTBEAT_TYPE_PHONE_READY).toString()
                for (n in connected) {
                    try {
                        Log.v(TAG, "Heartbeat: sending to ${n.id}")
                        Wearable.getMessageClient(applicationContext)
                            .sendMessage(n.id, configuredPath(), heartbeat.toByteArray(Charsets.UTF_8))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send heartbeat to ${n.id}", e)
                    }
                }
                return@withContext Result.success()
            }

            // Fallback: check capability reachable nodes
            val capInfo = try {
                Tasks.await(Wearable.getCapabilityClient(applicationContext).getCapability(capability, com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE), AWAIT_TIMEOUT_SECS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query capability info", e)
                null
            }

            val capNodes = capInfo?.nodes ?: emptySet()
            if (capNodes.isNotEmpty()) {
                val heartbeat = JSONObject().put(HEARTBEAT_TYPE_KEY, HEARTBEAT_TYPE_PHONE_READY).toString()
                for (n in capNodes) {
                    try {
                        Log.v(TAG, "Heartbeat (capability) sending to ${n.id}")
                        Wearable.getMessageClient(applicationContext)
                            .sendMessage(n.id, configuredPath(), heartbeat.toByteArray(Charsets.UTF_8))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send capability heartbeat to ${n.id}", e)
                    }
                }
                return@withContext Result.success()
            }

            Log.v(TAG, "No nodes available for heartbeat - skipping")
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat worker failed", e)
            return@withContext Result.retry()
        }
    }
}
