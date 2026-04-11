# Festival Connection

**Decentralized messaging for festivals and live events — powered by CrowdSync\u2122.**

Festival Connection lets people at the same event discover each other, chat, create group channels, and send payments through CrowdSync\u2122, a multi-transport communication layer that combines Nostr relay networking, Bluetooth LE mesh, and peer-to-peer wireless. The Nearby feed shows real-time activity from other festival-goers, channels broadcast via NIP-28, and direct messages are end-to-end encrypted via NIP-04 — all branded under the CrowdSync\u2122 umbrella.

No accounts. No servers. No phone numbers. Your identity is a cryptographic keypair generated on your device.

---

## Platforms

| | iOS | Android |
|---|---|---|
| **Language** | Swift 5.9 | Kotlin |
| **UI Framework** | SwiftUI | Jetpack Compose + Material 3 |
| **Min Version** | iOS 16.0 | Android 8.0 (API 26) |
| **Target** | iOS 26.4 | API 35 |
| **Architecture** | MVVM with ObservableObject | MVVM with Compose state |

**Monorepo structure:**
```
festival-connection/
  ios/                    # Xcode project — 42 Swift files, 4,414 lines
  android/                # Gradle project — 35 Kotlin files, 5,169 lines
```

---

## Features

### Chats
Direct messaging between users via CrowdSync\u2122. Messages are end-to-end encrypted (NIP-04 AES-256-CBC with ECDH shared secrets). Gradient-styled sent bubbles, dark received bubbles, real-time delivery, and inline payment requests via the Bitcoin button.

### Nearby (CrowdSync\u2122)
Real-time activity feed powered by CrowdSync\u2122. Displays a live stream of messages and channel creation events from other festival-goers. Shows connection status (green/red indicator) and message count badge. Each feed item shows sender avatar, display name, message content, relative timestamp, and channel tag. Under the hood, CrowdSync\u2122 connects via Nostr relays (kind-42 channel messages, kind-40 channel discovery), BLE mesh, and peer-to-peer wireless.

### Channels
CrowdSync\u2122-powered group chat. Create named channels that broadcast to all connected transports so anyone can discover and join them. Channel rows display stacked member avatars, unread indicators, last message previews with sender names, and relative timestamps. Messages propagate via NIP-28 (kind-40/42 events) with proper e-tag threading. Channel chat views subscribe to channel-specific feeds on enter and unsubscribe on leave. Connection status indicator shown in the channel header.

### Wallet
Built-in Lightning-compatible wallet interface with balance display, payment request generation (QR code with countdown timer), invoice scanning (camera and paste modes), Add Funds flow (card purchase and receive address), and full transaction history. Pay and Request buttons with numeric keypad input.

### QR
Two-mode QR screen. "My QR" generates a scannable code from the device's cryptographic public key for instant peer connection. "Scan" mode opens the camera to scan another user's Festival Connection QR code.

### Settings
Full settings panel with profile editing (display name, handle, bio, avatar), wallet access, connection status, privacy and security info views, notification preferences, storage management, CrowdSync\u2122 status, app info, and rating flow. Each settings row uses individually-themed icon colors following Instagram's design language.

---

## Transport Architecture

CrowdSync\u2122 is the unified transport layer that combines multiple independent communication backends. Messages are deduplicated across all transports so the same content is never delivered twice. All transports run in parallel — the frontend only ever references "CrowdSync\u2122".

### Nostr Protocol (Internet Relay)
The Nostr relay network provides the primary messaging infrastructure, fully wired to the frontend:

- **NIP-01** — Core event construction, SHA-256 ID computation, canonical JSON serialization, signing, REQ/EVENT/CLOSE client messages, EVENT/OK/EOSE/CLOSED/NOTICE relay messages, full filter objects
- **NIP-04** — Encrypted direct messages via ECDH shared secret + AES-256-CBC
- **NIP-19** — Bech32 encoding for npub/nsec/note identifiers
- **NIP-28** — Public channels (kind 40 creation, kind 42 messages, subscription filters) — fully wired to frontend
- **NIP-11** — Relay information document discovery

**Default relays (connected on app launch):**
| Relay | URL |
|---|---|
| Damus | `wss://relay.damus.io` |
| Nostr Band | `wss://relay.nostr.band` |
| nos.lol | `wss://nos.lol` |
| Snort | `wss://relay.snort.social` |
| Nostr Wine | `wss://nostr.wine` |

The relay manager maintains simultaneous WebSocket connections to all 5 relays with exponential backoff reconnection (2s to 120s), event deduplication with a 1-hour cache, and automatic subscription re-send on reconnect. A geo-relay database of 318 additional relays (`Resources/georelays.csv`) is available for festival-local filtering.

### Frontend Relay Integration
The Nostr relay layer is fully wired to the UI on both platforms:

- **AppState** (iOS) / **Screen-level state** (Android) connects to relays on launch via `startNostrRelay()` / `LaunchedEffect`
- **Nearby feed** subscribes to kind-42 events (channel messages) from the last hour and kind-40 events (channel discovery) from the last 24 hours, displaying them as a real-time scrolling feed
- **Channel creation** publishes a kind-40 NIP-28 event to all connected relays; the Nostr event ID becomes the channel ID
- **Channel messages** publish as kind-42 events with e-tag root references; channel chat views subscribe to channel-specific message feeds and unsubscribe on leave
- **Channel discovery** automatically populates the channel list with kind-40 events received from relays
- **Connection status** shown as green/red indicator dots throughout the UI (nearby header, channel chat header)

### Bluetooth LE Mesh (Offline)
Device discovery and short-range packet exchange via CoreBluetooth (iOS) and Android BLE APIs. Infrastructure is fully coded in `Mesh/BLE/` (iOS) and `mesh/ble/` (Android), with Bluetooth permissions wired in PermissionsManager and AndroidManifest.xml.

### Multipeer Connectivity (iOS-to-iOS)
Apple's peer-to-peer framework for iOS-to-iOS communication without internet. Infrastructure scaffolded in `Mesh/Multipeer/`.

### Wi-Fi Direct / Nearby Connections (Android-to-Android)
Android peer-to-peer wireless for Android-to-Android communication. Infrastructure scaffolded in `mesh/wifi/` and `mesh/nearby/`.

All offline mesh transports work with zero internet connectivity — messages hop through nearby devices to reach their destination.

### Cryptographic Identity
Two separate keypairs serve different purposes:
- **App identity** — Ed25519 via CryptoKit (iOS) / KeyStore (Android), stored in iOS Keychain / Android EncryptedSharedPreferences
- **Nostr identity** — secp256k1 keypair for relay signing, stored separately in Keychain / EncryptedSharedPreferences

Default display names and handles are derived from the public key fingerprint (e.g., "Peer 5F35" / "@5f359fae"). Users can customize these in the profile editor.

---

## Design System

The visual design follows an Instagram-inspired gradient philosophy — the purple-to-pink-to-tangerine gradient appears on approximately 10% of the UI for maximum impact at brand moments:

**Gradient (brand moments):**
- "Festival Connection" wordmark
- Sent message bubble backgrounds
- Avatar ring backgrounds
- Loading spinner arc
- Wallet balance hero amount
- Compose/create action icons

**Solid white (primary content):**
- All screen titles
- Tab bar active icon and label
- Body text and row labels

**Solid gray (secondary content):**
- Section headers (ACCOUNT, CONNECTIONS, RECENT ACTIVITY)
- Empty state icons
- Tab bar inactive icons
- Muted text and timestamps

**Themed icon colors (Settings):**
Each settings row uses an individually colored icon — orange for Wallet, blue for Connections, purple for Privacy, green for Security, pink for CrowdSync, red for Notifications, gold for Rate App.

### Color Palette
| Token | Hex | Usage |
|---|---|---|
| `backgroundBlack` | `#000000` | All screen backgrounds |
| `surfaceDark` | `#1C1C1E` | Cards, received bubbles, tab bar |
| `surfaceMedium` | `#2C2C2E` | Input fields, search bars |
| `textPrimary` | `#FFFFFF` | Primary text |
| `textSecondary` | `#999999` | Secondary text, timestamps |
| `textMuted` | `#666666` | Placeholders, inactive elements |
| `gradientPurple` | `#7B2FBE` | Gradient stop 1 |
| `gradientFuchsia` | `#C026D3` | Gradient stop 2 |
| `gradientPink` | `#EC4899` | Gradient stop 3 / accent |
| `gradientTangerine` | `#F59E0B` | Gradient stop 4 |

---

## Project Structure

### iOS
```
ios/FestivalConnection/
  App/
    AppState.swift              # Central state — conversations, channels, peers, Nostr relay lifecycle
    FestivalConnectionApp.swift # @main entry point
    RootView.swift              # Onboarding flow + Nostr event routing
  Security/
    IdentityManager.swift       # Ed25519 CrowdSync keypair (Keychain)
  Services/
    PermissionsManager.swift    # BLE, Location, Notification permissions
  Mesh/Nostr/
    NostrIdentity.swift         # secp256k1 Nostr keypair (Keychain)
    NostrEvent.swift            # NIP-01 event construction + signing
    NostrMessages.swift         # Client/relay message protocol + filters
    NostrRelayManager.swift     # WebSocket relay connections + dedup
    NostrDM.swift               # NIP-04 encrypted direct messages
    NostrChannels.swift         # NIP-28 public channel protocol
    NostrBech32.swift           # NIP-19 bech32 encoding
  Models/
    ChannelInfo.swift           # Channel with members, messages, geofence
    ChannelMessage.swift        # Channel message model
    ChatMessage.swift           # DM with payment fields
    PeerInfo.swift              # Discovered peer
    WalletTransaction.swift     # Payment transaction
  UI/
    Theme/FestivalTheme.swift   # Colors, gradients, icon colors
    Components/                 # CircularAvatarView, GradientIcon, NumericKeypad
    Screens/                    # 22 screen views
    Wallet/                     # 6 wallet sub-screens
```

### Android
```
android/app/src/main/java/com/appxstudios/festivalconnection/
  MainActivity.kt              # Entry point, tab navigation, sub-screen routing
  security/
    NostrIdentity.kt            # secp256k1 keypair (EncryptedSharedPreferences)
    NostrBech32.kt              # NIP-19 bech32 encoding
  mesh/nostr/
    NostrEvent.kt               # NIP-01 events (Gson serialization)
    NostrMessages.kt            # Client/relay messages + filters
    NostrRelayManager.kt        # OkHttp WebSocket connections + dedup
    NostrDM.kt                  # NIP-04 AES-256-CBC encryption
    NostrChannels.kt            # NIP-28 channel protocol
  models/                       # ChannelInfo, ChannelMessage, ChatMessage, PeerInfo, WalletTransaction
  ui/
    theme/FestivalTheme.kt      # Colors, gradients, GradientIcon, GradientText
    components/                 # CircularAvatarComposable, NumericKeypad
    screens/                    # 19 screen composables
```

---

## Screen Map

| Tab | Screen | iOS | Android |
|---|---|---|---|
| **Chats** | Conversation list | `ChatsView.swift` | `ChatsScreen.kt` |
| | New chat sheet | `NewChatView.swift` | `NewChatSheet.kt` |
| | Direct message | `ChatView.swift` | `ChatScreen.kt` |
| **Nearby** | CrowdSync\u2122 feed | `NearbyView.swift` | `NearbyScreen.kt` |
| **Channels** | Channel list | `ChannelsView.swift` | `ChannelsScreen.kt` |
| | Channel chat | `ChannelChatView.swift` | `ChannelChatScreen.kt` |
| | Create channel | `CreateChannelSheet.swift` | (inline in ChannelsScreen) |
| **Wallet** | Balance + actions | `WalletHomeView.swift` | `WalletHomeScreen.kt` |
| | Request payment | `RequestView.swift` | `RequestScreen.kt` |
| | Pay | `PayView.swift` | `PayScreen.kt` |
| | Invoice display | `InvoiceDisplayView.swift` | `InvoiceDisplayScreen.kt` |
| | Invoice scanner | `InvoiceScannerView.swift` | `InvoiceScannerScreen.kt` |
| | Add funds | `AddFundsView.swift` | `AddFundsScreen.kt` |
| | Transaction history | `TransactionHistoryView.swift` | `TransactionHistoryScreen.kt` |
| **QR** | My QR / Scan | `QRView.swift` | `QRScreen.kt` |
| **Settings** | Settings list | `SettingsView.swift` | `SettingsScreen.kt` |
| | Edit profile | `EditProfileSheet.swift` | `EditProfileSheet.kt` |
| | Onboarding | `PermissionsView.swift` | `PermissionsScreen.kt` |
| | Setup | `SettingUpView.swift` | `SettingUpScreen.kt` |
| | Launch | `LaunchScreen.swift` | `LaunchScreen.kt` |

---

## Dependencies

### iOS
Native frameworks only — no third-party package dependencies:
- CryptoKit, Security (cryptography + Keychain)
- CoreBluetooth (BLE — reserved for future mesh features)
- CoreLocation (proximity)
- AVFoundation (QR scanning)
- PhotosUI (profile pictures)
- UserNotifications (push)
- Network (connectivity)
- URLSession WebSocket (Nostr relay connections)

### Android
```kotlin
// UI
androidx.compose:compose-bom:2024.12.01
androidx.compose.material3:material3
androidx.compose.material:material-icons-extended
androidx.activity:activity-compose:1.9.3
androidx.navigation:navigation-compose:2.8.5

// Crypto + Security
org.bouncycastle:bcprov-jdk15on:1.70          // secp256k1
androidx.security:security-crypto:1.1.0-alpha06 // EncryptedSharedPreferences

// Networking
com.squareup.okhttp3:okhttp:4.12.0            // WebSocket (Nostr relays)
com.google.code.gson:gson:2.11.0              // JSON

// Utilities
com.google.zxing:core:3.5.3                   // QR code generation
com.google.accompanist:accompanist-permissions:0.36.0
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
```

---

## Building

### iOS
```bash
xcodebuild -project ios/FestivalConnection.xcodeproj \
  -scheme FestivalConnection \
  -sdk iphonesimulator \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro" \
  -configuration Debug build
```

### Android
```bash
cd android && ./gradlew assembleDebug
```

---

## Running

### iOS Simulator
```bash
xcrun simctl boot "iPhone 17 Pro"
xcrun simctl install booted path/to/FestivalConnection.app
xcrun simctl launch booted com.appxstudios.festivalconnection
```

### Android Emulator
```bash
cd android && ./gradlew installDebug
adb shell am start -n com.appxstudios.festivalconnection/.MainActivity
```

---

## User Flow

1. **Launch** — Splash screen with "Festival Connection" gradient wordmark and "Powered by CrowdSync\u2122" branding
2. **Setting Up** — Cryptographic identity generation (Ed25519 + secp256k1 keypairs) with animated gradient spinner
3. **Permissions** — Bluetooth, Wi-Fi, Location, and Notifications with auto-request and real-time status indicators
4. **Main App** — Six-tab interface: Chats, Nearby, Channels, Wallet, QR, Settings
5. **CrowdSync\u2122** — All transports (Nostr relays, BLE, peer-to-peer) connect automatically on launch; connection status shown via green/red indicators
6. **Nearby** — CrowdSync\u2122 feed shows real-time stream of messages from other festival attendees
7. **Channels** — Create or join group channels; messages broadcast via CrowdSync\u2122 (NIP-28 kind-40/42 + mesh)
8. **Messaging** — Tap a user to chat; direct messages encrypted end-to-end via NIP-04

---

## Privacy

- All data stored on-device only
- No accounts, no servers, no phone numbers
- Identity is a locally-generated cryptographic keypair
- Private keys never leave the device Keychain / EncryptedSharedPreferences
- Direct messages encrypted end-to-end (NIP-04 AES-256-CBC)
- Channel messages broadcast via CrowdSync\u2122 (decentralized Nostr relays + mesh) — no central server
- Location data used only for proximity features, never transmitted to servers

---

## License

Proprietary. All rights reserved by AppX Studios.
