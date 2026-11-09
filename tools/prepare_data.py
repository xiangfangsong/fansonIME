"""Fetch pinned upstream text assets once, then build offline runtime resources.

Normal subsequent runs are offline. Delete resources.lock.json intentionally to
select a new upstream revision; keep the source files and notices with the app.
"""
import hashlib
import json
import re
import sqlite3
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / "third_party"
ASSETS = ROOT / "app/src/main/assets"
LOCK = ROOT / "resources.lock.json"


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "FansonIME-build/0.1"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def revision(repo, ref):
    return json.loads(download(f"https://api.github.com/repos/{repo}/commits/{ref}"))["sha"]


def prepare():
    VENDOR.mkdir(exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    if LOCK.exists():
        lock = json.loads(LOCK.read_text(encoding="utf-8"))
    else:
        rime = revision("rime/rime-wubi", "master")
        cldr = revision("unicode-org/cldr", "release-46")
        lock = {"revisions": {"rime/rime-wubi": rime, "unicode-org/cldr": cldr}, "sources": {}}
        urls = {
            "wubi86.dict.yaml": f"https://raw.githubusercontent.com/rime/rime-wubi/{rime}/wubi86.dict.yaml",
            "RIME-LGPL-3.0.txt": f"https://raw.githubusercontent.com/rime/rime-wubi/{rime}/LICENSE",
            "RIME-AUTHORS.txt": f"https://raw.githubusercontent.com/rime/rime-wubi/{rime}/AUTHORS",
            "GPL-3.0.txt": "https://www.gnu.org/licenses/gpl-3.0.txt",
            "emoji-test.txt": "https://www.unicode.org/Public/emoji/16.0/emoji-test.txt",
            "annotations-zh.xml": f"https://raw.githubusercontent.com/unicode-org/cldr/{cldr}/common/annotations/zh.xml",
            "annotations-derived-zh.xml": f"https://raw.githubusercontent.com/unicode-org/cldr/{cldr}/common/annotationsDerived/zh.xml",
            "UNICODE-LICENSE.txt": f"https://raw.githubusercontent.com/unicode-org/cldr/{cldr}/LICENSE",
        }
        for name, url in urls.items():
            payload = download(url)
            (VENDOR / name).write_bytes(payload)
            lock["sources"][name] = {"url": url, "sha256": hashlib.sha256(payload).hexdigest()}
            print(f"Fetched {name}: {len(payload)} bytes", flush=True)
        LOCK.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for name, source in lock["sources"].items():
        path = VENDOR / name
        if not path.exists():
            path.write_bytes(download(source["url"]))
        assert hashlib.sha256(path.read_bytes()).hexdigest() == source["sha256"], name

    db_path = ASSETS / "wubi86.db"
    if db_path.exists():
        db_path.unlink()
    db = sqlite3.connect(db_path)
    db.executescript("CREATE TABLE entries(code TEXT NOT NULL, text TEXT NOT NULL, weight INTEGER NOT NULL, stem TEXT NOT NULL, PRIMARY KEY(code,text));")
    rows = []
    in_data = False
    for line in (VENDOR / "wubi86.dict.yaml").read_text(encoding="utf-8-sig").splitlines():
        if line.strip() == "...":
            in_data = True
            continue
        if not in_data or not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) < 2 or not re.fullmatch("[a-y]{1,4}", fields[1]):
            continue
        text, code = fields[:2]
        weight = int(fields[2]) if len(fields) > 2 and fields[2].isdigit() else 0
        stem = fields[3] if len(fields) > 3 else ""
        rows.append((code, text, weight, stem))
    db.executemany("INSERT OR IGNORE INTO entries VALUES(?,?,?,?)", rows)
    db.execute("CREATE INDEX entries_order ON entries(code,weight DESC,text)")
    db.execute("PRAGMA user_version=1")
    db.commit()
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    for code, text in [("a", "工"), ("b", "了"), ("w", "人"), ("r", "的")]:
        assert db.execute("SELECT text FROM entries WHERE code=? ORDER BY weight DESC,text LIMIT 1", (code,)).fetchone()[0] == text
    count = db.execute("SELECT count(*) FROM entries").fetchone()[0]
    assert count > 50000
    db.close()

    annotations = {}
    for name in ("annotations-zh.xml", "annotations-derived-zh.xml"):
        for node in ET.parse(VENDOR / name).iter("annotation"):
            key = node.attrib["cp"].replace("\ufe0f", "")
            values = annotations.setdefault(key, [])
            if node.attrib.get("type") == "tts":
                values.insert(0, "".join(node.itertext()))
            else:
                values.append("".join(node.itertext()))
    group = ""
    emojis = []
    for line in (VENDOR / "emoji-test.txt").read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("# group: "):
            group = line.removeprefix("# group: ")
        if "; fully-qualified" not in line:
            continue
        codepoints, rest = line.split(";", 1)
        sequence = "".join(chr(int(cp, 16)) for cp in codepoints.split())
        english = re.sub(r"^.*? E[0-9.]+ ", "", rest).strip()
        keywords = " | ".join(annotations.get(sequence.replace("\ufe0f", ""), []))
        # Base emoji appear in the grid; modifier variants are available by long press.
        variant = any(0x1F3FB <= ord(char) <= 0x1F3FF for char in sequence)
        emojis.append({"text": sequence, "group": group, "name": keywords.split(" | ")[0] or english,
                       "keywords": keywords + " | " + english, "variant": variant})
    (ASSETS / "emoji.json").write_text(json.dumps(emojis, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    licenses = ASSETS / "licenses"
    licenses.mkdir(exist_ok=True)
    for name in ("RIME-LGPL-3.0.txt", "RIME-AUTHORS.txt", "GPL-3.0.txt", "UNICODE-LICENSE.txt"):
        (licenses / name).write_bytes((VENDOR / name).read_bytes())
    info_path = ASSETS / "data_info.json"
    metadata = json.loads(info_path.read_text(encoding="utf-8")) if info_path.exists() else {}
    metadata.update({"wubi_entries": count, "emoji_sequences": len(emojis), "revisions": lock["revisions"]})
    metadata.setdefault("assets", {}).update({name: hashlib.sha256((ASSETS / name).read_bytes()).hexdigest() for name in ("wubi86.db", "emoji.json")})
    (ASSETS / "data_info.json").write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(metadata, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    prepare()
