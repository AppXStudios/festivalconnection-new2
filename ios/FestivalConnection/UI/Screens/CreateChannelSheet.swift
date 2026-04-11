import SwiftUI

struct CreateChannelSheet: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) var dismiss
    @State private var channelName = ""
    @State private var channelDescription = ""

    private var isValid: Bool {
        !channelName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        Image(systemName: "number")
                            .font(.system(size: 64))
                            .foregroundColor(FestivalTheme.textSecondary)
                            .padding(.top, 30)

                        Text("Create Channel")
                            .font(.system(size: 24, weight: .heavy))
                            .foregroundColor(.white)

                        // Channel Name
                        VStack(alignment: .leading, spacing: 6) {
                            Text("CHANNEL NAME")
                                .font(.system(size: 12, weight: .medium))
                                .tracking(1)
                                .foregroundColor(FestivalTheme.textMuted)

                            TextField("EDC Mainstage Meetup", text: $channelName)
                                .foregroundColor(.white)
                                .autocorrectionDisabled()
                                .padding(14)
                                .background(FestivalTheme.surfaceMedium)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .onChange(of: channelName) { newValue in
                                    if newValue.count > 50 { channelName = String(newValue.prefix(50)) }
                                }
                        }
                        .padding(.horizontal, 24)

                        // Description
                        VStack(alignment: .leading, spacing: 6) {
                            Text("DESCRIPTION")
                                .font(.system(size: 12, weight: .medium))
                                .tracking(1)
                                .foregroundColor(FestivalTheme.textMuted)

                            TextEditor(text: $channelDescription)
                                .foregroundColor(.white)
                                .scrollContentBackground(.hidden)
                                .autocorrectionDisabled()
                                .frame(height: 80)
                                .padding(10)
                                .background(FestivalTheme.surfaceMedium)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .onChange(of: channelDescription) { newValue in
                                    if newValue.count > 150 { channelDescription = String(newValue.prefix(150)) }
                                }
                        }
                        .padding(.horizontal, 24)

                        // CrowdSync broadcast info
                        HStack(spacing: 8) {
                            Image(systemName: "antenna.radiowaves.left.and.right")
                                .font(.system(size: 16))
                                .foregroundColor(FestivalTheme.accentPink)
                            Text("Broadcasts via CrowdSync\u{2122}")
                                .font(.system(size: 14))
                                .foregroundColor(FestivalTheme.textSecondary)
                        }
                        .padding(.horizontal, 24)
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(FestivalTheme.accentPink)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        appState.createChannel(
                            name: channelName.trimmingCharacters(in: .whitespacesAndNewlines),
                            description: channelDescription.trimmingCharacters(in: .whitespacesAndNewlines)
                        )
                        dismiss()
                    }
                    .foregroundColor(isValid ? FestivalTheme.accentPink : FestivalTheme.textMuted)
                    .disabled(!isValid)
                }
            }
        }
    }
}
