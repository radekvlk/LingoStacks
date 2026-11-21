//
//  MainAppView.swift
//  LingoStacks
//
//  Root view coordinating navigation and session flow
//

import SwiftUI

public enum AppScreen {
    case deckSelection
    case flashcards(VocabDeck)
    case sessionSummary([CardAttemptResult], VocabDeck)
}

public struct MainAppView: View {
    @State private var currentScreen: AppScreen = .deckSelection

    public init() {}

    public var body: some View {
        ZStack {
            switch currentScreen {
            case .deckSelection:
                DeckSelectionView { selectedDeck in
                    withAnimation(.easeInOut(duration: 0.25)) {
                        currentScreen = .flashcards(selectedDeck)
                    }
                }
                .transition(.opacity)

            case .flashcards(let deck):
                FlashcardSessionView(
                    deck: deck,
                    onSessionFinished: { results in
                        withAnimation(.easeInOut(duration: 0.25)) {
                            currentScreen = .sessionSummary(results, deck)
                        }
                    },
                    onDismiss: {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            currentScreen = .deckSelection
                        }
                    }
                )
                .transition(.opacity)

            case .sessionSummary(let results, let deck):
                SessionSummaryView(
                    results: results,
                    onRestart: {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            currentScreen = .flashcards(deck)
                        }
                    },
                    onFinish: {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            currentScreen = .deckSelection
                        }
                    }
                )
                .transition(.opacity)
            }
        }
    }
}
