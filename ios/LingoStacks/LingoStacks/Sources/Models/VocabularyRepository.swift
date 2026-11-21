//
//  VocabularyRepository.swift
//  LingoStacks
//
//  Ported from Android LingoStacks (v1.0.15 build 121)
//

import Foundation

public final class VocabularyRepository: ObservableObject {
    public static let shared = VocabularyRepository()

    @Published public private(set) var loadedLanguages: [LearningLanguage: [VocabDeck]] = [:]
    private var wordCache: [String: VocabWord] = [:]
    private let queue = DispatchQueue(label: "cz.lingostacks.vocabularyQueue", qos: .userInitiated)

    public init() {
        // Pre-load default language English on init
        _ = decks(for: .english)
    }

    /// Returns all decks for the given language, loading from bundled JSON on demand
    public func decks(for language: LearningLanguage) -> [VocabDeck] {
        if let cached = loadedLanguages[language] {
            return cached
        }

        let loaded = loadDecksFromBundle(language: language)
        DispatchQueue.main.async {
            self.loadedLanguages[language] = loaded
        }
        return loaded
    }

    /// Fast lookup of a single word by its ID across all loaded decks
    public func word(byId id: String) -> VocabWord? {
        if let cached = wordCache[id] {
            return cached
        }
        for (_, decks) in loadedLanguages {
            for deck in decks {
                if let found = deck.words.first(where: { $0.id == id }) {
                    wordCache[id] = found
                    return found
                }
            }
        }
        return nil
    }

    /// Fast metadata lookup (wordsCount to decksCount) matching Android catalogMetadata
    public func catalogMetadata(for language: LearningLanguage) -> (wordCount: Int, deckCount: Int) {
        switch language {
        case .english: return (1430, 19)
        case .spanish: return (1710, 19)
        case .german: return (1710, 19)
        case .french: return (1710, 19)
        case .italian: return (1710, 19)
        case .portuguese: return (1710, 19)
        case .czech: return (1430, 19)
        }
    }

    private func loadDecksFromBundle(language: LearningLanguage) -> [VocabDeck] {
        let filename = "vocabulary_\(language.code)"
        
        // Try Bundle.main first, fallback to Bundle.module if SPM
        guard let url = Bundle.main.url(forResource: filename, withExtension: "json") ??
                        Bundle(for: VocabularyRepository.self).url(forResource: filename, withExtension: "json") else {
            print("⚠️ [VocabularyRepository] JSON file \(filename).json not found in bundle!")
            return []
        }

        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            let decks = try decoder.decode([VocabDeck].self, from: data)
            
            // Populate word cache
            for deck in decks {
                for word in deck.words {
                    self.wordCache[word.id] = word
                }
            }
            return decks
        } catch {
            print("❌ [VocabularyRepository] Failed to decode \(filename).json: \(error)")
            return []
        }
    }
}
