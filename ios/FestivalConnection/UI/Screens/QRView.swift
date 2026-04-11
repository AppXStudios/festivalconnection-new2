import SwiftUI
import AVFoundation
import CoreImage.CIFilterBuiltins

struct QRView: View {
    @EnvironmentObject var identityManager: IdentityManager
    @EnvironmentObject var appState: AppState
    @State private var selectedMode = 0
    @State private var cameraPermission: AVAuthorizationStatus = .notDetermined
    @State private var scannedCode: String?
    @State private var scannedPeerKey: String?
    @State private var scanSessionId = UUID()

    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()

            VStack(spacing: 0) {
                Text("QR Code")
                    .font(.system(size: 34, weight: .heavy))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 12)

                Picker("Mode", selection: $selectedMode) {
                    Text("My QR").tag(0)
                    Text("Scan").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 24)

                if selectedMode == 0 {
                    myQRView
                } else {
                    scanView
                }
            }
        }
        .onAppear {
            cameraPermission = AVCaptureDevice.authorizationStatus(for: .video)
        }
    }

    // MARK: - My QR Tab

    private var myQRView: some View {
        VStack(spacing: 16) {
            Spacer()

            if let qrImage = generateQR(from: "festivalconnection://peer/\(identityManager.publicKeyHex)") {
                Image(uiImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 220, height: 220)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Text(UserDefaults.standard.string(forKey: "fc_nickname") ?? identityManager.displayName)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)

            Text("@\(UserDefaults.standard.string(forKey: "fc_handle") ?? identityManager.handle)")
                .font(.system(size: 15))
                .foregroundColor(FestivalTheme.textSecondary)

            Text("Scan to connect")
                .font(.system(size: 13))
                .foregroundColor(FestivalTheme.textMuted)

            Spacer()
        }
    }

    // MARK: - Scan Tab

    private var scanView: some View {
        Group {
            switch cameraPermission {
            case .authorized:
                if let peerKey = scannedPeerKey {
                    scannedPeerView(peerKey: peerKey)
                } else {
                    cameraPreviewView
                }
            case .notDetermined:
                requestPermissionView
            default:
                deniedPermissionView
            }
        }
        .onAppear {
            cameraPermission = AVCaptureDevice.authorizationStatus(for: .video)
            scannedCode = nil
            scannedPeerKey = nil
            scanSessionId = UUID()
        }
    }

    // MARK: - Camera Preview

    private var cameraPreviewView: some View {
        VStack(spacing: 20) {
            Spacer()

            ZStack {
                QRScannerCameraView(scannedCode: $scannedCode)
                    .id(scanSessionId)
                    .frame(width: 280, height: 280)
                    .clipShape(RoundedRectangle(cornerRadius: 20))

                RoundedRectangle(cornerRadius: 20)
                    .stroke(FestivalTheme.mainGradientDiagonal, lineWidth: 3)
                    .frame(width: 280, height: 280)
            }

            Text("Point at a QR code to connect")
                .font(.system(size: 15))
                .foregroundColor(FestivalTheme.textSecondary)

            Spacer()
        }
        .onChange(of: scannedCode) { code in
            guard let code = code, !code.isEmpty else { return }
            if code.hasPrefix("festivalconnection://peer/") {
                let peerKey = String(code.dropFirst("festivalconnection://peer/".count))
                if !peerKey.isEmpty {
                    scannedPeerKey = peerKey
                    let peer = PeerInfo(
                        publicKeyHex: peerKey,
                        displayName: "Peer \(peerKey.prefix(4).uppercased())",
                        handle: peerKey,
                        lastSeen: Date()
                    )
                    appState.updatePeer(peer)
                }
            }
        }
    }

    // MARK: - Scanned Peer View

    private func scannedPeerView(peerKey: String) -> some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(FestivalTheme.mainGradientDiagonal)

            Text("Peer Connected!")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(.white)

            Text(appState.peerName(for: peerKey))
                .font(.system(size: 15))
                .foregroundColor(FestivalTheme.textSecondary)

            Button(action: {
                scannedCode = nil
                scannedPeerKey = nil
                scanSessionId = UUID()
            }) {
                Text("Scan Another")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(FestivalTheme.mainGradientDiagonal)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 40)

            Spacer()
        }
    }

    // MARK: - Permission Request View

    private var requestPermissionView: some View {
        VStack(spacing: 16) {
            Spacer()

            GradientIcon(systemName: "camera.fill", size: 64)

            Text("Camera Access Needed")
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)

            Text("Allow camera access to scan\nFestival Connection QR codes")
                .font(.system(size: 15))
                .foregroundColor(FestivalTheme.textSecondary)
                .multilineTextAlignment(.center)

            Button(action: {
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    DispatchQueue.main.async {
                        cameraPermission = AVCaptureDevice.authorizationStatus(for: .video)
                    }
                }
            }) {
                Text("Enable Camera")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(FestivalTheme.mainGradientDiagonal)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 40)

            Spacer()
        }
    }

    // MARK: - Permission Denied View

    private var deniedPermissionView: some View {
        VStack(spacing: 16) {
            Spacer()

            GradientIcon(systemName: "camera.fill", size: 64)

            Text("Camera Access Required")
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)

            Text("Enable camera access in Settings\nto scan QR codes")
                .font(.system(size: 15))
                .foregroundColor(FestivalTheme.textSecondary)
                .multilineTextAlignment(.center)

            Button(action: {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }) {
                Text("Open Settings")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(FestivalTheme.mainGradientDiagonal)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 40)

            Spacer()
        }
    }

    // MARK: - QR Code Generator

    private func generateQR(from string: String) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
