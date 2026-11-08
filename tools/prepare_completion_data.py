"""Pinned offline conversion/completion data. Network only with explicit --fetch."""
import argparse
import hashlib
import json
import re
import shutil
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
SOURCES = ROOT / 'third_party'
OPENCC = '556ed22496d650bd0b13b6c163be9814637970ae'  # ver.1.1.9
WORDS = '525f9b560de45753a5ea01069454e72e9aa541c6'
URLS = {
    'STCharacters.txt': f'https://raw.githubusercontent.com/BYVoid/OpenCC/{OPENCC}/data/dictionary/STCharacters.txt',
    'STPhrases.txt': f'https://raw.githubusercontent.com/BYVoid/OpenCC/{OPENCC}/data/dictionary/STPhrases.txt',
    'OPENCC-LICENSE.txt': f'https://raw.githubusercontent.com/BYVoid/OpenCC/{OPENCC}/LICENSE',
    'OPENCC-AUTHORS.txt': f'https://raw.githubusercontent.com/BYVoid/OpenCC/{OPENCC}/AUTHORS',
    'en_50k.txt': f'https://raw.githubusercontent.com/hermitdave/FrequencyWords/{WORDS}/content/2018/en/en_50k.txt',
    'FREQUENCYWORDS-README.md': f'https://raw.githubusercontent.com/hermitdave/FrequencyWords/{WORDS}/README.md',
    'CC-BY-SA-4.0.txt': 'https://raw.githubusercontent.com/creativecommons/creativecommons.org/main/docroot/legalcode/by-sa_4.0.txt',
}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--fetch', action='store_true')
    args = parser.parse_args()
    lock_path = ROOT / 'resources.lock.json'
    lock = json.loads(lock_path.read_text(encoding='utf-8'))
    for name, url in URLS.items():
        path = SOURCES / name
        if args.fetch and not path.exists():
            with urllib.request.urlopen(url, timeout=60) as response:
                path.write_bytes(response.read())
        expected = lock['sources'].get(name)
        checksum = digest(path)
        if expected:
            assert expected['sha256'] == checksum, name
        else:
            assert args.fetch, f'Missing pinned checksum: {name}'
            lock['sources'][name] = {'url': url, 'sha256': checksum}
    lock['revisions'].update({'BYVoid/OpenCC': OPENCC, 'hermitdave/FrequencyWords': WORDS})
    lock_path.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    mappings = {}
    for name in ['STCharacters.txt', 'STPhrases.txt']:
        for line in (SOURCES / name).read_text(encoding='utf-8-sig').splitlines():
            if not line or line.startswith('#'):
                continue
            key, values = line.split('\t')
            mappings[key] = list(dict.fromkeys(values.split(' ')))
    (ASSETS / 'traditional.tsv').write_text(''.join(k + '\t' + ' '.join(v) + '\n' for k, v in sorted(mappings.items())), encoding='utf-8')
    words = {}
    for line in (SOURCES / 'en_50k.txt').read_text(encoding='utf-8').splitlines():
        word, frequency = line.rsplit(' ', 1)
        if re.fullmatch('[a-z]{1,32}', word):
            words[word] = max(int(frequency), words.get(word, 0))
    ordered = sorted(words, key=lambda w: (-words[w], w))
    (ASSETS / 'english.txt').write_text('\n'.join(ordered) + '\n', encoding='utf-8')
    for name in ['OPENCC-LICENSE.txt', 'OPENCC-AUTHORS.txt', 'CC-BY-SA-4.0.txt', 'FREQUENCYWORDS-README.md']:
        shutil.copyfile(SOURCES / name, ASSETS / 'licenses' / name)
    info_path = ASSETS / 'data_info.json'
    info = json.loads(info_path.read_text(encoding='utf-8'))
    info.update({'traditional_mappings': len(mappings), 'english_words': len(ordered), 'revisions': lock['revisions']})
    for name in ['traditional.tsv', 'english.txt']:
        info['assets'][name] = digest(ASSETS / name)
    info_path.write_text(json.dumps(info, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Prepared {len(mappings)} conversion mappings; {len(ordered)} English words')

if __name__ == '__main__':
    main()
