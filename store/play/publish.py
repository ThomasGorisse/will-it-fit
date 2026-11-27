"""Send a Will It Fit build to Play: production (staged) + internal, release notes, the listing
text (title, short and full description), plus framed phone screenshots and the 512 px icon
(icon-512.png) for locales with a frames/out/<locale>/ folder, in one edit. Text-only locales
use the default language graphics.

Uses the PlayApi client of ~/Projects/ar-model-viewer/store/scripts/sync-play.py (its own
`upload` refuses an AAB without Firebase, and reads ARMV's notes).

  python3 publish.py <signed.aab | versionCode> <fraction> dry|commit [internal-only]

  dry            uploads into an edit, runs edits:validate, deletes the edit: nothing ships.
  internal-only  internal track only, no production, no screenshots: test the Play-signed
                 APK first (get_apk.py), then reference the same versionCode for production.
"""
import sys, json, importlib.util, urllib.request
from pathlib import Path
spec = importlib.util.spec_from_file_location("sp", str(Path.home() / "Projects/ar-model-viewer/store/scripts/sync-play.py"))
sp = importlib.util.module_from_spec(spec); spec.loader.exec_module(sp)
aab, fraction, commit = Path(sys.argv[1]), float(sys.argv[2]), sys.argv[3] == "commit"
internal_only = "internal-only" in sys.argv
pkg = "com.gorisse.thomas.willitfit"
root = Path(__file__).resolve().parent  # this checkout's notes, whatever the main checkout is on
locales = json.loads((root / "locales.json").read_text())
notes = [{"language": l, "text": (root / l / "changelogs/default.txt").read_text().strip()}
         for l in locales if (root / l / "changelogs/default.txt").is_file()]
api = sp.PlayApi()
edit_id = api.call("POST", f"/androidpublisher/v3/applications/{pkg}/edits")["id"]
ok = False
try:
    if str(aab).isdigit():
        vc = int(str(aab))  # already on Play (internal): reference it, no re-upload
    else:
        req = urllib.request.Request(f"{sp.ANDROIDPUBLISHER_BASE}/upload/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/bundles?uploadType=media",
                                     data=aab.read_bytes(), method="POST")
        req.add_header("Authorization", f"Bearer {api.token}"); req.add_header("Content-Type", "application/octet-stream")
        with urllib.request.urlopen(req) as r: vc = json.loads(r.read())["versionCode"]
    print("uploaded into edit, versionCode", vc)
    tracks = f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/tracks"
    if not internal_only:
        # A build already in production keeps the notes it went out with: the changelogs here
        # speak for the next version, not for one being rolled further.
        prod = api.call("GET", f"{tracks}/production").get("releases", [])
        same = next((r for r in prod if str(vc) in r.get("versionCodes", [])), None)
        prod_notes = same.get("releaseNotes", notes) if same else notes
        api.call("PUT", f"{tracks}/production", body={"releases": [sp._release_body([vc], prod_notes, fraction)]})
    # Internal testers already on a newer build cannot go back to this one: Play refuses the edit.
    internal = api.call("GET", f"{tracks}/internal").get("releases", [])
    newest = max((int(c) for r in internal for c in r.get("versionCodes", [])), default=0)
    if newest > vc:
        print(f"internal stays on vc{newest}, newer than vc{vc}")
    else:
        api.call("PUT", f"{tracks}/internal", body={"releases": [sp._release_body([vc], notes, None)]})
    # Phone screenshots: the framed, captioned slides 1-5 of each locale replace the raw captures.
    frames = root / "frames/out"
    for l in ([] if internal_only else locales):
        # The listing text: title, short and full description, as in <locale>/*.txt.
        text = {k: (root / l / f).read_text().strip() for k, f in
                (("title", "title.txt"), ("shortDescription", "short_description.txt"),
                 ("fullDescription", "full_description.txt"))}
        # The trailer: video.txt (one URL for every locale), else the one already on Play. A PUT
        # without it would clear it.
        listing = f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/listings/{l}"
        video_file = root / "video.txt"
        video = video_file.read_text().strip() if video_file.exists() else api.call("GET", listing).get("video", "")
        if video:
            text["video"] = video
        api.call("PUT", listing, body={"language": l, **text})
        if not (frames / l).is_dir():
            # Leave localized graphics unset so Play uses the default language's assets.
            print(f"{l}: {text['title']!r}, text only (default language graphics) in the edit")
            continue
        base = f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/listings/{l}/phoneScreenshots"
        api.call("DELETE", base)
        for i in range(1, 6):
            img = frames / l / f"{i}.jpg"
            r = urllib.request.Request(f"{sp.ANDROIDPUBLISHER_BASE}/upload{base}?uploadType=media", data=img.read_bytes(), method="POST")
            r.add_header("Authorization", f"Bearer {api.token}"); r.add_header("Content-Type", "image/jpeg")
            with urllib.request.urlopen(r) as resp: resp.read()
        got = api.call("GET", base).get("images", [])
        # The 512 px icon of the listing (icon-512.png, the same art as the launcher icon).
        icon = root / "icon-512.png"
        if icon.exists():
            ibase = f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/listings/{l}/icon"
            api.call("DELETE", ibase)
            r = urllib.request.Request(f"{sp.ANDROIDPUBLISHER_BASE}/upload{ibase}?uploadType=media", data=icon.read_bytes(), method="POST")
            r.add_header("Authorization", f"Bearer {api.token}"); r.add_header("Content-Type", "image/png")
            with urllib.request.urlopen(r) as resp: resp.read()
        print(f"{l}: {text['title']!r}, {len(got)} phone screenshots" + (", icon" if icon.exists() else "") + " in the edit")
    if commit:
        api.call("POST", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}:commit"); ok = True
        print(f"COMMITTED vc{vc}: " + ("internal only" if internal_only else f"production {fraction:.0%}, internal 100%, screenshots"))
    else:
        api.call("POST", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}:validate")
        print(f"VALIDATED vc{vc} (dry run, nothing committed)")
finally:
    if not ok:
        api.call("DELETE", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}"); print("edit deleted")
