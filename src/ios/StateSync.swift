import Foundation
import WatchConnectivity

/// StateSync: minimal implementation to send/receive ops and snapshots via WCSession applicationContext
public class StateSync {

    private let store: StateStore
    private let basePath: String
    private var session: WCSession? = nil
    private let watchConnect = WatchConnect.shared
    private let messageNotificationName = Notification.Name("WearableStateSyncMessage")

    // Debounce ack flush (not fully implemented in Phase 1)
    private var pendingAcks: [StateEntry] = []
    private var ackFlushWorkItem: DispatchWorkItem? = nil

    public init(store: StateStore, configuredPath: String = "/data") {
        self.store = store
        self.basePath = configuredPath + "/state"

        if WCSession.isSupported() {
            self.session = WCSession.default
            // Observe applicationContext via NotificationCenter (WatchConnect forwards it)
            NotificationCenter.default.addObserver(self, selector: #selector(handleApplicationContextNotification(_:)), name: Notification.Name("WearableApplicationContextReceived"), object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(handleMessageNotification(_:)), name: messageNotificationName, object: nil)
        } else {
            Logger.error("WCSession not supported - StateSync disabled")
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    public func start() {
        Logger.debug("StateSync start invoked")
        // Bootstrap: attempt to read current applicationContext
        if let ctx = try? session?.receivedApplicationContext {
            if let dict = ctx as? [String: Any] {
                handleApplicationContext(dict)
            }
        }
        // Bi-directional handshake: send our snapshot and request peer's snapshot
        sendSnapshot()
        requestPeerSnapshot()
    }

    public func stop() {
        // noop for now
        Logger.debug("StateSync stop invoked")
    }

    public func setPath(_ path: String, value: Any?) {
        // Create op and apply locally
        let opId = UUID().uuidString
        let ts = Int64(Date().timeIntervalSince1970 * 1000)
        let version = store.nextLocalVersion(for: path)

        // Convert incoming Any value into a CodableValue using the generic helper
        var codableValue: CodableValue? = nil
        if let v = value {
            // Use the centralized converter which supports nested arrays/objects and numeric types
            codableValue = CodableValue.from(any: v)
        }

        let entry = StateEntry(path: path, tombstone: value == nil, version: version, value: codableValue)
        _ = store.applyEntry(entry)

        // Dispatch local notification for state listeners
        var userInfo: [String: Any] = [Wearable.DataKey.path: path, Wearable.DataKey.tombstone: value == nil]
        if let v = value {
            userInfo[Wearable.DataKey.value] = v
        }
        NotificationCenter.default.post(name: Notification.Name("WearableStateChanged"), object: nil, userInfo: userInfo)

        // Send as applicationContext update: include op under base/op/opId
        let opKey = basePath + "/op/" + opId
        var payload: [String: Any] = [:]
        payload[Wearable.DataKey.type] = (value == nil) ? "del" : "put"
        payload[Wearable.DataKey.path] = path
        payload[Wearable.DataKey.version] = ["ts": version.ts, "r": version.r]
        payload[Wearable.DataKey.opId] = opId
        payload[Wearable.DataKey.ts] = ts
        if let v = codableValue {
            // Use toAny() to preserve type information correctly
            payload[Wearable.DataKey.value] = v.toAny()
        }

        // Only send ops if connectivity is enabled
        guard watchConnect.getIsConnectivityEnabled() else {
            Logger.debug("Skipping op send because connectivity is disabled")
            return
        }

        if let session = session, session.isPaired {
            let success = RetryHelper.withRetry(
                tag: "StateSync",
                operation: "setPath",
                maxAttempts: RetryHelper.RetryConfig.maxAttempts
            ) { attempt -> Bool in
                if attempt > 1 {
                    Logger.debug("Retry attempt \(attempt) for setPath: \(path)")
                }
                do {
                    try session.updateApplicationContext([opKey: payload])
                    Logger.debug("StateSync sent op via applicationContext key=\(opKey)")
                    return true
                } catch {
                    Logger.warn("Failed to update applicationContext on attempt \(attempt): \(error.localizedDescription)")
                    if attempt >= RetryHelper.RetryConfig.maxAttempts {
                        // Last attempt, fall back to message
                        self.sendOpViaMessage(payload: payload)
                        return true
                    }
                    throw error
                }
            }
            
            if !success {
                Logger.error("Failed to send op after all retries")
            }
        } else {
            Logger.debug("Session not available or not paired; falling back to message")
            sendOpViaMessage(payload: payload)
        }
    }

    // Create and send a full snapshot of the store via applicationContext.
    // Uses a simple size check and falls back to transferUserInfo when the encoded payload
    // exceeds ~100KB (applicationContext size varies across watchOS versions).
    public func sendSnapshot() {
        let success = RetryHelper.withRetry(
            tag: "StateSync",
            operation: "sendSnapshot",
            maxAttempts: RetryHelper.RetryConfig.maxAttempts
        ) { attempt -> Bool in
            if attempt > 1 {
                Logger.debug("Retry attempt \(attempt) for sendSnapshot")
            }
            
            let snapshotId = UUID().uuidString
            let key = self.basePath + "/snapshot/" + snapshotId
            // Snapshot should include per-path versions. Build a flat dictionary: path -> { value, version }
            var snapshot: [String: Any] = [:]
            for entry in self.store.getAllEntries() {
                if entry.tombstone { continue }
                var item: [String: Any] = [:]
                if let v = entry.value {
                    item[Wearable.DataKey.value] = v.toAny()
                }
                item[Wearable.DataKey.version] = ["ts": entry.version.ts, "r": entry.version.r]
                snapshot[entry.path] = item
            }

            guard let data = try? JSONSerialization.data(withJSONObject: snapshot, options: []) else {
                Logger.error("Failed to serialize snapshot")
                return false
            }

            // Naive size threshold — if large, use transferUserInfo
            let threshold = 100 * 1024 // 100KB
            if data.count > threshold {
                // transferUserInfo fallback
                if let session = self.session, session.isPaired {
                    do {
                        try session.updateApplicationContext([key: "snapshot-ref"])
                    } catch {
                        Logger.warn("Failed to update small applicationContext for snapshot ref on attempt \(attempt): \(error.localizedDescription)")
                        if attempt >= RetryHelper.RetryConfig.maxAttempts {
                            throw error
                        }
                        return false
                    }
                    // send actual payload via transferUserInfo
                    session.transferUserInfo(["snapshotId": snapshotId, "payload": data.base64EncodedString()])
                }
                return true
            }

            // Encode snapshot as UTF8 string and send via applicationContext
            if let s = String(data: data, encoding: .utf8), let session = self.session, session.isPaired {
                do {
                    try session.updateApplicationContext([key: s])
                    Logger.debug("Sent snapshot via applicationContext: \(key)")
                    return true
                } catch {
                    Logger.warn("Failed to update applicationContext with snapshot on attempt \(attempt): \(error.localizedDescription)")
                    throw error
                }
            }
            return false
        }
        
        if !success {
            Logger.error("Failed to send snapshot after all retries")
        }
    }

    @objc private func handleApplicationContextNotification(_ note: Notification) {
        if let dict = note.userInfo as? [String: Any] {
            handleApplicationContext(dict)
        }
    }

    @objc private func handleMessageNotification(_ note: Notification) {
        if let dict = note.userInfo as? [String: Any] {
            handleMessage(dict)
        }
    }

    // Basic handler: scan for op keys under basePath/op/* and apply them
    private func handleApplicationContext(_ context: [String: Any]) {
        for (k, v) in context {
            if k.hasPrefix(basePath + "/op/") {
                if let payload = v as? [String: Any] {
                    processOpPayload(payload)
                }
            } else if k.hasPrefix(basePath + "/snapshot/") {
                // Received a snapshot string (JSON) or a small marker pointing to transferUserInfo
                if let s = v as? String {
                    // Attempt to parse as JSON
                    if let data = s.data(using: .utf8) {
                        if let obj = try? JSONSerialization.jsonObject(with: data, options: []), let dict = obj as? [String: Any] {
                            applySnapshotDict(dict)
                        }
                    }
                }
            } else if k.hasPrefix(basePath + "/ack/") {
                if let s = v as? String {
                    // ack payload is a JSON object mapping path -> { c, r } or peer id->map; try to parse
                    if let data = s.data(using: .utf8), let obj = try? JSONSerialization.jsonObject(with: data, options: []), let dict = obj as? [String: Any] {
                        processAckDict(dict)
                    }
                } else if let dict = v as? [String: Any] {
                    processAckDict(dict)
                }
            } else if k.contains("/request/") {
                if let payload = v as? [String: Any], let type = payload[Wearable.DataKey.type] as? String, type == "requestSnapshot" {
                    handleSnapshotRequest()
                }
            }
        }
    }

    private func handleMessage(_ message: [String: Any]) {
        guard let type = message[Wearable.DataKey.type] as? String else {
            Logger.verbose("Ignoring message without type: \(message)")
            return
        }

        switch type {
        case "stateOp":
            if let payload = message[Wearable.DataKey.op] as? [String: Any] {
                Logger.debug("Processing stateOp message for path=\(payload[Wearable.DataKey.path] ?? "<unknown>")")
                processOpPayload(payload)
            } else {
                Logger.warn("stateOp message missing payload: \(message)")
            }
        case "stateAck":
            if let ack = message[Wearable.DataKey.ack] as? [String: Any] {
                Logger.verbose("Processing stateAck message with paths=\(Array(ack.keys))")
                processAckDict(ack)
            } else {
                Logger.warn("stateAck message missing payload: \(message)")
            }
        case "stateSnapshot":
            if let snapshot = message[Wearable.DataKey.snapshot] as? [String: Any] {
                Logger.debug("Processing stateSnapshot message with \(snapshot.count) entries")
                applySnapshotDict(snapshot)
            } else {
                Logger.warn("stateSnapshot message missing payload: \(message)")
            }
        case "requestSnapshot":
            Logger.debug("Processing requestSnapshot message")
            handleSnapshotRequest()
        default:
            Logger.verbose("Unhandled state sync message type: \(type)")
        }
    }

    // Add an entry to pendingAcks and schedule flush
    public func recordAckForEntry(_ entry: StateEntry) {
        pendingAcks.append(entry)
        scheduleAckFlush()
    }

    private func scheduleAckFlush() {
        ackFlushWorkItem?.cancel()
        let wi = DispatchWorkItem { [weak self] in
            self?.flushAcks()
        }
        ackFlushWorkItem = wi
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.5, execute: wi)
    }

    private func flushAcks() {
        let toSend = pendingAcks
        pendingAcks = []
        guard !toSend.isEmpty else { return }

        // Build ack map: path -> { ts, r }
        var ackMap: [String: Any] = [:]
        for e in toSend {
            ackMap[e.path] = ["ts": e.version.ts, "r": e.version.r]
        }

        let ackId = UUID().uuidString
        let key = basePath + "/ack/" + ackId
        
        let success = RetryHelper.withRetry(
            tag: "StateSync",
            operation: "flushAcks",
            maxAttempts: RetryHelper.RetryConfig.maxAttempts
        ) { attempt -> Bool in
            if attempt > 1 {
                Logger.debug("Retry attempt \(attempt) for flushAcks")
            }
            
            if let session = self.session, session.isPaired {
                if let data = try? JSONSerialization.data(withJSONObject: ackMap, options: []), let s = String(data: data, encoding: .utf8) {
                    do {
                        try session.updateApplicationContext([key: s])
                        Logger.debug("Sent ack via applicationContext: \(key)")
                        return true
                    } catch {
                        Logger.warn("Failed to send ack on attempt \(attempt): \(error.localizedDescription)")
                        if attempt >= RetryHelper.RetryConfig.maxAttempts {
                            // Last attempt, fall back to message
                            self.sendAckViaMessage(ackMap: ackMap)
                            return true
                        }
                        throw error
                    }
                }
            }
            return false
        }
        
        if !success {
            Logger.error("Failed to flush acks after all retries")
        }
    }

    private func processAckDict(_ dict: [String: Any]) {
        // dict: path -> { ts, r }
        for (path, val) in dict {
            if let vdict = val as? [String: Any], let ts = vdict["ts"] as? Int64, let r = vdict["r"] as? String {
                let version = Version(ts: ts, r: r)
                // record that peer with unknown id has seen this path@version. We don't have explicit peer id here; use placeholder 'peer'
                store.recordPeerSeen(peerReplicaId: Wearable.DataKey.peer, path: path, version: version)
            }
        }
        // After recording, perform GC for that peer
        store.gcAcknowledgedTombstones(peerReplicaId: Wearable.DataKey.peer)
    }

    private func applySnapshotDict(_ dict: [String: Any]) {
        // dict is a snapshot where each key is a path and each value is { value: ..., version: { ts, r } }
        for (k, v) in dict {
            let path = k  // Use key as-is since it's already the full path
            guard let item = v as? [String: Any] else {
                Logger.warn("Snapshot item for path \(path) is not a dictionary, skipping")
                continue
            }
            
            // Extract the actual value (not the version metadata)
            var codableValue: CodableValue? = nil
            if let actualValue = item[Wearable.DataKey.value] {
                codableValue = CodableValue.from(any: actualValue)
            }
            
            // Extract the version metadata
            var version: Version
            if let versionDict = item[Wearable.DataKey.version] as? [String: Any],
               let ts = versionDict["ts"] as? Int64,
               let r = versionDict["r"] as? String {
                version = Version(ts: ts, r: r)
            } else {
                // Fallback if version is missing
                Logger.warn("Snapshot item for path \(path) missing version, using next local version")
                version = store.nextLocalVersion(for: path)
            }
            
            let entry = StateEntry(path: path, tombstone: false, version: version, value: codableValue)
            _ = store.applyEntry(entry)
        }
        Logger.debug("Applied snapshot with \(dict.keys.count) keys")
    }

    private func processOpPayload(_ payload: [String: Any]) {
        guard let type = payload[Wearable.DataKey.type] as? String,
              let path = payload[Wearable.DataKey.path] as? String,
              let versionDict = payload[Wearable.DataKey.version] as? [String: Any],
              let ts = versionDict["ts"] as? Int64,
              let r = versionDict["r"] as? String else {
            Logger.warn("Malformed op payload: \(payload)")
            return
        }

        let version = Version(ts: ts, r: r)
        var codableValue: CodableValue? = nil
        // Try to get value directly (new format) or from JSON string (legacy format)
        if let val = payload[Wearable.DataKey.value] {
            codableValue = CodableValue.from(any: val)
        } else if let valStr = payload[Wearable.DataKey.value] as? String, let data = valStr.data(using: .utf8) {
            codableValue = try? JSONDecoder().decode(CodableValue.self, from: data)
        }

        let entry = StateEntry(path: path, tombstone: (type == "del"), version: version, value: codableValue)
        let changed = store.applyEntry(entry)
        Logger.info("StateSync applied entry path=\(path) version=\(version.ts):\(version.r) tombstone=\(type == "del") changed=\(changed)")
        if changed {
            Logger.debug("Applied remote op for path \(path)")
        }
    }

    private func sendOpViaMessage(payload: [String: Any]) {
        Logger.debug("StateSync falling back to message for op path=\(payload[Wearable.DataKey.path] ?? "<unknown>")")
        watchConnect.sendMessage(data: ["type": "stateOp", "op": payload])
    }

    private func sendAckViaMessage(ackMap: [String: Any]) {
        Logger.verbose("StateSync falling back to message for ack keys=\(Array(ackMap.keys))")
        watchConnect.sendMessage(data: ["type": "stateAck", "ack": ackMap])
    }

    private func requestPeerSnapshot() {
        let payload: [String: Any] = [
            Wearable.DataKey.type: "requestSnapshot",
            "fromReplica": "ios"
        ]
        let key = basePath + "/request/" + UUID().uuidString
        
        guard watchConnect.getIsConnectivityEnabled() else {
            Logger.debug("Skipping snapshot request because connectivity is disabled")
            return
        }
        
        if let session = session, session.isPaired {
            let success = RetryHelper.withRetry(
                tag: "StateSync",
                operation: "requestPeerSnapshot",
                maxAttempts: RetryHelper.RetryConfig.maxAttempts
            ) { attempt -> Bool in
                if attempt > 1 {
                    Logger.debug("Retry attempt \(attempt) for requestPeerSnapshot")
                }
                do {
                    try session.updateApplicationContext([key: payload])
                    Logger.debug("Requested peer snapshot via applicationContext")
                    return true
                } catch {
                    Logger.warn("Failed to request peer snapshot on attempt \(attempt): \(error.localizedDescription)")
                    if attempt >= RetryHelper.RetryConfig.maxAttempts {
                        // Last attempt, fall back to message
                        self.watchConnect.sendMessage(data: ["type": "requestSnapshot"])
                        return true
                    }
                    throw error
                }
            }
            
            if !success {
                Logger.error("Failed to request peer snapshot after all retries")
            }
        } else {
            Logger.debug("Session not available or not paired; falling back to message for snapshot request")
            watchConnect.sendMessage(data: ["type": "requestSnapshot"])
        }
    }

    private func handleSnapshotRequest() {
        Logger.debug("Received snapshot request, sending snapshot")
        sendSnapshot()
    }
}
