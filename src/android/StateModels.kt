package cordova.plugin.wearable

import org.json.JSONObject

/** Logical version: ts (timestamp) and r (replicaId) */
data class Version(val ts: Long, val r: String) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        return when {
            this.ts > other.ts -> 1
            this.ts < other.ts -> -1
            else -> this.r.compareTo(other.r)
        }
    }

    fun toJson(): JSONObject = JSONObject().put(StateSyncSpec.JsonKeys.TS, ts).put(StateSyncSpec.JsonKeys.R, r)

    companion object {
        fun fromJson(obj: JSONObject): Version {
            val ts = obj.optLong(StateSyncSpec.JsonKeys.TS, 0L)
            val r = obj.optString(StateSyncSpec.JsonKeys.R, "")
            return Version(ts, r)
        }
    }
}

/**
 * Entry stored per JSON Pointer path.
 * If tombstone=true, value is ignored and the path is considered deleted.
 */
data class StateEntry(val path: String, val tombstone: Boolean, val version: Version, val value: Any?) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        // Ensure path is serialized as a JSON Pointer starting with '/'
        val serializedPath = if (path.startsWith("/")) path else "/${path.trimStart('/')}"
        obj.put(StateSyncSpec.JsonKeys.PATH, serializedPath)
        obj.put(StateSyncSpec.JsonKeys.TOMBSTONE, tombstone)
        obj.put(StateSyncSpec.JsonKeys.VERSION, version.toJson())
        if (!tombstone && value != null) obj.put(StateSyncSpec.JsonKeys.VALUE, value)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): StateEntry {
            var path = obj.getString(StateSyncSpec.JsonKeys.PATH)
            // Accept stored paths with or without leading '/'; normalize to no-leading-slash internally
            if (path.startsWith("/")) path = path.substring(1)
            val tomb = obj.optBoolean(StateSyncSpec.JsonKeys.TOMBSTONE, false)
            val ver = Version.fromJson(obj.getJSONObject(StateSyncSpec.JsonKeys.VERSION))
            val value = if (!tomb && obj.has(StateSyncSpec.JsonKeys.VALUE)) obj.get(StateSyncSpec.JsonKeys.VALUE) else null
            return StateEntry(path, tomb, ver, value)
        }
    }
}

/** Outbound operation payload */
data class StateOp(val type: String, val path: String, val version: Version, val value: Any?, val opId: String, val ts: Long) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put(StateSyncSpec.JsonKeys.TYPE, type)
        obj.put(StateSyncSpec.JsonKeys.PATH, path)
        obj.put(StateSyncSpec.JsonKeys.VERSION, version.toJson())
        obj.put(StateSyncSpec.JsonKeys.OP_ID, opId)
        obj.put(StateSyncSpec.JsonKeys.TS, ts)
        if (type == StateSyncSpec.OpType.PUT && value != null) obj.put(StateSyncSpec.JsonKeys.VALUE, value)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): StateOp {
            val type = obj.getString(StateSyncSpec.JsonKeys.TYPE)
            val path = obj.getString(StateSyncSpec.JsonKeys.PATH)
            val ver = Version.fromJson(obj.getJSONObject(StateSyncSpec.JsonKeys.VERSION))
            val opId = obj.optString(StateSyncSpec.JsonKeys.OP_ID)
            val ts = obj.optLong(StateSyncSpec.JsonKeys.TS, System.currentTimeMillis())
            val value = if (type == StateSyncSpec.OpType.PUT && obj.has(StateSyncSpec.JsonKeys.VALUE)) obj.get(StateSyncSpec.JsonKeys.VALUE) else null
            return StateOp(type, path, ver, value, opId, ts)
        }
    }
}
