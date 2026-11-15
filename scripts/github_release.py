import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.7"
NAME = "LingoStacks v1.0.7"

BODY = """[CS]
• **Plovoucí skleněná lišta Apple (Floating Glass Bar):** Elegantní 4-položková plovoucí lišta ve tvaru zaoblené kapsle (Domů, Trénink, Statistiky, Nastavení) se skleněným odleskem hrany a plynulým mizení při studiu pro maximální plochu karet.
• **Chytrý rychlý trénink:** Okamžitý návrat k rozpracovanému balíčku i prioritnímu opakování slovíček podle paměťové křivky (SRS) přímo ze spodní lišty.
• **Konzistentní tmavý režim (Apple HIG):** Horní rozbalovací nabídky (jazyk, směr, úroveň) i dialogy mají tmavé grafitové sklo (#242428) místo bílého pozadí; tlačítko Hotovo má vyvážený kapslový podklad.
• **Moderní kulaté ikony:** Všechny ikony přepínačů, notifikací i statistik sjednoceny na elegantní kruhy s bílými glyfy po vzoru Duolinga a Apple Fitness.

[EN]
• **Apple Floating Glass Bar:** Elegant 4-tab floating stadium capsule (Home, Practice, Stats, Settings) with specular edge highlights and smooth study dismissal.
• **Smart Quick Practice:** Immediate return to in-progress decks and prioritized SRS reviews right from the bottom bar.
• **Consistent Dark Mode (Apple HIG):** Filter dropdown popovers (language, study direction, CEFR level) and dialogs feature native dark glass surfaces (#242428); balanced capsule contrast for Done button.
• **Modern Circular Badges:** Clean, friendly circular icon badges with white glyphs across settings and stats inspired by Duolingo and Apple Fitness.
"""

def main():
    with open(TOKEN_PATH, "r", encoding="utf-8") as f:
        token = f.read().strip()

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "LingoStacks-Release-Script",
        "Content-Type": "application/json"
    }

    # 1. Create release
    payload = json.dumps({
        "tag_name": TAG,
        "name": NAME,
        "body": BODY,
        "draft": False,
        "prerelease": False
    }).encode("utf-8")

    req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases", data=payload, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            rel = json.loads(resp.read().decode("utf-8"))
            print(f"Created release ID: {rel['id']} ({rel['html_url']})")
            upload_url_template = rel["upload_url"] # e.g. https://uploads.github.com/.../assets{?name,label}
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        print(f"HTTPError: {e.code} - {err_msg}")
        # If release for tag already exists, fetch it
        req_get = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", headers=headers)
        with urllib.request.urlopen(req_get) as resp:
            rel = json.loads(resp.read().decode("utf-8"))
            print(f"Found existing release ID: {rel['id']}")
            upload_url_template = rel["upload_url"]

    upload_base = upload_url_template.split("{")[0]

    # 2. Upload APK assets
    apk_paths = [
        ("LingoStacks_v1.0.7.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.7.apk"),
        ("LingoStacks.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks.apk")
    ]

    for asset_name, path in apk_paths:
        if not os.path.exists(path):
            print(f"File not found: {path}")
            continue
        print(f"Uploading {asset_name} ({os.path.getsize(path)} bytes)...")
        with open(path, "rb") as f:
            apk_bytes = f.read()

        upload_req = urllib.request.Request(
            f"{upload_base}?name={asset_name}",
            data=apk_bytes,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github.v3+json",
                "User-Agent": "LingoStacks-Release-Script",
                "Content-Type": "application/vnd.android.package-archive"
            }
        )
        try:
            with urllib.request.urlopen(upload_req) as resp:
                asset_resp = json.loads(resp.read().decode("utf-8"))
                print(f"Uploaded {asset_name} -> {asset_resp.get('browser_download_url')}")
        except urllib.error.HTTPError as e:
            print(f"Error uploading {asset_name}: {e.code} - {e.read().decode('utf-8')}")

if __name__ == "__main__":
    main()
