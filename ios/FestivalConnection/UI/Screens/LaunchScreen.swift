import SwiftUI

struct LaunchScreen: View {
    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // Title
                Text("Festival Connection")
                    .font(.system(size: 32, weight: .heavy))
                    .foregroundStyle(FestivalTheme.mainGradient)
                    .padding(.bottom, 40)

                // App icon
                Image("LaunchIcon")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 200, height: 200)
                    .clipShape(RoundedRectangle(cornerRadius: 40))

                Spacer()

                // Powered by
                VStack(spacing: 4) {
                    Text("Powered by")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(FestivalTheme.textMuted)
                    Text("CrowdSync\u{2122}")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(FestivalTheme.mainGradient)
                }
                .padding(.bottom, 60)
            }
        }
    }
}
