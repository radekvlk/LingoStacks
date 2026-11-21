//
//  ProgressManager.swift
//  LingoStacks
//
//  Ported 1:1 from Android ProgressRepository (v1.0.15 build 121)
//

import Foundation
import Combine
import SwiftUI

public enum AppAppearance: String, CaseIterable, Identifiable {
    case system = "system"
    case light = "light"
    case dark = "dark"

    public var id: String { rawValue }

    public func title(isCzech: Bool = true) -> String {
        switch self {
        case .system: return isCzech ? "Podle systému ⚙️" : "System Default ⚙️"
        case .light: return isCzech ? "Světlý ☀️" : "Light ☀️"
        case .dark: return isCzech ? "Tmavý 🌙" : "Dark 🌙"
        }
    }
}

public struct AppleUserProfile: Codable, Equatable {
    public var userId: String
    public var displayName: String
    public var email: String
    public var inviteCode: String

    public init(userId: String, displayName: String, email: String, inviteCode: String) {
        self.userId = userId
        self.displayName = displayName
        self.email = email
        self.inviteCode = inviteCode
    }
}

public struct CefrLevelStat: Identifiable {
    public var id: String { level.id }
    public let level: CefrLevel
    public let masteredCount: Int
    public let totalCount: Int

    public var ratio: Double {
        guard totalCount > 0 else { return 0 }
        return Double(masteredCount) / Double(totalCount)
    }
}

public struct LanguageCompleteStats {
    public let language: LearningLanguage
    public let totalWords: Int
    public let activeMasteredCount: Int
    public let passiveMasteredCount: Int
    public let dueForReviewCount: Int
    public let levelStats: [CefrLevelStat]
}

public final class ProgressManager: ObservableObject {
    public static let shared = ProgressManager()

    private let defaults = UserDefaults.standard
    private let calendar = Calendar.current
    private let dateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.locale = Locale(identifier: "en_US_POSIX")
        return df
    }()

    // MARK: - Published Properties

    @Published public var appearance: AppAppearance {
        didSet {
            defaults.set(appearance.rawValue, forKey: "lingostacks_appearance")
            objectWillChange.send()
        }
    }

    @Published public var selectedLanguage: LearningLanguage {
        didSet {
            defaults.set(selectedLanguage.code, forKey: "lingostacks_selected_language")
            objectWillChange.send()
        }
    }

    @Published public var studyDirection: StudyDirection {
        didSet {
            defaults.set(studyDirection.rawValue, forKey: "lingostacks_study_direction")
            objectWillChange.send()
        }
    }

    @Published public var dailyGoalWords: Int {
        didSet {
            defaults.set(dailyGoalWords, forKey: "lingostacks_daily_goal")
            objectWillChange.send()
        }
    }

    @Published public var todayCompletedWords: Int = 0
    @Published public var todayXp: Int = 0
    @Published public var weeklyCompletedWords: Int = 0
    @Published public var weeklyXp: Int = 0
    @Published public var streakDays: Int = 0
    @Published public var totalSpokenCorrect: Int = 0
    @Published public var completedSessions: Int = 0
    @Published public var totalCorrect: Int = 0

    @Published public var autoPronounce: Bool = true {
        didSet { defaults.set(autoPronounce, forKey: "lingostacks_auto_pronounce") }
    }
    public var autoPronounceEnabled: Bool {
        get { autoPronounce }
        set { autoPronounce = newValue }
    }
    @Published public var pauseCardWhileSpeaking: Bool = true {
        didSet { defaults.set(pauseCardWhileSpeaking, forKey: "lingostacks_pause_speaking") }
    }
    @Published public var hapticFeedbackEnabled: Bool = true {
        didSet { defaults.set(hapticFeedbackEnabled, forKey: "lingostacks_haptic") }
    }

    // User Profile & Apple Sign-In
    @Published public var userProfile: AppleUserProfile? {
        didSet {
            if let user = userProfile {
                defaults.set(try? JSONEncoder().encode(user), forKey: "lingostacks_apple_profile")
            } else {
                defaults.removeObject(forKey: "lingostacks_apple_profile")
            }
            objectWillChange.send()
        }
    }

    public var userName: String {
        userProfile?.displayName ?? defaults.string(forKey: "lingostacks_user_name") ?? "Student"
    }

    public var userInviteCode: String {
        userProfile?.inviteCode ?? defaults.string(forKey: "lingostacks_invite_code") ?? "LINGO-1001"
    }

    // SRS State
    @Published public private(set) var wordSrsMap: [String: WordSrsStat] = [:]
    @Published public private(set) var needsReviewWordIds: Set<String> = []
    @Published public private(set) var masteredWordIds: Set<String> = []
    @Published public private(set) var srsRevision: Int = 0

    private init() {
        let appRaw = defaults.string(forKey: "lingostacks_appearance") ?? AppAppearance.system.rawValue
        self.appearance = AppAppearance(rawValue: appRaw) ?? .system

        let langCode = defaults.string(forKey: "lingostacks_selected_language") ?? "en"
        self.selectedLanguage = LearningLanguage(rawValue: langCode) ?? .english

        let dirRaw = defaults.string(forKey: "lingostacks_study_direction") ?? StudyDirection.nativeToForeign.rawValue
        self.studyDirection = StudyDirection(rawValue: dirRaw) ?? .nativeToForeign

        self.dailyGoalWords = defaults.integer(forKey: "lingostacks_daily_goal")
        if self.dailyGoalWords <= 0 { self.dailyGoalWords = 30 }

        if let userData = defaults.data(forKey: "lingostacks_apple_profile"),
           let user = try? JSONDecoder().decode(AppleUserProfile.self, from: userData) {
            self.userProfile = user
        }

        self.autoPronounce = defaults.object(forKey: "lingostacks_auto_pronounce") as? Bool ?? true
        self.pauseCardWhileSpeaking = defaults.object(forKey: "lingostacks_pause_speaking") as? Bool ?? true
        self.hapticFeedbackEnabled = defaults.object(forKey: "lingostacks_haptic") as? Bool ?? true

        loadSrsData()
        checkNewDayRollover()
    }

    // MARK: - Sign in with Apple & Profile Management

    public func signInWithApple(userId: String, name: String?, email: String?) {
        let cleanName = (name?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false) ? name! : "Uživatel Apple"
        let cleanEmail = (email?.contains("@") == true) ? email!.lowercased() : "\(userId)@privaterelay.appleid.com"
        let hash = abs(cleanEmail.hashValue) % 9000 + 1000
        let code = "SLOV-\(hash)"

        let profile = AppleUserProfile(
            userId: userId,
            displayName: cleanName,
            email: cleanEmail,
            inviteCode: code
        )
        self.userProfile = profile
        defaults.set(cleanName, forKey: "lingostacks_user_name")
        defaults.set(code, forKey: "lingostacks_invite_code")

        pushProgressToFirestore()
        LeaderboardService.shared.refreshLeaderboard()
    }

    public func signInManually(name: String, email: String) {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Student" : name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let hash = abs(cleanEmail.hashValue) % 9000 + 1000
        let code = "SLOV-\(hash)"

        let profile = AppleUserProfile(
            userId: UUID().uuidString,
            displayName: cleanName,
            email: cleanEmail,
            inviteCode: code
        )
        self.userProfile = profile
        defaults.set(cleanName, forKey: "lingostacks_user_name")
        defaults.set(code, forKey: "lingostacks_invite_code")

        pushProgressToFirestore()
        LeaderboardService.shared.refreshLeaderboard()
    }

    public func signOut() {
        self.userProfile = nil
        defaults.removeObject(forKey: "lingostacks_apple_profile")
        LeaderboardService.shared.refreshLeaderboard()
    }

    // MARK: - Firestore Sync (Shared with Android)

    public func pushProgressToFirestore() {
        guard let user = userProfile, user.email.contains("@") else { return }

        let firestoreUrl = "https://firestore.googleapis.com/v1/projects/lingowords-88ed4/databases/(default)/documents/leaderboard/\(user.userId)"
        guard let url = URL(string: firestoreUrl) else { return }

        let currentMastered = masteredWordIds.count
        let totalWords = 1430
        let accuracy = totalCorrect > 0 ? Int(Double(totalCorrect) / Double(max(1, totalCorrect + 5)) * 100) : 95

        let fields: [String: Any] = [
            "uid": ["stringValue": user.userId],
            "code": ["stringValue": user.inviteCode],
            "name": ["stringValue": user.displayName],
            "date": ["stringValue": todayKey],
            "lastActiveDate": ["stringValue": todayKey],
            "languageCode": ["stringValue": selectedLanguage.code],
            "todayCompleted": ["integerValue": "\(todayCompletedWords)"],
            "todayXp": ["integerValue": "\(todayXp)"],
            "weeklyCompleted": ["integerValue": "\(weeklyCompletedWords)"],
            "weeklyXp": ["integerValue": "\(weeklyXp)"],
            "dailyGoal": ["integerValue": "\(dailyGoalWords)"],
            "streakDays": ["integerValue": "\(streakDays)"],
            "totalSpoken": ["integerValue": "\(totalSpokenCorrect)"],
            "masteredCount": ["integerValue": "\(currentMastered)"],
            "totalCorrect": ["integerValue": "\(totalCorrect)"],
            "completedSessions": ["integerValue": "\(completedSessions)"],
            "accuracyPercent": ["integerValue": "\(accuracy)"],
            "catalogWordCount": ["integerValue": "\(totalWords)"],
            "ts": ["integerValue": "\(Int64(Date().timeIntervalSince1970 * 1000))"]
        ]

        let payload: [String: Any] = ["fields": fields]
        guard let body = try? JSONSerialization.data(withJSONObject: payload) else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body

        URLSession.shared.dataTask(with: request) { data, response, error in
            if error == nil {
                print("✓ [Firestore] Leaderboard synced for \(user.displayName)")
            }
        }.resume()
    }

    // MARK: - Date & Streak Helpers

    public var todayKey: String { dateFormatter.string(from: Date()) }

    public var yesterdayKey: String {
        let yesterday = calendar.date(byAdding: .day, value: -1, to: Date()) ?? Date()
        return dateFormatter.string(from: yesterday)
    }

    public func checkNewDayRollover() {
        let currentDay = todayKey
        let lastDay = defaults.string(forKey: "lingostacks_last_study_date")

        if lastDay != currentDay {
            if lastDay == yesterdayKey {
                // Maintained streak!
            } else if lastDay != nil {
                let yesterdayCompleted = defaults.integer(forKey: "lingostacks_completed_\(yesterdayKey)")
                if yesterdayCompleted < dailyGoalWords {
                    streakDays = 0
                    defaults.set(0, forKey: "lingostacks_streak_days")
                }
            }

            todayCompletedWords = defaults.integer(forKey: "lingostacks_completed_\(currentDay)")
            todayXp = defaults.integer(forKey: "lingostacks_xp_\(currentDay)")
        } else {
            todayCompletedWords = defaults.integer(forKey: "lingostacks_completed_\(currentDay)")
            todayXp = defaults.integer(forKey: "lingostacks_xp_\(currentDay)")
        }

        streakDays = defaults.integer(forKey: "lingostacks_streak_days")
        totalSpokenCorrect = defaults.integer(forKey: "lingostacks_total_spoken")
        completedSessions = defaults.integer(forKey: "lingostacks_completed_sessions")
        totalCorrect = defaults.integer(forKey: "lingostacks_total_correct")
        weeklyCompletedWords = calculateWeeklyCompleted()
        weeklyXp = calculateWeeklyXp()
    }

    private func calculateWeeklyCompleted() -> Int {
        var total = 0
        for i in 0..<7 {
            if let d = calendar.date(byAdding: .day, value: -i, to: Date()) {
                let k = dateFormatter.string(from: d)
                total += defaults.integer(forKey: "lingostacks_completed_\(k)")
            }
        }
        return total
    }

    private func calculateWeeklyXp() -> Int {
        var total = 0
        for i in 0..<7 {
            if let d = calendar.date(byAdding: .day, value: -i, to: Date()) {
                let k = dateFormatter.string(from: d)
                total += defaults.integer(forKey: "lingostacks_xp_\(k)")
            }
        }
        return total
    }

    // MARK: - SRS Mechanics (Leitner + SM-2)

    private func srsKey(wordId: String, isReversed: Bool) -> String {
        isReversed ? "\(wordId)__rev" : wordId
    }

    public func isWordMastered(wordId: String, useReversed: Bool = false) -> Bool {
        if studyDirection == .mixed {
            let fwd = wordSrsMap[srsKey(wordId: wordId, isReversed: false)]
            let rev = wordSrsMap[srsKey(wordId: wordId, isReversed: true)]
            let fwdMastered = (fwd != nil && fwd!.box >= 1 && !needsReviewWordIds.contains(srsKey(wordId: wordId, isReversed: false)))
            let revMastered = (rev != nil && rev!.box >= 1 && !needsReviewWordIds.contains(srsKey(wordId: wordId, isReversed: true)))
            return fwdMastered || revMastered
        }
        let key = srsKey(wordId: wordId, isReversed: useReversed)
        let stat = wordSrsMap[key]
        return (stat != nil && stat!.box >= 1 && !needsReviewWordIds.contains(key))
    }

    public func isWordNeedsReview(wordId: String, useReversed: Bool = false, nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> Bool {
        if studyDirection == .mixed {
            let fwdKey = srsKey(wordId: wordId, isReversed: false)
            let revKey = srsKey(wordId: wordId, isReversed: true)
            let fwd = wordSrsMap[fwdKey]
            let rev = wordSrsMap[revKey]
            let fwdNeeds = needsReviewWordIds.contains(fwdKey) || (fwd != nil && (fwd!.box == 0 || fwd!.dueAtMillis <= nowMillis))
            let revNeeds = needsReviewWordIds.contains(revKey) || (rev != nil && (rev!.box == 0 || rev!.dueAtMillis <= nowMillis))
            return fwdNeeds || revNeeds
        }
        let key = srsKey(wordId: wordId, isReversed: useReversed)
        let stat = wordSrsMap[key]
        return needsReviewWordIds.contains(key) || (stat != nil && (stat!.box == 0 || stat!.dueAtMillis <= nowMillis))
    }

    public func getDeckMasteredCount(deck: VocabDeck) -> Int {
        let rev = (studyDirection == .foreignToNative)
        return deck.words.filter { isWordMastered(wordId: $0.id, useReversed: rev) }.count
    }

    public func getDeckNeedsReviewCount(deck: VocabDeck) -> Int {
        let rev = (studyDirection == .foreignToNative)
        return deck.words.filter { isWordNeedsReview(wordId: $0.id, useReversed: rev) }.count
    }

    public func getLanguageCompleteStats(for language: LearningLanguage) -> LanguageCompleteStats {
        let decks = VocabularyRepository.shared.decks(for: language)
        let useRev = (studyDirection == .foreignToNative)
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        var total = 0
        var activeMastered = 0
        var passiveMastered = 0
        var dueCount = 0

        var levelStats: [CefrLevelStat] = []
        for level in CefrLevel.allCases {
            let levelWords = decks.filter { $0.level == level }.flatMap { $0.words }
            let mastered = levelWords.filter { isWordMastered(wordId: $0.id, useReversed: useRev) }.count
            levelStats.append(CefrLevelStat(level: level, masteredCount: mastered, totalCount: levelWords.count))
        }

        for deck in decks {
            for word in deck.words {
                total += 1
                if isWordMastered(wordId: word.id, useReversed: false) { activeMastered += 1 }
                if isWordMastered(wordId: word.id, useReversed: true) { passiveMastered += 1 }
                if isWordNeedsReview(wordId: word.id, useReversed: useRev, nowMillis: now) { dueCount += 1 }
            }
        }

        return LanguageCompleteStats(
            language: language,
            totalWords: total,
            activeMasteredCount: activeMastered,
            passiveMasteredCount: passiveMastered,
            dueForReviewCount: dueCount,
            levelStats: levelStats
        )
    }

    public func prioritizeWordsForSession(words: [VocabWord], targetCount: Int = 10) -> [VocabWord] {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let rev = (studyDirection == .foreignToNative)

        let dueWords = words.filter { isWordNeedsReview(wordId: $0.id, useReversed: rev, nowMillis: now) }
        let unlearned = words.filter { !isWordMastered(wordId: $0.id, useReversed: rev) && !dueWords.contains($0) }
        let mastered = words.filter { isWordMastered(wordId: $0.id, useReversed: rev) && !dueWords.contains($0) }

        var result: [VocabWord] = []
        result.append(contentsOf: dueWords.shuffled())
        result.append(contentsOf: unlearned.shuffled())
        result.append(contentsOf: mastered.shuffled())

        return Array(result.prefix(targetCount))
    }

    // MARK: - Record Attempt (SRS + XP)

    public func recordAttempt(result: CardAttemptResult) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let isReversed = result.wasReversed
        let srsWordKey = srsKey(wordId: result.word.id, isReversed: isReversed)
        let prevStat = wordSrsMap[srsWordKey]

        if result.isCorrect {
            let nextBox = min(5, (prevStat?.box ?? 0) + 1)
            let intervalMillis: Int64
            switch nextBox {
            case 1: intervalMillis = 6 * 3600 * 1000              // 6 hours
            case 2: intervalMillis = 24 * 3600 * 1000             // 1 day
            case 3: intervalMillis = 3 * 24 * 3600 * 1000         // 3 days
            case 4: intervalMillis = 7 * 24 * 3600 * 1000         // 7 days
            default: intervalMillis = 21 * 24 * 3600 * 1000       // 21 days
            }

            wordSrsMap[srsWordKey] = WordSrsStat(
                wordId: srsWordKey,
                box: nextBox,
                lapses: prevStat?.lapses ?? 0,
                dueAtMillis: now + intervalMillis
            )
            needsReviewWordIds.remove(srsWordKey)
            if !isReversed { masteredWordIds.insert(result.word.id) }

            let curDay = todayKey
            let lastDay = defaults.string(forKey: "lingostacks_last_study_date")
            if lastDay != curDay {
                streakDays = (lastDay == yesterdayKey) ? max(1, streakDays + 1) : 1
                defaults.set(streakDays, forKey: "lingostacks_streak_days")
                defaults.set(curDay, forKey: "lingostacks_last_study_date")
            }

            todayCompletedWords += 1
            defaults.set(todayCompletedWords, forKey: "lingostacks_completed_\(curDay)")

            let baseExp = (result.method == .spokenCorrect) ? 15 : 10
            let streakBonus = min(20, streakDays * 2)
            todayXp += (baseExp + streakBonus)
            defaults.set(todayXp, forKey: "lingostacks_xp_\(curDay)")

            if result.method == .spokenCorrect {
                totalSpokenCorrect += 1
                defaults.set(totalSpokenCorrect, forKey: "lingostacks_total_spoken")
            }
            totalCorrect += 1
            defaults.set(totalCorrect, forKey: "lingostacks_total_correct")
        } else {
            let lapses = (prevStat?.lapses ?? 0) + 1
            wordSrsMap[srsWordKey] = WordSrsStat(
                wordId: srsWordKey,
                box: 0,
                lapses: lapses,
                dueAtMillis: now + (2 * 3600 * 1000)
            )
            needsReviewWordIds.insert(srsWordKey)
            if !isReversed { masteredWordIds.remove(result.word.id) }
        }

        srsRevision += 1
        saveSrsData()
        pushProgressToFirestore()
    }

    public func completeSession() {
        completedSessions += 1
        defaults.set(completedSessions, forKey: "lingostacks_completed_sessions")
        weeklyCompletedWords = calculateWeeklyCompleted()
        weeklyXp = calculateWeeklyXp()
        pushProgressToFirestore()
    }

    // MARK: - Persistence

    private func saveSrsData() {
        var lines: [String] = []
        for (_, stat) in wordSrsMap {
            lines.append("\(stat.wordId)|\(stat.box)|\(stat.lapses)|\(stat.dueAtMillis)")
        }
        defaults.set(lines, forKey: "lingostacks_srs_records")
        defaults.set(Array(masteredWordIds), forKey: "lingostacks_mastered_ids")
        defaults.set(Array(needsReviewWordIds), forKey: "lingostacks_needs_review_ids")
    }

    private func loadSrsData() {
        if let lines = defaults.stringArray(forKey: "lingostacks_srs_records") {
            for line in lines {
                let parts = line.split(separator: "|")
                if parts.count >= 4 {
                    let wId = String(parts[0])
                    let box = Int(parts[1]) ?? 0
                    let lapses = Int(parts[2]) ?? 0
                    let dueAt = Int64(parts[3]) ?? 0
                    wordSrsMap[wId] = WordSrsStat(wordId: wId, box: box, lapses: lapses, dueAtMillis: dueAt)
                }
            }
        }
        if let mastered = defaults.stringArray(forKey: "lingostacks_mastered_ids") {
            masteredWordIds = Set(mastered)
        }
        if let needs = defaults.stringArray(forKey: "lingostacks_needs_review_ids") {
            needsReviewWordIds = Set(needs)
        }
    }
}
