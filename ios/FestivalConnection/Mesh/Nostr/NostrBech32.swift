import Foundation

enum NostrBech32 {
    private static let charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    static func encode(hrp: String, data: Data) -> String {
        let values = convertBits(data: [UInt8](data), fromBits: 8, toBits: 5, pad: true)
        let checksum = createChecksum(hrp: hrp, values: values)
        let combined = values + checksum
        let encoded = combined.map { charset[charset.index(charset.startIndex, offsetBy: Int($0))] }
        return hrp + "1" + String(encoded)
    }

    static func decode(_ str: String) -> (hrp: String, data: Data)? {
        guard let separatorIdx = str.lastIndex(of: "1") else { return nil }
        let hrp = String(str[str.startIndex..<separatorIdx]).lowercased()
        let dataStr = String(str[str.index(after: separatorIdx)...]).lowercased()

        var values: [UInt8] = []
        for char in dataStr {
            guard let idx = charset.firstIndex(of: char) else { return nil }
            values.append(UInt8(charset.distance(from: charset.startIndex, to: idx)))
        }

        guard values.count >= 6 else { return nil }
        let payload = Array(values.dropLast(6))
        let checksum = Array(values.suffix(6))

        // Verify the 6-symbol Bech32 checksum: createChecksum(hrp, payload) must equal the trailing 6 symbols.
        let expectedChecksum = createChecksum(hrp: hrp, values: payload)
        guard checksum == expectedChecksum else { return nil }

        let converted = convertBits(data: payload, fromBits: 5, toBits: 8, pad: false)
        return (hrp, Data(converted))
    }

    static func npub(from publicKeyHex: String) -> String {
        guard let data = hexToData(publicKeyHex), data.count == 32 else { return "" }
        return encode(hrp: "npub", data: data)
    }

    static func nsec(from privateKeyHex: String) -> String {
        guard let data = hexToData(privateKeyHex), data.count == 32 else { return "" }
        return encode(hrp: "nsec", data: data)
    }

    static func note(from eventIdHex: String) -> String {
        guard let data = hexToData(eventIdHex), data.count == 32 else { return "" }
        return encode(hrp: "note", data: data)
    }

    static func publicKeyHex(from npub: String) -> String? {
        guard let (hrp, data) = decode(npub), hrp == "npub", data.count == 32 else { return nil }
        return data.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Helpers

    private static func convertBits(data: [UInt8], fromBits: Int, toBits: Int, pad: Bool) -> [UInt8] {
        var acc: Int = 0
        var bits: Int = 0
        var result: [UInt8] = []
        let maxv = (1 << toBits) - 1

        for value in data {
            acc = (acc << fromBits) | Int(value)
            bits += fromBits
            while bits >= toBits {
                bits -= toBits
                result.append(UInt8((acc >> bits) & maxv))
            }
        }

        if pad {
            if bits > 0 {
                result.append(UInt8((acc << (toBits - bits)) & maxv))
            }
        }
        return result
    }

    private static func createChecksum(hrp: String, values: [UInt8]) -> [UInt8] {
        let hrpExpand = expandHRP(hrp)
        let polymodResult = polymod(hrpExpand + values + [0, 0, 0, 0, 0, 0]) ^ 1
        var checksum: [UInt8] = []
        for i in 0..<6 {
            checksum.append(UInt8((polymodResult >> (5 * (5 - i))) & 31))
        }
        return checksum
    }

    private static func expandHRP(_ hrp: String) -> [UInt8] {
        var result: [UInt8] = []
        for c in hrp.utf8 { result.append(c >> 5) }
        result.append(0)
        for c in hrp.utf8 { result.append(c & 31) }
        return result
    }

    private static func polymod(_ values: [UInt8]) -> UInt32 {
        let gen: [UInt32] = [0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3]
        var chk: UInt32 = 1
        for v in values {
            let b = chk >> 25
            chk = (chk & 0x1ffffff) << 5 ^ UInt32(v)
            for i in 0..<5 {
                if ((b >> i) & 1) != 0 {
                    chk ^= gen[i]
                }
            }
        }
        return chk
    }

    private static func hexToData(_ hex: String) -> Data? {
        var data = Data()
        var temp = hex
        while temp.count >= 2 {
            let s = String(temp.prefix(2))
            temp = String(temp.dropFirst(2))
            guard let b = UInt8(s, radix: 16) else { return nil }
            data.append(b)
        }
        return data
    }
}
