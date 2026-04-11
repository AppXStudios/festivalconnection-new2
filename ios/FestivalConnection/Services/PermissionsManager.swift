import Foundation
import CoreBluetooth
import CoreLocation
import UserNotifications
import Combine

@MainActor
final class PermissionsManager: ObservableObject {
    static let shared = PermissionsManager()

    @Published var bluetoothStatus: CBManagerAuthorization = CBManager.authorization
    @Published var locationStatus: CLAuthorizationStatus
    @Published var notificationStatus: UNAuthorizationStatus = .notDetermined
    @Published var wifiPermissionTriggered: Bool = false

    private var locationManager: CLLocationManager?
    private var centralManager: CBCentralManager?
    private var centralDelegate: BLEDelegate?
    private var locationDelegate: LocationDelegate?

    var allRequiredGranted: Bool {
        bluetoothStatus == .allowedAlways &&
        (locationStatus == .authorizedWhenInUse || locationStatus == .authorizedAlways) &&
        notificationStatus == .authorized
    }

    init() {
        locationStatus = CLLocationManager().authorizationStatus
        refreshNotificationStatus()
    }

    func requestAllPermissions() {
        // Bluetooth
        if centralManager == nil {
            centralDelegate = BLEDelegate(manager: self)
            centralManager = CBCentralManager(delegate: centralDelegate, queue: nil)
        }

        // Location
        if locationManager == nil {
            locationDelegate = LocationDelegate(manager: self)
            locationManager = CLLocationManager()
            locationManager?.delegate = locationDelegate
        }
        locationManager?.requestWhenInUseAuthorization()

        // Notifications
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { [weak self] granted, _ in
            DispatchQueue.main.async {
                self?.notificationStatus = granted ? .authorized : .denied
            }
        }
    }

    func refreshAllStatuses() {
        bluetoothStatus = CBManager.authorization
        if let lm = locationManager {
            locationStatus = lm.authorizationStatus
        }
        refreshNotificationStatus()
    }

    func startAutoPermissionRequest() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.requestAllPermissions()
        }
    }

    private func refreshNotificationStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            DispatchQueue.main.async {
                self?.notificationStatus = settings.authorizationStatus
            }
        }
    }

    // MARK: - Delegates

    private class BLEDelegate: NSObject, CBCentralManagerDelegate {
        weak var manager: PermissionsManager?
        init(manager: PermissionsManager) { self.manager = manager }

        func centralManagerDidUpdateState(_ central: CBCentralManager) {
            DispatchQueue.main.async { [weak self] in
                self?.manager?.bluetoothStatus = CBManager.authorization
                self?.manager?.wifiPermissionTriggered = true
            }
        }
    }

    private class LocationDelegate: NSObject, CLLocationManagerDelegate {
        weak var manager: PermissionsManager?
        init(manager: PermissionsManager) { self.manager = manager }

        func locationManagerDidChangeAuthorization(_ lm: CLLocationManager) {
            DispatchQueue.main.async { [weak self] in
                self?.manager?.locationStatus = lm.authorizationStatus
            }
        }
    }
}
