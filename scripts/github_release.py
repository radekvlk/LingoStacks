import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.15"
NAME = "LingoStacks v1.0.15 (Build 121)"

BODY = """versionCode: 121

[CS]
• **Úprava žebříčku:** Odstraněno herní slovo „hráčů“ z nadpisu statistického pořadí (nyní „Kompletní pořadí ($totalParticipants)“).
• **Oprava počítadla lekce:** Vyřešeno zamrzání čítače na 18/60 v lekcích (včetně nepravidelných sloves a smíšeného směru učení) – postup se nyní dynamicky načítá a plynule posouvá až k dokončení celé lekce.
• **Plná obousměrná podpora smíšeného režimu (MIXED):** Výpočet zvládnutých slovíček a prioritizace fronty správně zohledňuje oba směry procvičování.

[EN]
• **Leaderboard Header Copy:** Removed gaming terminology 'players/learners' from leaderboard header (now 'All Rankings ($totalParticipants)').
• **Lesson Progress Counter Fix:** Resolved counter freezing at 18/60 in lessons (including irregular verbs and mixed study direction) – counter and progress bar now update dynamically up to full lesson completion.
• **Full Bidirectional MIXED Mode Support:** Deck mastery calculation and spaced repetition queue now properly track both study directions.
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
        ("LingoStacks_v1.0.15.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.15.apk"),
        ("LingoStacks.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.15.apk"),
        ("LingoStacks_v1.0.15_playstore.aab", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.15_playstore.aab")
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
