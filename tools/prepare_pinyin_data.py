"""Build pinned offline Pinyin data. Original source data keeps its own license.

No downloads and no third-party program code. Large original YAML is stored as
deterministic gzip; decompression reproduces the upstream bytes exactly.
"""
import gzip
import hashlib
import heapq
import itertools
import json
import re
import shutil
import sqlite3
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
LOCK_PATH = ROOT / 'resources.lock.json'
lock = json.loads(LOCK_PATH.read_text(encoding='utf-8'))


def source(name):
    key = 'pinyin/' + name
    data = (ROOT / 'third_party' / key).read_bytes()
    record = lock['sources'][key]
    assert hashlib.sha256(data).hexdigest() == record['sha256'], key
    if name.endswith('.gz'):
        data = gzip.decompress(data)
        assert hashlib.sha256(data).hexdigest() == record['original_sha256'], key
    return data.decode('utf-8-sig')


def normalize(value):
    value = unicodedata.normalize('NFD', value.lower()).replace('u\u0308', 'v')
    value = ''.join(c for c in value if 'a' <= c <= 'z')
    for old, new in [('lve', 'lue'), ('nve', 'nue'), ('jv', 'ju'), ('qv', 'qu'), ('xv', 'xu')]:
        value = value.replace(old, new)
    return value


def han(text):
    return all(0x3400 <= ord(c) <= 0x9fff or 0x20000 <= ord(c) <= 0x323af for c in text)


entries = {}
counts = {}
for filename in ['zi.dict.yaml', 'jichu.dict.yaml.gz']:
    count = 0
    for line in source('wanxiang/' + filename).splitlines():
        fields = line.split('\t')
        if len(fields) != 3 or not fields[2].isdigit():
            continue
        text, phonetic, score = fields
        if not 1 <= len(text) <= 8 or not han(text):
            continue
        syllables = ' '.join(normalize(s) for s in phonetic.split())
        if len(syllables.split()) != len(text) or not re.fullmatch(r'[a-z]+(?: [a-z]+)*', syllables):
            continue
        key = (syllables.replace(' ', ''), text, syllables)
        weight = int(score)
        if key not in entries or entries[key][0] < weight:
            entries[key] = (weight, 'wanxiang')
        count += 1
    counts[filename] = count

# All characters remain searchable. Bound the common-word resource by frequency,
# not file order; no phrase probability model or sentence corpus is bundled.
characters = {key: value for key, value in entries.items() if len(key[1]) == 1}
words = sorted(((key, value) for key, value in entries.items() if len(key[1]) > 1),
               key=lambda row: (-row[1][0], row[0]))[:250000]
entries = dict(words)
entries.update(characters)

# Only whole-phrase readings already independently present in the reviewed MIT
# reading resource can annotate THUOCL terms. Never guess polyphonic combinations.
readings = {}
reading_path = 'pronunciation/phrase-pinyin-data/pinyin.txt'
reading_bytes = (ROOT / 'third_party' / reading_path).read_bytes()
assert hashlib.sha256(reading_bytes).hexdigest() == lock['sources'][reading_path]['sha256']
for line in reading_bytes.decode('utf-8').splitlines():
    if line.startswith('#') or ': ' not in line:
        continue
    text, phonetic = line.split(': ', 1)
    readings[text] = phonetic.split('#', 1)[0].strip()
known = {key[1] for key in entries}
supplemented = 0
for line in source('thuocl/THUOCL_IT.txt').splitlines():
    fields = line.split()
    if len(fields) != 2:
        continue
    text = fields[0]
    if text in known or not 2 <= len(text) <= 6 or not han(text) or text not in readings:
        continue
    syllables = ' '.join(normalize(s) for s in readings[text].split())
    if len(syllables.split()) != len(text):
        continue
    # THUOCL's domain DF is not comparable with the input lexicon's weights.
    # Missing domain terms are searchable with conservative fallback priority.
    entries[(syllables.replace(' ', ''), text, syllables)] = (1, 'thuocl')
    known.add(text)
    supplemented += 1

# Expand spelling variants per syllable in the index. Whole-string replacement
# would corrupt nv+er (女儿) into nuer, or miss mixed separated/unseparated words.
canonical_entries = len(entries)
for (code, text, syllables), value in list(entries.items()):
    variants = []
    for part in syllables.split():
        alternate = part.replace('lue', 'lve').replace('nue', 'nve')
        if part.startswith(('ju', 'qu', 'xu')):
            alternate = part[0] + 'v' + part[2:]
        variants.append([part] if alternate == part else [part, alternate])
    for parts in itertools.product(*variants):
        key = (''.join(parts), text, syllables)
        if key not in entries or entries[key][0] < value[0]:
            entries[key] = value

path = ASSETS / 'pinyin.db'
if path.exists():
    path.unlink()
with sqlite3.connect(path) as db:
    db.executescript('''
        PRAGMA page_size=4096;
        PRAGMA user_version=1;
        CREATE TABLE entries(code TEXT NOT NULL,text TEXT NOT NULL,syllables TEXT NOT NULL,
            weight INTEGER NOT NULL,source TEXT NOT NULL,PRIMARY KEY(code,text,syllables));
        CREATE INDEX entries_rank ON entries(code,weight DESC,text,syllables);
        CREATE TABLE completions(prefix TEXT NOT NULL,code TEXT NOT NULL,text TEXT NOT NULL,
            syllables TEXT NOT NULL,weight INTEGER NOT NULL,PRIMARY KEY(prefix,code,text,syllables));
        CREATE INDEX completions_rank ON completions(prefix,weight DESC,text,code);
    ''')
    rows = [(key[0], key[1], key[2], value[0], value[1]) for key, value in sorted(entries.items())]
    db.executemany('INSERT INTO entries VALUES(?,?,?,?,?)', rows)
    # Only 1-3 letter prefixes need precomputed bounded rankings. Longer ranges
    # are selective enough for the indexed query and a bounded completion list.
    prefixes = defaultdict(list)
    for code, text, syllables, weight, _ in rows:
        for length in range(1, min(3, len(code) - 1) + 1):
            prefixes[code[:length]].append((weight, text, code, syllables))
    prefix_rows = []
    for prefix, values in sorted(prefixes.items()):
        rank = lambda row: (-row[0], row[1], row[2], row[3])
        unique = {}
        for row in values:
            if row[1] not in unique or rank(row) < rank(unique[row[1]]):
                unique[row[1]] = row
        best = heapq.nsmallest(64, unique.values(), key=rank)
        prefix_rows.extend((prefix, code, text, syllables, weight) for weight, text, code, syllables in best)
    db.executemany('INSERT INTO completions VALUES(?,?,?,?,?)', prefix_rows)
    db.commit()
    db.execute('VACUUM')
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'

audit = {'entries': len(entries), 'characters': len({key[1] for key in entries if len(key[1]) == 1}),
         'words': len({key[1] for key in entries if len(key[1]) > 1}),
         'raw_valid_rows': counts, 'thuocl_supplemented_words': supplemented,
         'canonical_entries': canonical_entries, 'spelling_alias_entries': len(entries) - canonical_entries,
         'word_entry_limit': 250000, 'max_word_characters': 8, 'completion_rows': len(prefix_rows)}
(ROOT / 'third_party/pinyin/coverage.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
info_path = ASSETS / 'data_info.json'
info = json.loads(info_path.read_text(encoding='utf-8'))
info['pinyin'] = audit
info['assets']['pinyin.db'] = hashlib.sha256(path.read_bytes()).hexdigest()
for repo in ['amzxyz/rime-wanxiang-data', 'thunlp/THUOCL']:
    info['revisions'][repo] = lock['revisions'][repo]
info_path.write_text(json.dumps(info, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
licenses = ASSETS / 'licenses'
shutil.copyfile(ROOT / 'third_party/pinyin/NOTICE.txt', licenses / 'PINYIN-NOTICE.txt')
for original, target in [('wanxiang/LICENSE', 'PINYIN-CC-BY-4.0.txt'),
                         ('wanxiang/LICENSE-SCOPE.md', 'PINYIN-LICENSE-SCOPE.md'),
                         ('wanxiang/NOTICE.md', 'PINYIN-UPSTREAM-NOTICE.md'),
                         ('thuocl/LICENSE', 'THUOCL-MIT.txt')]:
    shutil.copyfile(ROOT / 'third_party/pinyin' / original, licenses / target)
print(json.dumps(audit, ensure_ascii=False))
print(f'pinyin.db: {path.stat().st_size} bytes')
