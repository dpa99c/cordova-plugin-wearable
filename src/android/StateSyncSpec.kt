package cordova.plugin.wearable

/**
 * StateSyncSpec centralizes constants for the Shared State synchronization protocol.
 * It mirrors the README "Shared state sync (spec)" and should be kept in sync.
 */
object StateSyncSpec {
    // Sub-paths under the configured plugin PATH, e.g. PATH = "/data" → base = "/data/state"
    const val STATE_SEGMENT = "/state"
    const val OP_SEGMENT = "/op"
    const val SNAPSHOT_SEGMENT = "/snapshot"
    const val ACK_SEGMENT = "/ack"

    // JSON fields for payloads
    object JsonKeys {
        const val TYPE = "type"           // "put" | "del"
        const val PATH = "path"           // JSON Pointer string
        const val VALUE = "value"         // any JSON (stringified)
        const val VERSION = "version"     // { c: number, r: string }
        const val OP_ID = "opId"          // uuid
        const val TS = "ts"               // optional millis

        const val FROM_REPLICA = "fromReplica"
        const val EPOCH = "epoch"
        const val ENTRIES = "entries"     // array of { path, tombstone, version, value? }
        const val TOMBSTONE = "tombstone"

        const val SEEN = "seen"           // array of { path, version }
        const val EVENT = "event"
        const val STATE = "state"

        // Version sub-keys
        const val C = "c"
        const val R = "r"

        // Snapshot request
        const val JSON = "json"
        const val REQUEST_SNAPSHOT = "requestSnapshot"
    }

    // Operation types
    object OpType {
        const val PUT = "put"
        const val DEL = "del"
    }

    // Retry and error handling constants
    object Retry {
        const val MAX_ATTEMPTS = 3
        const val INITIAL_DELAY_MS = 500L
        const val MAX_DELAY_MS = 5000L
        const val BACKOFF_MULTIPLIER = 2.0
    }
}
