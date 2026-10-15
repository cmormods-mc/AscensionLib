WebGUI (https://github.com/mc-webgui/webgui, MIT) is compiled against and loaded by the dev server, but its jar is not committed.
Download `webgui-fabric-2.0.0+mc1.21.1.jar` from https://github.com/mc-webgui/webgui/releases/tag/v2.0.0 into this folder
(`gh release download v2.0.0 -R mc-webgui/webgui -p 'webgui-fabric-2.0.0+mc1.21.1.jar' -D ascensionlib/libs`).

Then run `python -I tools/prepare_webgui_dev_jar.py`. It writes `webgui-dev.jar` (what Gradle uses), a local copy without the
`Fabric-Loom-Version` manifest line, because WebGUI is built with a newer Loom than this project can remap.
