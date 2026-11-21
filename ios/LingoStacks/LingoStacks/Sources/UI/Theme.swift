//
//  Theme.swift
//  LingoStacks
//
//  Cupertino Glassmorphism styles and color schemes
//

import SwiftUI

public struct CupertinoGlassModifier: ViewModifier {
    var cornerRadius: CGFloat = 20
    var opacity: CGFloat = 0.75
    var borderOpacity: CGFloat = 0.25

    public func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .opacity(opacity)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(Color.white.opacity(borderOpacity), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.08), radius: 12, x: 0, y: 6)
    }
}

public extension View {
    func cupertinoGlass(cornerRadius: CGFloat = 20, opacity: CGFloat = 0.85, borderOpacity: CGFloat = 0.3) -> some View {
        self.modifier(CupertinoGlassModifier(cornerRadius: cornerRadius, opacity: opacity, borderOpacity: borderOpacity))
    }
}

public struct LingoColors {
    public static let primaryGreen = Color(red: 0.20, green: 0.78, blue: 0.35)
    public static let primaryBlue = Color(red: 0.00, green: 0.48, blue: 1.00)
    public static let vibrantOrange = Color(red: 1.00, green: 0.58, blue: 0.00)
    public static let softRed = Color(red: 1.00, green: 0.27, blue: 0.33)
    public static let backgroundGradientTop = Color(red: 0.06, green: 0.08, blue: 0.14)
    public static let backgroundGradientBottom = Color(red: 0.02, green: 0.03, blue: 0.07)
}
