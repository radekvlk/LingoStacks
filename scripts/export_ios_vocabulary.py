import os
import re
import json

def parse_string_literals(call_str):
    args = []
    in_str = False
    escape = False
    cur = []
    for ch in call_str:
        if escape:
            cur.append(ch)
            escape = False
        elif ch == '\\':
            cur.append(ch)
            escape = True
        elif ch == '"':
            in_str = not in_str
            cur.append(ch)
        elif ch == ',' and not in_str:
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if cur:
        args.append("".join(cur).strip())
        
    cleaned_args = []
    for a in args:
        a = a.strip()
        if a.startswith('"') and a.endswith('"'):
            val = a[1:-1].replace('\\"', '"').replace('\\\\', '\\')
            cleaned_args.append(val)
        else:
            cleaned_args.append(a)
    return cleaned_args

def parse_w_call(call_str, default_emoji="📘", default_sentence=""):
    args = parse_string_literals(call_str)
    if len(args) < 3:
        return None
    
    word_id = args[0]
    czech = args[1]
    foreign = args[2]
    
    emoji = args[3] if len(args) > 3 and args[3] else default_emoji
    ex = args[4] if len(args) > 4 and args[4] else default_sentence
    ph = args[5] if len(args) > 5 and args[5] else ""
    vars_list = [v for v in args[6:] if v]
    
    return {
        "id": word_id,
        "czech": czech,
        "foreign": foreign,
        "acceptedVariants": vars_list,
        "phonetic": ph,
        "emoji": emoji,
        "exampleSentence": ex
    }

def extract_decks_from_file(filepath, default_emoji="📘"):
    if not os.path.exists(filepath):
        print(f"Warning: file {filepath} not found")
        return []
        
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    deck_chunks = re.split(r'VocabDeck\s*\(', content)[1:]
    decks = []

    for chunk in deck_chunks:
        id_m = re.search(r'id\s*=\s*"([^"]+)"', chunk)
        title_m = re.search(r'title\s*=\s*"([^"]+)"', chunk)
        sub_m = re.search(r'subtitle\s*=\s*"([^"]+)"', chunk)
        title_en_m = re.search(r'titleEn\s*=\s*"([^"]+)"', chunk)
        sub_en_m = re.search(r'subtitleEn\s*=\s*"([^"]+)"', chunk)
        emoji_m = re.search(r'emoji\s*=\s*"([^"]+)"', chunk)
        level_m = re.search(r'level\s*=\s*CefrLevel\.([A-Z0-9]+)', chunk)

        if not (id_m and title_m and level_m):
            continue

        deck_id = id_m.group(1)
        title = title_m.group(1)
        subtitle = sub_m.group(1) if sub_m else ""
        title_en = title_en_m.group(1) if title_en_m else title
        subtitle_en = sub_en_m.group(1) if sub_en_m else subtitle
        emoji = emoji_m.group(1) if emoji_m else default_emoji
        level = level_m.group(1)

        words = []
        w_matches = re.finditer(r'\bw\s*\(', chunk)
        for wm in w_matches:
            start_pos = wm.end()
            depth = 1
            idx = start_pos
            in_s = False
            esc = False
            while idx < len(chunk) and depth > 0:
                c = chunk[idx]
                if esc:
                    esc = False
                elif c == '\\':
                    esc = True
                elif c == '"':
                    in_s = not in_s
                elif not in_s:
                    if c == '(':
                        depth += 1
                    elif c == ')':
                        depth -= 1
                idx += 1
            
            if depth == 0:
                w_inner = chunk[start_pos:idx-1]
                word_obj = parse_w_call(w_inner, default_emoji=emoji)
                if word_obj:
                    words.append(word_obj)

        deck = {
            "id": deck_id,
            "title": title,
            "subtitle": subtitle,
            "titleEn": title_en,
            "subtitleEn": subtitle_en,
            "emoji": emoji,
            "level": level,
            "words": words
        }
        decks.append(deck)

    return decks

def main():
    base_model_dir = "app/src/main/java/cz/slovickaspeak/app/model"
    output_dir = "ios/LingoStacks/LingoStacks/Resources"
    os.makedirs(output_dir, exist_ok=True)

    languages = [
        {
            "code": "en",
            "speechLocaleTag": "en-US",
            "flagEmoji": "🇬🇧",
            "titleCs": "Angličtina",
            "titleEn": "English",
            "files": [f"VocabularyBankPart{i}.kt" for i in range(1, 5)]
        },
        {
            "code": "es",
            "speechLocaleTag": "es-ES",
            "flagEmoji": "🇪🇸",
            "titleCs": "Španělština",
            "titleEn": "Spanish",
            "files": [f"SpanishVocabularyPart{i}.kt" for i in range(1, 5)]
        },
        {
            "code": "de",
            "speechLocaleTag": "de-DE",
            "flagEmoji": "🇩🇪",
            "titleCs": "Němčina",
            "titleEn": "German",
            "files": [f"GermanVocabularyPart{i}.kt" for i in range(1, 5)]
        },
        {
            "code": "fr",
            "speechLocaleTag": "fr-FR",
            "flagEmoji": "🇫🇷",
            "titleCs": "Francouzština",
            "titleEn": "French",
            "files": [f"FrenchVocabularyPart{i}.kt" for i in range(1, 5)]
        },
        {
            "code": "it",
            "speechLocaleTag": "it-IT",
            "flagEmoji": "🇮🇹",
            "titleCs": "Italština",
            "titleEn": "Italian",
            "files": [f"ItalianVocabularyPart{i}.kt" for i in range(1, 5)]
        },
        {
            "code": "pt",
            "speechLocaleTag": "pt-PT",
            "flagEmoji": "🇵🇹",
            "titleCs": "Portugalština",
            "titleEn": "Portuguese",
            "files": [f"PortugueseVocabularyPart{i}.kt" for i in range(1, 5)]
        }
    ]

    catalog_summary = []
    total_all_words = 0
    all_language_decks = {}

    for lang in languages:
        lang_decks = []
        for fn in lang["files"]:
            fpath = os.path.join(base_model_dir, fn)
            decks = extract_decks_from_file(fpath)
            lang_decks.extend(decks)
            
        word_count = sum(len(d["words"]) for d in lang_decks)
        total_all_words += word_count
        all_language_decks[lang["code"]] = lang_decks

        out_file = os.path.join(output_dir, f"vocabulary_{lang['code']}.json")
        with open(out_file, "w", encoding="utf-8") as f:
            json.dump(lang_decks, f, ensure_ascii=False, indent=2)

        print(f"[{lang['flagEmoji']} {lang['titleCs']}] {len(lang_decks)} sad, {word_count} slovíček -> {out_file}")

        catalog_summary.append({
            "code": lang["code"],
            "speechLocaleTag": lang["speechLocaleTag"],
            "flagEmoji": lang["flagEmoji"],
            "titleCs": lang["titleCs"],
            "titleEn": lang["titleEn"],
            "deckCount": len(lang_decks),
            "wordCount": word_count
        })

    # Generate Czech decks (derived from English)
    en_decks = all_language_decks.get("en", [])
    cs_decks = []
    for d in en_decks:
        cs_words = []
        for w in d["words"]:
            cs_words.append({
                "id": "cz_" + w["id"],
                "czech": w["foreign"],
                "foreign": w["czech"],
                "acceptedVariants": [],
                "phonetic": "",
                "emoji": w["emoji"],
                "exampleSentence": w["exampleSentence"]
            })
        cs_decks.append({
            "id": "cz_" + d["id"],
            "title": d["titleEn"] if d["titleEn"] else d["title"],
            "subtitle": d["subtitleEn"] if d["subtitleEn"] else "Learn Czech",
            "titleEn": d["titleEn"] if d["titleEn"] else d["title"],
            "subtitleEn": d["subtitleEn"] if d["subtitleEn"] else "Practice speaking Czech out loud",
            "emoji": d["emoji"],
            "level": d["level"],
            "words": cs_words
        })
    cs_word_count = sum(len(d["words"]) for d in cs_decks)
    total_all_words += cs_word_count
    cs_out = os.path.join(output_dir, "vocabulary_cs.json")
    with open(cs_out, "w", encoding="utf-8") as f:
        json.dump(cs_decks, f, ensure_ascii=False, indent=2)

    catalog_summary.append({
        "code": "cs",
        "speechLocaleTag": "cs-CZ",
        "flagEmoji": "🇨🇿",
        "titleCs": "Čeština",
        "titleEn": "Czech",
        "deckCount": len(cs_decks),
        "wordCount": cs_word_count
    })
    print(f"[🇨🇿 Čeština] {len(cs_decks)} sad, {cs_word_count} slovíček -> {cs_out}")

    # Write languages_catalog.json
    cat_file = os.path.join(output_dir, "languages_catalog.json")
    with open(cat_file, "w", encoding="utf-8") as f:
        json.dump(catalog_summary, f, ensure_ascii=False, indent=2)

    print(f"\n Celkem vyexportováno: {total_all_words} slovíček v {len(catalog_summary)} jazycích.")
    print(f"Katalog uložen do: {cat_file}")

if __name__ == "__main__":
    main()
