import SwiftUI

@main
struct FestivalConnectionApp: App {
    @StateObject private var permissionsManager = PermissionsManager.shared
    @StateObject private var identityManager = IdentityManager.shared
    @StateObject private var appState = AppState.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(permissionsManager)
                .environmentObject(identityManager)
                .environmentObject(appState)
                .preferredColorScheme(.dark)
        }
    }
}
