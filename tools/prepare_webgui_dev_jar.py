"""Makes ascensionlib/libs/webgui-dev.jar from the downloaded WebGUI release jar.

WebGUI 2.0.0 is built with Loom 1.18.4, which this project (Loom 1.13.6) refuses to remap. The only change here is dropping the
Fabric-Loom-Version manifest line from a LOCAL copy used for compiling and the dev server; nothing in the copy is distributed.
Run: python -I tools/prepare_webgui_dev_jar.py
"""
import pathlib
import zipfile

libs = pathlib.Path(__file__).resolve().parent.parent / "ascensionlib" / "libs"
source = libs / "webgui-fabric-2.0.0+mc1.21.1.jar"
target = libs / "webgui-dev.jar"
if not source.exists():
    raise SystemExit(f"Download {source.name} first (see ascensionlib/libs/README.md)")
with zipfile.ZipFile(source) as zin, zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == "META-INF/MANIFEST.MF":
            lines = data.decode("utf-8").splitlines(keepends=True)
            data = "".join(l for l in lines if not l.startswith("Fabric-Loom-Version")).encode("utf-8")
        zout.writestr(item, data)
print("wrote", target)
