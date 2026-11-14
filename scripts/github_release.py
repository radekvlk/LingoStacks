import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.2"
NAME = "LingoStacks v1.0.2"

BODY = """[CS]
• **Bleskové vyhodnocení řeči:** Sjednocen model rozpoznávání a zkrácena detekce ticha na ~480 ms – vyhodnocení správnosti po vyslovení slova je nyní okamžité v obou směrech (CZ i EN).
• **Animace denního cíle:** Světelný efekt splněného cíle přejede po liště přesně jednou při každém návratu na hlavní obrazovku.
• **Optimalizace RAM a startu:** Katalog slovní zásoby se načítá na vyžádání (lazy loading), což výrazně snižuje využití RAM a zrychluje přepínání jazyků.
• **Komprese zálohy postupu:** Cloudová záloha nově využívá efektivní GZIP kompresi s úsporou přes 80 % přenesených dat při zálohování a obnově.
• **Úsporný režim mikrofonu (Smart Mic Sleep):** Při 3 minutách nečinnosti v lekci se mikrofon automaticky uspí pro úsporu baterie; probudíte jej jediným klepnutím na kartu, banner nebo mikrofon.

[EN]
• **Instant Speech Recognition:** Unified language model and optimized silence detection timeout – answer evaluation is now immediate in both study directions (EN and CS).
• **Daily Goal Animation:** Completion shine effect sweeps cleanly across the progress bar once upon returning to the main screen.
• **RAM & Startup Optimization:** Vocabulary catalog is now loaded on-demand, significantly reducing memory consumption and speeding up language switching.
• **Cloud Backup Compression:** Progress backups are now compressed using GZIP, saving over 80% bandwidth on backup and restore operations.
• **Battery-Saving Smart Mic Sleep:** Microphone automatically enters sleep mode after 3 minutes of inactivity in study sessions; wake it up with a single tap anywhere on screen.
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
        ("LingoStacks_v1.0.2.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.2.apk"),
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
