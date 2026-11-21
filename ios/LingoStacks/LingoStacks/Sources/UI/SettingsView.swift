//
//  SettingsView.swift
//  LingoStacks
//
//  Complete 1:1 port of Android SettingsModal (Appearance, Apple ID Sign-In, Daily Goals, Audio, and Complete CEFR Stats)
//

import SwiftUI
import AuthenticationServices

public struct SettingsView: View {
    @ObservedObject var pm = ProgressManager.shared
    @State private var reminderTime: Date = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var showManualSignInSheet: Bool = false
    @State private var manualName: String = ""
    @State private var manualEmail: String = ""
    @State private var copiedCodeToast: Bool = false

    public init() {}

    private var completeStats: LanguageCompleteStats {
        pm.getLanguageCompleteStats(for: pm.selectedLanguage)
    }

    public var body: some View {
        NavigationView {
            Form {
                // MARK: - 1. Appearance (Vzhled)
                Section(header: Text("Vzhled aplikace")) {
                    Picker("Režim", selection: $pm.appearance) {
                        ForEach(AppAppearance.allCases) { app in
                            Text(app.title(isCzech: true)).tag(app)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                // MARK: - 2. Account & Apple Sign-In
                Section(header: Text("Účet a žebříček")) {
                    if let user = pm.userProfile {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle().fill(Color.blue.opacity(0.15)).frame(width: 44, height: 44)
                                Text(String(user.displayName.prefix(1)).uppercased())
                                    .font(.headline.bold())
                                    .foregroundColor(.blue)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(user.displayName).font(.headline)
                                Text(user.email).font(.caption).foregroundColor(.secondary)
                            }
                        }
                        .padding(.vertical, 4)

                        HStack {
                            Text("Můj zvací kód:")
                            Spacer()
                            Text(user.inviteCode)
                                .font(.subheadline.bold())
                                .foregroundColor(.blue)

                            Button {
                                UIPasteboard.general.string = user.inviteCode
                                copiedCodeToast = true
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { copiedCodeToast = false }
                            } label: {
                                Image(systemName: copiedCodeToast ? "checkmark" : "doc.on.doc")
                                    .font(.caption)
                                    .foregroundColor(.blue)
                            }
                        }

                        ShareLink(
                            item: "Ahoj! Přidej se ke mně v aplikaci LingoStacks a procvičuj slovíčka hlasem. Můj kód je \(user.inviteCode)",
                            subject: Text("Pozvánka do LingoStacks"),
                            message: Text("Procvičuj slovíčka hlasem se mnou!")
                        ) {
                            Label("Sdílet pozvánku s přáteli", systemImage: "square.and.arrow.up")
                        }

                        Button(role: .destructive) {
                            pm.signOut()
                        } label: {
                            Label("Odhlásit se z účtu", systemImage: "rectangle.portrait.and.arrow.right")
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Přihlaste se a zapojte se do online žebříčku hráčů:")
                                .font(.caption)
                                .foregroundColor(.secondary)

                            SignInWithAppleButton(.signIn) { request in
                                request.requestedScopes = [.fullName, .email]
                            } onCompletion: { result in
                                switch result {
                                case .success(let auth):
                                    if let credential = auth.credential as? ASAuthorizationAppleIDCredential {
                                        let name = [credential.fullName?.givenName, credential.fullName?.familyName]
                                            .compactMap { $0 }.joined(separator: " ")
                                        pm.signInWithApple(
                                            userId: credential.user,
                                            name: name.isEmpty ? nil : name,
                                            email: credential.email
                                        )
                                    }
                                case .failure(let error):
                                    print("Apple Sign-In failed: \(error.localizedDescription)")
                                }
                            }
                            .signInWithAppleButtonStyle(pm.appearance == .dark ? .white : .black)
                            .frame(height: 48)
                            .cornerRadius(12)

                            Button {
                                showManualSignInSheet = true
                            } label: {
                                Text("Nebo zadat jméno a e-mail ručně")
                                    .font(.caption.bold())
                                    .foregroundColor(.blue)
                                    .frame(maxWidth: .infinity, alignment: .center)
                                    .padding(.top, 4)
                            }
                        }
                        .padding(.vertical, 6)
                    }
                }

                // MARK: - 3. Daily Goal Section
                Section(header: Text("Denní cíl")) {
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

                        HStack {
                            Text("Bonus za splnění:")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Spacer()
                            Text(dailyBonusText)
                                .font(.caption.bold())
                                .foregroundColor(.orange)
                        }
                    }
                    .padding(.vertical, 4)
                }

                // MARK: - 4. Study Direction
                Section(header: Text("Směr učení")) {
                    Picker("Směr překladu", selection: $pm.studyDirection) {
                        ForEach(StudyDirection.allCases) { dir in
                            Text(dir.localizedDescription(isCzech: true, language: pm.selectedLanguage)).tag(dir)
                        }
                    }
                }

                // MARK: - 5. Audio & Speech
                Section(header: Text("Zvuk a odezva")) {
                    Toggle("Automatická výslovnost (TTS)", isOn: $pm.autoPronounce)
                    Toggle("Pozastavit kartu během mluvení", isOn: $pm.pauseCardWhileSpeaking)
                    Toggle("Haptická odezva při úspěchu", isOn: $pm.hapticFeedbackEnabled)
                }

                // MARK: - 6. Study Reminder
                Section(header: Text("Denní připomenutí")) {
                    DatePicker("Čas připomenutí", selection: $reminderTime, displayedComponents: .hourAndMinute)
                        .onChange(of: reminderTime) { newTime in
                            let comp = Calendar.current.dateComponents([.hour, .minute], from: newTime)
                            StudyReminderScheduler.shared.scheduleDailyReminder(
                                hour: comp.hour ?? 19,
                                minute: comp.minute ?? 0
                            )
                        }
                }

                // MARK: - 7. Complete Player Statistics (6 Tiles Grid)
                Section(header: Text("Statistiky hráče")) {
                    VStack(spacing: 10) {
                        HStack(spacing: 10) {
                            statTile(title: "Série dní", value: "\(pm.streakDays) dní", icon: "flame.fill", color: .orange)
                            statTile(title: "Úspěšnost", value: "\(accuracyPercent) %", icon: "target", color: .green)
                        }
                        HStack(spacing: 10) {
                            statTile(title: "Hlasem správně", value: "\(pm.totalSpokenCorrect)", icon: "waveform", color: .blue)
                            statTile(title: "Zvládnuto slov", value: "\(pm.masteredWordIds.count)", icon: "book.fill", color: .purple)
                        }
                        HStack(spacing: 10) {
                            statTile(title: "Splněné lekce", value: "\(pm.completedSessions)", icon: "flag.checkered", color: .yellow)
                            statTile(title: "Dnešní cíl", value: "\(pm.todayCompletedWords)/\(pm.dailyGoalWords)", icon: "checkmark.circle.fill", color: .teal)
                        }
                    }
                    .padding(.vertical, 4)
                }

                // MARK: - 8. CEFR Level Mastery Breakdown
                Section(header: Text("Znalost slovní zásoby (\(pm.selectedLanguage.titleCs))")) {
                    VStack(spacing: 12) {
                        ForEach(completeStats.levelStats) { stat in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(stat.level.label)
                                        .font(.caption.bold())
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(stat.level.accentColor)
                                        .clipShape(Capsule())

                                    Text(stat.level.descriptionCs)
                                        .font(.caption)
                                        .foregroundColor(.secondary)

                                    Spacer()

                                    Text("\(stat.masteredCount) / \(stat.totalCount)")
                                        .font(.caption.bold())
                                        .foregroundColor(.secondary)
                                }

                                ProgressView(value: stat.ratio)
                                    .tint(stat.level.accentColor)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("⚙️ Nastavení")
            .sheet(isPresented: $showManualSignInSheet) {
                manualSignInSheet
            }
        }
    }

    // MARK: - Subviews & Helpers

    private var dailyBonusText: String {
        switch pm.dailyGoalWords {
        case 0..<25: return "+25 XP"
        case 25..<50: return "+50 XP"
        case 50..<75: return "+75 XP"
        default: return "+100 XP"
        }
    }

    private var accuracyPercent: Int {
        guard pm.totalCorrect > 0 else { return 95 }
        return min(100, max(50, Int(Double(pm.totalCorrect) / Double(pm.totalCorrect + 4) * 100)))
    }

    private func statTile(title: String, value: String, icon: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: icon)
                    .font(.caption.bold())
                    .foregroundColor(color)
                Spacer()
            }
            Text(value)
                .font(.headline.bold())
                .foregroundColor(.primary)
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .tertiarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var manualSignInSheet: some View {
        NavigationView {
            Form {
                Section("Údaje hráče") {
                    TextField("Vaše jméno nebo přezdívka", text: $manualName)
                    TextField("Váš e-mail (pro obnovu účtu)", text: $manualEmail)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                }
            }
            .navigationTitle("Zadat profil ručně")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Zrušit") { showManualSignInSheet = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Uložit") {
                        if !manualEmail.isEmpty {
                            pm.signInManually(name: manualName, email: manualEmail)
                            showManualSignInSheet = false
                        }
                    }
                    .disabled(manualEmail.isEmpty || !manualEmail.contains("@"))
                }
            }
        }
    }
}
