//
//  LingoStacksApp.swift
//  LingoStacks
//
//  iOS Main Entry Point with dynamic Appearance management
//

import SwiftUI

@main
struct LingoStacksApp: App {
    @ObservedObject var pm = ProgressManager.shared

    init() {
        _ = ProgressManager.shared
        _ = VocabularyRepository.shared
    }

    var body: some Scene {
        WindowGroup {
            MainAppView()
                .preferredColorScheme(
                    pm.appearance == .dark ? .dark : (pm.appearance == .light ? .light : nil)
                )
        }
    }
}
