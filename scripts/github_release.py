import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.5"
NAME = "LingoStacks v1.0.5"

BODY = """[CS]
• **Nový plovoucí popover filtrů:** Rozbalovací menu pro výběr jazyka, směru učení a úrovně CEFR jsou nyní plovoucí zaoblené karty s měkkými rohy (18 dp), vzdušným 8dp odstupem od lišty a přesným vycentrováním na střed zvolené pilulky.
• **Zvýraznění aktivní volby:** Vybraná položka je zřetelně odlišena jemnou světle modrou kapslí s modrým textem a modrou fajfkou.
• **Zarovnání na středovou osu:** Středová pilulka filtrů a pilulka denního cíle na domovské obrazovce jsou nově zarovnány na společnou vertikální osu.

[EN]
• **Modern Floating Filter Popovers:** Filter dropdown menus (language, study direction, CEFR level) are now floating cards with 18dp rounded corners, an 8dp breathing gap below the pill, and precise center alignment.
• **Active Item Highlight:** Current active selection is clearly highlighted with a soft blue capsule, blue text, and blue checkmark.
• **Center Alignment:** The middle filter pill and daily goal progress capsule are perfectly aligned on a shared vertical center axis.
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
        ("LingoStacks_v1.0.5.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.5.apk"),
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
