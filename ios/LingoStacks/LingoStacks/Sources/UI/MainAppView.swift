//
//  MainAppView.swift
//  LingoStacks
//
//  Root navigation with native SwiftUI TabView
//

import SwiftUI

public struct MainAppView: View {
    @State private var selectedTab: Int = 0
    @State private var activeSessionDeck: VocabDeck? = nil
    @State private var summaryResults: [CardAttemptResult]? = nil
    @State private var summaryDeck: VocabDeck? = nil

    @ObservedObject var pm = ProgressManager.shared
    @ObservedObject var vocabRepo = VocabularyRepository.shared

    public init() {}

    private var dueCount: Int {
        vocabRepo.decks(for: pm.selectedLanguage).reduce(0) { $0 + pm.getDeckNeedsReviewCount(deck: $1) }
    }

    public var body: some View {
        TabView(selection: $selectedTab) {
            DeckSelectionView { deck in
                activeSessionDeck = deck
            }
            .tabItem {
                Label("Domů", systemImage: "house.fill")
            }
            .tag(0)

            PracticeLauncherView { deck in
                activeSessionDeck = deck
            }
            .tabItem {
                Label("Trénink", systemImage: "bolt.fill")
            }
            .badge(dueCount > 0 ? dueCount : 0)
            .tag(1)

            LeaderboardView()
            .tabItem {
                Label("Žebříček", systemImage: "trophy.fill")
            }
            .tag(2)

            SettingsView()
            .tabItem {
                Label("Nastavení", systemImage: "gearshape.fill")
            }
            .tag(3)
        }
        .fullScreenCover(item: $activeSessionDeck) { deck in
            FlashcardSessionView(
                deck: deck,
                onSessionFinished: { results in
                    activeSessionDeck = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        summaryDeck = deck
                        summaryResults = results
                    }
                },
                onDismiss: {
                    activeSessionDeck = nil
                }
            )
        }
        .sheet(isPresented: Binding(
            get: { summaryResults != nil },
            set: { if !$0 { summaryResults = nil; summaryDeck = nil } }
        )) {
            if let results = summaryResults, let deck = summaryDeck {
                SessionSummaryView(
                    results: results,
                    onRestart: {
                        summaryResults = nil
                        summaryDeck = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            activeSessionDeck = deck
                        }
                    },
                    onFinish: {
                        summaryResults = nil
                        summaryDeck = nil
                    }
                )
            }
        }
    }
}
