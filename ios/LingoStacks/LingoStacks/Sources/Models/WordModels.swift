//
//  WordModels.swift
//  LingoStacks
//
//  Ported from Android LingoStacks (v1.0.15 build 121)
//

import Foundation
import SwiftUI

public enum LearningLanguage: String, Codable, CaseIterable, Identifiable {
    case english = "en"
    case spanish = "es"
    case german = "de"
    case french = "fr"
    case italian = "it"
    case portuguese = "pt"
    case czech = "cs"

    public var id: String { rawValue }

    public var code: String { rawValue }

    public var speechLocaleTag: String {
        switch self {
        case .english: return "en-US"
        case .spanish: return "es-ES"
        case .german: return "de-DE"
        case .french: return "fr-FR"
        case .italian: return "it-IT"
        case .portuguese: return "pt-PT"
        case .czech: return "cs-CZ"
        }
    }

    public var flagEmoji: String {
        switch self {
        case .english: return "🇬🇧"
        case .spanish: return "🇪🇸"
        case .german: return "🇩🇪"
        case .french: return "🇫🇷"
        case .italian: return "🇮🇹"
        case .portuguese: return "🇵🇹"
        case .czech: return "🇨🇿"
        }
    }

    public var titleCs: String {
        switch self {
        case .english: return "Angličtina"
        case .spanish: return "Španělština"
        case .german: return "Němčina"
        case .french: return "Francouzština"
        case .italian: return "Italština"
        case .portuguese: return "Portugalština"
        case .czech: return "Čeština"
        }
    }

    public var titleEn: String {
        switch self {
        case .english: return "English"
        case .spanish: return "Spanish"
        case .german: return "German"
        case .french: return "French"
        case .italian: return "Italian"
        case .portuguese: return "Portuguese"
        case .czech: return "Czech"
        }
    }

    public func localizedTitle(isCzech: Bool = true) -> String {
        isCzech ? titleCs : titleEn
    }
}

public enum CefrLevel: String, Codable, CaseIterable, Identifiable {
    case a1 = "A1"
    case a2 = "A2"
    case b1 = "B1"
    case b2 = "B2"
    case c1 = "C1"
    case c2 = "C2"

    public var id: String { rawValue }
    public var label: String { rawValue }

    public var descriptionCs: String {
        switch self {
        case .a1: return "Začátečník"
        case .a2: return "Mírně pokročilý"
        case .b1: return "Středně pokročilý"
        case .b2: return "Vyšší středně pokročilý"
        case .c1: return "Pokročilý"
        case .c2: return "Expert / Rodilý mluvčí"
        }
    }

    public var descriptionEn: String {
        switch self {
        case .a1: return "Beginner"
        case .a2: return "Elementary"
        case .b1: return "Intermediate"
        case .b2: return "Upper Intermediate"
        case .c1: return "Advanced"
        case .c2: return "Mastery / Native"
        }
    }

    public func localizedDescription(isCzech: Bool = true) -> String {
        isCzech ? descriptionCs : descriptionEn
    }

    public var accentColor: Color {
        switch self {
        case .a1: return Color(red: 0.20, green: 0.78, blue: 0.35) // #34C759
        case .a2: return Color(red: 0.00, green: 0.48, blue: 1.00) // #007AFF
        case .b1: return Color(red: 1.00, green: 0.58, blue: 0.00) // #FF9500
        case .b2: return Color(red: 0.69, green: 0.32, blue: 0.87) // #AF52DE
        case .c1: return Color(red: 1.00, green: 0.18, blue: 0.33) // #FF2D55
        case .c2: return Color(red: 0.35, green: 0.34, blue: 0.84) // #5856D6
        }
    }
}

public struct VocabWord: Codable, Identifiable, Hashable {
    public let id: String
    public let czech: String
    public let foreign: String
    public let acceptedVariants: [String]
    public let phonetic: String
    public let emoji: String
    public let exampleSentence: String

    public init(
        id: String,
        czech: String,
        foreign: String,
        acceptedVariants: [String] = [],
        phonetic: String = "",
        emoji: String = "📘",
        exampleSentence: String = ""
    ) {
        self.id = id
        self.czech = czech
        self.foreign = foreign
        self.acceptedVariants = acceptedVariants
        self.phonetic = phonetic
        self.emoji = emoji
        self.exampleSentence = exampleSentence
    }

    /// English compatibility accessor for legacy references
    public var english: String { foreign }
}

public struct VocabDeck: Codable, Identifiable, Hashable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let titleEn: String
    public let subtitleEn: String
    public let emoji: String
    public let level: CefrLevel
    public let words: [VocabWord]

    public func localizedTitle(isCzech: Bool = true) -> String {
        isCzech ? title : (titleEn.isEmpty ? title : titleEn)
    }

    public func localizedSubtitle(isCzech: Bool = true) -> String {
        isCzech ? subtitle : (subtitleEn.isEmpty ? subtitle : subtitleEn)
    }
}

public enum StudyDirection: String, Codable, CaseIterable, Identifiable {
    case nativeToForeign = "native_to_foreign" // CZ -> Target Language
    case foreignToNative = "foreign_to_native" // Target Language -> CZ
    case mixed = "mixed"                      // Alternating both ways

    public var id: String { rawValue }

    public func shortLabel(language: LearningLanguage, nativeLang: LearningLanguage = .czech) -> String {
        let natCode = nativeLang.code.uppercased()
        let foreignCode = language.code.uppercased()
        switch self {
        case .nativeToForeign: return "\(natCode) → \(foreignCode)"
        case .foreignToNative: return "\(foreignCode) → \(natCode)"
        case .mixed: return "\(natCode) ⇄ \(foreignCode)"
        }
    }

    public func localizedDescription(isCzech: Bool, language: LearningLanguage, nativeLang: LearningLanguage = .czech) -> String {
        let natCode = nativeLang.code.uppercased()
        let foreignCode = language.code.uppercased()
        switch self {
        case .nativeToForeign: return "\(natCode) → \(foreignCode)"
        case .foreignToNative: return "\(foreignCode) → \(natCode)"
        case .mixed: return isCzech ? "Střídavě oba směry" : "Mixed both ways"
        }
    }
}

public enum AnswerMethod: String, Codable {
    case spokenCorrect = "spoken_correct"
    case swipedKnow = "swiped_know"
    case swipedDontKnow = "swiped_dont_know"
}

public struct CardAttemptResult: Identifiable {
    public var id = UUID()
    public let word: VocabWord
    public let isCorrect: Bool
    public let method: AnswerMethod
    public let spokenTranscript: String?
    public let wasReversed: Bool

    public init(
        word: VocabWord,
        isCorrect: Bool,
        method: AnswerMethod,
        spokenTranscript: String? = nil,
        wasReversed: Bool = false
    ) {
        self.word = word
        self.isCorrect = isCorrect
        self.method = method
        self.spokenTranscript = spokenTranscript
        self.wasReversed = wasReversed
    }
}

public struct WordSrsStat: Codable, Identifiable {
    public var id: String { wordId }
    public let wordId: String
    public var box: Int          // Leitner box 0 (Failed/Unknown) .. 5 (Long-term Mastered)
    public var lapses: Int       // Number of times user didn't know this word
    public var dueAtMillis: Int64 // Milliseconds timestamp when due for review

    public init(wordId: String, box: Int = 0, lapses: Int = 0, dueAtMillis: Int64 = 0) {
        self.wordId = wordId
        self.box = box
        self.lapses = lapses
        self.dueAtMillis = dueAtMillis
    }
}

public struct FriendProgress: Codable, Identifiable {
    public let id: String
    public let displayName: String
    public let emailOrCode: String
    public let todayCompleted: Int
    public let dailyGoal: Int
    public let streakDays: Int
    public let totalSpoken: Int
    public let statusText: String
    public let isCurrentUser: Bool
    public let masteredCount: Int
    public let totalCorrectCount: Int
    public let completedSessions: Int
    public let accuracyPercent: Int
    public let catalogWordCount: Int
    public let languageCode: String
    public let lastActiveDate: String

    public init(
        id: String,
        displayName: String,
        emailOrCode: String,
        todayCompleted: Int,
        dailyGoal: Int,
        streakDays: Int,
        totalSpoken: Int,
        statusText: String = "Aktivní dnes",
        isCurrentUser: Bool = false,
        masteredCount: Int = 0,
        totalCorrectCount: Int = 0,
        completedSessions: Int = 0,
        accuracyPercent: Int = 0,
        catalogWordCount: Int = 0,
        languageCode: String = "en",
        lastActiveDate: String = ""
    ) {
        self.id = id
        self.displayName = displayName
        self.emailOrCode = emailOrCode
        self.todayCompleted = todayCompleted
        self.dailyGoal = dailyGoal
        self.streakDays = streakDays
        self.totalSpoken = totalSpoken
        self.statusText = statusText
        self.isCurrentUser = isCurrentUser
        self.masteredCount = masteredCount
        self.totalCorrectCount = totalCorrectCount
        self.completedSessions = completedSessions
        self.accuracyPercent = accuracyPercent
        self.catalogWordCount = catalogWordCount
        self.languageCode = languageCode
        self.lastActiveDate = lastActiveDate
    }
}
