# 🗣️ LingoStacks — Chytrá hlasová výuka slovíček pro Android

Moderní Android aplikace v designu **Cupertino Glass** pro aktivní procvičování **5 světových jazyků** (angličtina, španělština, němčina, francouzština a italština) hlasem, gesty a chytrou metodou opakování (*Spaced Repetition*).

## 📸 Ukázky z aplikace

<p align="center">
  <img src="docs/screenshots/01_home.png" alt="Domovská obrazovka" width="180" />
  <img src="docs/screenshots/02_flashcard.png" alt="Hlasové procvičování a paměťový kontext" width="180" />
  <img src="docs/screenshots/03_flashcard_hint.png" alt="Odhalení překladu a výslovnosti" width="180" />
  <img src="docs/screenshots/04_settings.png" alt="Nastavení" width="180" />
  <img src="docs/screenshots/05_stats.png" alt="Statistiky a denní žebříček" width="180" />
</p>

## ✨ Hlavní funkce

- **🔄 Obousměrné učení (`CZ ↔ EN/ES/DE/FR/IT` i `Mix`):**
  - Rychlé přepínání **CZ → cizí jazyk (aktivní výbavnost)**, **cizí jazyk → CZ (pasivní porozumění)** nebo **Mix obou směrů** přímo na symetrické horní liště (**Jazyk • Směr • Úroveň**) pod prosvítající vlající vlajkou vybraného jazyka i v Nastavení.
  - V otočeném směru aplikace zobrazí cizí slovo s výslovností, automaticky ho přečte (`AUTO`), rozpoznává **český překlad přes `cs-CZ` rozpoznávání řeči** (včetně synonym a drobných odchylek ve skloňování) a ukládá pro pasivní směr **oddělené SRS intervaly**, aby neovlivnila aktivní znalost.
- **🎙️ Nepřetržité rozpoznávání hlasu (`SpeechRecognizer`):**
  - Vyslov překlad nahlas – aplikace průběžně poslouchá v angličtině, španělštině, němčině, francouzštině, italštině i češtině (`cs-CZ`), využívá jazykově specifickou fonetickou toleranci ve `WordMatcher`, ihned vyhodnotí odpověď, přehraje rodilou výslovnost (`TextToSpeech`) a plynule přejde na další kartu.
  - Vizuální hlasový ekvalizér v reálném čase a automatické skrývání tlačítek **Vím / Nevím** s *bounce* animací při vypnutí mikrofonu.
- **🃏 Plynulé Swipe kartičky & chytrý kontext:**
  - Gesto **doprava (Vím)** / **doleva (Nevím)** s okamžitým plynulým odsunem karty a paralelní výslovností.
  - Rozbalovací nápověda s fonetickým přepisem, nápovědnou větou s jedním klepnutím k přehrání a historií posledních pokusů.
- **📚 8 270+ slovíček a frází v 95 tematických okruzích (A1–C2):**
  - 🇬🇧 **Angličtina (1 430 položek / 19 okruhů):** slovní zásoba A1–C2, základní i pokročilá **nepravidelná slovesa** (trojice tvarů) a nejpoužívanější cestovatelské i konverzační **fráze**.
  - 🇪🇸 **Španělština (1 710 položek / 19 okruhů):** slovní zásoba A1–C2, **nepravidelná slovesa** (přítomný i minulý čas *indefinido*) a každodenní i konverzační **fráze**.
  - 🇩🇪 **Němčina (1 710 položek / 19 okruhů):** slovní zásoba A1–C2 se členy, **nepravidelná slovesa** (přítomný i minulý čas / *Partizip II*) a cestovní i konverzační **fráze**.
  - 🇫🇷 **Francouzština (1 710 položek / 19 okruhů):** slovní zásoba A1–C2 se členy, **nepravidelná slovesa** (*présent* i *passé composé*) a cestovní i konverzační **fráze**.
  - 🇮🇹 **Italština (1 710 položek / 19 okruhů):** slovní zásoba A1–C2 se členy, **nepravidelná slovesa** (*presente* i *passato prossimo*) a cestovní i konverzační **fráze**.
- **🧠 Chytré opakování (Leitner + SM-2):**
  - Neznámá slovíčka a slovíčka s nápovědou se automaticky vrací v optimálních intervalech zvlášť pro aktivní i pasivní směr učení.
- **📱 Optimalizace pro skládací telefony (Galaxy Z Fold):**
  - Dvousloupcové rozvržení na šířku (*landscape*) pro pohodlné ovládání na velkém displeji.
- **🏆 Denní cíl, streak a online žebříček:**
  - Automatické půlnoční nulování denního cíle, sledování řady dnů (*streak*), celkový progres slovní zásoby a kompaktní denní žebříček s *bounce* detailem hráčů.
- **🚀 Jednotné GitHub OTA aktualizace (`release`):**
  - Automatická kontrola nových verzí z **GitHub Releases**, stažení optimalizovaného `release` APK jedním kliknutím přímo v aplikaci (včetně bilingvního changelogu a nápovědy k Play Protect) a zachování veškerého postupu díky stálému podpisu.

---

## ⬇️ Stažení a instalace (Android 8.0+)

Nejnovější instalační balíček **`LingoStacks.apk`** najdete vždy v sekci [**Releases**](https://github.com/radekvlk/LingoStacks/releases/latest).
Další aktualizace probíhá OTA přímo v aplikaci.

> [!NOTE]
> **Varování Google Play Protect při instalaci:** Při instalaci aplikace mimo Obchod Play může Android zobrazit varování *(„Nebezpečná aplikace zablokována“ / „Harmful app blocked“)*. **Není potřeba se toho obávat** — hláška se objevuje automaticky jen proto, že aplikace používá mikrofon pro hlasové učení a přímé OTA aktualizace z GitHubu a není distribuována přes Obchod Play. Veškerý zdrojový kód aplikace je veřejně dostupný zde v repozitáři. Pro pokračování stačí v okně klepnout na **Více informací → Přesto nainstalovat** (*More details → Install anyway*).

---

## 🛠️ Technické informace

- **Jazyk & UI:** Kotlin, Jetpack Compose, Material 3
- **Minimální Android:** Android 8.0 (API 26) • **Target SDK:** Android 15 (API 35)
- **Oprávnění:** `RECORD_AUDIO` (hlasové procvičování), `INTERNET` (synchronizace žebříčku a OTA), `REQUEST_INSTALL_PACKAGES` (přímá instalace OTA aktualizace)

---

## 📄 Autorská práva (*All Rights Reserved*)

**© 2026 Radek Vlk — Všechna práva vyhrazena (*All Rights Reserved*).**

Zdrojový kód je zveřejněn výhradně pro nahlédnutí a ověření bezpečnosti aplikace (*source-available*). Bez předchozího písemného souhlasu autora není dovoleno kód ani jeho části kopírovat, upravovat, dále šířit ani vydávat jako vlastní nebo odvozenou aplikaci.
