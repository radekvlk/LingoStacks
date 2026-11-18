import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.12"
NAME = "LingoStacks v1.0.12 (Build 118)"

BODY = """versionCode: 118

[CS]
• **Symetrický plovoucí mikrofon (100% fixní střed & čistý bounce):** Mikrofon je nyní pevně ukotven přesně v geometrickém středu obrazovky (Alignment.Center). Při přepínání mezi poslechem a manuálním režimem se jeho pozice nehne ani o 0,0 px (odstraněno jakékoliv ujíždění do stran či pocit přijíždění zleva). Tlačítka [✕] a [✓] se plynule a symetricky vysouvají zpoza mikrofonu na obě strany. Kliknutí na mikrofon má přirozený taktilní pružinový stisk na místě (tactile bounce).
• **Kompaktní odznak denního limitu při jiskřičkách:** Opraveno roztahování zeleného obdélníku s čísly při splnění cíle. Odznak si drží čistou pevnou velikost přesně podle textu, zatímco zlaté a smaragdové částice volně vyletují do prostoru bez oříznutí.
• **Optimalizace pro velká písma (Accessibility & Large Fonts):** Karta se slovíčkem si drží maximální velikost, čitelnost a centrální postavení bez ořezu.

[EN]
• **Symmetrical Floating Mic Island (100% Fixed Center & In-Place Bounce):** The microphone is anchored to the exact screen center at all times. Switching between voice listening and manual mode maintains 0.0 px horizontal and vertical movement (completely eliminated any sideways drift). Action buttons [✕] and [✓] smoothly emerge symmetrically from behind the center mic. Tapping the mic provides a tactile elastic in-place bounce.
• **Fixed Daily Goal Badge Bounds During Particle Burst:** Prevented the goal badge rectangle from stretching during celebration. The pill badge maintains its compact bounds while gold and emerald sparkle particles burst freely into the surrounding space.
• **Large System Fonts Optimization:** Flashcard content keeps maximum prominence and scale without clipping under any accessibility font settings.
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
        ("LingoStacks_v1.0.12.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.12.apk"),
        ("LingoStacks.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.12.apk"),
        ("LingoStacks_v1.0.12_playstore.aab", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.12_playstore.aab")
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
