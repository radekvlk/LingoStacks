//
//  SessionSummaryView.swift
//  LingoStacks
//
//  Lesson summary with accuracy score, XP earned, and review list
//  Dynamic Cupertino styling supporting Light and Dark mode
//

import SwiftUI

public struct SessionSummaryView: View {
    let results: [CardAttemptResult]
    let onRestart: () -> Void
    let onFinish: () -> Void

    @ObservedObject var progressManager = ProgressManager.shared

    private var correctCount: Int {
        results.filter { $0.isCorrect }.count
    }

    private var spokenCount: Int {
        results.filter { $0.method == .spokenCorrect }.count
    }

    private var accuracyPercent: Int {
        guard !results.isEmpty else { return 0 }
        return Int((Double(correctCount) / Double(results.count)) * 100)
    }

    private var earnedXp: Int {
        let base = results.reduce(0) { sum, r in
            guard r.isCorrect else { return sum }
            return sum + (r.method == .spokenCorrect ? 15 : 10)
        }
        let streakBonus = min(20, progressManager.streakDays * 2) * correctCount
        return base + streakBonus
    }

    private var failedWords: [VocabWord] {
        results.filter { !$0.isCorrect }.map { $0.word }
    }

    public var body: some View {
        ZStack {
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                // Celebration Icon & Title
                VStack(spacing: 8) {
                    Text("🏆")
                        .font(.system(size: 64))
                        .padding(.top, 24)

                    Text("Skvělá práce!")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)

                    Text("Lekce byla úspěšně dokončena")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                // 3 Score Badges in Cupertino Glass
                HStack(spacing: 12) {
                    summaryStat(
                        icon: "percent",
                        color: LingoColors.appleGreen,
                        title: "Úspěšnost",
                        value: "\(accuracyPercent) %"
                    )

                    summaryStat(
                        icon: "waveform",
                        color: LingoColors.appleBlue,
                        title: "Hlasem",
                        value: "\(spokenCount)"
                    )

                    summaryStat(
                        icon: "sparkles",
                        color: LingoColors.appleOrange,
                        title: "Získáno XP",
                        value: "+\(earnedXp) XP"
                    )
                }
                .padding(.horizontal, 16)

                // Words Review List
                VStack(alignment: .leading, spacing: 8) {
                    Text("Přehled slovíček v lekci")
                        .font(.subheadline.bold())
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 20)

                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(Array(results.enumerated()), id: \.offset) { index, attempt in
                                HStack(spacing: 12) {
                                    Text(attempt.word.emoji)
                                        .font(.title3)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(attempt.word.czech)
                                            .font(.subheadline.bold())
                                            .foregroundColor(.primary)
                                        Text(attempt.word.foreign)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }

                                    Spacer()

                                    if attempt.isCorrect {
                                        HStack(spacing: 4) {
                                            if attempt.method == .spokenCorrect {
                                                Image(systemName: "waveform")
                                                    .font(.caption)
                                                    .foregroundColor(LingoColors.appleBlue)
                                            }
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundColor(LingoColors.appleGreen)
                                        }
                                    } else {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundColor(LingoColors.appleRed)
                                    }
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                                .background(Color(uiColor: .secondarySystemGroupedBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Color(uiColor: .separator).opacity(0.4), lineWidth: 0.6)
                                )
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }

                Spacer()

                // Bottom Buttons
                VStack(spacing: 10) {
                    if !failedWords.isEmpty {
                        Button {
                            onRestart()
                        } label: {
                            HStack {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                Text("Zopakovat chyby (\(failedWords.count))")
                            }
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(LingoColors.appleOrange)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                    }

                    Button {
                        onFinish()
                    } label: {
                        Text("Pokračovat")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(LingoColors.appleBlue)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 20)
            }
        }
    }

    private func summaryStat(icon: String, color: Color, title: String, value: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.headline.bold())
                .foregroundColor(color)

            Text(value)
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .foregroundColor(.primary)

            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color(uiColor: .separator).opacity(0.4), lineWidth: 0.6)
        )
    }
}
