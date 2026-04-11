import SwiftUI
import PhotosUI

struct EditProfileSheet: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appState: AppState
    @State private var displayName: String
    @State private var handle: String
    @State private var aboutText: String
    @State private var showImagePicker = false
    @State private var showCameraOptions = false
    @State private var showCamera = false
    @State private var showCameraUnavailable = false
    @State private var handleConflict = false

    private let originalHandle: String

    init() {
        let savedHandle = UserDefaults.standard.string(forKey: "fc_handle") ?? ""
        _displayName = State(initialValue: UserDefaults.standard.string(forKey: "fc_nickname") ?? "")
        _handle = State(initialValue: savedHandle)
        _aboutText = State(initialValue: UserDefaults.standard.string(forKey: "fc_about") ?? "")
        originalHandle = savedHandle
    }

    private var isHandleValid: Bool {
        let regex = try? NSRegularExpression(pattern: "^[a-zA-Z0-9_]{1,30}$")
        let range = NSRange(handle.startIndex..<handle.endIndex, in: handle)
        return regex?.firstMatch(in: handle, range: range) != nil
    }

    private var isHandleTaken: Bool {
        let trimmed = handle.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if trimmed == originalHandle.lowercased() { return false }
        return appState.connectedPeers.contains { $0.handle.lowercased() == trimmed }
    }

    private var isFormValid: Bool {
        !displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && isHandleValid && !isHandleTaken
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {
                        // Avatar
                        ZStack(alignment: .bottomTrailing) {
                            CircularAvatarView(displayName: displayName, size: 80)

                            Button(action: { showCameraOptions = true }) {
                                Circle()
                                    .fill(FestivalTheme.accentPink)
                                    .frame(width: 28, height: 28)
                                    .overlay(
                                        Image(systemName: "camera.fill")
                                            .font(.system(size: 14))
                                            .foregroundColor(.white)
                                    )
                            }
                        }
                        .padding(.top, 20)
                        .confirmationDialog("Profile Photo", isPresented: $showCameraOptions) {
                            Button("Choose from Library") { showImagePicker = true }
                            Button("Take Photo") {
                                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                                    showCamera = true
                                } else {
                                    showCameraUnavailable = true
                                }
                            }
                            Button("Remove Photo", role: .destructive) { }
                            Button("Cancel", role: .cancel) {}
                        }

                        // Display Name
                        VStack(alignment: .leading, spacing: 6) {
                            Text("DISPLAY NAME")
                                .font(.system(size: 12, weight: .medium))
                                .tracking(1)
                                .foregroundColor(FestivalTheme.textMuted)

                            TextField("Display Name", text: $displayName)
                                .foregroundColor(.white)
                                .autocorrectionDisabled()
                                .padding(14)
                                .background(FestivalTheme.surfaceMedium)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .onChange(of: displayName) { newValue in
                                    if newValue.count > 50 { displayName = String(newValue.prefix(50)) }
                                }
                        }

                        // Handle
                        VStack(alignment: .leading, spacing: 6) {
                            Text("HANDLE")
                                .font(.system(size: 12, weight: .medium))
                                .tracking(1)
                                .foregroundColor(FestivalTheme.textMuted)

                            HStack(spacing: 0) {
                                Text("@")
                                    .foregroundStyle(FestivalTheme.mainGradient)
                                    .font(.system(size: 17))

                                TextField("handle", text: $handle)
                                    .foregroundColor(.white)
                                    .autocapitalization(.none)
                                    .autocorrectionDisabled()
                                    .onChange(of: handle) { newValue in
                                        let filtered = newValue.filter { $0.isLetter || $0.isNumber || $0 == "_" }
                                        if filtered != newValue { handle = filtered }
                                        if handle.count > 30 { handle = String(handle.prefix(30)) }
                                    }
                            }
                            .padding(14)
                            .background(FestivalTheme.surfaceMedium)
                            .clipShape(RoundedRectangle(cornerRadius: 12))

                            if isHandleTaken {
                                Text("This handle is already taken by another user")
                                    .font(.system(size: 12))
                                    .foregroundColor(FestivalTheme.errorRed)
                            } else if !handle.isEmpty && !isHandleValid {
                                Text("Letters, numbers, and underscores only (1-30 chars)")
                                    .font(.system(size: 12))
                                    .foregroundColor(FestivalTheme.errorRed)
                            }
                        }

                        // About
                        VStack(alignment: .leading, spacing: 6) {
                            Text("ABOUT")
                                .font(.system(size: 12, weight: .medium))
                                .tracking(1)
                                .foregroundColor(FestivalTheme.textMuted)

                            TextEditor(text: $aboutText)
                                .foregroundColor(.white)
                                .autocorrectionDisabled()
                                .scrollContentBackground(.hidden)
                                .frame(height: 100)
                                .padding(10)
                                .background(FestivalTheme.surfaceMedium)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .onChange(of: aboutText) { newValue in
                                    if newValue.count > 150 { aboutText = String(newValue.prefix(150)) }
                                }
                        }
                    }
                    .padding(.horizontal, 24)
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(FestivalTheme.accentPink)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .foregroundColor(isFormValid ? FestivalTheme.accentPink : FestivalTheme.textMuted)
                        .disabled(!isFormValid)
                }
            }
            .alert("Camera Unavailable", isPresented: $showCameraUnavailable) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Camera not available on simulator")
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraPickerView()
                    .ignoresSafeArea()
            }
        }
    }

    private func save() {
        let trimmedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedHandle = handle.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "@", with: "")

        UserDefaults.standard.set(trimmedName, forKey: "fc_nickname")
        UserDefaults.standard.set(trimmedHandle, forKey: "fc_handle")
        UserDefaults.standard.set(aboutText, forKey: "fc_about")
        UserDefaults.standard.synchronize()

        dismiss()
    }
}

struct CameraPickerView: UIViewControllerRepresentable {
    @Environment(\.dismiss) var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(dismiss: dismiss) }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let dismiss: DismissAction
        init(dismiss: DismissAction) { self.dismiss = dismiss }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            dismiss()
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            dismiss()
        }
    }
}
