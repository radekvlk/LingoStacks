//
//  PracticeLauncherView.swift
//  LingoStacks
//
//  Practice launcher tab focusing on words due for review (Spaced Repetition)
//

import SwiftUI

public struct PracticeLauncherView: View {
    @ObservedObject var pm = ProgressManager.shared
    @ObservedObject var vocabRepo = VocabularyRepository.shared

    public var onLaunchPractice: (VocabDeck) -> Void

    public init(onLaunchPractice: @escaping (VocabDeck) -> Void) {
        self.onLaunchPractice = onLaunchPractice
    }

    private var allDecks: [VocabDeck] {
        vocabRepo.decks(for: pm.selectedLanguage)
    }

    private var decksWithDueWords: [VocabDeck] {
        allDecks.filter { pm.getDeckNeedsReviewCount(deck: $0) > 0 }
    }

    private var totalDueCount: Int {
        allDecks.reduce(0) { $0 + pm.getDeckNeedsReviewCount(deck: $1) }
    }

    public var body: some View {
        NavigationView {
            ZStack {
                Color(.systemGroupedBackground).ignoresSafeArea()

                VStack(spacing: 24) {
                    // Header card
                    VStack(spacing: 12) {
                        Text("⚡")
                            .font(.system(size: 60))

                        Text(totalDueCount > 0 ? "\(totalDueCount) slovíček k procvičení" : "Všechna slovíčka procvičena!")
                            .font(.title2.bold())
                            .foregroundColor(.primary)

                        Text(totalDueCount > 0 ?
                             "Chytrý algoritmus Leitner + SM-2 vybral slovíčka, která je čas si zopakovat pro trvalé uložení v paměti." :
                             "Nemáte žádná zpožděná slovíčka k opakování. Můžete si procvičit libovolnou sadu z Domů."
                        )
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                    }
                    .padding(.vertical, 28)
                    .frame(maxWidth: .infinity)
                    .cupertinoGlass(cornerRadius: 24)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                    // Launch button
                    Button {
                        if let target = decksWithDueWords.first ?? allDecks.first {
                            onLaunchPractice(target)
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "bolt.fill")
                            Text("Spustit rychlý trénink")
                        }
                        .font(.headline.bold())
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.blue)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    .padding(.horizontal, 24)

                    Spacer()
                }
            }
            .navigationTitle("⚡ Rychlý trénink")
        }
    }
}
