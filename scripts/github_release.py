import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.4"
NAME = "LingoStacks v1.0.4"

BODY = """[CS]
• **Dynamic Island kapsle:** Denní cíl, streak a zbývající slova jsou sjednoceny v jedné elegantní Bento pilulce, která šetří místo a zpřehledňuje úvodní obrazovku.
• **Trvalý progres v lekci:** V záhlaví studijní karty se nyní zobrazuje skutečný celkový stav zvládnutí balíčku (např. 10 / 80), nikoliv vynulované počítadlo relace.
• **Organický světelný lem (Breathing Glow):** Studijní karta má při aktivním poslechu mikrofonu jemný pulzující modrý lem s ambientním stínem.
• **Minimalistická ovládací pilulka:** Tři ikony v rohu karty (zobrazení slova, výslovnost, historie) jsou sjednoceny do čisté kapsle s tenkými vektorovými piktogramy.
• **Opravená česká vlajka a synchronizace:** Opravena geometrie modrého trojúhelníkového klínu a nasazena podpora češtiny v Cloud Firestore pravidlech.

[EN]
• **Dynamic Island Capsule:** Daily goal, streak, and word counters unified into a compact, space-saving Bento pill widget.
• **Persistent Deck Progress:** Lesson header accurately reflects permanent deck mastery progress (e.g. 10 / 80) across sessions.
• **Organic Breathing Glow:** Active voice listening wraps the flashcard in a soft, ambient pulsing blue light.
• **Minimalist Toolbar Capsule:** Balanced, outline vector icons unified in a discreet corner capsule.
• **Czech Flag & Cloud Fixes:** True triangular wedge geometry for Czech flag waving animation and Firestore security rules update.
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
        ("LingoStacks_v1.0.4.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.4.apk"),
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
