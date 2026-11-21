//
//  LingoStacksApp.swift
//  LingoStacks
//
//  iOS Main Entry Point
//

import SwiftUI

@main
struct LingoStacksApp: App {
    init() {
        // Pre-warm audio session and vocabulary on app launch
        _ = ProgressManager.shared
        _ = VocabularyRepository.shared
    }

    var body: some Scene {
        WindowGroup {
            MainAppView()
                .preferredColorScheme(.dark)
        }
    }
}
