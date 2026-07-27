# -*- coding: utf-8 -*-
"""
국립국어원 표준국어대사전 XML에서 단어별 뜻풀이를 뽑는다.

이전 소스(acidsound/korean_wordlist)는 표제어당 뜻이 하나뿐이었고 하필 희귀한
동음이의어를 골라 뒀다 — '사과'가 沙果(과일)나 謝過(잘못을 빎)가 아니라
赦過(잘못을 용서함)로, '관리'가 管理가 아니라 菅履(엄짚신)로 실리는 식이었다.

이 XML은 동음이의어가 <item> 으로 하나씩 다 들어 있어서, 그중 **실제로 흔히 쓰는
뜻**을 골라낼 수 있다. 빈도 수치는 없으므로 사전이 남긴 흔적으로 가늠한다:
용례가 많이 달렸는가, 방언·옛말·북한어는 아닌가, 전문 분야 용어는 아닌가, 뜻이
여러 갈래로 갈리는가. 흔히 쓰는 말일수록 용례가 붙고 뜻이 갈라진다.

용례(example)는 문학작품·기사에서 따온 것이 있어 저작권이 따로 걸린다.
점수 계산에 개수만 쓰고 **본문에는 절대 싣지 않는다.**

    python tools/extract_stdict.py <XML 폴더> <출력 tsv>
"""
import glob
import io
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

MAX_BYTES = 160

# 표제어에 붙는 표시들: 동형어 번호(사과01), 붙임표(가-계), 구 경계(^)
HOMONYM_NO = re.compile(r'\d+$')
MARKS = str.maketrans('', '', '-^ ')

# 뜻풀이 자체가 다른 표제어로 넘기기만 하는 경우
REDIRECT = re.compile(r'^\s*[=→]')

# 표준 표기가 아닌 갈래. 이런 뜻만 있는 항목은 대표 뜻으로 삼지 않는다.
NONSTANDARD = {'방언', '북한어', '옛말', '비표준어', '은어', '속어'}


def norm(word):
    """<word> 값에서 표제어만 남긴다. 가03 → 가, 가-계 → 가계"""
    w = word.strip().translate(MARKS)
    return HOMONYM_NO.sub('', w)


def text_of(node, tag):
    el = node.find(tag)
    return (el.text or '').strip() if el is not None and el.text else ''


def senses_of(item):
    """<sense_info> 를 모두 모아 온다. pos/comm_pattern 깊이가 들쭉날쭉해서 통째로 훑는다."""
    return item.iter('sense_info')


def score_and_gloss(item):
    """이 동음이의어 항목의 (점수, 뜻풀이) — 쓸 수 없으면 None"""
    senses = list(senses_of(item))
    if not senses:
        return None

    best_def = None
    examples = 0
    usable = 0
    specialist = 0

    for s in senses:
        stype = text_of(s, 'type') or '일반어'
        d = text_of(s, 'definition')
        if not d:
            continue
        n_ex = len(s.findall('.//example'))
        examples += n_ex
        cat = ''
        cat_el = s.find('.//cat')
        if cat_el is not None and cat_el.text:
            cat = cat_el.text.strip()
        if cat and cat != '없음':
            specialist += 1
        if stype in NONSTANDARD:
            continue
        usable += 1
        # 대표로 보여 줄 뜻은 넘겨보기가 아닌 첫 번째 정상 뜻
        if best_def is None and not REDIRECT.match(d):
            best_def = d
    if best_def is None:
        # 넘겨보기밖에 없으면 그거라도 쓴다
        for s in senses:
            d = text_of(s, 'definition')
            if d and text_of(s, 'type') not in NONSTANDARD:
                best_def = d
                break
    if best_def is None:
        return None

    sc = 0
    if usable == 0:
        sc -= 100                      # 방언·옛말뿐인 항목
    sc += min(examples, 12) * 4        # 용례가 붙는다 = 실제로 쓰인다
    sc += min(usable, 6) * 3           # 뜻이 여러 갈래 = 두루 쓰인다
    if specialist and specialist == len(senses):
        sc -= 12                       # 전부 전문 분야 용어
    if REDIRECT.match(best_def):
        sc -= 20
    if '의 잘못' in best_def or '의 옛말' in best_def:
        sc -= 40
    if '의 어근' in best_def or '의 준말' in best_def:
        sc -= 8
    sc += min(len(best_def), 60) // 12

    return sc, best_def


def cut(s, limit=MAX_BYTES):
    """UTF-8 기준 limit 바이트 안으로 자른다. 말줄임표 자리까지 미리 뺀다."""
    b = s.encode('utf-8')
    if len(b) <= limit:
        return s
    b = b[:limit - 3]                 # '…' 이 3바이트다
    while b and (b[-1] & 0xC0) == 0x80:
        b = b[:-1]
    if b and b[-1] >= 0x80:
        b = b[:-1]
    return b.decode('utf-8', 'ignore').rstrip(' .,·/') + '…'


def main():
    xml_dir, out_path = sys.argv[1], sys.argv[2]
    files = sorted(glob.glob(os.path.join(xml_dir, '*.xml')))
    if not files:
        print('XML을 못 찾음: %s' % xml_dir)
        return 1

    best = {}      # 표제어 -> (점수, 한자, 뜻)
    items = 0
    for k, path in enumerate(files, 1):
        for _, item in ET.iterparse(path, events=('end',)):
            if item.tag != 'item':
                continue
            items += 1
            wi = item.find('word_info')
            if wi is not None:
                word = norm(text_of(wi, 'word'))
                if word:
                    got = score_and_gloss(item)
                    if got:
                        sc, gloss = got
                        hanja = ''
                        ol = item.find('.//original_language')
                        if ol is not None and ol.text:
                            h = ol.text.strip()
                            # 한자만 취한다. ＜＜＜용가＞ 같은 출전 표기는 버린다
                            if h and all('一' <= c <= '鿿' for c in h):
                                hanja = h
                        cur = best.get(word)
                        if cur is None or sc > cur[0]:
                            best[word] = (sc, hanja, gloss)
            item.clear()
        if k % 20 == 0 or k == len(files):
            print('  %d/%d 파일 · 항목 %s · 표제어 %s'
                  % (k, len(files), f'{items:,}', f'{len(best):,}'))

    with open(out_path, 'w', encoding='utf-8', newline='\n') as f:
        for w, (sc, hanja, gloss) in best.items():
            line = ('%s %s' % (hanja, gloss)) if hanja else gloss
            f.write('%s\t%s\n' % (w, cut(re.sub(r'\s+', ' ', line).strip())))
    print('저장: %s (표제어 %s개)' % (out_path, f'{len(best):,}'))
    return 0


if __name__ == '__main__':
    sys.exit(main())
