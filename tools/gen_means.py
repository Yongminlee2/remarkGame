# -*- coding: utf-8 -*-
"""
뜻풀이가 비어 있는 단어를 어근의 뜻에서 규칙으로 만들어 채운다.

사전 원본에는 파생어·합성어의 뜻이 자주 빠져 있다. '가가대소'는 실려 있는데
'가가대소하다'는 없는 식이다. 게임에서는 단어를 낼 때마다 뜻이 뜨는 게 재미의 일부라
빈칸을 남겨 두면 아쉽다. 그래서 어근을 찾아 접미사가 하는 일만 얹어 문장을 만든다.

없는 뜻을 지어내지 않는 것이 원칙이다. 어근을 못 찾으면 더 약한 단서로 후퇴하고,
그마저 없으면 모른다고 밝힌다.

어근을 끝내 못 찾는 낱말(작품에서 따온 이름 따위)은 끄투 낱말표의 품사·분야 꼬리표로
"부사." "《화학》 분야의 명사." 정도라도 채운다. 꼬리표 파일은 아래로 만든다:

    python tools/extract_kkutu_meta.py db.sql tools/kkutu_meta.tsv

    python tools/gen_means.py            # 미리보기(파일 안 씀)
    python tools/gen_means.py --write    # means.bin/means.idx 갱신
"""
import io
import os
import re
import struct
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets')
META = os.path.join(HERE, 'kkutu_meta.tsv')
MAX_BYTES = 160          # 원본 뜻풀이도 이 길이로 잘려 있다
GLOSS_BYTES = 96         # 어근 뜻을 인용할 때 쓰는 길이

# 작품이 아니라 갈래를 가리키는 꼬리표. 문장 만드는 틀이 다르다.
CATEGORY_WORKS = {
    '국내 방송 프로그램', '대한민국 철도역', '라면/과자', '온라인 게임',
    '모바일 게임', '유명인', '외국 영화', '한국 영화',
}

_HANJA_HEAD = re.compile(r'^([㐀-鿿豈-﫿]+)\s*')
_SENSE_NO = re.compile(r'^\d+\.\s*')


# ──────────────────────────── 어근 뜻 다듬기 ────────────────────────────

def split_hanja(gloss):
    """앞머리 한자 표기와 나머지 설명을 갈라 준다."""
    m = _HANJA_HEAD.match(gloss.strip())
    if m:
        return m.group(1), gloss.strip()[m.end():].strip()
    return '', gloss.strip()


def clean(body):
    """설명에서 파생어 문장에 쓸 한 줄만 남긴다."""
    g = body.split('❖')[0]           # 예문 제거
    g = g.split('≒')[0]              # 유의어 목록 제거
    g = _SENSE_NO.sub('', g.strip())  # "1. " 같은 뜻 번호
    g = g.split(' 2.')[0]            # 둘째 뜻부터 잘라 첫 뜻만
    g = re.sub(r'\s+', ' ', g).strip().rstrip('.').strip()
    if g.startswith('='):            # "=엄짚신." 같은 넘겨보기 표제
        g = g[1:].strip()
    return g


def is_useless(g):
    """'○○하다'의 어근. 처럼 그 자체로는 뜻이 안 되는 설명"""
    return (not g) or ('의 어근' in g)


# ──────────────────────────── 조사 고르기 ────────────────────────────

def has_jong(ch):
    return '가' <= ch <= '힣' and (ord(ch) - 0xAC00) % 28 != 0


def eul(w):
    return '을' if has_jong(w[-1]) else '를'


def iga(w):
    return '이' if has_jong(w[-1]) else '가'


def wa(w):
    return '과' if has_jong(w[-1]) else '와'


# ──────────────────────────── 규칙표 ────────────────────────────
#
# 접미사 파생. 긴 접미사가 먼저 와야 '거리다'가 '이다'로 잘못 잡히지 않는다.
# {s}=어근, {eul}/{iga}=어근에 맞춘 조사

VERB_SUFFIX = [
    ('시키다', "'{s}'{eul} 하게 하다"),
    ('스럽다', "'{s}'한 느낌이나 데가 있다"),
    ('거리다', "자꾸 '{s}' 하다"),
    ('뜨리다', "'{s}'{eul} 세게 하다"),
    ('트리다', "'{s}'{eul} 세게 하다"),
    ('어지다', "'{s}'{iga} 되어 가다"),
    ('아지다', "'{s}'{iga} 되어 가다"),
    ('스레', "'{s}'한 느낌이 있게"),
    ('로이', "'{s}'한 느낌이 있게"),
    ('롭다', "'{s}'한 느낌이나 성질이 있다"),
    ('답다', "'{s}'의 성질이나 자격이 있다"),
    ('하다', "'{s}'{eul} 하다"),
    ('되다', "'{s}'{iga} 되다"),
    ('대다', "자꾸 '{s}' 하다"),
    ('지다', "'{s}'{iga} 되다"),
    ('이다', "자꾸 '{s}' 하다"),
    ('히', "'{s}'하게"),
]

# 이름씨 뒤에 붙어 뜻을 더하는 꼬리
NOUN_SUFFIX = [
    ('스러움', "'{s}'한 느낌"),
    ('쟁이', "'{s}'{wa} 관련된 사람을 낮추어 이르는 말"),
    ('뱅이', "'{s}'{wa} 관련된 사람을 낮추어 이르는 말"),
    ('배기', "'{s}'{iga} 있는 것"),
    ('꾸러기', "'{s}'{iga} 많은 사람"),
    ('적', "'{s}'에 관계되거나 그런 성질을 띤 것"),
    ('성', "'{s}'한 성질"),
    ('형', "'{s}'의 모양이나 유형"),
    ('식', "'{s}'의 방식이나 형식"),
    ('학', "'{s}'{eul} 연구하는 학문"),
    ('론', "'{s}'에 대한 이론이나 주장"),
    ('법', "'{s}'에 관한 법이나 방법"),
    ('술', "'{s}'{eul} 다루는 기술"),
    ('료', "'{s}'에 드는 값이나 재료"),
    ('제', "'{s}'에 쓰는 약이나 물질"),
    ('기', "'{s}'{eul} 하는 기계나 도구"),
    ('자', "'{s}'{eul} 하는 사람"),
    ('가', "'{s}'{eul} 전문으로 하는 사람"),
    ('꾼', "'{s}'{eul} 일삼는 사람"),
    ('인', "'{s}'에 속한 사람"),
    ('원', "'{s}'에서 일하는 사람. 또는 그 기관"),
    ('실', "'{s}'{eul} 하는 방"),
    ('소', "'{s}'{eul} 하는 곳"),
    ('장', "'{s}'{eul} 하는 곳. 또는 그 우두머리"),
    ('부', "'{s}'{eul} 맡은 부서나 부분"),
    ('청', "'{s}'{eul} 맡은 관청"),
    ('단', "'{s}'{eul} 위해 모인 집단"),
    ('회', "'{s}'{eul} 위한 모임"),
    ('증', "'{s}'{iga} 나타나는 병이나 증세"),
    ('염', "'{s}'에 생기는 염증"),
    ('균', "'{s}'에 관련된 세균"),
    ('선', "'{s}'{eul} 잇는 선이나 노선"),
    ('점', "'{s}'{eul} 파는 가게. 또는 그 지점"),
    ('비', "'{s}'에 드는 비용"),
    ('율', "'{s}'의 비율"),
    ('량', "'{s}'의 분량"),
    ('력', "'{s}'{eul} 하는 힘"),
    ('감', "'{s}'에서 오는 느낌"),
    ('판', "'{s}' 모양으로 만든 판"),
    ('통', "'{s}'{iga} 아픈 증세"),
    ('어', "'{s}'에서 쓰는 말"),
    ('색', "'{s}'{wa} 같은 빛깔"),
    ('빛', "'{s}'{wa} 같은 빛깔"),
]

# 땅이름·기관 꼬리. 어근 뜻을 몰라도 무엇을 가리키는지는 알려 줄 수 있다.
PLACE_SUFFIX = [
    ('해협', '바다의 좁은 물길 이름'),
    ('반도', '바다로 튀어나온 땅 이름'),
    ('산맥', '산줄기 이름'),
    ('공화국', '나라 이름'),
    ('왕국', '나라 이름'),
    ('제국', '나라 이름'),
    ('열도', '줄지어 늘어선 섬들의 이름'),
    ('군도', '무리 지어 있는 섬들의 이름'),
    ('평야', '넓고 평평한 들 이름'),
    ('고원', '높고 평평한 땅 이름'),
    ('사막', '사막 이름'),
    ('운하', '사람이 판 물길 이름'),
    ('대학교', '대학 이름'),
    ('대학', '대학 이름'),
    ('공항', '공항 이름'),
    ('신전', '신을 모신 건물 이름'),
    ('궁전', '궁전 이름'),
    ('성당', '성당 이름'),
    ('사원', '사원 이름'),
    ('현', '일본의 행정 구역 이름'),
    ('강', '강 이름'),
    ('산', '산 이름'),
    ('섬', '섬 이름'),
    ('호', '호수 이름'),
    ('역', '철도역 이름'),
    ('교', '다리 이름'),
    ('탑', '탑 이름'),
]

# '조각 살리기'에서 이런 말이 잡히면 뜻풀이가 되지 않는다 — 문법 꼬리이기 때문.
TAIL_WORDS = {
    '하다', '되다', '지다', '이다', '대다', '거리다', '시키다', '스럽다', '롭다',
    '답다', '어지다', '아지다', '뜨리다', '트리다', '있다', '없다', '같다', '주다',
    '보다', '가다', '오다', '나다', '내다', '들다', '만들다', '삼다', '치다', '쓰다',
}


def build(words, mean_of, meta):
    """빈 뜻을 채운다. {단어: 새 뜻}과 방법별 집계를 돌려준다."""
    out = {}
    stat = {}

    def note(k):
        stat[k] = stat.get(k, 0) + 1

    def look(w):
        """뜻이 쓸 만하면 (한자, 다듬은 뜻), 아니면 None"""
        raw = mean_of(w)
        if raw is None:
            return None
        hanja, body = split_hanja(raw)
        g = clean(body)
        return None if is_useless(g) else (hanja, g)

    def hanja_only(w):
        """설명은 못 쓰지만 한자 표기라도 있는 경우"""
        raw = mean_of(w)
        if raw is None:
            return ''
        return split_hanja(raw)[0]

    def stem_gloss(stem, self_word):
        """어근 자체 또는 어근의 다른 파생형에서 뜻을 빌린다."""
        for cand in (stem, stem + '하다', stem + '스럽다', stem + '거리다',
                     stem + '대다', stem + '이다', stem + '지다'):
            if cand == self_word:
                continue
            got = look(cand)
            if got:
                return got[1]
        return None

    def say(tpl, stem, gloss):
        head = tpl.format(s=stem, eul=eul(stem), iga=iga(stem), wa=wa(stem))
        return cut('%s. %s: %s' % (head, stem, cut(gloss, GLOSS_BYTES)))

    missing = [w for w in words if mean_of(w) is None]

    # ── 1) 접미사 파생 ──────────────────────────────────────────────
    rest = []
    for w in missing:
        hit = False
        for suf, tpl in VERB_SUFFIX:
            if not w.endswith(suf) or len(w) - len(suf) < 1:
                continue
            stem = w[:-len(suf)]
            g = stem_gloss(stem, w)
            if g:
                out[w] = say(tpl, stem, g)
                note('파생 -' + suf)
                hit = True
            break                     # 접미사는 가장 먼저 걸린 것 하나만
        if not hit:
            rest.append(w)

    # ── 2) 이름씨 꼬리 ─────────────────────────────────────────────
    rest2 = []
    for w in rest:
        hit = False
        for suf, tpl in NOUN_SUFFIX:
            if not w.endswith(suf) or len(w) - len(suf) < 2:
                continue
            stem = w[:-len(suf)]
            g = stem_gloss(stem, w)
            if g:
                out[w] = say(tpl, stem, g)
                note('꼬리 -' + suf)
                hit = True
            break
        if not hit:
            rest2.append(w)

    # ── 3) 합성어: 양쪽 다 뜻이 있으면 둘을 이어 붙인다 ──────────────
    rest3 = []
    for w in rest2:
        best = None
        for c in range(2, len(w)):     # 앞 조각을 짧게 잡는 쪽부터
            a, b = w[:c], w[c:]
            if b in TAIL_WORDS:
                continue
            ga, gb = look(a), look(b)
            if ga and gb:
                best = (a, b, ga[1], gb[1])
                break
        if best:
            a, b, ga, gb = best
            out[w] = cut("'%s'%s '%s'%s 어울려 이룬 말. %s: %s / %s: %s"
                         % (a, wa(a), b, iga(b),
                            a, cut(ga, 56), b, cut(gb, 56)))
            note('합성어')
        else:
            rest3.append(w)

    # ── 4) 어근 뜻은 없고 한자만 아는 파생어 ────────────────────────
    #    '가감'의 뜻풀이가 "'가감하다'의 어근." 뿐이면 설명은 못 빌리지만
    #    한자 표기(可堪)는 그대로 알려 줄 수 있다.
    rest4 = []
    for w in rest3:
        hit = False
        for suf, tpl in VERB_SUFFIX:
            if not w.endswith(suf) or len(w) - len(suf) < 1:
                continue
            stem = w[:-len(suf)]
            hj = hanja_only(stem)
            if hj:
                head = tpl.format(s=stem, eul=eul(stem), iga=iga(stem), wa=wa(stem))
                out[w] = cut('%s. %s: %s' % (head, stem, hj))
                note('한자만')
                hit = True
            break
        if not hit:
            rest4.append(w)

    # ── 4.5) 거꾸로 파생: 어근만 표제어로 오른 낱말 ─────────────────
    #    '가느슥'처럼 홀로는 쓰이지 않고 '가느슥하다'의 어근으로만 있는 말.
    #    파생형에 뜻이 있으면 그쪽을 가리켜 주는 게 사전이 하는 방식이다.
    rest4b = []
    for w in rest4:
        hit = None
        for suf in ('하다', '거리다', '대다', '스럽다', '이다', '되다'):
            got = look(w + suf)
            if got:
                hit = (w + suf, got[1])
                break
        if hit:
            form, g = hit
            out[w] = cut("'%s'의 어근. %s: %s" % (form, form, cut(g, 100)))
            note('어근')
        else:
            rest4b.append(w)
    rest4 = rest4b

    # ── 5) 작품에서 따온 이름 ──────────────────────────────────────
    #    '그랑디스쿠가몬' 같은 말은 우리말 어근이 없다. 어디서 온 이름인지가
    #    사실상 유일하게 해 줄 수 있는 설명이다.
    rest5 = []
    for w in rest4:
        work = meta.get(w, ('', '', ''))[2]
        if not work:
            rest5.append(w)
            continue
        if work == '신조어':
            out[w] = '새로 생긴 말.'
        elif work in CATEGORY_WORKS:
            out[w] = '%s 이름.' % work
        else:
            out[w] = '《%s》에 나오는 이름.' % work
        note('작품 이름')

    # ── 6) 땅이름·기관 꼬리 ────────────────────────────────────────
    rest6 = []
    for w in rest5:
        hit = False
        for suf, desc in PLACE_SUFFIX:
            if w.endswith(suf) and len(w) - len(suf) >= 2:
                out[w] = desc + '.'
                note('이름꼬리 -' + suf)
                hit = True
                break
        if not hit:
            rest6.append(w)

    # ── 7) 남은 것: 품사·분야 꼬리표에 아는 앞머리를 얹는다 ──────────
    #    앞머리는 반드시 앞에서부터 본다. 뒤에서 아무 데나 자르면
    #    '가감하다'에서 '감하'가 잡히는 식으로 엉뚱해진다.
    rest7 = []
    for w in rest6:
        pos_, field, _ = meta.get(w, ('', '', ''))
        lead = ' '.join(x for x in (('《%s》' % field) if field else '', pos_) if x)

        head = None
        for c in range(len(w) - 1, 1, -1):     # 아는 앞머리 중 가장 긴 것
            a = w[:c]
            if a in TAIL_WORDS:
                continue
            got = look(a)
            if got:
                head = (a, got[1])
                break

        if head and lead:
            a, g = head
            out[w] = cut("%s. '%s'%s 앞에 붙은 말. %s: %s"
                         % (lead, a, iga(a), a, cut(g, 80)))
            note('꼬리표+앞머리')
        elif head:
            a, g = head
            out[w] = cut("'%s'%s 앞에 붙은 말. %s: %s" % (a, iga(a), a, cut(g, 90)))
            note('앞머리')
        elif lead:
            out[w] = lead + '.'
            note('꼬리표만')
        else:
            rest7.append(w)

    # ── 8) 끝까지 모르면 아는 만큼만 밝힌다 ────────────────────────
    for w in rest7:
        out[w] = '사전에 실린 %d글자 낱말. 자세한 뜻풀이는 전하지 않는다.' % len(w)
        note('뜻풀이 없음')

    return out, stat


def cut(s, limit=MAX_BYTES):
    """UTF-8 기준 limit 바이트 안으로 자른다. 글자 중간에서 끊기지 않게, 말줄임표 자리까지 빼고."""
    b = s.encode('utf-8')
    if len(b) <= limit:
        return s
    b = b[:limit - 3]                 # '…' 이 3바이트다
    while b and (b[-1] & 0xC0) == 0x80:
        b = b[:-1]
    if b and b[-1] >= 0x80:
        b = b[:-1]
    return b.decode('utf-8', 'ignore').rstrip(' .,·/') + '…'


# ──────────────────────────── 파일 입출력 ────────────────────────────

def main():
    words = [w.strip() for w in open(os.path.join(ASSETS, 'dict_all.txt'),
                                     encoding='utf-8') if w.strip()]
    raw = open(os.path.join(ASSETS, 'means.idx'), 'rb').read()
    idx = struct.unpack('<%dI' % (len(raw) // 4), raw)
    blob = open(os.path.join(ASSETS, 'means.bin'), 'rb').read()
    assert len(idx) == len(words) + 1, 'means.idx 항목 수가 단어 수 + 1 이 아니다'

    pos = {w: i for i, w in enumerate(words)}

    def mean_of(w):
        i = pos.get(w)
        if i is None or idx[i] == idx[i + 1]:
            return None
        return blob[idx[i]:idx[i + 1]].decode('utf-8', 'replace')

    meta = {}
    if os.path.exists(META):
        for line in open(META, encoding='utf-8'):
            p = line.rstrip('\n').split('\t')
            if len(p) >= 4 and p[0] not in meta:
                meta[p[0]] = (p[1], p[2], p[3])
        print('꼬리표 %s개 읽음 (%s)' % (f'{len(meta):,}', os.path.basename(META)))
    else:
        print('!! %s 없음 — 품사·분야 꼬리표 없이 진행한다' % META)

    before = sum(1 for i in range(len(words)) if idx[i] == idx[i + 1])
    print('단어 %s개 · 뜻 없음 %s개 (%.1f%%)'
          % (f'{len(words):,}', f'{before:,}', 100.0 * before / len(words)))

    made, stat = build(words, mean_of, meta)

    print('\n채운 방법 (상위 20)')
    for k, v in sorted(stat.items(), key=lambda kv: -kv[1])[:20]:
        print('  %-16s %8s' % (k, f'{v:,}'))
    tail = sum(v for k, v in sorted(stat.items(), key=lambda kv: -kv[1])[20:])
    if tail:
        print('  %-16s %8s' % ('(나머지 꼬리들)', f'{tail:,}'))
    print('  %-16s %8s' % ('합계', f'{len(made):,}'))

    print('\n미리보기')
    want = ['가가대소하다', '가감하다', '가결되다', '뻑뻑거리다', '절실히', '스산스레',
            '중성화하다', '관리지역', '왕복선', '가게쟁이', '가계분석', '위악적',
            '가가와현', '청담역', '땅끝', '알콩달콩', '월드오브탱크', '그랑디스쿠가몬']
    for w in want:
        if w in made:
            print('  %-12s %s' % (w, made[w]))

    if '--write' not in sys.argv:
        print('\n(미리보기만 함. 실제로 쓰려면 --write)')
        return

    parts, offs, total = [], [0], 0
    for i, w in enumerate(words):
        chunk = (blob[idx[i]:idx[i + 1]] if idx[i] != idx[i + 1]
                 else made[w].encode('utf-8'))
        parts.append(chunk)
        total += len(chunk)
        offs.append(total)
    assert total < 2 ** 31, 'means.bin 이 2GB를 넘었다 — 오프셋이 4바이트다'

    with open(os.path.join(ASSETS, 'means.bin'), 'wb') as f:
        for p in parts:
            f.write(p)
    with open(os.path.join(ASSETS, 'means.idx'), 'wb') as f:
        f.write(struct.pack('<%dI' % len(offs), *offs))
    print('\nmeans.bin %.1fMB · means.idx %.1fMB · 뜻 없는 단어 0개'
          % (total / 1e6, len(offs) * 4 / 1e6))


if __name__ == '__main__':
    main()
