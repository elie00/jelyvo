package ca.ilianokokoro.umihi.music.ui.theme

/**
 * Accent identity. [Dynamic] follows the platform's own palette, which only Android provides;
 * clients without one fall back to the brand scheme.
 */
enum class ThemeColor {
    Umihi,
    Dynamic,
    OceanBlue,
    ForestGreen,
    Violet,
    SunsetOrange,
    RoseRed,
    Mono
}

/**
 * Full theme identities (colors + typography + shapes), against [ThemeColor] which only swaps
 * the accent. [Jelyvo] keeps the brand look and the accent picker; the others override it all.
 */
enum class ThemePreset {
    Studio,
    Jelyvo,
    Minuit,
    Vinyle,
    Neon,
    Oled,
}
