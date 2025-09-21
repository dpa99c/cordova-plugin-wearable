import Foundation
import CoreFoundation

// Version: Last-writer-wins version vector for a single path
public struct Version: Codable, Comparable {
    public let ts: Int64  // timestamp in milliseconds since epoch
    public let r: String  // replica ID for tie-breaking

    public init(ts: Int64, r: String) {
        self.ts = ts
        self.r = r
    }

    // Compare versions: higher timestamp wins (most recent); if timestamps equal, compare replica id lexicographically
    public static func < (lhs: Version, rhs: Version) -> Bool {
        if lhs.ts != rhs.ts { return lhs.ts < rhs.ts }
        return lhs.r < rhs.r
    }
}

public struct StateEntry: Codable {
    public let path: String
    public let tombstone: Bool
    public let version: Version
    public let value: CodableValue?

    public init(path: String, tombstone: Bool, version: Version, value: CodableValue?) {
        self.path = path
        self.tombstone = tombstone
        self.version = version
        self.value = value
    }
}

public struct StateOp: Codable {
    public let type: String // "put" | "del"
    public let path: String
    public let value: CodableValue?
    public let version: Version
    public let opId: String
    public let ts: Int64
}

// A thin Codable wrapper to allow storing arbitrary JSON values
public enum CodableValue: Codable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: CodableValue])
    case array([CodableValue])
    case null

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
            return
        }
        if let b = try? container.decode(Bool.self) {
            self = .bool(b); return
        }
        if let n = try? container.decode(Double.self) {
            self = .number(n); return
        }
        if let s = try? container.decode(String.self) {
            self = .string(s); return
        }
        if let arr = try? container.decode([CodableValue].self) {
            self = .array(arr); return
        }
        if let obj = try? container.decode([String: CodableValue].self) {
            self = .object(obj); return
        }
        throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported JSON type")
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .null:
            try container.encodeNil()
        case .bool(let b):
            try container.encode(b)
        case .number(let n):
            try container.encode(n)
        case .string(let s):
            try container.encode(s)
        case .array(let a):
            try container.encode(a)
        case .object(let o):
            try container.encode(o)
        }
    }

    /// Convert CodableValue to Any for use in dictionaries
    public func toAny() -> Any {
        switch self {
        case .null:
            return NSNull()
        case .bool(let b):
            return b
        case .number(let n):
            if n.isFinite {
                let rounded = n.rounded(.towardZero)
                if rounded == n && rounded >= Double(Int64.min) && rounded <= Double(Int64.max) {
                    return NSNumber(value: Int64(rounded))
                }
            }
            return n
        case .string(let s):
            return s
        case .array(let a):
            return a.map { $0.toAny() }
        case .object(let o):
            return o.mapValues { $0.toAny() }
        }
    }

    /// Create CodableValue from Any
    public static func from(any: Any?) -> CodableValue? {
        guard let value = any else { return nil }
        if value is NSNull { return .null }
        if let num = value as? NSNumber {
            // Check objCType to distinguish true booleans from numeric 0/1
            let objCType = String(cString: num.objCType)
            if objCType == "c" || objCType == "B" {
                // 'c' = char (used for BOOL in Obj-C), 'B' = C++ bool
                return .bool(num.boolValue)
            }
            return .number(num.doubleValue)
        }
        if let b = value as? Bool { return .bool(b) }
        if let i = value as? Int { return .number(Double(i)) }
        if let i64 = value as? Int64 { return .number(Double(i64)) }
        if let u = value as? UInt { return .number(Double(u)) }
        if let f = value as? Float { return .number(Double(f)) }
        if let n = value as? Double { return .number(n) }
        if let s = value as? String { return .string(s) }
        if let arr = value as? [Any] {
            return .array(arr.compactMap { from(any: $0) })
        }
        if let obj = value as? [String: Any] {
            var result: [String: CodableValue] = [:]
            for (k, v) in obj {
                if let cv = from(any: v) {
                    result[k] = cv
                }
            }
            return .object(result)
        }
        return nil
    }
}
