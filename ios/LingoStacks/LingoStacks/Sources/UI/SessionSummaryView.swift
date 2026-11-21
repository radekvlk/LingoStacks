//
//  SessionSummaryView.swift
//  LingoStacks
//
//  Lesson summary with accuracy score, XP earned, and review list
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
            Color(red: 0.06, green: 0.08, blue: 0.12)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                // Celebration Icon & Title
                VStack(spacing: 8) {
                    Text("🏆")
                        .font(.system(size: 64))
                        .padding(.top, 24)

                    Text("Skvělá práce!")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(.white)

                    Text("Lekce byla úspěšně dokončena")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                // 3 Score Badges in Cupertino Glass
                HStack(spacing: 12) {
                    summaryStat(
                        icon: "percent",
                        color: .green,
                        title: "Úspěšnost",
                        value: "\(accuracyPercent) %"
                    )

                    summaryStat(
                        icon: "waveform",
                        color: .blue,
                        title: "Hlasem",
                        value: "\(spokenCount)"
                    )

                    summaryStat(
                        icon: "bolt.fill",
                        color: .yellow,
                        title: "Získáno XP",
                        value: "+\(earnedXp)"
                    )
                }
                .padding(.horizontal, 16)

                // Words that need review
                if !failedWords.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("K zopakování:")
                            .font(.subheadline.bold())
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 20)

                        ScrollView {
                            VStack(spacing: 8) {
                                ForEach(failedWords) { word in
                                    HStack {
                                        Text(word.emoji)
                                        Text(word.czech)
                                            .font(.headline)
                                            .foregroundColor(.white)
                                        Spacer()
                                        Text(word.foreign)
                                            .font(.subheadline.bold())
                                            .foregroundColor(.orange)
                                    }
                                    .padding(12)
                                    .cupertinoGlass(cornerRadius: 14)
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                } else {
                    Spacer()
                    VStack(spacing: 6) {
                        Text("🌟 Perfektní skóre!")
                            .font(.headline)
                            .foregroundColor(.yellow)
                        Text("Všechna slovíčka jste zvládl na jedničku.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }

                Spacer()

                // Bottom Action Buttons
                VStack(spacing: 12) {
                    Button(action: onRestart) {
                        HStack {
                            Image(systemName: "arrow.counterclockwise")
                            Text("Procvičit znovu")
                        }
                        .font(.headline.bold())
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.blue)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }

                    Button(action: onFinish) {
                        Text("Zpět na výběr sady")
                            .font(.headline)
                            .foregroundColor(.secondary)
                            .padding(.vertical, 10)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }
        }
    }

    private func summaryStat(icon: String, color: Color, title: String, value: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(color)

            Text(value)
                .font(.title3.bold())
                .foregroundColor(.white)

            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .cupertinoGlass(cornerRadius: 16)
    }
}
