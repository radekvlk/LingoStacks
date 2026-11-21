//
//  SettingsView.swift
//  LingoStacks
//
//  Dedicated TabView screen for user profile, daily goals, and preferences
//

import SwiftUI

public struct SettingsView: View {
    @ObservedObject var pm = ProgressManager.shared
    @State private var reminderTime: Date = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: Date()) ?? Date()

    public init() {}

    public var body: some View {
        NavigationView {
            Form {
                Section("Můj profil") {
                    HStack {
                        Text("Jméno hráče")
                        Spacer()
                        TextField("Vaše jméno", text: $pm.userName)
                            .multilineTextAlignment(.trailing)
                    }

                    HStack {
                        Text("Můj kód")
                        Spacer()
                        Text(pm.userInviteCode)
                            .font(.subheadline.bold())
                            .foregroundColor(.secondary)
                    }
                }

                Section("Denní cíl") {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Cíl slovíček:")
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
            .navigationTitle("⚙️ Nastavení")
        }
    }
}
