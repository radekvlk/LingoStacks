import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.9"
NAME = "LingoStacks v1.0.9 (Build 115)"

BODY = """versionCode: 115

[CS]
• **Optimalizace rozpoznávání krátkých slov a písmen:** Okamžité vyhodnocení písmenných homofonů (např. TEA vs. T) a fonetických variant bez zpoždění.
• **Zpřesnění kontextu frází:** Přidán kontext v závorkách u kulinářských i zdvořilostních frází napříč všemi 6 jazyky (např. „To je výborné! (o jídle)“, „Účet, prosím (v restauraci)“).
• **Rozšíření uznávaných odpovědí:** Širší škála přirozených synonym u překladů frází (např. „it is great / that's great / it is delicious“).
• **Fluidní tekutá navigace (Liquid Glass Bar):** Spodní plovoucí lišta s interaktivním přetahováním prstem, organickou tekutou deformací (squash & stretch) a pružinovým usazením.
• **Plynulé animace dialogů:** Statistiky i Nastavení s bounce efektem a hladkým zavíráním.

[EN]
• **Short Words & Letter Homophones:** Instant matching for letter homophones (e.g. TEA vs T) and phonetic variants without delay.
• **Contextual Phrase Hints:** Added parenthesized context for dining and courtesy phrases across all 6 languages (e.g. "To je výborné! (o jídle)", "Účet, prosím (v restauraci)").
• **Expanded Accepted Variants:** Broader set of natural spoken synonyms accepted for phrases (e.g. "it is great / that's great / it is delicious").
• **Fluid Liquid Glass Bottom Bar:** Interactive real-time finger dragging, liquid squash & stretch deformation, spring bounce snap, and subtle haptics.
• **Smooth Sheet Animations:** Statistics and Settings sheets feature spring bounce enter and smooth exit transitions.
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
        ("LingoStacks_v1.0.9.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.9.apk"),
        ("LingoStacks.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.9.apk")
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
