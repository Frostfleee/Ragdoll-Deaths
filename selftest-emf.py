"""Downloads what selftest.sh's EMF mode installs: Entity Model Features and Entity Texture Features for one loader and
game version, and the test resource packs (Fresh Animations and AL's packs), into a cache folder. Prints the files.

Usage: python selftest-emf.py <fabric|neoforge|forge> <game version> <cache folder>
"""
import json
import sys
import urllib.request
from pathlib import Path

UA = {'User-Agent': 'ragdoll-deaths-selftest'}
PACKS = ['fresh-animations', 'als-zombies-revamped-x-fresh-animations', 'als-skeletons-revamped-x-fresh-animations', 'als-creepers-revamped',
         'als-enderman-revamped-x-fresh-animations', 'als-spiders-revamped-x-fresh-animations', 'als-piglins-revamped-x-fresh-animations']


def versions(slug):
    return json.load(urllib.request.urlopen(urllib.request.Request(f'https://api.modrinth.com/v2/project/{slug}/version', headers=UA)))


def download(version, cache):
    f = next((x for x in version['files'] if x['primary']), version['files'][0])
    target = cache / f['filename']
    if not target.exists():
        with urllib.request.urlopen(urllib.request.Request(f['url'], headers=UA)) as r:
            target.write_bytes(r.read())
    return target


def main():
    loader, game, cache = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    (cache / 'mods').mkdir(parents=True, exist_ok=True)
    (cache / 'packs').mkdir(parents=True, exist_ok=True)
    for slug in ('entity-model-features', 'entitytexturefeatures'):
        match = [v for v in versions(slug) if loader in v['loaders'] and game in v['game_versions']]
        if not match:
            raise SystemExit(f'no {slug} for {loader} {game}')
        print('mod', download(match[0], cache / 'mods').name)
    for slug in PACKS:
        print('pack', download(versions(slug)[0], cache / 'packs').name)


if __name__ == '__main__':
    main()
