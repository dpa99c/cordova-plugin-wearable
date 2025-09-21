import Foundation

/// Minimal LWW StateStore for iOS - persistent via UserDefaults
public class StateStore {

    private let userDefaults: UserDefaults
    private let entriesKey = "wearable_state_entries_v1"
    private let peerAcksKey = "wearable_state_peer_acks_v1"
    private let replicaIdKey = "wearable_state_replica_id_v1"

    /// Normalize path by removing leading slashes to ensure consistent storage
    private func normalizePath(_ path: String) -> String {
        var normalized = path
        while normalized.hasPrefix("/") {
            normalized.removeFirst()
        }
        return normalized.isEmpty ? path : normalized  // Don't allow empty paths
    }

    private var entries: [String: StateEntry] = [:]
    // peerReplicaId -> (path -> Version)
    private var peerAcks: [String: [String: Version]] = [:]
    private var replicaId: String

    public init(suiteName: String? = nil) {
        if let name = suiteName {
            self.userDefaults = UserDefaults(suiteName: name) ?? UserDefaults.standard
        } else {
            self.userDefaults = UserDefaults.standard
        }

        // Check if we're migrating from old UUID-based replica ID
        if let oldReplicaId = userDefaults.string(forKey: replicaIdKey), oldReplicaId != "phone" {
            Logger.info("StateStore migrating from old replicaId=\(oldReplicaId) to phone, clearing state")
            // Clear old state to avoid version conflicts
            userDefaults.removeObject(forKey: entriesKey)
            userDefaults.removeObject(forKey: peerAcksKey)
        }
        
        // Use fixed replica ID for phone (not a UUID)
        self.replicaId = "phone"
        userDefaults.set(self.replicaId, forKey: replicaIdKey)
        Logger.debug("StateStore initialized with replicaId=\(self.replicaId)")

        load()
    }

    private func load() {
        // Load entries
        if let data = userDefaults.data(forKey: entriesKey) {
            if let decoded = try? JSONDecoder().decode([String: StateEntry].self, from: data) {
                entries = decoded
            }
        }

        if let data = userDefaults.data(forKey: peerAcksKey) {
            if let decoded = try? JSONDecoder().decode([String: [String: Version]].self, from: data) {
                peerAcks = decoded
            }
        }

        // Clean up duplicate path entries from previous bugs
        cleanupDuplicatePaths()
    }

    /// Clean up duplicate path entries (e.g., "alpha", "/alpha", "//alpha") by keeping the latest version
    private func cleanupDuplicatePaths() {
        var normalizedEntries: [String: StateEntry] = [:]
        var changed = false

        for (originalPath, entry) in entries {
            let normalized = normalizePath(originalPath)
            
            if originalPath != normalized {
                Logger.info("StateStore cleaning up duplicate path: \(originalPath) -> \(normalized)")
                changed = true
            }

            if let existing = normalizedEntries[normalized] {
                // Keep the entry with the latest version
                if entry.version > existing.version {
                    normalizedEntries[normalized] = entry
                }
            } else {
                normalizedEntries[normalized] = entry
            }
        }

        if changed {
            entries = normalizedEntries
            persist()
            Logger.info("StateStore cleaned up \(entries.count) unique paths")
        }
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(entries) {
            userDefaults.set(data, forKey: entriesKey)
        }
        if let data = try? JSONEncoder().encode(peerAcks) {
            userDefaults.set(data, forKey: peerAcksKey)
        }
    }

    public func getReplicaId() -> String {
        return replicaId
    }

    public func nextLocalVersion(for path: String) -> Version {
        let ts = Int64(Date().timeIntervalSince1970 * 1000)  // milliseconds since epoch
        return Version(ts: ts, r: replicaId)
    }

    public func getEntry(path: String) -> StateEntry? {
        return entries[normalizePath(path)]
    }

    public func getAllEntries() -> [StateEntry] {
        return Array(entries.values)
    }

    public func getValueAtPath(_ path: String) -> Any? {
        guard let entry = entries[normalizePath(path)], !entry.tombstone else {
            return nil
        }
        return entry.value?.toAny()
    }

    public func hasPath(_ path: String) -> Bool {
        guard let entry = entries[normalizePath(path)] else {
            return false
        }
        return !entry.tombstone
    }

    // Peer ack bookkeeping
    public func recordPeerSeen(peerReplicaId: String, path: String, version: Version) {
        var m = peerAcks[peerReplicaId] ?? [:]
        m[path] = version
        peerAcks[peerReplicaId] = m
        persist()
    }

    public func hasPeerSeen(peerReplicaId: String, path: String, version: Version) -> Bool {
        if let m = peerAcks[peerReplicaId], let v = m[path] {
            // return true if v >= version
            if !(v < version) { return true }
        }
        return false
    }

    // Remove tombstones acknowledged by peerReplicaId
    public func gcAcknowledgedTombstones(peerReplicaId: String) {
        var changed = false
        for (path, entry) in entries {
            if entry.tombstone {
                if let m = peerAcks[peerReplicaId], let v = m[path] {
                    if !(v < entry.version) {
                        entries.removeValue(forKey: path)
                        changed = true
                    }
                }
            }
        }
        if changed { persist() }
    }

    /// Apply entry using LWW semantics. Returns true if the store changed.
    @discardableResult
    public func applyEntry(_ entry: StateEntry) -> Bool {
        let normalizedPath = normalizePath(entry.path)
        if let existing = entries[normalizedPath] {
            // Compare versions
            if existing.version < entry.version {
                entries[normalizedPath] = entry
                persist()
                // Notify listeners that state changed for this path
                var userInfo: [String: Any] = [Wearable.DataKey.path: normalizedPath, Wearable.DataKey.tombstone: entry.tombstone]
                if let v = entry.value {
                    userInfo[Wearable.DataKey.value] = v.toAny()
                } else {
                    userInfo[Wearable.DataKey.value] = NSNull()
                }
                NotificationCenter.default.post(name: Notification.Name("WearableStateChanged"), object: nil, userInfo: userInfo)
                return true
            } else {
                return false
            }
        } else {
            entries[normalizedPath] = entry
            persist()
            // Notify listeners of new entry
            var userInfo: [String: Any] = [Wearable.DataKey.path: normalizedPath, Wearable.DataKey.tombstone: entry.tombstone]
            if let v = entry.value {
                userInfo[Wearable.DataKey.value] = v.toAny()
            } else {
                userInfo[Wearable.DataKey.value] = NSNull()
            }
            NotificationCenter.default.post(name: Notification.Name("WearableStateChanged"), object: nil, userInfo: userInfo)
            return true
        }
    }

    /// Materialize non-tombstone entries into a basic dictionary. Paths are simple keys.
    public func materialize() -> [String: Any] {
        var result: [String: Any] = [:]
        // Expect paths to be JSON Pointer-like (e.g. /foo/bar/0/baz) or simple keys.
        for entry in entries.values {
            if entry.tombstone { continue }
            guard let value = entry.value else { continue }
            let native = value.toAny()

            // Normalize path: allow both '/a/b' and 'a/b' and single-key paths
            var p = entry.path
            if p.hasPrefix("/") { p.removeFirst() }
            if p.isEmpty {
                // Root value — merge at top-level under empty key
                result = mergeValues(result, with: native, forPathComponents: [])
                continue
            }

            let components = p.split(separator: "/").map { String($0) }
            result = mergeValues(result, with: native, forPathComponents: components)
        }
        return result
    }

    // Merge a native value into a nested result at the given JSON pointer components.
    // Creates dictionaries or arrays as needed. When merging into an existing value,
    // nested dictionaries are deep-merged, and non-dictionary values are replaced.
    private func mergeValues(_ base: [String: Any], with value: Any, forPathComponents comps: [String]) -> [String: Any] {
        var out = base
        if comps.isEmpty {
            // Merge top-level: if value is object, deep-merge into out; else place under "value"
            if let obj = value as? [String: Any] {
                for (k, v) in obj { out[k] = v }
            } else {
                out[Wearable.DataKey.value] = value
            }
            return out
        }

        // Walk components, creating dictionaries/arrays as needed. We only support string keys and numeric array indices.
        var cursor: Any = out
        var parents: [Any] = []
        var keys: [String] = []

        for (i, comp) in comps.enumerated() {
            let isLast = (i == comps.count - 1)

            if var dict = cursor as? [String: Any] {
                if isLast {
                    dict[comp] = value
                    // rehydrate chain back into out
                    cursor = dict
                    parents.append(cursor)
                    keys.append(comp)
                    break
                } else {
                    if let next = dict[comp] {
                        parents.append(cursor)
                        keys.append(comp)
                        cursor = next
                        continue
                    } else {
                        // create intermediate dict
                        dict[comp] = [String: Any]()
                        parents.append(cursor)
                        keys.append(comp)
                        cursor = dict[comp]!
                        continue
                    }
                }
            } else if var arr = cursor as? [Any] {
                // component should be index
                if let idx = Int(comp) {
                    if idx < arr.count {
                        if isLast {
                            arr[idx] = value
                            cursor = arr
                            parents.append(cursor)
                            keys.append(String(idx))
                            break
                        } else {
                            parents.append(cursor)
                            keys.append(String(idx))
                            cursor = arr[idx]
                            continue
                        }
                    } else if idx == arr.count {
                        // append
                        if isLast {
                            arr.append(value)
                            cursor = arr
                            parents.append(cursor)
                            keys.append(String(idx))
                            break
                        } else {
                            // append an empty dict for traversal
                            let newDict: [String: Any] = [:]
                            arr.append(newDict)
                            cursor = newDict
                            parents.append(cursor)
                            keys.append(String(idx))
                            continue
                        }
                    } else {
                        // sparse index - fill with NSNull until index
                        while arr.count < idx { arr.append(NSNull()) }
                        if isLast { arr.append(value) } else { arr.append([String: Any]()) }
                        cursor = arr
                        parents.append(cursor)
                        keys.append(String(idx))
                        break
                    }
                } else {
                    // non-numeric key into array - replace
                    if isLast {
                        // cannot set named key on array; place a dict at this position
                        let newDict: [String: Any] = [comp: value]
                        cursor = newDict
                        parents.append(cursor)
                        keys.append(comp)
                        break
                    } else {
                        let newDict: [String: Any] = [:]
                        cursor = newDict
                        parents.append(cursor)
                        keys.append(comp)
                        continue
                    }
                }
            } else {
                // cursor is a primitive or NSNull - replace with dict and continue
                var newDict: [String: Any] = [:]
                if isLast {
                    newDict[comp] = value
                    cursor = newDict
                    parents.append(cursor)
                    keys.append(comp)
                    break
                } else {
                    newDict[comp] = [String: Any]()
                    cursor = newDict[comp]!
                    parents.append(cursor)
                    keys.append(comp)
                    continue
                }
            }
        }

        // Reconstruct out from parents and keys
        // Start from the deepest parent and work backwards
        if parents.isEmpty { return out }

        // Build a stack of nodes (as Any) and their keys
        var nodes = parents
        var k = keys

        // The final node (nodes.last) is the updated cursor
        var updated: Any = nodes.removeLast()
        _ = k.removeLast()

        while !nodes.isEmpty {
            let parent = nodes.removeLast()
            let key = k.removeLast()
            if var pd = parent as? [String: Any] {
                pd[key] = updated
                updated = pd
            } else if var pa = parent as? [Any] {
                if let idx = Int(key) {
                    if idx < pa.count { pa[idx] = updated } else if idx == pa.count { pa.append(updated) }
                    updated = pa
                } else {
                    // can't set non-numeric key on array — wrap
                    updated = [key: updated]
                }
            } else {
                // parent primitive — create dict
                updated = [key: updated]
            }
        }

        if let top = updated as? [String: Any] {
            // merge top into out
            for (kk, vv) in top { out[kk] = vv }
            return out
        } else {
            // final value not a dict — place under last component
            if let lastKey = comps.first {
                out[lastKey] = updated
            }
            return out
        }
    }

}
