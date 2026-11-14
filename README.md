<p align="right">
  🇬🇧 <strong>English</strong> | <a href="README.cs.md">🇨🇿 Česky</a>
</p>

# 🗣️ LingoStacks — Voice-First Multilingual Flashcards for Android

A modern Android app in **Cupertino Glass** design for actively mastering **6 world languages** (English, Spanish, German, French, Italian, and Portuguese) from **any native language** using your voice, swipe gestures, and smart *Spaced Repetition*.

<table>
  <tr>
    <td>
      <h3>⬇️ Download & Try (Android 8.0–16 / API 26–36)</h3>
      <p>
        <a href="https://github.com/radekvlk/LingoStacks/releases/latest/download/LingoStacks.apk">
          <img src="https://img.shields.io/badge/Download_LingoStacks-.apk-0A84FF?style=for-the-badge&logo=android&logoColor=white" alt="Download LingoStacks APK" />
        </a>
        <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22cz.slovickaspeak.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fradekvlk%2FLingoStacks%22%2C%22author%22%3A%22radekvlk%22%2C%22name%22%3A%22LingoStacks%22%7D">
          <img src="https://img.shields.io/badge/Get_it_on-Obtainium-6C47FF?style=for-the-badge&logo=android&logoColor=white" alt="Get it on Obtainium" />
        </a>
        <a href="https://ko-fi.com/radekvlk">
          <img src="https://img.shields.io/badge/Support_on-Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Support on Ko-fi" />
        </a>
      </p>
      <p>
        🌐 <strong>Project Website:</strong> <a href="https://radekvlk.github.io/LingoStacks/">radekvlk.github.io/LingoStacks</a><br/>
        📦 <strong>All Releases:</strong> <a href="https://github.com/radekvlk/LingoStacks/releases/latest">GitHub Releases</a><br/>
        📱 <strong>Google Play:</strong> In Closed Testing (v1.0.0, API 36)
      </p>
      <p><sub>Scan the QR code with your phone camera to download the latest APK directly.</sub></p>
    </td>
    <td align="center" width="160">
      <img src="docs/qr_download.png" alt="QR code to download LingoStacks APK" width="136" />
    </td>
  </tr>
</table>

## 📸 App Preview & Screenshots

<p align="center">
  <img src="docs/demo.gif" alt="LingoStacks Animated Walkthrough" width="220" />
</p>

<p align="center">
  <img src="docs/screenshots/01_home.png" alt="Home Screen" width="180" />
  <img src="docs/screenshots/02_flashcard.png" alt="Voice Practice & Memory Context" width="180" />
  <img src="docs/screenshots/03_flashcard_hint.png" alt="Translation Reveal & Pronunciation" width="180" />
  <img src="docs/screenshots/04_settings.png" alt="Settings & Native Language Selection" width="180" />
  <img src="docs/screenshots/05_stats.png" alt="Statistics & Daily Leaderboard" width="180" />
</p>

## ✨ Key Features

- **🌍 Truly Global & Multilingual (Cross-Language Native Bridge):**
  - Not limited to a single native language! Choose your **Native Base Language** from 7 options: **English, Spanish, German, French, Italian, Portuguese, or Czech**.
  - Study any of the **6 target languages** with over **30 language combinations**. All prompts, card hints, example sentences, speech recognition listeners, and pronunciation engines dynamically adapt to your selected native-target pair.
- **🔄 Bidirectional Learning (`Native ↔ Target` & `Mix`):**
  - Quickly switch between **Native → Target (active recall)**, **Target → Native (passive comprehension & listening)**, or **Dynamic Mix** directly from the symmetrical top bar (**Language • Direction • Level**) beneath a waving flag of the target language, or in Settings.
  - In passive mode (Target → Native), the app automatically reads the target word out loud (`AUTO`), listens for your spoken translation in your native language via localized speech recognition, and tracks **independent SRS intervals** so passive practice never distorts your active mastery score.
- **🎙️ Continuous Voice-First Practice (`SpeechRecognizer`):**
  - Speak your answers naturally — the app listens continuously across all supported languages, applies language-tailored phonetic tolerance in `WordMatcher` (handling diacritics, elisions, articles, and common synonyms), instantly verifies your answer, triggers native audio (`TextToSpeech`), and smoothly auto-advances to the next card.
  - Real-time animated voice equalizer and automatic hiding/showing of **Know / Don't Know** swipe buttons with a smooth *bounce* animation when muting the microphone.
- **📚 9,980+ Words & Phrases Across 114 Thematic Decks (A1–C2):**
  - 🇬🇧 **English (1,430 items / 19 decks):** A1–C2 vocabulary, essential and advanced **irregular verbs** (all 3 forms), and conversational & travel **phrases**.
  - 🇪🇸 **Spanish (1,710 items / 19 decks):** A1–C2 vocabulary, **irregular verbs** (*presente* & *indefinido*), and everyday & travel **phrases**.
  - 🇩🇪 **German (1,710 items / 19 decks):** A1–C2 vocabulary with gender articles, **irregular verbs** (present & past / *Partizip II*), and conversational & travel **phrases**.
  - 🇫🇷 **French (1,710 items / 19 decks):** A1–C2 vocabulary with gender articles, **irregular verbs** (*présent* & *passé composé*), and everyday & travel **phrases**.
  - 🇮🇹 **Italian (1,710 items / 19 decks):** A1–C2 vocabulary with gender articles, **irregular verbs** (*presente* & *passato prossimo*), and conversational & travel **phrases**.
  - 🇵🇹 **Portuguese (1,710 items / 19 decks):** A1–C2 vocabulary with gender articles, **irregular verbs** (*presente* & *pretérito perfeito*), and everyday & travel **phrases**.
- **🃏 Fluid Swipe Cards & Context Hints:**
  - Swipe **right (Know)** / **left (Don't Know)** with immediate card dismissal and simultaneous native audio playback.
  - Expandable context drawer with phonetic transcription, tap-to-play example sentences, and recent trial history.
- **🧠 Smart Spaced Repetition (Leitner + SM-2):**
  - Challenging words and cards answered with hints automatically resurface at scientifically calculated optimal intervals, tracked separately for active speaking and passive listening directions.
- **☁️ Cloud Backup & Community Leaderboards:**
  - One-tap **Google Sign-In** with secure Cloud Firestore backup. Never lose your learning streaks or SRS levels when switching phones.
  - Daily and weekly global leaderboards with *bounce* profile cards to keep your motivation high.
- **📱 Responsive & Foldable Phone Optimized:**
  - Fully adaptive layouts, including dedicated two-column view for book-style foldables (e.g. Galaxy Z Fold, Pixel Fold) and tablets.
- **🚀 Dual Distribution & GitHub In-App OTA Updates:**
  - **Google Play Store:** Version 1.0.0 in Closed Testing.
  - **GitHub Releases:** Instant direct APK download with background OTA update checks, one-tap in-app installation, and cryptographically verified APK signatures.

---

## ⬇️ Download & Installation (Android 8.0+)

The latest official release **`LingoStacks.apk`** is always available on [**GitHub Releases**](https://github.com/radekvlk/LingoStacks/releases/latest).
Subsequent updates install seamlessly in-app via OTA.

> [!NOTE]
> **Google Play Protect Notice During Direct Sideloading:** When sideloading an APK outside Google Play, Android may show a standard verification prompt *(“Harmful app blocked” / „Nebezpečná aplikace zablokována“)*. **There is no risk** — this prompt triggers routinely because the app requests microphone permissions for voice practice and installs OTA updates directly from GitHub. The full source code is open and verifiable right here in this repository. To proceed, tap **More details → Install anyway**.

---

## 🛠️ Technical Overview

- **Language & Framework:** Kotlin 2.0+, Jetpack Compose, Material 3 (Cupertino Glass theme)
- **Minimum Android:** Android 8.0 (API 26) • **Target SDK:** Android 16 (API 36)
- **Architecture:** Clean Architecture + MVVM, StateFlow, Kotlin Coroutines, Room / SharedPreferences / Firebase Firestore
- **Audio & Speech:** Android `SpeechRecognizer` (continuous loop with auto-restart), Android native `TextToSpeech` (with instant pre-caching)
- **Permissions:** `RECORD_AUDIO` (voice recognition), `INTERNET` (cloud sync & leaderboards), `REQUEST_INSTALL_PACKAGES` (direct OTA updates)

---

## 📄 Copyright (*All Rights Reserved*)

**© 2026 Radek Vlk — All Rights Reserved.**

The source code is published strictly for personal inspection, security verification, and evaluation (*source-available*). Without explicit prior written permission from the author, copying, modifying, redistributing, or publishing this code or derivative works thereof as your own application is strictly prohibited.
