"""Send a Will It Fit build to Play: production (staged) + internal, release notes and framed
phone screenshots (frames/out/<locale>/1..5.jpg) from this folder, in one edit.

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
root = Path.home() / "Projects/will-it-fit/store/play"
notes = [{"language": l, "text": (root / l / "changelogs/default.txt").read_text().strip()}
         for l in ["en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR"]]
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
    if not internal_only:
        api.call("PUT", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/tracks/production",
                 body={"releases": [sp._release_body([vc], notes, fraction)]})
    api.call("PUT", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/tracks/internal",
             body={"releases": [sp._release_body([vc], notes, None)]})
    # Phone screenshots: the framed, captioned slides 1-5 of each locale replace the raw captures.
    frames = root / "frames/out"
    for l in ([] if internal_only else ["en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR"]):
        base = f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/listings/{l}/phoneScreenshots"
        api.call("DELETE", base)
        for i in range(1, 6):
            img = frames / l / f"{i}.jpg"
            r = urllib.request.Request(f"{sp.ANDROIDPUBLISHER_BASE}/upload{base}?uploadType=media", data=img.read_bytes(), method="POST")
            r.add_header("Authorization", f"Bearer {api.token}"); r.add_header("Content-Type", "image/jpeg")
            with urllib.request.urlopen(r) as resp: resp.read()
        got = api.call("GET", base).get("images", [])
        print(f"{l}: {len(got)} phone screenshots in the edit")
    if commit:
        api.call("POST", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}:commit"); ok = True
        print(f"COMMITTED vc{vc}: " + ("internal only" if internal_only else f"production {fraction:.0%}, internal 100%, screenshots"))
    else:
        api.call("POST", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}:validate")
        print(f"VALIDATED vc{vc} (dry run, nothing committed)")
finally:
    if not ok:
        api.call("DELETE", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}"); print("edit deleted")
