"""FontTools-only rebuild QA: python tools/verify_root_font.py --source-font <font>.

The regular application build does not require FontTools. Compare every existing
root against the pinned full font, including the 21 keys outside this correction.
"""
import argparse
import hashlib
import json
from pathlib import Path

from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]


def outline(font, code):
    glyphs = font.getGlyphSet()
    pen = DecomposingRecordingPen(glyphs)
    glyphs[font.getBestCmap()[code]].draw(pen)
    return pen.value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-font', type=Path, required=True)
    args = parser.parse_args()
    lock = json.loads((ROOT / 'resources.lock.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(args.source_font.read_bytes()).hexdigest() == lock['wubi_root_font']['sha256']
    source = TTFont(args.source_font)
    result = TTFont(ROOT / 'app/src/main/assets/fonts/fanson_wubi_roots.ttf')
    roots = json.loads((ROOT / 'app/src/main/assets/wubi_roots.json').read_text(encoding='utf-8'))
    codes = {ord(char) for values in roots['keys'].values() for value in values for char in value}
    assert codes <= result.getBestCmap().keys()
    for code in codes - {0xE000}:
        assert outline(source, code) == outline(result, code), hex(code)
        assert source['hmtx'][source.getBestCmap()[code]] == result['hmtx'][result.getBestCmap()[code]], hex(code)
    # The private glyph retains both contours (including the first dot), removes
    # only the connected final dot, and preserves the folded/vertical strokes.
    glyph = result['glyf'][result.getBestCmap()[0xE000]]
    original = source['glyf'][source.getBestCmap()[0x793B]]
    assert list(glyph.coordinates) == list(original.coordinates[:32]) + list(original.coordinates[43:])
    assert list(glyph.flags) == list(original.flags[:32]) + list(original.flags[43:])
    assert glyph.endPtsOfContours == [13, 64]
    assert len(glyph.program.getBytecode()) == 0
    assert outline(result, 0xE000) != outline(source, 0x793B)
    print(f'PASS: {len(codes)} glyphs covered; existing outlines/metrics unchanged; private shiyi root verified')


if __name__ == '__main__':
    main()
