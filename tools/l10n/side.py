#!/usr/bin/env python3
"""English and a translation side by side, for reviewing one.

    side.py fr

Each entry prints as `name [where] | English | translation`, under the section comment the
English file gives it. Flags: LONG (a short label grown well past the English, which may not fit
its button), SAME (left in English; fine for brands and codes, not for words).
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from check import RES, load

def sections(path):
    """The comment above each entry in the English file: where it shows up."""
    where, out = "", {}
    for line in path.read_text(encoding="utf-8").splitlines():
        comment = re.search(r"<!--\s*(.*?)\s*-->", line)
        if comment:
            where = comment.group(1)
        name = re.search(r'<(?:string|plurals) name="([^"]+)"', line)
        if name:
            out[name.group(1)] = where
    return out

def text(entry):
    kind, _, value, items = entry
    return value if kind == "string" else " / ".join(f"{q}: {v}" for q, v in items.items())

def main(qualifier):
    english_path = RES / "values" / "strings.xml"
    english, where = load(english_path), sections(english_path)
    ours = load(RES / f"values-{qualifier}" / "strings.xml")
    last = None
    for name, entry in english.items():
        if entry[1]:
            continue
        if where.get(name) != last:
            last = where.get(name)
            print(f"\n## {last}")
        en, tr = text(entry), text(ours[name]) if name in ours else "(MISSING)"
        flags = []
        if entry[0] == "string" and len(en) <= 20 and len(tr) > max(12, 1.6 * len(en)):
            flags.append("LONG")
        if tr == en and re.search(r"[a-z]{3}", en):
            flags.append("SAME")
        print(f"{name} | {en} | {tr}" + (f"   <{' '.join(flags)}>" if flags else ""))

if __name__ == "__main__":
    main(sys.argv[1])
