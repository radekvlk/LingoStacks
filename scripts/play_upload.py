"""Upload a signed .aab to Google Play via the Android Publisher API.

Usage:
  python scripts/play_upload.py --check                      # verify access only (no changes)
  python scripts/play_upload.py <path.aab> [--track alpha] [--status completed|draft]
                                [--notes-file notes.txt]

The service account key is read from %USERPROFILE%\\.lingostacks\\play-publisher-key.json
(or the PLAY_KEY_FILE env var). It must never be committed to Git.
"""
import argparse
import os
import re
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = "cz.slovickaspeak.app"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
DEFAULT_KEY = os.path.join(os.path.expanduser("~"), ".lingostacks", "play-publisher-key.json")


def service():
    key = os.environ.get("PLAY_KEY_FILE", DEFAULT_KEY)
    creds = service_account.Credentials.from_service_account_file(key, scopes=SCOPES)
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def parse_notes(path):
    """Notes file format: <en-GB>...</en-GB><cs-CZ>...</cs-CZ>"""
    if not path:
        return []
    text = open(path, encoding="utf-8").read()
    return [{"language": lang, "text": body.strip()}
            for lang, body in re.findall(r"<([a-zA-Z-]+)>(.*?)</\1>", text, re.S)]


def check(api):
    edit = api.edits().insert(packageName=PACKAGE, body={}).execute()
    try:
        tracks = api.edits().tracks().list(packageName=PACKAGE, editId=edit["id"]).execute()
        for t in tracks.get("tracks", []):
            rel = [(r.get("name"), r.get("status"), r.get("versionCodes")) for r in t.get("releases", [])]
            print(f"  track={t['track']}: {rel}")
        print("ACCESS OK")
    finally:
        api.edits().delete(packageName=PACKAGE, editId=edit["id"]).execute()


def upload(api, aab, track, status, notes):
    edit_id = api.edits().insert(packageName=PACKAGE, body={}).execute()["id"]
    media = MediaFileUpload(aab, mimetype="application/octet-stream", resumable=True)
    bundle = api.edits().bundles().upload(packageName=PACKAGE, editId=edit_id, media_body=media).execute()
    vc = bundle["versionCode"]
    print(f"Uploaded bundle versionCode={vc}")
    release = {"versionCodes": [str(vc)], "status": status}
    if notes:
        release["releaseNotes"] = notes
    api.edits().tracks().update(packageName=PACKAGE, editId=edit_id, track=track,
                                body={"track": track, "releases": [release]}).execute()
    api.edits().commit(packageName=PACKAGE, editId=edit_id).execute()
    print(f"Committed: track={track}, status={status}, versionCode={vc}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("aab", nargs="?")
    p.add_argument("--check", action="store_true")
    p.add_argument("--track", default="alpha")  # 'alpha' = Closed testing - Alpha
    p.add_argument("--status", default="completed", choices=["completed", "draft"])
    p.add_argument("--notes-file")
    a = p.parse_args()
    api = service()
    if a.check or not a.aab:
        check(api)
        return
    upload(api, a.aab, a.track, a.status, parse_notes(a.notes_file))


if __name__ == "__main__":
    sys.exit(main())
