//
//  Theme.swift
//  LingoStacks
//
//  Dynamic Apple Cupertino Palette & Glassmorphism supporting Light and Dark modes
//

import SwiftUI

public struct CupertinoGlassModifier: ViewModifier {
    @Environment(\.colorScheme) var colorScheme
    var cornerRadius: CGFloat = 20

    public func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(.ultraThinMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        colorScheme == .dark ?
                            Color.white.opacity(0.18) :
                            Color.black.opacity(0.08),
                        lineWidth: 1
                    )
            )
            .shadow(
                color: colorScheme == .dark ?
                    Color.black.opacity(0.35) :
                    Color.black.opacity(0.06),
                radius: 12,
                x: 0,
                y: 6
            )
    }
}

public extension View {
    func cupertinoGlass(cornerRadius: CGFloat = 20) -> some View {
        self.modifier(CupertinoGlassModifier(cornerRadius: cornerRadius))
    }
}

public struct LingoColors {
    public static let appleBlue = Color(red: 0.04, green: 0.52, blue: 1.0)
    public static let appleGreen = Color(red: 0.20, green: 0.78, blue: 0.35)
    public static let appleRed = Color(red: 1.0, green: 0.23, blue: 0.19)
    public static let appleOrange = Color(red: 1.0, green: 0.62, blue: 0.04)
    public static let appleIndigo = Color(red: 0.37, green: 0.36, blue: 0.90)
    public static let applePurple = Color(red: 0.75, green: 0.35, blue: 0.95)

    // Dynamic semantic system colors for Light & Dark mode
    public static var surfaceBg: Color { Color(uiColor: .systemGroupedBackground) }
    public static var cardSurface: Color { Color(uiColor: .secondarySystemGroupedBackground) }
    public static var cardElevated: Color { Color(uiColor: .tertiarySystemGroupedBackground) }
    public static var textPrimary: Color { Color(uiColor: .label) }
    public static var textMuted: Color { Color(uiColor: .secondaryLabel) }
    public static var borderGray: Color { Color(uiColor: .separator) }
}
