import SwiftUI

struct NumericKeypad: View {
    @Binding var amount: String

    private let keys = [["1","2","3"],["4","5","6"],["7","8","9"],[".","0","<"]]

    var body: some View {
        VStack(spacing: 12) {
            ForEach(keys, id: \.self) { row in
                HStack(spacing: 16) {
                    ForEach(row, id: \.self) { key in
                        Button(action: { tapKey(key) }) {
                            if key == "<" {
                                Image(systemName: "delete.left")
                                    .font(.system(size: 22))
                                    .foregroundColor(.white)
                                    .frame(width: 72, height: 72)
                                    .background(FestivalTheme.surfaceMedium)
                                    .clipShape(Circle())
                            } else {
                                Text(key)
                                    .font(.system(size: 28, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(width: 72, height: 72)
                                    .background(FestivalTheme.surfaceMedium)
                                    .clipShape(Circle())
                            }
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 24)
    }

    private func tapKey(_ key: String) {
        if key == "<" {
            if !amount.isEmpty { amount.removeLast() }
        } else if key == "." {
            if !amount.contains(".") { amount += "." }
        } else {
            amount += key
        }
    }
}
