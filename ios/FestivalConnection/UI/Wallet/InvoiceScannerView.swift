import SwiftUI
import AVFoundation

struct InvoiceScannerView: View {
    @Environment(\.dismiss) var dismiss
    var payAmountCents: String = ""
    @State private var selectedMode = 0
    @State private var pastedInvoice = ""
    @State private var showParsedAlert = false
    @State private var parsedAmount: String = ""
    @State private var isSending = false
    @State private var sendResult: String?
    @State private var showSendResult = false
    @State private var scannedCode: String?

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 20) {
                    Picker("Mode", selection: $selectedMode) {
                        Text("Scanner").tag(0)
                        Text("Paste").tag(1)
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 24)
                    .padding(.top, 20)

                    if selectedMode == 0 {
                        // Live camera QR scanner
                        QRScannerCameraView(scannedCode: $scannedCode)
                            .frame(height: 300)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(FestivalTheme.mainGradient, lineWidth: 3)
                            )
                            .padding(.horizontal, 24)
                            .onChange(of: scannedCode) { code in
                                if let code = code, !code.isEmpty {
                                    pastedInvoice = code
                                    // Auto-parse scanned invoice
                                    Task {
                                        do {
                                            let parsed = try WalletManager.shared.parseInvoice(code)
                                            parsedAmount = parsed.amountSat.map { "\($0) sats" } ?? "Unknown amount"
                                        } catch {
                                            parsedAmount = "Scanned: \(code.prefix(30))..."
                                        }
                                        showParsedAlert = true
                                    }
                                }
                            }
                    } else {
                        VStack(spacing: 16) {
                            TextField("Paste invoice here", text: $pastedInvoice, axis: .vertical)
                                .foregroundColor(.white)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                                .lineLimit(5...10)
                                .padding(14)
                                .background(FestivalTheme.surfaceMedium)
                                .clipShape(RoundedRectangle(cornerRadius: 12))

                            Button("Continue") {
                                guard !pastedInvoice.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                                isSending = true
                                Task {
                                    do {
                                        let parsed = try WalletManager.shared.parseInvoice(pastedInvoice.trimmingCharacters(in: .whitespacesAndNewlines))
                                        parsedAmount = parsed.amountSat.map { "\($0) sats" } ?? "Unknown amount"
                                        showParsedAlert = true
                                    } catch {
                                        parsedAmount = "Invalid invoice"
                                        showParsedAlert = true
                                    }
                                    isSending = false
                                }
                            }
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 10)
                                .background(FestivalTheme.mainGradient)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .padding(.horizontal, 24)
                    }

                    Spacer()
                }
            }
            .alert("Invoice Received", isPresented: $showParsedAlert) {
                Button("Pay") {
                    Task {
                        // Convert cents to sats if user entered an amount
                        let amountSat: UInt64? = {
                            guard !payAmountCents.isEmpty, let cents = Double(payAmountCents) else { return nil }
                            let usd = cents / 100.0
                            // Use the live sats-per-USD rate maintained by WalletManager
                            // (refreshed from Breez fetchFiatRates() on every refreshBalance).
                            return UInt64(usd * WalletManager.shared.satPerUSD)
                        }()
                        try? await WalletManager.shared.sendPayment(invoice: pastedInvoice.trimmingCharacters(in: .whitespacesAndNewlines), amountSat: amountSat)
                        dismiss()
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text(parsedAmount)
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(FestivalTheme.accentPink)
                }
            }
        }
    }
}

// MARK: - Live Camera QR Scanner

struct QRScannerCameraView: UIViewRepresentable {
    @Binding var scannedCode: String?

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .black

        let session = AVCaptureSession()
        context.coordinator.session = session

        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            return view
        }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        if session.canAddOutput(output) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(context.coordinator, queue: .main)
            output.metadataObjectTypes = [.qr]
        }

        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.frame = UIScreen.main.bounds
        view.layer.addSublayer(previewLayer)
        context.coordinator.previewLayer = previewLayer

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }

        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.previewLayer?.frame = uiView.bounds
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(scannedCode: $scannedCode)
    }

    class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        @Binding var scannedCode: String?
        var session: AVCaptureSession?
        var previewLayer: AVCaptureVideoPreviewLayer?
        private var hasScanned = false

        init(scannedCode: Binding<String?>) {
            _scannedCode = scannedCode
        }

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
            guard !hasScanned,
                  let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  let value = object.stringValue, !value.isEmpty else { return }
            hasScanned = true
            scannedCode = value
            session?.stopRunning()
        }
    }
}
