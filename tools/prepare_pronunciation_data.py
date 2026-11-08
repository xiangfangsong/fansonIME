"""Build the pinned offline reading dictionary; never downloads during a build."""
import hashlib
import json
import sqlite3
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
lock = json.loads((ROOT / 'resources.lock.json').read_text(encoding='utf-8'))


def source(name):
    key = 'pronunciation/' + name
    data = (ROOT / 'third_party' / key).read_bytes()
    assert hashlib.sha256(data).hexdigest() == lock['sources'][key]['sha256'], key
    return data.decode('utf-8')


readings = defaultdict(list)
for line in source('pinyin-data/pinyin.txt').splitlines():
    line = line.split('#', 1)[0].strip()
    if not line:
        continue
    code, values = line.split(':', 1)
    text = chr(int(code.removeprefix('U+'), 16))
    for value in values.split(','):
        reading = unicodedata.normalize('NFC', value.strip())
        if reading and reading not in readings[text]:
            readings[text].append(reading)

characters = set(readings)
for line in source('phrase-pinyin-data/pinyin.txt').splitlines():
    line = line.split('#', 1)[0].strip()
    if not line:
        continue
    text, value = line.split(':', 1)
    text = text.strip()
    reading = unicodedata.normalize('NFC', ' '.join(value.split()))
    if len(text) > 1 and reading and reading not in readings[text]:
        readings[text].append(reading)

# Match the existing offline traditional conversion, so 銀行 keeps the phrase
# reading of 银行 rather than falling back to the unrelated readings of 行.
info_path = ASSETS / 'data_info.json'
info = json.loads(info_path.read_text(encoding='utf-8'))
traditional_path = ASSETS / 'traditional.tsv'
assert hashlib.sha256(traditional_path.read_bytes()).hexdigest() == info['assets']['traditional.tsv']
mappings = dict(line.split('\t') for line in traditional_path.read_text(encoding='utf-8').splitlines())
max_length = max(map(len, mappings))


def convert(text):
    if text in mappings:
        return mappings[text].split()
    result = []
    offset = 0
    while offset < len(text):
        for length in range(min(max_length, len(text) - offset), 0, -1):
            key = text[offset:offset + length]
            if key in mappings:
                result.append(mappings[key].split()[0])
                offset += length
                break
        else:
            result.append(text[offset])
            offset += 1
    return [''.join(result)]


original_phrases = {text: values[:] for text, values in readings.items() if len(text) > 1}
for text, values in original_phrases.items():
    for traditional in convert(text):
        if traditional not in original_phrases:
            for reading in values:
                if reading not in readings[traditional]:
                    readings[traditional].append(reading)

path = ASSETS / 'pronunciation.db'
path.unlink(missing_ok=True)
with sqlite3.connect(path) as db:
    db.execute('PRAGMA page_size=4096')
    db.execute('CREATE TABLE readings(text TEXT PRIMARY KEY, pronunciations TEXT NOT NULL) WITHOUT ROWID')
    db.executemany('INSERT INTO readings VALUES(?,?)', ((text, '\n'.join(values)) for text, values in sorted(readings.items())))
    db.execute('PRAGMA user_version=1')
    db.commit()
    db.execute('VACUUM')

with sqlite3.connect(f'file:{(ASSETS / "wubi86.db").as_posix()}?mode=ro', uri=True) as db:
    wubi_characters = {text for text, in db.execute('SELECT DISTINCT text FROM entries') if len(text) == 1}
covered = wubi_characters & characters
audit = {
    'characters': len(characters),
    'phrases': sum(len(text) > 1 for text in readings),
    'source_phrases': len(original_phrases),
    'traditional_phrase_aliases': sum(len(text) > 1 for text in readings) - len(original_phrases),
    'wubi_characters': len(wubi_characters),
    'wubi_characters_with_readings': len(covered),
    'wubi_characters_without_readings': len(wubi_characters - covered),
    'missing_by_block': {},
}
for label, lo, hi in [('Basic', 0x4e00, 0x9fff), ('A', 0x3400, 0x4dbf), ('B', 0x20000, 0x2a6df), ('C', 0x2a700, 0x2b73f), ('D', 0x2b740, 0x2b81f), ('E', 0x2b820, 0x2ceaf)]:
    audit['missing_by_block'][label] = sum(lo <= ord(char) <= hi for char in wubi_characters - covered)
(ROOT / 'third_party/pronunciation/coverage.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
info['assets']['pronunciation.db'] = hashlib.sha256(path.read_bytes()).hexdigest()
info['pronunciation'] = audit
for repo in ['mozillazg/pinyin-data', 'mozillazg/phrase-pinyin-data']:
    info['revisions'][repo] = lock['revisions'][repo]
info_path.write_text(json.dumps(info, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
notice_dir = ASSETS / 'licenses'
for repo in ['pinyin-data', 'phrase-pinyin-data']:
    (notice_dir / (repo + '-MIT.txt')).write_text(source(repo + '/LICENSE'), encoding='utf-8', newline='\n')
print(json.dumps(audit, ensure_ascii=False))
print(f'pronunciation.db: {path.stat().st_size} bytes')
