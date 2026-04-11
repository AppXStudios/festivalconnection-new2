import SwiftUI

struct PermissionsView: View {
    @EnvironmentObject var permissionsManager: PermissionsManager
    let onGetStarted: () -> Void

    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer().frame(height: 60)

                Text("Permissions Required")
                    .font(.system(size: 28, weight: .heavy))
                    .foregroundStyle(FestivalTheme.mainGradient)

                Text("Festival Connection needs these permissions\nto work")
                    .font(.system(size: 15))
                    .foregroundColor(FestivalTheme.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)

                VStack(spacing: 0) {
                    permissionRow(
                        icon: "antenna.radiowaves.left.and.right",
                        name: "Bluetooth",
                        description: "Used by CrowdSync™ to discover\nnearby people",
                        isGranted: permissionsManager.bluetoothStatus == .allowedAlways,
                        iconColor: FestivalTheme.iconBlue
                    )
                    Divider().background(FestivalTheme.surfaceMedium)
                    permissionRow(
                        icon: "wifi",
                        name: "Wi-Fi",
                        description: "Used by CrowdSync™ for nearby\nconnections",
                        isGranted: permissionsManager.wifiPermissionTriggered,
                        iconColor: FestivalTheme.iconBlue
                    )
                    Divider().background(FestivalTheme.surfaceMedium)
                    permissionRow(
                        icon: "location.fill",
                        name: "Location",
                        description: "Required to find people nearby\nand enable local channels",
                        isGranted: permissionsManager.locationStatus == .authorizedWhenInUse || permissionsManager.locationStatus == .authorizedAlways,
                        iconColor: FestivalTheme.iconPurple
                    )
                    Divider().background(FestivalTheme.surfaceMedium)
                    permissionRow(
                        icon: "bell.fill",
                        name: "Notifications",
                        description: "Optional — so you never miss\na message",
                        isGranted: permissionsManager.notificationStatus == .authorized,
                        iconColor: FestivalTheme.iconRed
                    )
                }
                .background(FestivalTheme.surfaceDark)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding(.horizontal, 24)
                .padding(.top, 40)

                Spacer()

                Button(action: {
                    if permissionsManager.allRequiredGranted {
                        onGetStarted()
                    } else {
                        permissionsManager.requestAllPermissions()
                    }
                }) {
                    Text(permissionsManager.allRequiredGranted ? "Get Started" : "Enable All")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(FestivalTheme.mainGradientDiagonal)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 40)
                .disabled(false)
            }
        }
        .onAppear {
            permissionsManager.startAutoPermissionRequest()
            permissionsManager.refreshAllStatuses()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            permissionsManager.refreshAllStatuses()
        }
    }

    private func permissionRow(icon: String, name: String, description: String, isGranted: Bool, iconColor: Color = .white) -> some View {
        HStack(spacing: 16) {
            GradientIcon(systemName: icon, size: 28)
                .frame(width: 32, height: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(FestivalTheme.textPrimary)
                Text(description)
                    .font(.system(size: 13))
                    .foregroundColor(FestivalTheme.textSecondary)
            }

            Spacer()

            if isGranted {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 24))
                    .foregroundColor(FestivalTheme.presenceGreen)
            } else {
                Circle()
                    .stroke(FestivalTheme.textMuted, lineWidth: 2)
                    .frame(width: 24, height: 24)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}
