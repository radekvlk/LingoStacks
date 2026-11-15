import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.3"
NAME = "LingoStacks v1.0.3"

BODY = """[CS]
• **Nový moderní vektorový design:** Všechny ikony napříč aplikací (zlatý pohár v záhlaví, balíčky A1–C2 včetně vědy, akademie a práva i ovládací prvky v Nastavení) byly nahrazeny čistými vektorovými ikonami místo emoji.
• **Vyvážená flashkarta a plynulá nápověda:** Kartička se slovem má nyní vertikálně symetrické rozvržení s nezávislým středovým ukotvením; po kliknutí na nápovědu se rámeček rozbalí bez jakéhokoliv posunutí či odskoku slova.
• **Přirozené řízení spotřeby:** Zrušeno manuální uspávání mikrofonu; aplikace plně respektuje systémové zhasínání displeje (pohyb karty funguje jako dotyk).
• **Striktní soulad s pravidly Google Play:** V Google Play variantě je kompletně zablokováno externí stahování APK.

[EN]
• **Modern Vector UI:** Replaced all emoji icons across decks, headers, and settings with crisp monochrome vector icons.
• **Balanced Flashcard Layout:** Card content is vertically centered; hint reveals smoothly downwards without jumping the word above.
• **Native Screen Sleep:** Hands-free learning respects Android display timeout (card advance acts like a screen touch).
• **Google Play Compliance:** External OTA APK self-updating strictly disabled in Play Store builds.
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
        ("LingoStacks_v1.0.3.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.3.apk"),
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
