import SwiftUI

struct SettingUpView: View {
    @EnvironmentObject var identityManager: IdentityManager
    @State private var rotation: Double = 0

    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()

            VStack(spacing: 16) {
                Text("Festival Connection")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundStyle(FestivalTheme.mainGradient)

                Text("Setting up your connection...")
                    .font(.system(size: 15))
                    .foregroundColor(FestivalTheme.textSecondary)

                ZStack {
                    Circle()
                        .stroke(FestivalTheme.surfaceDark, lineWidth: 4)
                        .frame(width: 80, height: 80)

                    Circle()
                        .trim(from: 0, to: 0.75)
                        .stroke(
                            AngularGradient(
                                colors: FestivalTheme.mainGradientColors + [.clear],
                                center: .center,
                                startAngle: .degrees(0),
                                endAngle: .degrees(300)
                            ),
                            style: StrokeStyle(lineWidth: 4, lineCap: .round)
                        )
                        .frame(width: 80, height: 80)
                        .rotationEffect(.degrees(rotation))
                }
                .padding(.top, 32)
            }
        }
        .onAppear {
            withAnimation(.linear(duration: 1.5).repeatForever(autoreverses: false)) {
                rotation = 360
            }
            Task {
                await identityManager.initialize()
                NostrIdentity.shared.initialize()
            }
        }
    }
}
