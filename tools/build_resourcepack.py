"""Builds the wallet currency items' resource pack and everything derived from design/currency-items.json.

usage: python -I tools/build_resourcepack.py

Writes (all committed, regenerate after editing the design file or the art below):
  resourcepack/                                                  the pack sources (pack.mcmeta, textures, models, base-item overrides)
  ascensionlib/src/main/resources/web/ascensionlib/icons/*.png   the same icons for the web pages
  ascensionlib/src/main/resources/data/ascensionlib/loot_table/currency/*.json   one-item loot tables other mods and datapacks can reference
and a zip you can host for clients, in build/AscensionLib-resourcepack.zip, printing its sha1 (put both in server.properties:
resource-pack=<url>, resource-pack-sha1=<sha1>, require-resource-pack=true).
"""
import hashlib
import json
import pathlib
import struct
import sys
import zipfile
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
DESIGN = json.loads((ROOT / 'design' / 'currency-items.json').read_text(encoding='utf-8'))
NS = DESIGN['namespace']
PACK = ROOT / 'resourcepack'
ICONS = ROOT / 'ascensionlib/src/main/resources/web/ascensionlib/icons'
LOOT = ROOT / 'ascensionlib/src/main/resources/data' / NS / 'loot_table/currency'
PACK_FORMAT = 34  # Minecraft 1.21.1

OUT = '1b120d'
ART = {
    'resonance_dust': ({'g': 'd9a441', 'h': 'a77b2c', 'w': 'fff3b0'}, [
        "................",
        "................",
        ".......o........",
        "......owo.......",
        ".....owgwo......",
        "......owo..o....",
        "...o......owo...",
        "..owo....owgwo..",
        ".owgwo....owo...",
        "..owo...o..o....",
        "...o...owo......",
        "......owgwo.....",
        ".....oggggho....",
        "....ogggggho....",
        "....oooooooo....",
        "................",
    ]),
    'facet': ({'W': 'bfe3ff', 'B': '4f9ad9', 'D': '2f5f94'}, [
        "................",
        "................",
        "....oooooooo....",
        "...oWWWBBBBDo...",
        "..oWWWBBBBBDDo..",
        ".oooooooooooooo.",
        ".oWWBBBBBBBDDDo.",
        "..oWBBBBBBBDDo..",
        "...oWBBBBBDDo...",
        "....oWBBBBDo....",
        ".....oWBBDo.....",
        "......oBDo......",
        ".......oo.......",
        "................",
        "................",
        "................",
    ]),
    'ascension_core': ({'R': 'e0603f', 'r': '8f2f1c', 'L': 'ffb08f'}, [
        "................",
        ".....oooooo.....",
        "....orRRRRro....",
        "...orRLLRRRro...",
        "..orRLLRRRRRro..",
        "..orRLRRRRRRro..",
        "..orRRRRRRRRro..",
        "..orRRRRRRRrro..",
        "..orRRRRRRrrro..",
        "...orRRRRrrro...",
        "....orrrrrro....",
        ".....oooooo.....",
        "................",
        "................",
        "................",
        "................",
    ]),
    'unique_fragment': ({'P': '8c63c7', 'p': '4d2f7a', 'L': 'd4b8ff'}, [
        "................",
        ".........o......",
        "........oLo.....",
        ".......oLPPo....",
        "......oLPPPPo...",
        ".....oLPPPPPpo..",
        ".....oLPPPPPpo..",
        "......oPPPPppo..",
        "......oPPPpppo..",
        ".......oPPppo...",
        ".......oPpppo...",
        "........oppo....",
        "........oppo....",
        ".........oo.....",
        "................",
        "................",
    ]),
    'unique_catalyst': ({'M': 'b04fa0', 'm': '6a2a60', 'G': 'cfe9ff', 'L': 'ffd1f5'}, [
        "................",
        ".....oooooo.....",
        ".....oGGGGo.....",
        "......oGGo......",
        "......oGGo......",
        ".....oGMMGo.....",
        "....oGMMMMGo....",
        "...oGMLMMMMGo...",
        "..oGMLMMMMMMGo..",
        "..oGMMMMMMMMGo..",
        "..oGMMMMMMmmGo..",
        "..oGMMMMMmmmGo..",
        "...oGmmmmmmGo...",
        "....oGGGGGGo....",
        ".....oooooo.....",
        "................",
    ]),
    'scouter': ({'E': '6f9a52', 'e': '3f6130', 'L': 'c8f0a0', 'p': '1b120d', 'w': 'ffffff'}, [
        "................",
        "................",
        "................",
        "....oooooooo....",
        "..ooLLLLLLLLoo..",
        ".oLLLEEEEEELLLo.",
        "oLLEEEppppEEELLo",
        "oLEEEppwwppEEELo",
        "oLEEEppwwppEEELo",
        "oLLEEEppppEEELLo",
        ".oLLLEEEEEELLLo.",
        "..ooeeeeeeeeoo..",
        "....oooooooo....",
        "................",
        "................",
        "................",
    ]),
    'ascension_sigil': ({'Y': 'ffd437', 'y': 'b8890a', 'W': 'fff6c2'}, [
        "................",
        ".....oooooo.....",
        "...ooYYYYYYoo...",
        "..oYYyyyyyyYYo..",
        ".oYYy..YY..yYYo.",
        ".oYy..YWWY..yYo.",
        "oYYy.YWWWWY.yYYo",
        "oYy..YWWWWY..yYo",
        "oYy...YWWY...yYo",
        "oYYy..YYYY..yYYo",
        ".oYy...YY...yYo.",
        ".oYYyy....yyYYo.",
        "..oYYyyyyyyYYo..",
        "...ooYYYYYYoo...",
        ".....oooooo.....",
        "................",
    ]),
    'tower_mark': ({'S': 'e8e1c9', 's': 'a89f7e', 'K': '5b5340', 'L': 'fffbe8'}, [
        "................",
        "....oooooooo....",
        "..ooLLLLSSSSoo..",
        ".oLLSSSSSSSSsso.",
        ".oLSSKKSSKKSsso.",
        ".oLSSKKKKKKSsso.",
        ".oLSSSKKKKSSsso.",
        ".oLSSSKSSKSSsso.",
        ".oLSSSKKKKSSsso.",
        ".oSSSSKSSKSSsso.",
        ".oSSSKKKKKKSsso.",
        ".oSSSSSSSSSssso.",
        "..ossSSSSSssso..",
        "...ooosssssooo..",
        ".....oooooo.....",
        "................",
    ]),
}


DEX_ART = ({'R': 'd23a46', 'r': '8a1f2b', 'L': 'ff9aa0', 'B': '56c1f4', 'b': '2a79a8', 'W': 'eaf8ff', 'K': '2c2c3a', 'Y': 'f2cf3a', 'G': '52c24a'}, [
    "................",
    "..oooooooooooo..",
    ".oRRRRRRRRRRRRo.",
    ".oLRWBRRYRGRKro.",
    ".oLRBbRRRRRRRro.",
    ".oLRRRRRRRRRRro.",
    ".oLRoooooooooro.",
    ".oLRoWWBBWBBoro.",
    ".oLRoWBBWWWBoro.",
    ".oLRoBBBBWBBoro.",
    ".oLRobbbbbbboro.",
    ".oLRoooooooooro.",
    ".oLRRKKRLRKKRro.",
    ".oLrrrrrrrrrrro.",
    "..oooooooooooo..",
    "................",
])


def png(rows, palette):
    colors = dict(palette)
    colors['o'] = OUT
    raw = bytearray()
    for row in rows:
        if len(row) != 16:
            raise SystemExit(f'row is {len(row)} wide, not 16: {row!r}')
        raw.append(0)  # filter: none
        for ch in row:
            if ch == '.':
                raw += bytes((0, 0, 0, 0))
            else:
                h = colors[ch]
                raw += bytes((int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255))
    if len(rows) != 16:
        raise SystemExit(f'{len(rows)} rows, not 16')

    def chunk(kind, data):
        body = kind + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, str):
        data = data.encode('utf-8')
    path.write_bytes(data)


def component_text(text, color):
    return json.dumps({'text': text, 'color': color, 'italic': False}, separators=(',', ':'))


def main():
    items = DESIGN['items']
    # pack metadata
    write(PACK / 'pack.mcmeta', json.dumps({'pack': {'pack_format': PACK_FORMAT, 'description': 'AscensionLib wallet currency items'}}, indent=2) + '\n')
    overrides = {}
    for material, spec in items.items():
        palette, rows = ART[material]
        image = png(rows, palette)
        write(PACK / f'assets/{NS}/textures/item/{material}.png', image)
        write(ICONS / f'{material}.png', image)
        write(PACK / f'assets/{NS}/models/item/{material}.json',
              json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{material}'}}, indent=2) + '\n')
        base = spec['base'].split(':', 1)[1]
        overrides.setdefault(base, []).append({'predicate': {'custom_model_data': spec['model']}, 'model': f'{NS}:item/{material}'})
        # the one-item loot table
        components = {
            'minecraft:custom_data': {NS: {'material': material}},
            'minecraft:custom_model_data': spec['model'],
            'minecraft:custom_name': component_text(spec['name'], spec['color']),
            'minecraft:lore': [component_text(line, 'gray') for line in spec['lore']],
        }
        table = {'type': 'minecraft:generic', 'pools': [{'rolls': 1, 'entries': [
            {'type': 'minecraft:item', 'name': spec['base'], 'functions': [{'function': 'minecraft:set_components', 'components': components}]}]}]}
        write(LOOT / f'{material}.json', json.dumps(table, indent=2) + '\n')
    # the AscensionDex: not a currency, so no icon list or loot table entry, but the same pack
    dex = DESIGN['dex']
    palette, rows = DEX_ART
    image = png(rows, palette)
    write(PACK / f'assets/{NS}/textures/item/ascension_dex.png', image)
    write(ICONS / 'ascension_dex.png', image)
    write(PACK / f'assets/{NS}/models/item/ascension_dex.json',
          json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/ascension_dex'}}, indent=2) + '\n')
    overrides.setdefault(dex['base'].split(':', 1)[1], []).append({'predicate': {'custom_model_data': dex['model']}, 'model': f'{NS}:item/ascension_dex'})
    dex_components = {
        'minecraft:custom_data': {NS: {'dex': True}},
        'minecraft:custom_model_data': dex['model'],
        'minecraft:custom_name': component_text(dex['name'], dex['color']),
        'minecraft:lore': [component_text(line, 'gray') for line in dex['lore']],
    }
    write(LOOT.parent / 'ascension_dex.json', json.dumps({'type': 'minecraft:generic', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': dex['base'], 'functions': [{'function': 'minecraft:set_components', 'components': dex_components}]}]}]}, indent=2) + '\n')
    # each base item's model gains the overrides; the base items are all plain generated items, so the override file is the whole model
    for base, entries in overrides.items():
        model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'minecraft:item/{base}'}, 'overrides': entries}
        write(PACK / f'assets/minecraft/models/item/{base}.json', json.dumps(model, indent=2) + '\n')
    # a deterministic zip
    out = ROOT / 'build' / 'AscensionLib-resourcepack.zip'
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(p for p in PACK.rglob('*') if p.is_file()):
            info = zipfile.ZipInfo(path.relative_to(PACK).as_posix(), (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes())
    print('pack:', out)
    print('sha1:', hashlib.sha1(out.read_bytes()).hexdigest())


if __name__ == '__main__':
    sys.exit(main())
