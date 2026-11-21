import os
import json
import urllib.request
import mimetypes

TOKEN_PATH = r"C:\Users\radek.vlk\.gemini\antigravity\brain\43b0acd7-1097-4287-b01f-eedb0d465b28\scratch\github_token.txt"
REPO = "radekvlk/LingoStacks"
TAG = "v1.0.16"
NAME = "LingoStacks v1.0.16 (Build 122)"

BODY = """versionCode: 122

[CS]
• **Přepracovaná plovoucí navigační lišta (styl Samsung Health):** Domů, Statistiky a Nastavení v hlavní plovoucí liště, samostatně oddělené tlačítko Trénink se zachováním vyskakovacího procvičování.
• **Plnohodnotné záložky Statistiky a Nastavení:** Již nezakrývají spodní navigační lištu jako modální okna, navigace zůstává stále přístupná.
• **Neoříznutá pilulka s počtem slovíček na tlačítku Trénink:** Odznak nyní volně přesahuje přes okraj tlačítka a dynamicky se rozšiřuje pro 1 až 3+ ciferná čísla.
• **Plynulý závoj / rozostření za lištou:** Zesílený spodní závoj (210 dp) plynule rozpouští procházející karty a zabraňuje prosvítání písmen v mezeře mezi tlačítky.
• **Oprava odkazu na offline řeč:** V nastavení naviguje přímo k návodu a instalaci offline hlasových balíčků namísto Google Asistenta.
• **Ukazatel vlastní pozice v žebříčku:** Přidán přehledný indikátor pořadí uživatele bez ohledu na celkový počet účastníků.
• **Úprava textů:** Odstraněno slovo „bounce“ v nastavení a herní texty v záhlaví žebříčku.

[EN]
• **Redesigned Floating Navigation Bar (Samsung Health style):** Home, Statistics, and Settings in the main floating pill, with a separate standalone Training button keeping the full-screen practice flow.
• **Full-Page Statistics & Settings Tabs:** Modern standalone tabs replacing modal popups, keeping the navigation bar permanently accessible.
• **Unclipped Review Counter Badge:** The badge on the Training button now overflows cleanly without clipping and expands flexibly for 1 to 3+ digit counts.
• **Smooth Background Veil / Blur:** 210 dp gradient veil gently diffuses scrolling cards and completely eliminates text bleeding through the gap between bar items.
• **Offline Speech Setup Fix:** Offline speech settings action now leads directly to TTS guide dialog instead of Google Assistant.
• **Leaderboard User Position:** Added clear user rank indicator regardless of total participants.
• **Copy Polish:** Removed 'bounce' wording from settings and non-educational gaming terms from leaderboard header.
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
        ("LingoStacks_v1.0.16.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.16.apk"),
        ("LingoStacks.apk", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.16.apk"),
        ("LingoStacks_v1.0.16_playstore.aab", r"C:\Users\radek.vlk\.gemini\antigravity\scratch\SlovickaSpeak\LingoStacks_v1.0.16_playstore.aab")
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
