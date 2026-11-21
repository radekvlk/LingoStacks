//
//  SettingsAndLeaderboardView.swift
//  LingoStacks
//
//  Settings, daily goal customization, and online leaderboard
//

import SwiftUI

public struct SettingsAndLeaderboardView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var pm = ProgressManager.shared
    @ObservedObject var leaderboardService = LeaderboardService.shared

    @State private var selectedTab: Int = 0 // 0 = Nastavení, 1 = Žebříček
    @State private var selectedPeriod: LeaderboardPeriod = .today
    @State private var reminderTime: Date = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var reminderEnabled: Bool = false

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Segmented Control Header
                Picker("Zvolte sekci", selection: $selectedTab) {
                    Text("Cíle & Nastavení").tag(0)
                    Text("🏆 Žebříček").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                if selectedTab == 0 {
                    settingsTab
                } else {
                    leaderboardTab
                }
            }
            .navigationTitle(selectedTab == 0 ? "Nastavení a profil" : "Online žebříček")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Hotovo") { dismiss() }
                }
            }
        }
    }

    // MARK: - Tab 1: Settings & Goals

    private var settingsTab: some View {
        Form {
            Section("Můj profil") {
                HStack {
                    Text("Jméno hráče")
                    Spacer()
                    TextField("Vaše jméno", text: $pm.userName)
                        .multilineTextAlignment(.trailing)
                        .foregroundColor(.primary)
                }

                HStack {
                    Text("Můj kód")
                    Spacer()
                    Text(pm.userInviteCode)
                        .font(.subheadline.bold())
                        .foregroundColor(.secondary)
                }
            }

            Section("Denní cíl slovíček") {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Denní cíl:")
                        Spacer()
                        Text("\(pm.dailyGoalWords) slov / den")
                            .font(.headline)
                            .foregroundColor(.blue)
                    }

                    Slider(
                        value: Binding(
                            get: { Double(pm.dailyGoalWords) },
                            set: { pm.dailyGoalWords = Int($0) }
                        ),
                        in: 10...100,
                        step: 5
                    )
                    .tint(.blue)
                }
                .padding(.vertical, 4)
            }

            Section("Směr učení") {
                Picker("Směr překladu", selection: $pm.studyDirection) {
                    ForEach(StudyDirection.allCases) { dir in
                        Text(dir.localizedDescription(isCzech: true, language: pm.selectedLanguage)).tag(dir)
                    }
                }
            }

            Section("Zvuk a odezva") {
                Toggle("Automatická výslovnost (TTS)", isOn: $pm.autoPronounce)
                Toggle("Haptická odezva při úspěchu", isOn: $pm.hapticFeedbackEnabled)
            }

            Section("Denní připomenutí") {
                DatePicker("Čas připomenutí", selection: $reminderTime, displayedComponents: .hourAndMinute)
                    .onChange(of: reminderTime) { newTime in
                        let comp = Calendar.current.dateComponents([.hour, .minute], from: newTime)
                        StudyReminderScheduler.shared.scheduleDailyReminder(
                            hour: comp.hour ?? 19,
                            minute: comp.minute ?? 0
                        )
                    }
            }
        }
    }

    // MARK: - Tab 2: Leaderboard

    private var leaderboardTab: some View {
        VStack(spacing: 0) {
            // Period Picker (Today vs Week)
            Picker("Období", selection: $selectedPeriod) {
                ForEach(LeaderboardPeriod.allCases) { period in
                    Text(period.title(isCzech: true)).tag(period)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)

            let entries = (selectedPeriod == .today) ?
                leaderboardService.dailyLeaderboard :
                leaderboardService.weeklyLeaderboard

            if entries.isEmpty {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                List {
                    ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                        leaderboardRow(rank: index + 1, entry: entry)
                    }
                }
                .listStyle(.insetGrouped)
                .refreshable {
                    leaderboardService.refreshLeaderboard()
                }
            }
        }
    }

    private func leaderboardRow(rank: Int, entry: FriendProgress) -> some View {
        HStack(spacing: 12) {
            // Rank icon or number
            ZStack {
                if rank == 1 {
                    Text("🥇").font(.title2)
                } else if rank == 2 {
                    Text("🥈").font(.title2)
                } else if rank == 3 {
                    Text("🥉").font(.title2)
                } else {
                    Text("\(rank)")
                        .font(.headline)
                        .foregroundColor(.secondary)
                }
            }
            .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(entry.displayName)
                        .font(.headline)
                        .foregroundColor(entry.isCurrentUser ? .blue : .primary)

                    if entry.isCurrentUser {
                        Text("(Vy)")
                            .font(.caption2.bold())
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.blue)
                            .clipShape(Capsule())
                    }
                }

                Text("Série: \(entry.streakDays) dní 🔥")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            // Score Badge
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(entry.todayCompleted)")
                    .font(.headline.bold())
                    .foregroundColor(.green)

                Text("slovíček")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
