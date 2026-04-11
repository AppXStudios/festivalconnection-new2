import Foundation

struct ChatMessage: Identifiable, Equatable {
    let id: String
    let senderKey: String
    let recipientKey: String
    let content: String
    let timestamp: Date
    let isIncoming: Bool
    var messageType: UInt8 = 0x02
    var paymentInvoice: String?
    var paymentAmount: UInt64?
    var paymentDescription: String?
    var paymentHash: Data?
    var paymentDirection: UInt8?
    var paymentConfirmed: Bool = false

    init(id: String = UUID().uuidString, senderKey: String, recipientKey: String, content: String, timestamp: Date = Date(), isIncoming: Bool, messageType: UInt8 = 0x02) {
        self.id = id
        self.senderKey = senderKey
        self.recipientKey = recipientKey
        self.content = content
        self.timestamp = timestamp
        self.isIncoming = isIncoming
        self.messageType = messageType
    }
}
