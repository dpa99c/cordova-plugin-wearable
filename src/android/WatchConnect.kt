package cordova.plugin.wearable

import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlin.concurrent.thread

/**
 * This class connects to the watch and sends it messages
 */
class WatchConnect {

    companion object {
        private val TAG = WatchConnect::class.simpleName ?: "WatchConnect"
    }

    fun sendMessage(activity: AppCompatActivity, path: String, message: String) {
        thread {
            try {
                val capabilityInfo: CapabilityInfo = Tasks.await(
                    Wearable.getCapabilityClient(activity)
                        .getCapability(WearablePlugin.capability, CapabilityClient.FILTER_REACHABLE)
                )
                val nodes = capabilityInfo.nodes
                val bestNode = pickBestNode(nodes)
                val watchId = bestNode?.id //  ID of the paired watch
                if (watchId !== null) {
                    val watchName = bestNode.displayName
                    Logger.verbose(TAG, "Send message to watch", hashMapOf(
                        "watchId" to watchId,
                        "watchName" to watchName,
                        "path" to path,
                        "message" to message
                    ))
                    Wearable.getMessageClient(activity)
                        .sendMessage(watchId, path, message.toByteArray(Charsets.UTF_8))
                }
            }
            catch (e: Exception) {
                // if message contains "ApiException" then the phone doesn't have a Wearable API installed
                if (e.message?.contains("ApiException") == true) {
                    Logger.verbose(TAG, "ApiException in sendMessage: ${e.message}")
                }else{
                    Logger.error(TAG, "Exception in sendMessage", e)
                }
            }
        }
    }

    private fun pickBestNode(nodes: Set<Node>): Node? {
        // Find a nearby node or pick one arbitrarily
        return nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
    }
}
