# 🍏 LingoStacks pro iOS (TestFlight Guide)

Tento adresář obsahuje kompletní nativní iOS verzi aplikace **LingoStacks** (SwiftUI, iOS 16.0+) plně synchronizovanou s Android verzí `v1.0.15` (build `121`).

---

## ⚡ Rychlé spuštění na Macu (1 minuta)

Otevřete **Terminál** na svém Macu a spusťte:

```bash
# 1. Přejděte do repozitáře a stáhněte nejnovější stav
git pull

# 2. Přejděte do složky iOS aplikace
cd ios/LingoStacks

# 3. Spusťte automatický generátor projektu
chmod +x setup_mac.sh
./setup_mac.sh
```

Skript automaticky:
1. Nainstaluje `xcodegen` (pokud ještě není nainstalován).
2. Vygeneruje čistý soubor **`LingoStacks.xcodeproj`** bez konfliktů.
3. Otevře projekt přímo v **Xcode**.

---

## 📱 Spuštění a testování na iPhonu

1. V Xcode nahoře v liště vyberte připojený **fyzický iPhone** (případně iOS Simulator).
2. V levém sloupci klikněte na modrou ikonu projektu **LingoStacks** -> záložka **Signing & Capabilities**.
3. Zaškrtněte **Automatically manage signing** a v kolonce **Team** vyberte svůj **Apple Developer účet**.
4. Stiskněte **Cmd + R** (nebo ikonu ▶️ Play).
5. Aplikace se nainstaluje na váš iPhone:
   * Při prvním spuštění povolte **Mikrofon** a **Rozpoznávání řeči**.
   * Vyzkoušejte vyslovit slovíčko – mikrofon snímá hlasitost (ekvalizér) a po správné odpovědi ihned přehraje rodilou výslovnost a posune kartu.

---

## 🚀 Nahrání do TestFlightu

Až budete spokojeni:
1. V Xcode v liště cílů zvolte **Any iOS Device (arm64)**.
2. V horním menu klikněte na: **Product → Archive**.
3. Po dokončení archivu se otevře okno *Organizer*.
4. Klikněte na tlačítko **Distribute App** (vpravo nahoře).
5. Zvolte **TestFlight & App Store** → **Upload** → potvrďte automatické podepsání.
6. Během několika minut je nová verze v **App Store Connect / TestFlight** a nainstaluje se testerům do telefonů!
