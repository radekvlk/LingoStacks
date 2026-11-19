import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.14"
NAME = "LingoStacks v1.0.14 (Build 120)"

BODY = """versionCode: 120

[CS]
• **Ukazatel pozice v žebříčku:** Přímo v záhlaví i na připnuté kartě v žebříčku vidíte své přesné umístění (#X z N hráčů), počet XP i dynamický odstup na hráče před vámi. Není potřeba nikam rolovat ani se hledat v dlouhém seznamu.
• **Oprava odkazu na offline balíčky řeči:** Tlačítko v nastavení nyní otevírá přímo hlasové nastavení Google / správu klávesnic namísto chybného přesměrování na výběr digitálního asistenta.
• **Oprava denního cíle a popisků:** Tlačítko Zopakovat chyby již nepřepisuje denní cíl a statistická dlaždice nese jasný popisek „Dnes splněno“.
• **Výslovnostní homofon kravata (tie / thai):** Bezpečné uznání slova tie i při izolovaném rozpoznání jako Thai.
• **Úprava textu v nastavení:** Odstraněn matoucí technický pojem bounce.

[EN]
• **Leaderboard User Rank Indicator:** Prominent pinned card in the leaderboard section displays your exact ranking (#X of N learners), your period XP, and dynamic gap to the player ahead.
• **Fixed Offline Speech Pack Settings Link:** Direct navigation to Google speech / keyboard settings without redirecting to the digital assistant selection screen.
• **Fixed Daily Goal & Clarified Stats:** Retrying missed cards never overwrites the user's daily goal setting, and the stat tile is clearly labeled "Today's Progress".
• **Pronunciation Homophone 'tie' / 'thai':** Reliable acceptance of 'tie' even when Google STT transcribes isolated audio as 'Thai'.
• **Updated Settings Copy:** Removed the technical term 'bounce'.
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

    # 1. Create or fetch release
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
            upload_url_template = rel["upload_url"]
    except urllib.error.HTTPError as e:
        print(f"HTTPError: {e.code}")
        # If release for tag already exists, fetch it and update body & title
        req_get = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", headers=headers)
        with urllib.request.urlopen(req_get) as resp:
            rel = json.loads(resp.read().decode("utf-8"))
            print(f"Found existing release ID: {rel['id']}")
            upload_url_template = rel["upload_url"]

        patch_req = urllib.request.Request(
            f"https://api.github.com/repos/{REPO}/releases/{rel['id']}",
            data=json.dumps({"name": NAME, "body": BODY}).encode("utf-8"),
            headers=headers,
            method="PATCH"
        )
        try:
            with urllib.request.urlopen(patch_req) as resp:
                print("Updated release metadata successfully")
        except Exception as pe:
            print(f"Could not patch release: {pe}")

    upload_base = upload_url_template.split("{")[0]

    # 2. Upload APK assets
    apk_paths = [
        ("LingoStacks_v1.0.14.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.14.apk"),
        ("LingoStacks.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.14.apk"),
        ("LingoStacks_v1.0.14_playstore.aab", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.14_playstore.aab")
    ]

    # Delete existing asset with same name if any
    for asset in rel.get("assets", []):
        if asset["name"] in [a[0] for a in apk_paths]:
            del_req = urllib.request.Request(asset["url"], headers=headers, method="DELETE")
            try:
                urllib.request.urlopen(del_req)
                print(f"Deleted old asset {asset['name']}")
            except Exception as de:
                print(f"Could not delete old asset {asset['name']}: {de}")

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
