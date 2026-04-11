import SwiftUI

// MARK: - Festival Connection Theme
// Single source of truth for every color and gradient value in the entire app.
// Do not define any color value outside this file.
// Do not hardcode any hex color value outside this file.

enum FestivalTheme {
    // MARK: - Gradient Stops (4-stop purple-fuchsia-pink-tangerine)
    static let gradientPurple    = Color(red: 0.482, green: 0.184, blue: 0.745) // #7B2FBE
    static let gradientFuchsia   = Color(red: 0.752, green: 0.149, blue: 0.824) // #C026D3
    static let gradientPink      = Color(red: 0.925, green: 0.286, blue: 0.600) // #EC4899
    static let gradientTangerine = Color(red: 0.961, green: 0.620, blue: 0.043) // #F59E0B

    // Backward-compat aliases for existing code referencing old names
    static let gradientMagenta = gradientFuchsia
    static let gradientCoral   = gradientPink
    static let gradientOrange  = gradientTangerine
    static let gradientAmber   = gradientTangerine

    // MARK: - Accent / Interactive
    static let accentPink = gradientPink  // buttons, FAB, send, active tab, links

    // MARK: - Backgrounds
    static let backgroundBlack = Color(red: 0.0, green: 0.0, blue: 0.0)      // Pure #000000
    static let surfaceDark     = Color(red: 0.110, green: 0.110, blue: 0.118) // ~#1C1C1E
    static let surfaceMedium   = Color(red: 0.173, green: 0.173, blue: 0.180) // ~#2C2C2E

    // MARK: - Text
    static let textPrimary   = Color.white
    static let textSecondary = Color(white: 0.60)
    static let textMuted     = Color(white: 0.40)

    // MARK: - Status
    static let presenceGreen = Color(red: 0.196, green: 0.843, blue: 0.294)
    static let errorRed      = Color.red
    static let bitcoinOrange = Color(red: 0.478, green: 0.224, blue: 0.0) // ~#7A3A00

    // Backward-compat alias
    static let paymentBorder = bitcoinOrange

    // MARK: - Settings Icon Colors (individual tones)
    static let iconOrange = Color(red: 1.0, green: 0.58, blue: 0.0)
    static let iconBlue   = Color(red: 0.0, green: 0.48, blue: 1.0)
    static let iconPurple = Color(red: 0.58, green: 0.24, blue: 0.83)
    static let iconGreen  = Color(red: 0.20, green: 0.78, blue: 0.35)
    static let iconPink   = Color(red: 0.93, green: 0.29, blue: 0.60)
    static let iconRed    = Color(red: 1.0, green: 0.27, blue: 0.23)
    static let iconGold   = Color(red: 1.0, green: 0.84, blue: 0.0)
    static let iconGray   = Color(white: 0.55)

    // MARK: - Gradient Expressions

    static let mainGradientColors: [Color] = [
        gradientPurple, gradientFuchsia, gradientPink, gradientTangerine
    ]

    static let mainGradient = LinearGradient(
        colors: mainGradientColors,
        startPoint: .leading,
        endPoint: .trailing
    )

    static let mainGradientVertical = LinearGradient(
        colors: mainGradientColors,
        startPoint: .top,
        endPoint: .bottom
    )

    static let mainGradientDiagonal = LinearGradient(
        colors: mainGradientColors,
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}
