#!/usr/bin/env python3
"""Checks translations against values/strings.xml: same keys, same placeholders, valid escaping.

    check.py                 every values-*/strings.xml
    check.py fr de b+es+419  just those
    check.py --missing fr    the English entries fr still lacks, as XML to translate
"""
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parents[2] / "app/src/main/res"
PLACEHOLDER = re.compile(r"%(\d+\$)?[sd]")

SCRIPTS = {
    "ar": "ARABIC", "fa": "ARABIC", "ur": "ARABIC", "ps": "ARABIC", "sd": "ARABIC", "b+ckb": "ARABIC",
    "iw": "HEBREW", "hi": "DEVANAGARI", "mr": "DEVANAGARI", "ne": "DEVANAGARI", "bn": "BENGALI",
    "as": "BENGALI", "pa": "GURMUKHI", "gu": "GUJARATI", "or": "ORIYA", "ta": "TAMIL", "te": "TELUGU",
    "kn": "KANNADA", "ml": "MALAYALAM", "si": "SINHALA", "th": "THAI", "lo": "LAO", "my": "MYANMAR",
    "km": "KHMER", "am": "ETHIOPIC", "ka": "GEORGIAN", "hy": "ARMENIAN", "el": "GREEK",
    "ru": "CYRILLIC", "uk": "CYRILLIC", "be": "CYRILLIC", "bg": "CYRILLIC", "mk": "CYRILLIC",
    "sr": "CYRILLIC", "kk": "CYRILLIC", "ky": "CYRILLIC", "tg": "CYRILLIC", "mn": "CYRILLIC",
    "ja": "CJK HIRAGANA KATAKANA KATAKANA-HIRAGANA", "zh-rCN": "CJK", "zh-rTW": "CJK", "zh-rHK": "CJK",
    "ko": "HANGUL CJK",
}
NEUTRAL = {"MODIFIER", "MASCULINE", "FEMININE", "COMBINING"}
ALWAYS = {"LATIN"} | NEUTRAL

def script(ch):
    return unicodedata.name(ch, "?").split()[0]

def stray_letters(qualifier, text):
    """Letters from a script the language isn't written in, and words that mix two scripts (a
    Latin i in a Cyrillic word looks right and isn't). CJK runs words together, so only the first."""
    allowed = ALWAYS | set(SCRIPTS.get(qualifier, "").split())
    text = PLACEHOLDER.sub(" ", text or "")
    stray = {ch for ch in text if unicodedata.category(ch)[0] in "LM" and script(ch) not in allowed}
    if not qualifier.startswith(("ja", "zh", "ko")):
        for word in re.findall(r"\w+", text):
            if len({script(ch) for ch in word if unicodedata.category(ch)[0] == "L"} - NEUTRAL) > 1:
                stray |= set(word)
    return sorted(stray)

def load(path):
    root = ET.parse(path).getroot()
    entries = {}
    for el in root:
        name = el.get("name")
        if el.tag == "string":
            entries[name] = ("string", el.get("translatable") == "false", "".join(el.itertext()), None)
        elif el.tag == "plurals":
            items = {item.get("quantity"): "".join(item.itertext()) for item in el}
            entries[name] = ("plurals", el.get("translatable") == "false", None, items)
    return entries

def raw_texts(path):
    """Each entry's text as written in the file, before XML unescaping, for the apostrophe check."""
    text = path.read_text(encoding="utf-8")
    return re.findall(r'<(?:string|item)[^>]*>(.*?)</(?:string|item)>', text, re.S)

def placeholders(text):
    return sorted(m.group(0) for m in PLACEHOLDER.finditer(text or ""))

def check(qualifier, base):
    path = RES / f"values-{qualifier}" / "strings.xml"
    if not path.exists():
        return [f"{qualifier}: no {path.relative_to(RES.parents[2])}"], []
    errors, missing = [], []
    try:
        entries = load(path)
    except ET.ParseError as e:
        return [f"{qualifier}: not valid XML: {e}"], []
    for raw in raw_texts(path):
        stripped = raw.strip()
        if stripped.startswith('"') and stripped.endswith('"'):
            continue
        if re.search(r"(?<!\\)(?:\\\\)*'", raw):
            errors.append(f"{qualifier}: unescaped apostrophe (write \\'): {raw[:60]!r}")
        if re.search(r'(?<!\\)(?:\\\\)*"', raw):
            errors.append(f"{qualifier}: unescaped double quote (write \\\"): {raw[:60]!r}")
        if stripped[:1] in ("@", "?"):
            errors.append(f"{qualifier}: starts with {stripped[0]} (write \\{stripped[0]}): {raw[:60]!r}")
    for name, (kind, _, text, items) in entries.items():
        if name not in base:
            errors.append(f"{qualifier}: {name} isn't in values/strings.xml")
            continue
        bkind, untranslatable, btext, bitems = base[name]
        if untranslatable:
            errors.append(f"{qualifier}: {name} is translatable=false; leave it out")
            continue
        if kind != bkind:
            errors.append(f"{qualifier}: {name} is a {kind}, should be a {bkind}")
            continue
        if kind == "string":
            if placeholders(text) != placeholders(btext):
                errors.append(f"{qualifier}: {name} placeholders {placeholders(text)} != {placeholders(btext)}")
            if not (text or "").strip():
                errors.append(f"{qualifier}: {name} is empty")
            if stray := stray_letters(qualifier, text):
                errors.append(f"{qualifier}: {name} has letters from another script: {''.join(stray)} in {text!r}")
        else:
            if "other" not in items:
                errors.append(f"{qualifier}: {name} has no quantity=\"other\"")
            want = placeholders(bitems["other"])
            for q, t in items.items():
                if q not in ("zero", "one", "two", "few", "many", "other"):
                    errors.append(f"{qualifier}: {name} has unknown quantity {q}")
                elif stray := stray_letters(qualifier, t):
                    errors.append(f"{qualifier}: {name}[{q}] has letters from another script: {''.join(stray)}")
                elif placeholders(t) != want and not (q in ("zero", "one", "two") and placeholders(t) == []):
                    errors.append(f"{qualifier}: {name}[{q}] placeholders {placeholders(t)} != {want}")
    for name, (_, untranslatable, _, _) in base.items():
        if not untranslatable and name not in entries:
            missing.append(name)
    return errors, missing

def missing_xml(qualifier):
    base_path = RES / "values/strings.xml"
    base = load(base_path)
    _, missing = check(qualifier, base) if (RES / f"values-{qualifier}/strings.xml").exists() else ([], [n for n, e in base.items() if not e[1]])
    text = base_path.read_text(encoding="utf-8")
    out = []
    for name in missing:
        m = re.search(r'\s*<(string|plurals) name="%s"[^>]*>.*?</\1>' % re.escape(name), text, re.S)
        if m:
            out.append(m.group(0).strip("\n"))
    print("\n".join(out))

def main(args):
    if args[:1] == ["--missing"]:
        missing_xml(args[1])
        return 0
    base = load(RES / "values/strings.xml")
    qualifiers = args or sorted(p.parent.name[len("values-"):] for p in RES.glob("values-*/strings.xml"))
    bad = 0
    for q in qualifiers:
        errors, missing = check(q, base)
        for e in errors:
            print("ERROR", e)
        if missing:
            print(f"MISSING {q}: {len(missing)} ({', '.join(missing[:8])}{'…' if len(missing) > 8 else ''})")
        if not errors and not missing:
            print(f"ok {q}")
        bad += bool(errors or missing)
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
