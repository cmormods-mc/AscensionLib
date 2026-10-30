# Screens as web pages (WebGUI)

Owner goal (2026-10-14): our mods live only in the server mods folder. Every screen is a web page the server ships to players through
[WebGUI](https://github.com/mc-webgui/webgui) (MIT; players install WebGUI, nothing of ours). AscensionLib has no client code left.

## How it works

- Pages are in the jar at `ascensionlib/src/main/resources/web/ascensionlib/`. `web/WebUi.publish` copies them to `config/webgui/web/ascensionlib/`
  when the server starts and asks WebGUI to rescan; WebGUI sends them to each client over the game connection (no port to open).
- Server to page: `WebUi.emit(player, "event", object)` sends Gson JSON; the page listens with `A.on("event", fn)` (`ui.js`).
- Page to server: `A.send("channel", {...})` posts `window.webgui.postToGame`; `WebUi.listen(channel, handler)` receives a parsed JSON object
  on the server thread. Messages over 4 KiB or malformed are dropped.
- A page asks for its data with a `*_ready` message once loaded. The server keeps "which Pokemon / which host" per player (`PENDING` maps) and
  answers with the data. Nothing is trusted from the page: ids are re-resolved to the player's own Pokemon, every craft carries an operation id and
  the revisions it was shown, and the existing rate limits apply.
- Server text goes into pages with `textContent` only, never HTML.
- Card pictures: CobblemonCards (on the server too) holds a 48x32 sprite per species in its jar. `web/WebSprites` reads the file for a species and
  sends it to the open page as a data URL (the `sprites` event, batches under WebGUI's 32 KB event limit, once per page). Nothing is copied into
  our jar or into WebGUI's page folder, whose 2000-file cap AscensionLib, CobbleRaids and CobbleTowers share. Forms and shinies are not sent yet.
- WebGUI refuses a single event over 32 KB: `WebUi.emitRaw` logs and drops anything larger instead of truncating.

## Pages

| Page | Opened by | Server class | Replaces |
|---|---|---|---|
| `craft.html` | `/ascend craft <slot>`, the Upgrade button on the inspect page | `craft/CraftNet` | `CraftScreen` |
| `fusion.html` | the Fuse button on the craft page, `/ascend fuse <slot>` | `fusion/FusionNet`, `FusionWeb` | `FusionScreen`, picker |
| `inspect.html` | `/ascend inspect <slot>`, the overlay's team and enemy rows | `scout/InspectWeb` | `AscendInspectScreen` |
| `hud.html` | the server, as an overlay: a capture card, and a panel while the player is in a scouted encounter | `web/HudWeb` | capture reveal screen, battle inspect buttons, scouting screen |

Shared: `ascension.css` (the dark-LCD Pokedex tablet look: Monocraft font from `fonts/`, OFL, copied from CobbleRaids' bundle; scanlines; pixel keys; a stepped pixel bezel whose colour follows the Pokemon's rarity via `A.setBezel` in `ui.js`), `ui.js` (helpers). Never use a blurred `box-shadow` outside a page: WebGUI renders it as a black halo.

## Not replaceable by a page

- The Summary-screen and battle-tile inspect buttons were mixins into Cobblemon's GUI; they are gone. The overlay panel and `/ascend inspect` cover them.
- The old client settings (reveal size, sounds) and keybind are gone.

## Requirements and limits

- Without WebGUI on the server the pages do not exist; commands fall back to text where they had text before.
- A player without WebGUI is told once what to install when they open a screen.
- WebGUI's `autoHudOnJoin` shows its own HUD page; ours replaces it while open. Do not enable both.
- Build: WebGUI is compile-only (`ascensionlib/libs/webgui-dev.jar`, made by `tools/prepare_webgui_dev_jar.py`; see `libs/README.md`).
- Nothing here has been seen in a real WebGUI client yet. Page layout, the overlay's interaction key and sprite scaling need a live pass.
