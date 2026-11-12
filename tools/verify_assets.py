"""Offline integrity and privacy checks. Does not contact any connected phone."""
import hashlib
import gzip
import json
import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
info = json.loads((ASSETS / "data_info.json").read_text(encoding="utf-8"))
for name, expected in info["assets"].items():
    assert hashlib.sha256((ASSETS / name).read_bytes()).hexdigest() == expected, name
db = sqlite3.connect(f"file:{(ASSETS / 'wubi86.db').as_posix()}?mode=ro", uri=True)
assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
assert db.execute("SELECT count(*) FROM entries").fetchone()[0] == info["wubi_entries"]
assert db.execute("SELECT max(weight) FROM entries").fetchone()[0] > 2**31
for code, text in {"a": "工", "b": "了", "c": "以", "d": "在", "e": "有", "f": "地", "g": "一", "h": "上", "i": "不", "j": "是", "k": "中", "l": "国", "m": "同", "n": "民", "o": "为", "p": "这", "q": "我", "r": "的", "s": "要", "t": "和", "u": "产", "v": "发", "w": "人", "x": "经", "y": "主"}.items():
    assert db.execute("SELECT text FROM entries WHERE code=? ORDER BY weight DESC,text LIMIT 1", (code,)).fetchone()[0] == text, code
assert not db.execute("SELECT code FROM entries WHERE length(code)>4 OR code GLOB '*[^a-y]*' LIMIT 1").fetchall()
db.close()
readings_db = sqlite3.connect(f"file:{(ASSETS / 'pronunciation.db').as_posix()}?mode=ro", uri=True)
assert readings_db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
assert readings_db.execute('SELECT count(*) FROM readings').fetchone()[0] == info['pronunciation']['characters'] + info['pronunciation']['phrases']
readings = lambda text: readings_db.execute('SELECT pronunciations FROM readings WHERE text=?', (text,)).fetchone()[0].split('\n')
assert {'zhòng', 'chóng'} <= set(readings('重'))
assert {'háng', 'xíng'} <= set(readings('行'))
assert readings('银行') == ['yín háng']
assert readings('重庆') == ['chóng qìng']
assert readings('銀行') == ['yín háng']
assert readings('重慶') == ['chóng qìng']
assert readings('𠮟') == ['chì']
readings_db.close()
lock = json.loads((ROOT / 'resources.lock.json').read_text(encoding='utf-8'))
for name, entry in lock['sources'].items():
    data = (ROOT / 'third_party' / name).read_bytes()
    assert hashlib.sha256(data).hexdigest() == entry['sha256'], name
    if entry.get('compression') == 'gzip':
        assert hashlib.sha256(gzip.decompress(data)).hexdigest() == entry['original_sha256'], name
with sqlite3.connect(f"file:{(ASSETS / 'pinyin.db').as_posix()}?mode=ro", uri=True) as pinyin:
    assert pinyin.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    assert pinyin.execute('SELECT count(*) FROM entries').fetchone()[0] == info['pinyin']['entries']
    assert pinyin.execute('SELECT count(DISTINCT text) FROM entries WHERE length(text)=1').fetchone()[0] == info['pinyin']['characters']
    assert pinyin.execute('SELECT count(*) FROM completions').fetchone()[0] == info['pinyin']['completion_rows']
    for code, text in {'ni': '你', 'weixin': '微信', 'zhifubao': '支付宝', 'xiaochengxu': '小程序', 'erweima': '二维码', 'nver': '女儿'}.items():
        assert pinyin.execute('SELECT text FROM entries WHERE code=? ORDER BY weight DESC,text,syllables LIMIT 1', (code,)).fetchone()[0] == text, code
    assert pinyin.execute("SELECT count(DISTINCT text) FROM entries WHERE source='thuocl'").fetchone()[0] == info['pinyin']['thuocl_supplemented_words']
    assert info['pinyin']['canonical_entries'] + info['pinyin']['spelling_alias_entries'] == info['pinyin']['entries']
    assert not pinyin.execute("SELECT 1 FROM entries WHERE code GLOB '*[^a-z]*' OR length(text)>8 LIMIT 1").fetchall()
for source_name, packaged in [('wanxiang/LICENSE', 'PINYIN-CC-BY-4.0.txt'), ('wanxiang/LICENSE-SCOPE.md', 'PINYIN-LICENSE-SCOPE.md'),
                              ('wanxiang/NOTICE.md', 'PINYIN-UPSTREAM-NOTICE.md'), ('thuocl/LICENSE', 'THUOCL-MIT.txt'), ('NOTICE.txt', 'PINYIN-NOTICE.txt')]:
    assert (ROOT / 'third_party/pinyin' / source_name).read_bytes() == (ASSETS / 'licenses' / packaged).read_bytes(), packaged
words = (ASSETS / 'english.txt').read_text(encoding='utf-8').splitlines()
roots = json.loads((ASSETS / 'wubi_roots.json').read_text(encoding='utf-8'))
assert set(roots['keys']) == set('abcdefghijklmnopqrstuvwxy')
assert all(roots['keys'][key] for key in roots['keys'])
assert sum(map(len, roots['keys'].values())) == info['wubi_root_forms'] == 262
original_roots = json.loads((ROOT / 'third_party/wubi_roots/keys.json').read_text(encoding='utf-8'))
corrections = json.loads((ROOT / 'tools/wubi_root_corrections.json').read_text(encoding='utf-8'))
assert set(corrections['keys']) == set('blop')
assert roots['local_corrections'] == corrections
assert roots['sources'] == original_roots['sources']
for key, values in original_roots['keys'].items():
    assert roots['keys'][key] == corrections['keys'].get(key, values), key
assert {'田', '力', '車'} <= set(roots['keys']['l'])
assert '火' in roots['keys']['o'] and '耳' in roots['keys']['b']
assert '\ue000' in roots['keys']['p']
assert not {'礻', '衤'} & set(roots['keys']['p'])
assert all('{' not in values and '}' not in values for values in roots['keys'].values())
assert (ASSETS / 'fonts/fanson_wubi_roots.ttf').stat().st_size < 200000
assert len(words) == info['english_words'] and len(set(words)) == len(words)
assert all(word.isascii() and word.isalpha() and word.islower() for word in words)
assert {'apple', 'application', 'apply'} <= set(words)
mapping = dict(line.split('\t') for line in (ASSETS / 'traditional.tsv').read_text(encoding='utf-8').splitlines())
assert len(mapping) == info['traditional_mappings']
assert {'發', '髮'} <= set(mapping['发'].split())
assert mapping['头发'] == '頭髮' and mapping['发展'] == '發展'
emoji = json.loads((ASSETS / "emoji.json").read_text(encoding="utf-8"))
assert len(emoji) == info["emoji_sequences"]
assert len({entry["text"] for entry in emoji}) == len(emoji)
for value in ["😀", "👍🏽", "👨‍👩‍👧‍👦", "🇨🇳", "❤️"]:
    assert any(entry["text"] == value for entry in emoji), value
assert any("笑" in entry["keywords"] for entry in emoji)
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
permissions = {node.attrib[ns + "name"] for node in manifest.findall("uses-permission")}
assert permissions == {"android.permission.VIBRATE"}, permissions
assert manifest.find("application").attrib[ns + "allowBackup"] == "false"
for service in manifest.findall("application/service"):
    assert service.attrib[ns + "permission"] == "android.permission.BIND_INPUT_METHOD"
for mode in ET.parse(ROOT / "app/src/main/res/xml/data_extraction_rules.xml").getroot():
    domains = {node.attrib["domain"] for node in mode.findall("exclude")}
    assert {"root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"} <= domains
print(f"PASS: {info['wubi_entries']} Wubi entries; {info['pinyin']['entries']} Pinyin index entries; 25 abbreviated-code preferences; {len(emoji)} emoji sequences; checksums; licenses; privacy manifest; backup exclusions")
