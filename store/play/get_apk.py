"""Download Play's universal APK for a versionCode: python3 get_apk.py <versionCode> <out.apk>.
Install it with `adb install -r -i com.android.vending` (or its licence check closes the app)."""
import sys, json, importlib.util, urllib.request
from pathlib import Path
spec = importlib.util.spec_from_file_location("sp", str(Path.home() / "Projects/ar-model-viewer/store/scripts/sync-play.py"))
sp = importlib.util.module_from_spec(spec); spec.loader.exec_module(sp)
api = sp.PlayApi(); pkg = "com.gorisse.thomas.willitfit"; vc = sys.argv[1]; out = Path(sys.argv[2])
res = api.call("GET", f"/androidpublisher/v3/applications/{pkg}/generatedApks/{vc}")
for g in res.get("generatedApks", []):
    u = g.get("generatedUniversalApk")
    if u:
        req = urllib.request.Request(f"{sp.ANDROIDPUBLISHER_BASE}/androidpublisher/v3/applications/{pkg}/generatedApks/{vc}/downloads/{u['downloadId']}:download?alt=media")
        req.add_header("Authorization", f"Bearer {api.token}")
        with urllib.request.urlopen(req) as r: out.write_bytes(r.read())
        print("downloaded", out.stat().st_size); break
else:
    print("no universal apk yet:", json.dumps(res)[:300])
