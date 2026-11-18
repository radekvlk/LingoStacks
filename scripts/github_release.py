import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.11"
NAME = "LingoStacks v1.0.11 (Build 117)"

BODY = """versionCode: 117

[CS]
• **Optimalizace pro velká písma (Accessibility & Large Fonts):** Karta se slovíčkem si nyní vždy drží maximální velikost, čitelnost a centrální postavení bez ořezu. Při zvětšeném systémovém písmu se horní panel a hlasový banner inteligentně přizpůsobí do kompaktního režimu a stav upevnění slova v paměti se elegantně přesune do horního odznáčku karty.
• **Stabilní plovoucí ovládací panel (Bottom Control Bar):** Odstraněno lepení ovládacího baru na spodní kartu paměti — přidán garantovaný 14dp odstup. Mikrofon má v obou režimech (aktivní poslech i manuální režim) naprosto identický rozměr (54 dp) a přesně ukotvenou svislou osu Y (0,0 px svislý skok při přepínání).
• **Taktilní pružinové animace (Spring & Bounce physics):** Při přepnutí do manuálního režimu se kontejner plynule rozšíří s pružinovým tlumením a tlačítka [✕] a [✓] naskočí s hravým spring pop-in efektem ze stran. Klepnutí na samotný mikrofon má taktilní kompresní odezvu (bounce squash & spring back).
• **Širokoúhlý jiskřivý particle burst:** Zlaté a smaragdové částice při splnění denního limitu se nyní volně rozletují do prostoru bez oříznutí hranou odznaku.

[EN]
• **Accessibility & Large System Fonts Optimization:** Flashcard content maintains maximum visibility, scale, and center prominence without clipping. With larger system fonts, top bar and speech banner adapt into a space-efficient compact layout, while memory progress neatly moves into the card's top header badge.
• **Stable Floating Control Bar (Zero Vertical Jump):** Completely eliminated collision with bottom cards by guaranteeing a clean 14dp breathing gap. The microphone button maintains the exact same 54dp size and vertical Y center in both voice and manual modes (0.0 px vertical shift).
• **Tactile Spring & Bounce Animations:** Switching to manual mode smoothly expands the capsule island with iOS spring physics, popping in the [✕] and [✓] action buttons with spring bounce. Tapping the mic button delivers tactile elastic compression and spring back.
• **Unconstrained Sparkle Particle Burst:** Golden & emerald celebratory particles on daily goal completion now expand freely across the screen without clipping.
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
        ("LingoStacks_v1.0.11.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.11.apk"),
        ("LingoStacks.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.11.apk"),
        ("LingoStacks_v1.0.11_playstore.aab", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.11_playstore.aab")
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
