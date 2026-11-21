//
//  DeckSelectionView.swift
//  LingoStacks
//
//  Overview of decks and study progress in Cupertino Glass design with signature floating glass bottom bar
//

import SwiftUI

public struct DeckSelectionView: View {
    @ObservedObject var progressManager = ProgressManager.shared
    @ObservedObject var vocabularyRepo = VocabularyRepository.shared

    @State private var selectedLevelFilter: CefrLevel? = nil
    @State private var showingSettings: Bool = false
    @State private var settingsInitialTab: Int = 0 // 0 = Nastavení, 1 = Žebříček
    @State private var showingLanguagePicker: Bool = false
    @State private var activeBottomTab: Int = 0 // 0 = Domů, 1 = Trénink, 2 = Žebříček, 3 = Nastavení

    public var onSelectDeck: (VocabDeck) -> Void

    public init(onSelectDeck: @escaping (VocabDeck) -> Void) {
        self.onSelectDeck = onSelectDeck
    }

    private var currentDecks: [VocabDeck] {
        let all = vocabularyRepo.decks(for: progressManager.selectedLanguage)
        if let level = selectedLevelFilter {
            return all.filter { $0.level == level }
        }
        return all
    }

    private var totalNeedsReviewCount: Int {
        currentDecks.reduce(0) { $0 + progressManager.getDeckNeedsReviewCount(deck: $1) }
    }

    public var body: some View {
        ZStack {
            // Elegant background gradient
            LinearGradient(
                colors: [Color(.systemGroupedBackground), Color(.systemBackground)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // MARK: - Header
                headerView
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 12)

                // MARK: - CEFR Level Filter
                levelFilterBar
                    .padding(.bottom, 10)

                // MARK: - Decks ScrollView
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(currentDecks) { deck in
                            DeckCardRow(deck: deck) {
                                onSelectDeck(deck)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 100) // Space for floating bottom bar
                }
            }

            // MARK: - Signature Apple Floating Glass Bar (Bottom)
            VStack {
                Spacer()
                AppleFloatingGlassBar(
                    selectedTab: activeBottomTab,
                    reviewBadgeCount: totalNeedsReviewCount
                ) { index in
                    activeBottomTab = index
                    switch index {
                    case 0:
                        // Domů -> stay here
                        break
                    case 1:
                        // Trénink -> launch first deck with due words or first deck
                        if let targetDeck = currentDecks.first(where: { progressManager.getDeckNeedsReviewCount(deck: $0) > 0 }) ?? currentDecks.first {
                            onSelectDeck(targetDeck)
                        }
                        activeBottomTab = 0
                    case 2:
                        // Žebříček
                        settingsInitialTab = 1
                        showingSettings = true
                        activeBottomTab = 0
                    case 3:
                        // Nastavení
                        settingsInitialTab = 0
                        showingSettings = true
                        activeBottomTab = 0
                    default:
                        break
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 16)
            }
        }
        .sheet(isPresented: $showingSettings) {
            SettingsAndLeaderboardView(initialTab: settingsInitialTab)
        }
        .sheet(isPresented: $showingLanguagePicker) {
            LanguagePickerSheet()
        }
    }

    // MARK: - Subviews

    private var headerView: some View {
        HStack(spacing: 10) {
            // Language selector button
            Button {
                showingLanguagePicker = true
            } label: {
                HStack(spacing: 6) {
                    Text(progressManager.selectedLanguage.flagEmoji)
                        .font(.title3)
                    Text(progressManager.selectedLanguage.localizedTitle(isCzech: true))
                        .font(.headline)
                        .foregroundColor(.primary)
                    Image(systemName: "chevron.down")
                        .font(.caption.bold())
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .cupertinoGlass(cornerRadius: 16)
            }

            Spacer()

            // Streak Pill
            HStack(spacing: 4) {
                Text("🔥")
                Text("\(progressManager.streakDays)")
                    .font(.subheadline.bold())
                    .foregroundColor(.orange)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .cupertinoGlass(cornerRadius: 16)

            // Daily Goal Pill
            HStack(spacing: 4) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(progressManager.todayCompletedWords >= progressManager.dailyGoalWords ? .green : .blue)
                Text("\(progressManager.todayCompletedWords)/\(progressManager.dailyGoalWords)")
                    .font(.subheadline.bold())
                    .foregroundColor(.primary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .cupertinoGlass(cornerRadius: 16)
        }
    }

    private var levelFilterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterPill(
                    title: "Vše",
                    isSelected: selectedLevelFilter == nil,
                    color: .primary
                ) {
                    withAnimation(.spring(response: 0.25)) {
                        selectedLevelFilter = nil
                    }
                }

                ForEach(CefrLevel.allCases) { level in
                    FilterPill(
                        title: level.label,
                        isSelected: selectedLevelFilter == level,
                        color: level.accentColor
                    ) {
                        withAnimation(.spring(response: 0.25)) {
                            selectedLevelFilter = level
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

// MARK: - Signature Apple Floating Glass Bar

public struct AppleFloatingGlassBar: View {
    let selectedTab: Int
    let reviewBadgeCount: Int
    let onSelectTab: (Int) -> Void

    public var body: some View {
        HStack(spacing: 0) {
            tabButton(index: 0, title: "Domů", icon: "house.fill")
            tabButton(index: 1, title: "Trénink", icon: "bolt.fill", badge: reviewBadgeCount)
            tabButton(index: 2, title: "Žebříček", icon: "trophy.fill")
            tabButton(index: 3, title: "Nastavení", icon: "gearshape.fill")
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(
            Capsule()
                .fill(.ultraThinMaterial)
                .overlay(
                    Capsule()
                        .stroke(
                            LinearGradient(
                                colors: [Color.white.opacity(0.35), Color.white.opacity(0.10)],
                                startPoint: .top,
                                endPoint: .bottom
                            ),
                            lineWidth: 1
                        )
                )
                .shadow(color: Color.black.opacity(0.25), radius: 16, x: 0, y: 8)
        )
    }

    private func tabButton(index: Int, title: String, icon: String, badge: Int = 0) -> some View {
        Button {
            onSelectTab(index)
        } label: {
            VStack(spacing: 3) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: icon)
                        .font(.system(size: 20, weight: selectedTab == index ? .bold : .medium))
                        .foregroundColor(selectedTab == index ? .blue : .secondary)

                    if badge > 0 {
                        Text("\(badge)")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Color.orange)
                            .clipShape(Capsule())
                            .offset(x: 10, y: -6)
                    }
                }

                Text(title)
                    .font(.system(size: 11, weight: selectedTab == index ? .semibold : .regular))
                    .foregroundColor(selectedTab == index ? .blue : .secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

private struct FilterPill: View {
    let title: String
    let isSelected: Bool
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.bold())
                .foregroundColor(isSelected ? .white : .primary)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(isSelected ? color : Color.secondary.opacity(0.15))
                )
        }
    }
}

private struct DeckCardRow: View {
    let deck: VocabDeck
    let onTap: () -> Void
    @ObservedObject var pm = ProgressManager.shared

    private var masteredCount: Int {
        pm.getDeckMasteredCount(deck: deck)
    }

    private var needsReviewCount: Int {
        pm.getDeckNeedsReviewCount(deck: deck)
    }

    private var progressRatio: Double {
        guard !deck.words.isEmpty else { return 0 }
        return Double(masteredCount) / Double(deck.words.count)
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Text(deck.emoji)
                    .font(.system(size: 32))
                    .frame(width: 52, height: 52)
                    .background(Color.secondary.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(deck.localizedTitle(isCzech: true))
                            .font(.headline)
                            .foregroundColor(.primary)
                            .lineLimit(1)

                        Spacer()

                        Text(deck.level.label)
                            .font(.caption2.bold())
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(deck.level.accentColor)
                            .clipShape(Capsule())
                    }

                    Text(deck.localizedSubtitle(isCzech: true))
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)

                    HStack(spacing: 8) {
                        ProgressView(value: progressRatio)
                            .tint(deck.level.accentColor)

                        Text("\(masteredCount)/\(deck.words.count)")
                            .font(.caption2.bold())
                            .foregroundColor(.secondary)

                        if needsReviewCount > 0 {
                            HStack(spacing: 2) {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                Text("\(needsReviewCount)")
                            }
                            .font(.caption2.bold())
                            .foregroundColor(.orange)
                        }
                    }
                    .padding(.top, 2)
                }
            }
            .padding(14)
            .cupertinoGlass(cornerRadius: 18)
        }
        .buttonStyle(.plain)
    }
}

private struct LanguagePickerSheet: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var pm = ProgressManager.shared

    var body: some View {
        NavigationView {
            List(LearningLanguage.allCases) { lang in
                Button {
                    pm.selectedLanguage = lang
                    dismiss()
                } label: {
                    HStack {
                        Text(lang.flagEmoji)
                            .font(.title2)
                        Text(lang.titleCs)
                            .font(.headline)
                            .foregroundColor(.primary)
                        Spacer()
                        if pm.selectedLanguage == lang {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                                .font(.headline)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("Zvolte jazyk výuky")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Zavřít") { dismiss() }
                }
            }
        }
    }
}
