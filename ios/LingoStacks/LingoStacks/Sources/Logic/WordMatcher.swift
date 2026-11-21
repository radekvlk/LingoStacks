//
//  WordMatcher.swift
//  LingoStacks
//
//  Ported 1:1 from Android LingoStacks (v1.0.15 build 121)
//

import Foundation

public final class WordMatcher {
    public static let shared = WordMatcher()

    private let leadingArticles = try! NSRegularExpression(
        pattern: "^(a|an|the|to|el|la|los|las|un|una|unos|unas|der|die|das|den|dem|des|ein|eine|einen|einem|einer|zu|le|les|du|l|il|lo|gli|uno|o|os|as|uma|uns|umas|ao|do|da|dos|das|no|na|nos|nas|dum|duma|pelo|pela|pelos|pelas)\\s+",
        options: .caseInsensitive
    )
    private let elidedArticlePrefix = try! NSRegularExpression(
        pattern: "^(l|un)'([a-zàâäéèêëïîôöùûüçæœ])",
        options: .caseInsensitive
    )

    private let britishToAmerican: [String: String] = [
        "colour": "color", "colours": "colors", "favour": "favor", "favourite": "favorite",
        "flavour": "flavor", "humour": "humor", "labour": "labor", "neighbour": "neighbor",
        "behaviour": "behavior", "honour": "honor", "centre": "center", "theatre": "theater",
        "metre": "meter", "litre": "liter", "grey": "gray", "tyre": "tire",
        "analyse": "analyze", "organise": "organize", "realise": "realize", "apologise": "apologize",
        "recognise": "recognize", "practise": "practice", "licence": "license", "defence": "defense",
        "offence": "offense", "dialogue": "dialog", "catalogue": "catalog", "programme": "program",
        "travelling": "traveling", "cancelled": "canceled", "jewellery": "jewelry", "cheque": "check",
        "mum": "mom", "pyjamas": "pajamas", "aeroplane": "airplane", "doughnut": "donut"
    ]

    private let commonContractions: [String: String] = [
        "i'm": "i am", "im": "i am", "you're": "you are", "youre": "you are",
        "he's": "he is", "she's": "she is", "it's": "it is", "we're": "we are",
        "they're": "they are", "theyre": "they are", "i've": "i have", "ive": "i have",
        "you've": "you have", "youve": "you have", "we've": "we have", "weve": "we have",
        "they've": "they have", "theyve": "they have", "don't": "do not", "dont": "do not",
        "doesn't": "does not", "doesnt": "does not", "didn't": "did not", "didnt": "did not",
        "can't": "cannot", "cant": "cannot", "won't": "will not", "wont": "will not",
        "isn't": "is not", "isnt": "is not", "aren't": "are not", "arent": "are not",
        "wasn't": "was not", "wasnt": "was not", "weren't": "were not", "werent": "were not",
        "haven't": "have not", "havent": "have not", "hasn't": "has not", "hasnt": "has not",
        "let's": "let us"
    ]

    private let digitToWord: [String: String] = [
        "0": "zero", "1": "one", "2": "two", "3": "three", "4": "four",
        "5": "five", "6": "six", "7": "seven", "8": "eight", "9": "nine",
        "10": "ten", "11": "eleven", "12": "twelve", "13": "thirteen", "14": "fourteen",
        "15": "fifteen", "16": "sixteen", "17": "seventeen", "18": "eighteen", "19": "nineteen",
        "20": "twenty", "30": "thirty", "40": "forty", "50": "fifty", "60": "sixty",
        "70": "seventy", "80": "eighty", "90": "ninety", "100": "hundred", "1000": "thousand",
        "1st": "first", "2nd": "second", "3rd": "third", "4th": "fourth", "5th": "fifth",
        "6th": "sixth", "7th": "seventh", "8th": "eighth", "9th": "ninth", "10th": "tenth"
    ]

    private let englishLetterToName: [String: String] = [
        "a": "ay", "b": "bee", "c": "see", "d": "dee", "e": "ee", "f": "ef", "g": "gee",
        "h": "aitch", "i": "eye", "j": "jay", "k": "kay", "l": "el", "m": "em", "n": "en",
        "o": "oh", "p": "pee", "q": "cue", "r": "are", "s": "ess", "t": "tea", "u": "you",
        "v": "vee", "w": "double u", "x": "ex", "y": "why", "z": "zee"
    ]

    private let homophoneGroups: [Set<String>] = [
        ["one", "won", "1"], ["two", "to", "too", "2"], ["three", "tree", "free", "3"],
        ["four", "for", "fore", "4"], ["eight", "ate", "8"], ["sun", "son", "san"],
        ["tea", "tee", "t"], ["be", "bee", "b"], ["sea", "see", "c"], ["eye", "i", "aye"],
        ["you", "u", "ewe", "yew"], ["are", "r", "our"], ["why", "y"], ["oh", "owe", "o"],
        ["pea", "pee", "p"], ["queue", "cue", "q"], ["ex", "x"], ["jay", "j"],
        ["kay", "k"], ["el", "l"], ["em", "m"], ["en", "n"], ["ess", "es", "s"],
        ["ay", "a"], ["dee", "d"], ["ee", "e"], ["vee", "v"], ["zee", "zed", "z"],
        ["ok", "okay", "o k"], ["hour", "our"], ["meat", "meet"], ["weak", "week"],
        ["peace", "piece"], ["right", "write", "rite"], ["wear", "where", "ware"],
        ["weather", "whether"], ["wait", "weight"], ["whole", "hole"], ["wood", "would"],
        ["hear", "here"], ["break", "brake"], ["steak", "stake"], ["flour", "flower"],
        ["night", "knight"], ["pair", "pear"], ["hair", "hare"], ["bear", "bare"],
        ["dear", "deer"], ["fair", "fare"], ["heal", "heel"], ["steal", "steel"],
        ["tail", "tale"], ["tie", "thai", "tye", "ty"], ["way", "weigh"], ["which", "witch"],
        ["root", "route"], ["board", "bored"], ["aloud", "allowed"], ["buy", "by", "bye"],
        ["cell", "sell"], ["cent", "sent", "scent"], ["die", "dye"], ["knew", "new"],
        ["know", "no"], ["mail", "male"], ["main", "mane"], ["none", "nun"],
        ["pain", "pane"], ["plain", "plane"], ["rain", "reign", "rein"], ["read", "red"],
        ["role", "roll"], ["sail", "sale"], ["scene", "seen"], ["sole", "soul"],
        ["some", "sum"], ["stair", "stare"], ["sweet", "suite"], ["their", "there", "theyre"],
        ["threw", "through"], ["waist", "waste"], ["war", "worn", "wore"], ["warn", "worn"],
        ["work", "walk", "wok"], ["bird", "beard", "bert"], ["bed", "bad", "bat", "bet"],
        ["cat", "cut", "caught", "kat", "ket"], ["dog", "dock", "dug", "dok"],
        ["cup", "cap", "kap"], ["run", "ran"], ["bus", "bass", "bas"], ["bag", "beg", "bak"],
        ["big", "bik"], ["box", "boks"], ["can", "ken"], ["man", "men"], ["sit", "set"],
        ["pen", "pan"], ["hi", "high"], ["red", "ret"], ["sad", "sat", "sed"],
        ["leg", "lek"], ["egg", "ek"], ["pig", "pick", "peak"], ["sheep", "ship", "cheap"],
        ["full", "fool"], ["pull", "pool"], ["hat", "hut", "hot"], ["heart", "hard", "hurt"],
        ["shirt", "short"], ["skirt", "skate"], ["think", "sink", "fink", "thing"],
        ["thank", "tank", "sank"], ["thin", "tin", "fin"], ["thick", "tick", "sick"],
        ["mouth", "mouse"], ["south", "sauce"], ["north", "norse"], ["bath", "bus", "bass"],
        ["saw", "so", "sore"], ["blew", "blue"], ["flew", "flu"], ["rode", "road"],
        ["wrote", "rote"], ["been", "bean", "bin"], ["made", "maid"], ["paid", "payed"],
        ["find", "fined"], ["caught", "court"], ["grown", "groan"], ["thrown", "throne"],
        ["led", "lead"], ["rung", "wrung"],
        // Spanish
        ["zero", "cero", "0"], ["one", "won", "uno", "una", "1"], ["two", "to", "too", "dos", "2"],
        ["three", "tree", "free", "tres", "3"], ["four", "for", "fore", "cuatro", "4"],
        ["five", "cinco", "5"], ["six", "seis", "6"], ["seven", "siete", "7"],
        ["eight", "ate", "ocho", "8"], ["nine", "nueve", "9"], ["ten", "diez", "10"],
        ["eleven", "once", "11"], ["twelve", "doce", "12"], ["thirteen", "trece", "13"],
        ["fourteen", "catorce", "14"], ["fifteen", "quince", "15"], ["sixteen", "dieciseis", "16"],
        ["seventeen", "diecisiete", "17"], ["eighteen", "dieciocho", "18"],
        ["nineteen", "diecinueve", "19"], ["twenty", "veinte", "20"], ["thirty", "treinta", "30"],
        ["forty", "cuarenta", "40"], ["fifty", "cincuenta", "50"], ["sixty", "sesenta", "60"],
        ["seventy", "setenta", "70"], ["eighty", "ochenta", "80"], ["ninety", "noventa", "90"],
        ["hundred", "cien", "ciento", "100"], ["thousand", "mil", "1000"],
        ["hola", "ola"], ["haber", "a ver"], ["hecho", "echo"], ["hasta", "asta"],
        ["hora", "ora"], ["hoy", "oy"], ["hay", "ay", "ahi"], ["vaca", "baca"],
        ["vaso", "baso"], ["vino", "bino"], ["bello", "vello"], ["votar", "botar"],
        ["casa", "caza"], ["cocer", "coser"], ["cien", "sien"], ["cerrar", "serrar"],
        ["callo", "cayo"], ["te", "té", "tea", "t"], ["si", "sí", "see"],
        // German
        ["null", "0"], ["eins", "ein", "eine", "1"], ["zwei", "zwo", "2"], ["drei", "3"],
        ["vier", "für", "fur", "4"], ["fünf", "funf", "5"], ["sechs", "sex", "6"],
        ["sieben", "7"], ["acht", "8"], ["neun", "9"], ["zehn", "10"], ["zwanzig", "20"],
        ["hundert", "100"], ["tausend", "1000"], ["das", "dass"], ["seid", "seit"],
        ["war", "wahr"], ["mehr", "meer"], ["mal", "mahl"], ["leere", "lehre"],
        ["seite", "saite"], ["wieder", "wider"], ["mann", "man"], ["viel", "fiel"],
        ["rad", "rat"], ["tod", "tot"], ["stadt", "statt"], ["lid", "lied"],
        ["biss", "bis"], ["isst", "ist"], ["laib", "leib"], ["stiel", "stil"],
        ["tee", "te", "t"], ["ei", "ay", "ai"], ["kuh", "ku"], ["rot", "rod"],
        ["gut", "gud"], ["bad", "bat"], ["tag", "tak"], ["zug", "zuk"],
        // French
        ["un", "une", "1"], ["deux", "2"], ["trois", "3"], ["quatre", "4"], ["cinq", "5"],
        ["six", "6"], ["sept", "7"], ["huit", "8"], ["neuf", "9"], ["dix", "dis", "dit", "10"],
        ["vingt", "vin", "vain", "20"], ["cent", "sans", "sang", "sent", "100"],
        ["mille", "mil", "1000"], ["eau", "haut", "au", "o"], ["the", "tea", "t"],
        ["lait", "lai", "les"], ["riz", "ri"], ["pain", "pin"], ["mer", "mere", "maire"],
        ["pere", "paire", "pair"], ["vert", "verre", "ver", "vers"], ["faim", "fin"],
        ["foi", "fois", "foie"], ["cour", "cours", "court"], ["compte", "comte", "conte"],
        ["sain", "saint", "sein"], ["et", "est", "es"], ["ou", "août"], ["son", "sont"],
        ["temps", "tant", "tend"], ["voix", "voie", "vois", "voit"], ["coeur", "choeur"],
        // Italian
        ["uno", "una", "1"], ["due", "2"], ["tre", "3"], ["quattro", "4"], ["cinque", "5"],
        ["sei", "6"], ["sette", "7"], ["otto", "8"], ["nove", "9"], ["dieci", "10"],
        ["venti", "20"], ["cento", "100"], ["anno", "hanno"], ["o", "ho"], ["ai", "hai"],
        ["a", "ha"],
        // Czech
        ["led", "let"], ["dub", "dup"], ["plot", "plod"], ["med", "met"],
        ["krev", "kref"], ["lev", "lef"], ["zeď", "zed", "zet"], ["hrad", "hrat"],
        ["oběd", "obed", "obet"], ["nůž", "nuz", "nus"], ["čaj", "caj"], ["sýr", "sir"]
    ]

    public func stripAccents(_ input: String) -> String {
        input.folding(options: .diacriticInsensitive, locale: .current)
    }

    public func normalize(_ input: String, stripLeadingForeignArticles: Bool = true) -> String {
        var text = input
            .replacingOccurrences(of: "ß", with: "ss")
            .replacingOccurrences(of: "ẞ", with: "ss")
            .replacingOccurrences(of: "œ", with: "oe")
            .replacingOccurrences(of: "Œ", with: "OE")
            .replacingOccurrences(of: "æ", with: "ae")
            .replacingOccurrences(of: "Æ", with: "AE")
            .replacingOccurrences(of: "’", with: "'")
            .lowercased()

        if stripLeadingForeignArticles {
            let range = NSRange(text.startIndex..., in: text)
            text = elidedArticlePrefix.stringByReplacingMatches(in: text, options: [], range: range, withTemplate: "$1 $2")
        }

        let stripped = stripAccents(text)
        let filtered = stripped.unicodeScalars.map { CharacterSet.alphanumerics.contains($0) || $0 == "'" || $0 == " " ? Character($0) : " " }
        let spaceCleaned = String(filtered).split(separator: " ", omittingEmptySubsequences: true).map(String.init)

        let expandedWords: [String]
        if stripLeadingForeignArticles {
            expandedWords = spaceCleaned.flatMap { token -> [String] in
                let expanded = commonContractions[token]
                let parts = expanded?.split(separator: " ").map(String.init) ?? [token.replacingOccurrences(of: "'", with: "")]
                return parts.map { britishToAmerican[$0] ?? $0 }.filter { !$0.isEmpty }
            }
        } else {
            expandedWords = spaceCleaned.map { $0.replacingOccurrences(of: "'", with: "") }.filter { !$0.isEmpty }
        }

        var joined = expandedWords.joined(separator: " ")
        if stripLeadingForeignArticles {
            let range = NSRange(joined.startIndex..., in: joined)
            joined = leadingArticles.stringByReplacingMatches(in: joined, options: [], range: range, withTemplate: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return joined
    }

    public func phoneticCode(word: String, language: LearningLanguage = .english) -> String {
        switch language {
        case .czech: return czechPhoneticCode(word)
        case .spanish: return spanishPhoneticCode(word)
        case .german: return germanPhoneticCode(word)
        case .french: return frenchPhoneticCode(word)
        case .italian: return italianPhoneticCode(word)
        case .portuguese: return portuguesePhoneticCode(word)
        case .english: return englishPhoneticCode(word)
        }
    }

    private func englishPhoneticCode(_ word: String) -> String {
        if word.isEmpty { return "" }
        var s = stripAccents(word).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        s = digitToWord[s] ?? s
        if s.count == 1, let ch = s.first, ch >= "a" && ch <= "z" {
            s = englishLetterToName[s] ?? s
        }
        if s.count > 2 && s.hasPrefix("h") {
            s.removeFirst()
        }

        s = s.replacingOccurrences(of: "ough", with: "o")
            .replacingOccurrences(of: "augh", with: "af")
            .replacingOccurrences(of: "ight", with: "it")
            .replacingOccurrences(of: "tion", with: "sn")
            .replacingOccurrences(of: "sion", with: "sn")
            .replacingOccurrences(of: "ture", with: "cr")
            .replacingOccurrences(of: "sure", with: "sr")
            .replacingOccurrences(of: "kn", with: "n")
            .replacingOccurrences(of: "wr", with: "r")
            .replacingOccurrences(of: "wh", with: "v")
            .replacingOccurrences(of: "gn", with: "n")
            .replacingOccurrences(of: "ps", with: "s")
            .replacingOccurrences(of: "ph", with: "f")
            .replacingOccurrences(of: "gh", with: "")
            .replacingOccurrences(of: "sh", with: "s")
            .replacingOccurrences(of: "tch", with: "c")
            .replacingOccurrences(of: "ch", with: "c")
            .replacingOccurrences(of: "th", with: "t")
            .replacingOccurrences(of: "qu", with: "k")
            .replacingOccurrences(of: "ck", with: "k")
            .replacingOccurrences(of: "dg", with: "j")
            .replacingOccurrences(of: "ce", with: "se")
            .replacingOccurrences(of: "ci", with: "si")
            .replacingOccurrences(of: "cy", with: "sy")
            .replacingOccurrences(of: "ee", with: "i")
            .replacingOccurrences(of: "ea", with: "i")
            .replacingOccurrences(of: "oo", with: "u")
            .replacingOccurrences(of: "ou", with: "u")
            .replacingOccurrences(of: "ow", with: "o")
            .replacingOccurrences(of: "ai", with: "e")
            .replacingOccurrences(of: "ay", with: "e")
            .replacingOccurrences(of: "oa", with: "o")

        if s.count > 3 && s.hasSuffix("e") { s.removeLast() }
        if s.count > 3 && s.hasSuffix("mb") { s.removeLast() }

        var result = ""
        var prevChar: Character = " "
        for ch in s {
            let mapped: Character
            switch ch {
            case "w", "v": mapped = "v"
            case "z": mapped = "s"
            case "c", "q": mapped = "k"
            case "y": mapped = "i"
            case "a", "e": mapped = "a"
            case "d": mapped = "t"
            case "b": mapped = "p"
            case "g", "j": mapped = "k"
            default: mapped = ch
            }
            if mapped != prevChar {
                result.append(mapped)
                prevChar = mapped
            }
        }
        return result
    }

    private func spanishPhoneticCode(_ word: String) -> String {
        if word.isEmpty { return "" }
        var s = stripAccents(word).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        s = digitToWord[s] ?? s
        s = s.replacingOccurrences(of: "ch", with: "x")
            .replacingOccurrences(of: "h", with: "")
            .replacingOccurrences(of: "gue", with: "ge")
            .replacingOccurrences(of: "gui", with: "gi")
            .replacingOccurrences(of: "que", with: "ke")
            .replacingOccurrences(of: "qui", with: "ki")
            .replacingOccurrences(of: "qu", with: "k")
            .replacingOccurrences(of: "ge", with: "je")
            .replacingOccurrences(of: "gi", with: "ji")
            .replacingOccurrences(of: "ce", with: "se")
            .replacingOccurrences(of: "ci", with: "si")
            .replacingOccurrences(of: "ll", with: "i")
            .replacingOccurrences(of: "rr", with: "r")

        var result = ""
        var prev: Character = " "
        for ch in s {
            let mapped: Character
            switch ch {
            case "z": mapped = "s"
            case "v", "b": mapped = "p"
            case "w": mapped = "u"
            case "y": mapped = "i"
            case "c", "q": mapped = "k"
            default: mapped = ch
            }
            if mapped != prev {
                result.append(mapped)
                prev = mapped
            }
        }
        return result
    }

    private func germanPhoneticCode(_ word: String) -> String {
        if word.isEmpty { return "" }
        var s = stripAccents(word).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        s = digitToWord[s] ?? s
        s = s.replacingOccurrences(of: "tsch", with: "c")
            .replacingOccurrences(of: "sch", with: "s")
            .replacingOccurrences(of: "chs", with: "ks")
            .replacingOccurrences(of: "ch", with: "x")
            .replacingOccurrences(of: "ck", with: "k")
            .replacingOccurrences(of: "qu", with: "kv")
            .replacingOccurrences(of: "ph", with: "f")
            .replacingOccurrences(of: "th", with: "t")
            .replacingOccurrences(of: "dt", with: "t")
            .replacingOccurrences(of: "tz", with: "ts")
            .replacingOccurrences(of: "z", with: "ts")
            .replacingOccurrences(of: "eu", with: "oi")
            .replacingOccurrences(of: "ei", with: "ai")
            .replacingOccurrences(of: "ie", with: "i")

        var result = ""
        var prev: Character = " "
        for ch in s {
            let mapped: Character
            switch ch {
            case "v": mapped = "f"
            case "w": mapped = "v"
            case "y", "j": mapped = "i"
            case "c", "q": mapped = "k"
            default: mapped = ch
            }
            if mapped != prev {
                result.append(mapped)
                prev = mapped
            }
        }
        return result
    }

    private func frenchPhoneticCode(_ word: String) -> String {
        if word.isEmpty { return "" }
        var s = stripAccents(word).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        s = digitToWord[s] ?? s
        s = s.replacingOccurrences(of: "eaux", with: "o")
            .replacingOccurrences(of: "eau", with: "o")
            .replacingOccurrences(of: "au", with: "o")
            .replacingOccurrences(of: "ph", with: "f")
            .replacingOccurrences(of: "ch", with: "s")
            .replacingOccurrences(of: "qu", with: "k")
            .replacingOccurrences(of: "gue", with: "ge")
            .replacingOccurrences(of: "gui", with: "gi")
            .replacingOccurrences(of: "gn", with: "ni")
            .replacingOccurrences(of: "ill", with: "i")
            .replacingOccurrences(of: "ou", with: "u")
            .replacingOccurrences(of: "oi", with: "va")
            .replacingOccurrences(of: "ai", with: "e")
            .replacingOccurrences(of: "ei", with: "e")
            .replacingOccurrences(of: "ce", with: "se")
            .replacingOccurrences(of: "ci", with: "si")
            .replacingOccurrences(of: "ge", with: "je")
            .replacingOccurrences(of: "gi", with: "ji")

        if s.count > 3, let last = s.last, "stdpxz".contains(last) {
            s.removeLast()
        }

        var result = ""
        var prev: Character = " "
        for ch in s {
            if ch == "h" { continue }
            let mapped: Character
            switch ch {
            case "c", "q": mapped = "k"
            case "w": mapped = "v"
            case "y": mapped = "i"
            case "z": mapped = "s"
            default: mapped = ch
            }
            if mapped != prev {
                result.append(mapped)
                prev = mapped
            }
        }
        return result
    }

    private func italianPhoneticCode(_ word: String) -> String {
        if word.isEmpty { return "" }
        var s = stripAccents(word).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        s = digitToWord[s] ?? s
        s = s.replacingOccurrences(of: "gli", with: "li")
            .replacingOccurrences(of: "gn", with: "ni")
            .replacingOccurrences(of: "sce", with: "se")
            .replacingOccurrences(of: "sci", with: "si")
            .replacingOccurrences(of: "chi", with: "ki")
            .replacingOccurrences(of: "che", with: "ke")
            .replacingOccurrences(of: "ghi", with: "gi")
            .replacingOccurrences(of: "ghe", with: "ge")
            .replacingOccurrences(of: "ce", with: "je")
            .replacingOccurrences(of: "ci", with: "ji")
            .replacingOccurrences(of: "qu", with: "kv")
            .replacingOccurrences(of: "z", with: "ts")

        var result = ""
        var prev: Character = " "
        for ch in s {
            if ch == "h" { continue }
            let mapped: Character
            switch ch {
            case "c", "q": mapped = "k"
            case "w": mapped = "v"
            case "y", "j": mapped = "i"
            default: mapped = ch
            }
            if mapped != prev {
                result.append(mapped)
                prev = mapped
            }
        }
        return result
    }

    private func portuguesePhoneticCode(_ word: String) -> String {
        if word.isEmpty { return "" }
        var s = stripAccents(word).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        s = digitToWord[s] ?? s
        s = s.replacingOccurrences(of: "lh", with: "li")
            .replacingOccurrences(of: "nh", with: "ni")
            .replacingOccurrences(of: "ch", with: "x")
            .replacingOccurrences(of: "rr", with: "r")
            .replacingOccurrences(of: "ss", with: "s")
            .replacingOccurrences(of: "qu", with: "k")
            .replacingOccurrences(of: "gu", with: "g")
            .replacingOccurrences(of: "z", with: "s")

        var result = ""
        var prev: Character = " "
        for ch in s {
            if ch == "h" { continue }
            let mapped: Character
            switch ch {
            case "c", "q": mapped = "k"
            case "v", "b": mapped = "p"
            default: mapped = ch
            }
            if mapped != prev {
                result.append(mapped)
                prev = mapped
            }
        }
        return result
    }

    private func czechPhoneticCode(_ word: String) -> String {
        if word.isEmpty { return "" }
        var s = stripAccents(word).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        s = s.replacingOccurrences(of: "ch", with: "x")
            .replacingOccurrences(of: "me", with: "mne")
            .replacingOccurrences(of: "qu", with: "kv")

        var result = ""
        var prev: Character = " "
        for ch in s {
            let mapped: Character
            switch ch {
            case "y": mapped = "i"
            case "b": mapped = "p"
            case "d": mapped = "t"
            case "g": mapped = "k"
            case "v", "w": mapped = "f"
            case "z": mapped = "s"
            case "q": mapped = "k"
            default: mapped = ch
            }
            if mapped != prev {
                result.append(mapped)
                prev = mapped
            }
        }
        return result
    }

    private func areHomophones(_ a: String, _ b: String) -> Bool {
        if a == b { return true }
        return homophoneGroups.contains { $0.contains(a) && $0.contains(b) }
    }

    public func levenshteinDistance(_ s1: String, _ s2: String) -> Int {
        let empty = [Int](repeating: 0, count: s2.count + 1)
        var last = [Int](0...s2.count)

        for (i, char1) in s1.enumerated() {
            var cur = [i + 1] + empty.dropFirst()
            for (j, char2) in s2.enumerated() {
                cur[j + 1] = char1 == char2 ? last[j] : min(last[j], last[j + 1], cur[j]) + 1
            }
            last = cur
        }
        return last.last ?? 0
    }

    /// Evaluates if spokenCandidates matches targetWord across all 5 confidence tiers
    public func matches(
        spokenCandidates: [String],
        targetWord: VocabWord,
        isFinal: Bool = true,
        expectCzech: Bool = false,
        language: LearningLanguage = .english
    ) -> String? {
        let isCzech = (language == .czech)
        let rawTargets = extractNormalizedTargets(targetWord: targetWord, expectCzech: expectCzech, language: language)
        let matchedDisplay = expectCzech ? targetWord.czech : targetWord.foreign

        var parsedCandidates: [(norm: String, words: [String], phrases: [String], noSpace: [String])] = []
        for cand in spokenCandidates {
            let norm = normalize(cand, stripLeadingForeignArticles: !isCzech)
            if norm.isEmpty { continue }
            let words = norm.split(separator: " ").map(String.init)
            var phrasesSet = [norm]
            if words.count >= 2 {
                phrasesSet.append(words.last!)
                phrasesSet.append(words.suffix(2).joined(separator: " "))
                let limit = min(words.count, 10)
                for i in 0..<limit {
                    phrasesSet.append(words[i])
                    if i + 1 < limit { phrasesSet.append("\(words[i]) \(words[i+1])") }
                }
            }
            let noSpace = phrasesSet.map { $0.replacingOccurrences(of: " ", with: "") }
            parsedCandidates.append((norm, words, phrasesSet, noSpace))
        }

        if parsedCandidates.isEmpty || rawTargets.isEmpty { return nil }

        // TIER 1: Exact matches & space-agnostic compounds
        for target in rawTargets {
            let targetWords = target.split(separator: " ").map(String.init)
            let targetNoSpace = target.replacingOccurrences(of: " ", with: "")
            for cand in parsedCandidates {
                for phrase in cand.phrases {
                    if phrase == target || cand.words.contains(target) { return matchedDisplay }
                    if target.contains(" ") && phrase.contains(target) { return matchedDisplay }
                }
                if targetNoSpace.count >= 3 && cand.noSpace.contains(targetNoSpace) { return matchedDisplay }
                if targetWords.count >= 2 && cand.words.count >= targetWords.count {
                    if targetWords.allSatisfy({ cand.words.contains($0) }) { return matchedDisplay }
                }
            }
        }

        // TIER 2: Homophones
        for target in rawTargets {
            for cand in parsedCandidates {
                for phrase in cand.phrases {
                    if areHomophones(phrase, target) || cand.words.contains(where: { areHomophones($0, target) }) {
                        return matchedDisplay
                    }
                }
            }
        }

        // TIER 3: Inflections
        for target in rawTargets {
            let inflections = [
                "\(target)s", "\(target)es", "\(target)d", "\(target)ed", "\(target)ing"
            ]
            for cand in parsedCandidates {
                for phrase in cand.phrases {
                    if inflections.contains(phrase) || cand.words.contains(where: { inflections.contains($0) }) {
                        return matchedDisplay
                    }
                }
            }
        }

        // TIER 4: Phonetic Skeletons
        for target in rawTargets {
            let targetNoSpace = target.replacingOccurrences(of: " ", with: "")
            let targetPhonetic = phoneticCode(word: targetNoSpace, language: language)
            if targetPhonetic.count >= 2 {
                for cand in parsedCandidates {
                    if cand.noSpace.contains(where: { phoneticCode(word: $0, language: language) == targetPhonetic }) ||
                       cand.words.contains(where: { phoneticCode(word: $0, language: language) == targetPhonetic }) {
                        return matchedDisplay
                    }
                }
            }
        }

        // TIER 5: Levenshtein near-phonetic tolerance
        if isFinal {
            for target in rawTargets {
                let targetNoSpace = target.replacingOccurrences(of: " ", with: "")
                let targetPhonetic = phoneticCode(word: targetNoSpace, language: language)
                if targetPhonetic.count >= 4 {
                    for cand in parsedCandidates {
                        for phrase in cand.noSpace {
                            let candPhonetic = phoneticCode(word: phrase, language: language)
                            if candPhonetic.count >= 3 &&
                               abs(candPhonetic.count - targetPhonetic.count) <= 1 &&
                               levenshteinDistance(candPhonetic, targetPhonetic) <= 1 {
                                return matchedDisplay
                            }
                        }
                    }
                }
            }
        }

        return nil
    }

    public func extractNormalizedTargets(
        targetWord: VocabWord,
        expectCzech: Bool = false,
        language: LearningLanguage = .english
    ) -> [String] {
        let stripArticles = (language != .czech)
        if expectCzech {
            let cleaned = targetWord.czech.replacingOccurrences(of: "\\s*\\(.*?\\)", with: " ", options: .regularExpression)
            let parts = cleaned.split(separator: "/").map { $0.trimmingCharacters(in: .whitespaces) }
            return ([cleaned] + parts).map { normalize($0, stripLeadingForeignArticles: stripArticles) }.filter { !$0.isEmpty }
        } else {
            let cleanPrimary = targetWord.foreign.replacingOccurrences(of: "\\s*\\(.*?\\)", with: " ", options: .regularExpression)
            let primaryParts = cleanPrimary.split(separator: "/").map { $0.trimmingCharacters(in: .whitespaces) }
            let variantParts = targetWord.acceptedVariants.flatMap {
                $0.replacingOccurrences(of: "\\s*\\(.*?\\)", with: " ", options: .regularExpression)
                    .split(separator: "/")
                    .map { $0.trimmingCharacters(in: .whitespaces) }
            }
            return ([cleanPrimary] + primaryParts + variantParts).map { normalize($0, stripLeadingForeignArticles: stripArticles) }.filter { !$0.isEmpty }
        }
    }
}
