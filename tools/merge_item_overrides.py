"""Make AscensionLib's item art work on a server that already uses the same vanilla items for other packs' art.

Items are vanilla items plus a custom_model_data (1.21.1 has no item_model component), so the art is an `overrides` list in
assets/minecraft/models/item/<base>.json. Polymer copies include_zips files over each other WHOLE: when two packs ship the same base item
(glowstone_dust, knowledge_book, nautilus_shell, ...), only the last pack's file survives and the other pack's art vanishes.

This tool reads the pack Polymer generated from the server's include list (polymer/resource_pack.zip, so every other pack is already in
it), takes each base item AscensionLib uses, and writes one file per base holding the union of the overrides. The output is a small zip to
list LAST in include_zips. Re-run it whenever another pack changes.

    python -I tools/merge_item_overrides.py <server>/polymer/resource_pack.zip build/AscensionLib-resourcepack.zip build/AscensionLib-merge.zip
"""
import json
import sys
import zipfile

PREFIX = 'assets/minecraft/models/item/'


def overrides(archive, name):
    if name not in archive.namelist():
        return None, []
    model = json.loads(archive.read(name))
    return model, model.get('overrides', [])


def main(argv):
    if len(argv) != 4:
        print(__doc__)
        return 2
    generated, ours, out = zipfile.ZipFile(argv[1]), zipfile.ZipFile(argv[2]), argv[3]
    files = {}
    for name in sorted(n for n in ours.namelist() if n.startswith(PREFIX) and n.endswith('.json')):
        base_model, ours_list = overrides(ours, name)
        other_model, other_list = overrides(generated, name)
        taken = {o['predicate'].get('custom_model_data') for o in other_list}
        clash = [o['predicate']['custom_model_data'] for o in ours_list if o['predicate']['custom_model_data'] in taken
                 and not any(x['predicate'].get('custom_model_data') == o['predicate']['custom_model_data'] and x['model'] == o['model'] for x in other_list)]
        if clash:
            print(f'ERROR {name}: custom_model_data {clash} is already used by another pack on this item; renumber before merging')
            return 1
        merged = dict(other_model or base_model)
        have = {(json.dumps(o, sort_keys=True)) for o in other_list}
        merged['overrides'] = list(other_list) + [o for o in ours_list if json.dumps(o, sort_keys=True) not in have]
        files[name] = merged
        print(f'{name.split("/")[-1]:28} {len(other_list):4} existing + {len(merged["overrides"]) - len(other_list):2} ours')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
        meta = json.loads(ours.read('pack.mcmeta'))
        meta['pack']['description'] = 'AscensionLib item overrides merged with the other packs (list last in include_zips)'
        archive.writestr(zipfile.ZipInfo('pack.mcmeta', (1980, 1, 1, 0, 0, 0)), json.dumps(meta, indent=2))
        for name, model in files.items():
            archive.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), json.dumps(model, indent=2))
    print('wrote', out)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
