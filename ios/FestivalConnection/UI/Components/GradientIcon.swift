import SwiftUI

struct GradientIcon: View {
    let systemName: String
    var size: CGFloat = 24
    var accessibilityLabel: String = ""
    var colors: [Color]? = nil

    var body: some View {
        let gradientColors = colors ?? FestivalTheme.mainGradientColors
        Rectangle()
            .fill(LinearGradient(
                colors: gradientColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ))
            .frame(width: size, height: size)
            .mask(
                Image(systemName: systemName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: size, height: size)
            )
            .accessibilityLabel(accessibilityLabel.isEmpty ? systemName : accessibilityLabel)
    }
}
