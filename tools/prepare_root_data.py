"""Build the offline Wubi86 root-card font subset. Requires fonttools==4.55.8.

Pass --source-font for the original UMingCN-wubi-86-kb.ttf; it is never downloaded
by the build. Its URL and SHA-256 are recorded in resources.lock.json.
"""
import argparse
import hashlib
import json
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-font", type=Path, required=True)
    args = parser.parse_args()
    lock = json.loads((ROOT / "resources.lock.json").read_text(encoding="utf-8"))
    expected = lock["wubi_root_font"]["sha256"]
    assert hashlib.sha256(args.source_font.read_bytes()).hexdigest() == expected, "Unexpected source font"
    source = ROOT / "third_party/wubi_roots/keys.json"
    data = json.loads(source.read_text(encoding="utf-8"))
    assert set(data["keys"]) == set("abcdefghijklmnopqrstuvwxy")
    characters = {ord(char) for roots in data["keys"].values() for char in roots}
    font = TTFont(args.source_font, recalcTimestamp=False)
    assert characters <= font.getBestCmap().keys(), "A root shape is missing"
    options = subset.Options()
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.recalc_timestamp = False
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=characters)
    subsetter.subset(font)
    notice = "Modified 2026-11-08 for fansonIME: subset to Wubi86 root glyphs only; renamed Fanson Wubi Roots. Glyph outlines unchanged. Distributed under ARPHIC PUBLIC LICENSE."
    names = {1: "Fanson Wubi Roots", 2: "Regular", 3: "FansonWubiRoots-0.1.8", 4: "Fanson Wubi Roots", 6: "FansonWubiRoots", 10: notice, 16: "Fanson Wubi Roots", 17: "Regular"}
    for name_id, value in names.items():
        font["name"].removeNames(nameID=name_id)
        font["name"].setName(value, name_id, 3, 1, 0x409)
    output = ASSETS / "fonts/fanson_wubi_roots.ttf"
    output.parent.mkdir(parents=True, exist_ok=True)
    font.save(output)
    (ASSETS / "wubi_roots.json").write_bytes(source.read_bytes())
    for name in ["ARPHICPL.TXT", "FONT-COPYRIGHT.txt", "UPSTREAM-FONT-CHANGES.adoc"]:
        (ASSETS / "licenses" / name).write_bytes((ROOT / "third_party/wubi_roots" / name).read_bytes())
    info_path = ASSETS / "data_info.json"
    info = json.loads(info_path.read_text(encoding="utf-8"))
    info["revisions"] = lock["revisions"]
    for name in ["fonts/fanson_wubi_roots.ttf", "wubi_roots.json"]:
        info["assets"][name] = hashlib.sha256((ASSETS / name).read_bytes()).hexdigest()
    info["wubi_root_forms"] = sum(map(len, data["keys"].values()))
    info_path.write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Built 25 keys, {info['wubi_root_forms']} root forms, {output.stat().st_size} font bytes")


if __name__ == "__main__":
    main()
