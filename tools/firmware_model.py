#!/usr/bin/env python3
"""Exports a firmware snapshot for the developer demo (assets/demo/firmware-<version>.json).

Run after building the firmware once (tools/preview/build/sim is needed):

    python3 tools/firmware_model.py --firmware ../FireLampBLE/FireLamp_ESP32 \
        --version 0.11.1 --build-date "Nov  3 2026 15:31:00"

The JSON is a snapshot of the BLE attribute values the app reads: the device info and the effect
catalog with all parameters. Settings defaults are added in DemoLampState.kt.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--firmware', required=True)
    ap.add_argument('--version', required=True)
    ap.add_argument('--build-date', required=True)
    ap.add_argument('--out', help='default: app/src/main/assets/demo/firmware-<version>.json')
    args = ap.parse_args()

    fw = Path(args.firmware)
    sim = fw / 'tools/preview/build/sim'
    effect_c = (fw / 'main/effect.c').read_text()
    block = effect_c[effect_c.index('const effect_descriptor_t effects[] = {'):effect_c.index('const size_t effects_count')]
    order = re.findall(r'DESCR_EFFECT_([A-Z0-9_]+)', block)

    descr = {}
    for h in (fw / 'main/effects').glob('*.h'):
        for m in re.finditer(r'#define\s+DESCR_EFFECT_([A-Z0-9_]+)\s+DECL_EFFECT_(?:FULL|SHORT)\((\w+),\s*"([^"]+)"\)', h.read_text()):
            descr[m.group(1)] = (m.group(2), m.group(3))

    params = {}
    for c in (fw / 'main/effects').glob('*.c'):
        for m in re.finditer(r'EFFECT_PARAMS\((\w+),\s*\d+\)\s*=\s*\{(.*?)\n\};', c.read_text(), re.S):
            items = []
            for p in re.finditer(r'DECL_PARAM_(\w+)\(([^;]*?)\)\s*,', m.group(2), re.S):
                kind = p.group(1)
                parts = [x.strip() for x in p.group(2).split(',')]
                if kind == 'RANGE':
                    items.append({'code': parts[1].strip('"'), 'name': parts[2].strip('"'), 'type': 0})
                elif kind == 'BOOL':
                    items.append({'code': parts[1].strip('"'), 'name': parts[2].strip('"'), 'type': 1})
                elif kind == 'TEMPO':
                    items.append({'code': 'tempo', 'name': 'Tempo', 'type': 0})
            params[m.group(1)] = items

    effects = []
    for i, token in enumerate(order):
        code, name = descr[token]
        numeric = {}
        for line in subprocess.run([str(sim), '--params', code], capture_output=True, text=True, check=True).stdout.splitlines():
            parts = line.split()
            if len(parts) >= 4:
                numeric[parts[0]] = tuple(map(int, parts[1:4]))
        plist = []
        for p in params.get(code, []):
            if p['code'] not in numeric:
                raise SystemExit(f'{code}.{p["code"]}: no numeric range from the simulator')
            mn, mx, default = numeric[p['code']]
            plist.append({**p, 'min': mn, 'max': mx, 'default': default, 'value': default})
        effects.append({'position': i, 'code': code, 'name': name, 'inDemo': True, 'params': plist})

    data = {
        'version': args.version,
        'buildDate': args.build_date,
        'buildVariant': 'default',
        'protocol': 1,
        'chip': 0,
        'flashMb': 4,
        'features': 15,
        'matrixWidth': 16,
        'matrixHeight': 16,
        'controls': 0,
        'effects': effects,
    }
    out = Path(args.out or f'app/src/main/assets/demo/firmware-{args.version}.json')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=1) + '\n')
    print(f'{out}: {len(effects)} effects, {sum(len(e["params"]) for e in effects)} parameters')


if __name__ == '__main__':
    main()
