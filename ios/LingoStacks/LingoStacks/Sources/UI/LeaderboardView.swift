//
//  LeaderboardView.swift
//  LingoStacks
//
//  Dedicated TabView screen for online leaderboard
//

import SwiftUI

public struct LeaderboardView: View {
    @ObservedObject var leaderboardService = LeaderboardService.shared
    @State private var selectedPeriod: LeaderboardPeriod = .today

    public init() {}

    public var body: some View {
        NavigationView {
            ZStack {
                Color(.systemGroupedBackground).ignoresSafeArea()

                VStack(spacing: 0) {
                    Picker("Období", selection: $selectedPeriod) {
                        ForEach(LeaderboardPeriod.allCases) { period in
                            Text(period.title(isCzech: true)).tag(period)
                        }
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    .padding(.bottom, 8)

                    let entries = (selectedPeriod == .today) ?
                        leaderboardService.dailyLeaderboard :
                        leaderboardService.weeklyLeaderboard

                    if entries.isEmpty {
                        Spacer()
                        ProgressView("Načítám žebříček...")
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
            .navigationTitle(selectedPeriod == .today ? "🏆 Denní žebříček" : "🏆 Týdenní žebříček")
        }
    }

    private func leaderboardRow(rank: Int, entry: FriendProgress) -> some View {
        let isWeek = (selectedPeriod == .week)
        let xpValue = isWeek ? entry.weeklyXp : entry.todayXp
        let wordsValue = isWeek ? entry.weeklyCompleted : entry.todayCompleted

        return HStack(spacing: 12) {
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
                        .foregroundColor(entry.isCurrentUser ? LingoColors.appleBlue : .primary)

                    if entry.isCurrentUser {
                        Text("Vy")
                            .font(.caption2.bold())
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(LingoColors.appleBlue)
                            .clipShape(Capsule())
                    }
                }

                HStack(spacing: 6) {
                    Text("🔥 \(entry.streakDays) dní")
                        .font(.caption)
                        .foregroundColor(LingoColors.appleOrange)
                    Text("•")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("\(wordsValue) slov")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text("\(xpValue) XP")
                    .font(.headline.bold())
                    .foregroundColor(LingoColors.appleGreen)

                Text(entry.statusText)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
