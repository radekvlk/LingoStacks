# Podklady pro Google Play Console — LingoStacks

Tento dokument obsahuje veškeré texty, grafické podklady a odpovědi na povinné formuláře pro publikaci **LingoStacks** do Google Play Console.

---

## 1. Základní informace o aplikaci (Main Store Listing)

### Jazyk: Čeština (Výchozí nebo pro CZ/SK)

* **Název aplikace (max 30 znaků):**
  ```text
  LingoStacks: Hlasové kartičky
  ```
  *(případně alternativně: `LingoStacks: Slovíčka hlasem`)*

* **Krátký popis (max 80 znaků):**
  ```text
  Procvičujte slovíčka a fráze hlasem. Chytré kartičky s okamžitým vyhodnocením.
  ```

* **Úplný popis (max 4000 znaků):**
  ```text
  Učte se cizí jazyky mluvením, ne pouhým klikáním! 

  LingoStacks je moderní hlasová aplikace pro procvičování slovní zásoby a frází v angličtině, španělštině, němčině, francouzštině a italštině. Místo pasivního koukání na obrazovku vyslovujete překlady nahlas — aplikace váš hlas v reálném čase rozpozná, zkontroluje výslovnost a posune vás dál.

  HLAVNÍ VLASTNOSTI:
  🎙️ Mluvení v reálném čase — pokročilé rozpoznávání hlasu s tolerancí pro začátečníky.
  🧠 Chytré intervalové opakování (SRS) — těžší slova se vracejí častěji, snadná méně.
  🔊 Rodilá výslovnost — po každé odpovědi uslyšíte autentický přízvuk.
  📚 8 200+ připravených slovíček a frází (od úrovně A1 až po C2) v 5 jazycích.
  ✍️ Vlastní balíčky — snadno si přidejte vlastní slovíčka ze školy či z práce.
  ☁️ Cloudová záloha — synchronizujte svůj pokrok přes Google účet.
  🏆 Žebříček — motivujte se v denním a týdenním srovnání s ostatními.
  🚫 100% zdarma a bez reklam — žádné otravné bannery ani předplatná.

  Dostupné jazyky:
  • Angličtina (A1 – C2 + Fráze)
  • Španělština (A1 – C2 + Fráze)
  • Němčina (A1 – B2)
  • Francouzština (A1 – B2)
  • Italština (A1 – B2)

  Rozmluvte se ještě dnes s LingoStacks!
  ```

---

### Jazyk: Angličtina (English - US / Global)

* **App name (max 30 chars):**
  ```text
  LingoStacks: Voice Flashcards
  ```

* **Short description (max 80 chars):**
  ```text
  Master vocabulary by speaking out loud. Smart voice flashcards & spaced recall.
  ```

* **Full description (max 4000 chars):**
  ```text
  Learn languages by speaking, not just tapping!

  LingoStacks is a voice-first flashcard app designed to build active speaking fluency in English, Spanish, German, French, and Italian. Instead of passive multiple-choice quizzes, you pronounce translations out loud — the app listens in real time, evaluates your pronunciation, and advances automatically.

  KEY FEATURES:
  🎙️ Real-Time Speech Recognition — practice speaking with instant voice feedback.
  🧠 Smart Spaced Repetition (SRS) — optimizes review intervals for permanent retention.
  🔊 Native Pronunciation — hear authentic speech playback for every single word.
  📚 8,200+ curated words and phrases spanning CEFR levels A1 through C2.
  ✍️ Custom Decks — create and practice your own vocabulary lists anytime.
  ☁️ Cloud Backup — seamlessly sync your study streak and progress with Google.
  🏆 Leaderboard — stay motivated with daily and weekly community milestones.
  🚫 100% Free & No Ads — clean, distraction-free learning experience.

  Supported Languages:
  • English (A1 – C2 + Essential Idioms & Phrases)
  • Spanish (A1 – C2 + Everyday Phrases)
  • German (A1 – B2)
  • French (A1 – B2)
  • Italian (A1 – B2)

  Start speaking with confidence today with LingoStacks!
  ```

---

## 2. Grafické podklady (Graphics Assets)

Všechny soubory jsou vygenerované a připravené ve složce `docs/`:

1. **Ikona aplikace pro obchod (App Icon):**
   * Soubor: `docs/app_icon_512.png`
   * Rozměry: `512 × 512 px` (32-bit PNG)

2. **Hlavní grafika (Feature Graphic):**
   * Soubor: `docs/feature_graphic.png`
   * Rozměry: `1024 × 500 px` (24-bit RGB PNG bez průhlednosti)

3. **Screenshoty pro telefony (Screenshots):**
   * `docs/screenshots/01_home.png` (Domovská obrazovka a výběr jazyků)
   * `docs/screenshots/02_flashcard.png` (Hlasové kartičky v akci)
   * `docs/screenshots/03_flashcard_hint.png` (Nápověda a fonetická výslovnost)
   * `docs/screenshots/04_settings.png` (Nastavení a možnosti)
   * `docs/screenshots/05_stats.png` (Statistiky a pokrok)

---

## 3. Zásady ochrany osobních údajů (Privacy Policy URL)

* **Oficiální odkaz do konzole:**
  `https://radekvlk.github.io/LingoStacks/privacy.html`

---

## 4. Dotazník: Zabezpečení údajů (Data Safety Form)

V sekci *App Content -> Data Safety* v Google Play Console klikněte na Start a vyplňte:

1. **Shromažďuje nebo sdílí vaše aplikace nějaké uživatelské údaje?**
   * Zvolte: **Ano (Yes)** *(kvůli volitelnému Google přihlášení)*.
2. **Jsou všechny údaje šifrovány při přenosu?**
   * Zvolte: **Ano (Yes)** *(Firebase používá výhradně šifrované HTTPS/TLS spojení)*.
3. **Umožňuje aplikace uživateli požádat o smazání údajů?**
   * Zvolte: **Ano (Yes)**.
   * Do pole URL pro smazání dat zadejte: `https://radekvlk.github.io/LingoStacks/privacy.html`.

### Konkrétní kategorie dat:
* **Audio (Zvukové nahrávky / Mikrofon):**
  * Zvolte: **Ne (No)** — Zvuk se v aplikaci neukládá ani neposílá na servery (zpracovává se lokálně na zařízení přes Android SpeechRecognizer).
* **Osobní údaje (Personal info) -> E-mailová adresa a Jméno:**
  * Shromažďuje se: **Ano** (pouze při dobrovolném přihlášení přes Google).
  * Sdílí se s třetími stranami: **Ne**.
  * Účel: **Správa účtu (Account management)**.
  * Je shromažďování volitelné: **Ano (Optional)** — uživatel může aplikaci používat anonymně bez přihlášení.
* **Aktivita v aplikaci (App activity) -> Interakce s aplikací:**
  * Ukládá studijní pokrok (kartičky a skóre) pro synchronizaci a žebříček.
  * Účel: **Funkčnost aplikace (App functionality)**.

---

## 5. Ostatní deklarace v Google Play Console

* **Cílová věková skupina (Target Age):** Zvolte **13 a více let (13+)**. *(Vyhnete se tím přísným pravidlům programu Pro rodiny / Families Policy).*
* **Reklamy (Ads):** Zvolte **Ne, aplikace neobsahuje reklamy**.
* **Přístup k aplikaci (App Access):** Zvolte **Všechny funkce jsou dostupné bez omezení** (nebo uveďte, že přihlášení je dobrovolné a nevyžaduje placený účet).
* **Kategorie obchodu (Category):** **Vzdělávání (Education)**.
* **Kontaktní e-mail:** Váš kontaktní e-mail (např. `radek.vlk.app@gmail.com`).

## 6. Soubor balíčku pro nahrání do Google Play

* **Název souboru:** `LingoStacks_v1.6.46_playstore.aab`
* **Umístění:** Kořenový adresář projektu nebo `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab`
* **Vlastnosti:** 
  * 100% v souladu s Google Play Developer Policy.
  * Zcela bez externích platebních odkazů (žádné Ko-fi ani PayPal).
  * Zcela bez zakázaného oprávnění `REQUEST_INSTALL_PACKAGES` (aktualizace řídí výhradně Google Play).
  * Podepsáno produkčním klíčem `lingowords.keystore`.
