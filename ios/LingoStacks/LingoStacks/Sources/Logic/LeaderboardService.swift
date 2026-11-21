//
//  LeaderboardService.swift
//  LingoStacks
//
//  Ported 1:1 from Android Firestore Leaderboard (v1.0.15 build 121)
//

import Foundation
import Combine

public enum LeaderboardPeriod: String, CaseIterable, Identifiable {
    case today = "today"
    case week = "week"

    public var id: String { rawValue }

    public func title(isCzech: Bool = true) -> String {
        switch self {
        case .today: return isCzech ? "Dnes" : "Today"
        case .week: return isCzech ? "Tento týden" : "This Week"
        }
    }
}

public final class LeaderboardService: ObservableObject {
    public static let shared = LeaderboardService()

    private let firestoreBaseUrl = "https://firestore.googleapis.com/v1/projects/lingowords-88ed4/databases/(default)/documents/leaderboard"

    @Published public private(set) var dailyLeaderboard: [FriendProgress] = []
    @Published public private(set) var weeklyLeaderboard: [FriendProgress] = []
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var syncStatusMessage: String = "Online"

    private init() {
        refreshLeaderboard()
    }

    public func refreshLeaderboard() {
        guard !isLoading else { return }
        isLoading = true

        guard let url = URL(string: firestoreBaseUrl) else {
            isLoading = false
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 10.0

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.isLoading = false
                if let error = error {
                    self.syncStatusMessage = "Chyba připojení: \(error.localizedDescription)"
                    self.populateLocalFallback()
                    return
                }

                guard let data = data else {
                    self.populateLocalFallback()
                    return
                }

                do {
                    if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                       let documents = json["documents"] as? [[String: Any]] {
                        self.parseDocuments(documents)
                        self.syncStatusMessage = "Synchronizováno online ✓"
                    } else {
                        self.populateLocalFallback()
                    }
                } catch {
                    self.populateLocalFallback()
                }
            }
        }.resume()
    }

    private func parseDocuments(_ documents: [[String: Any]]) {
        var entries: [FriendProgress] = []
        let myInviteCode = ProgressManager.shared.userInviteCode

        for doc in documents {
            guard let fields = doc["fields"] as? [String: [String: Any]] else { continue }

            let code = fields["code"]?["stringValue"] as? String ?? ""
            let name = fields["name"]?["stringValue"] as? String ?? "Student"
            let todayCompleted = Int(fields["todayCompleted"]?["integerValue"] as? String ?? "0") ?? 0
            let dailyGoal = Int(fields["dailyGoal"]?["integerValue"] as? String ?? "30") ?? 30
            let streakDays = Int(fields["streakDays"]?["integerValue"] as? String ?? "0") ?? 0
            let totalSpoken = Int(fields["totalSpoken"]?["integerValue"] as? String ?? "0") ?? 0
            let mastered = Int(fields["masteredCount"]?["integerValue"] as? String ?? "0") ?? 0
            let totalCorrect = Int(fields["totalCorrect"]?["integerValue"] as? String ?? "0") ?? 0
            let completedSessions = Int(fields["completedSessions"]?["integerValue"] as? String ?? "0") ?? 0
            let lang = fields["languageCode"]?["stringValue"] as? String ?? "en"
            let lastActive = fields["lastActiveDate"]?["stringValue"] as? String ?? ""

            let isMe = (code == myInviteCode)

            entries.append(FriendProgress(
                id: code.isEmpty ? UUID().uuidString : code,
                displayName: name,
                emailOrCode: code,
                todayCompleted: todayCompleted,
                dailyGoal: dailyGoal,
                streakDays: streakDays,
                totalSpoken: totalSpoken,
                statusText: todayCompleted > 0 ? "Aktivní dnes" : "Odpočívá",
                isCurrentUser: isMe,
                masteredCount: mastered,
                totalCorrectCount: totalCorrect,
                completedSessions: completedSessions,
                accuracyPercent: 92,
                catalogWordCount: 1430,
                languageCode: lang,
                lastActiveDate: lastActive
            ))
        }

        // Add or merge current user if not already present
        if !entries.contains(where: { $0.isCurrentUser }) {
            entries.append(currentUserEntry())
        }

        self.dailyLeaderboard = entries.sorted { $0.todayCompleted > $1.todayCompleted }
        self.weeklyLeaderboard = entries.sorted { $0.streakDays > $1.streakDays }
    }

    private func currentUserEntry() -> FriendProgress {
        let pm = ProgressManager.shared
        return FriendProgress(
            id: pm.userInviteCode,
            displayName: pm.userName,
            emailOrCode: pm.userInviteCode,
            todayCompleted: pm.todayCompletedWords,
            dailyGoal: pm.dailyGoalWords,
            streakDays: pm.streakDays,
            totalSpoken: pm.totalSpokenCorrect,
            statusText: "Aktivní dnes",
            isCurrentUser: true,
            masteredCount: pm.masteredWordIds.count,
            totalCorrectCount: pm.totalCorrect,
            completedSessions: pm.completedSessions,
            accuracyPercent: 95,
            catalogWordCount: 1430,
            languageCode: pm.selectedLanguage.code,
            lastActiveDate: pm.todayKey
        )
    }

    private func populateLocalFallback() {
        let me = currentUserEntry()
        self.dailyLeaderboard = [me]
        self.weeklyLeaderboard = [me]
    }
}
