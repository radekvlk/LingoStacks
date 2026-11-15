"""Upload phone screenshots to Google Play Console via Android Publisher API.

Usage:
  python scripts/play_screenshots_upload.py [--dry-run]
"""
import argparse
import glob
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = "cz.slovickaspeak.app"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
DEFAULT_KEY = os.path.join(os.path.expanduser("~"), ".lingostacks", "play-publisher-key.json")
SCREENSHOTS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "screenshots")

SCREENSHOT_FILES = [
    "01_home.png",
    "02_flashcard.png",
    "03_flashcard_hint.png",
    "04_settings.png",
    "05_stats.png",
]


def service():
    key = os.environ.get("PLAY_KEY_FILE", DEFAULT_KEY)
    creds = service_account.Credentials.from_service_account_file(key, scopes=SCOPES)
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def upload_screenshots(api, dry_run=False):
    edit_id = api.edits().insert(packageName=PACKAGE, body={}).execute()["id"]
    print(f"Created edit {edit_id}")
    try:
        listings = api.edits().listings().list(packageName=PACKAGE, editId=edit_id).execute().get("listings", [])
        languages = [item.get("language") for item in listings]
        print(f"Active store listing languages: {languages}")

        for lang in languages:
            print(f"\nProcessing language: {lang}")
            current = api.edits().images().list(
                packageName=PACKAGE, editId=edit_id, language=lang, imageType="phoneScreenshots"
            ).execute().get("images", [])
            print(f"  Current {lang} screenshots count: {len(current)}")

            if dry_run:
                print(f"  [DRY RUN] Would delete {len(current)} images and upload {len(SCREENSHOT_FILES)} new images.")
                continue

            # Delete existing
            if current:
                api.edits().images().deleteall(
                    packageName=PACKAGE, editId=edit_id, language=lang, imageType="phoneScreenshots"
                ).execute()
                print(f"  Deleted previous screenshots for {lang}")

            # Upload fresh
            for fname in SCREENSHOT_FILES:
                fpath = os.path.join(SCREENSHOTS_DIR, fname)
                if not os.path.exists(fpath):
                    print(f"  WARNING: File not found: {fpath}")
                    continue
                media = MediaFileUpload(fpath, mimetype="image/png")
                uploaded = api.edits().images().upload(
                    packageName=PACKAGE,
                    editId=edit_id,
                    language=lang,
                    imageType="phoneScreenshots",
                    media_body=media,
                ).execute()
                print(f"  Uploaded {fname} -> image id: {uploaded.get('id')}")

        if dry_run:
            print("\nDry run completed, discarding edit.")
            api.edits().delete(packageName=PACKAGE, editId=edit_id).execute()
        else:
            api.edits().commit(packageName=PACKAGE, editId=edit_id).execute()
            print("\nSUCCESS: Committed new screenshots to Google Play Console!")
    except Exception:
        api.edits().delete(packageName=PACKAGE, editId=edit_id).execute()
        raise


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--dry-run", action="store_true", help="Simulate without committing")
    a = p.parse_args()
    api = service()
    upload_screenshots(api, dry_run=a.dry_run)


if __name__ == "__main__":
    sys.exit(main())
