# -*- coding: utf-8 -*-
"""
뜻풀이 자산(means.bin / means.idx)을 소스 tsv 들로부터 다시 만든다.

우선순위가 있다. 앞선 소스에 뜻이 있으면 그걸 쓰고, 없을 때만 다음으로 넘어간다.

  1) means_stdict.tsv  — 국립국어원 표준국어대사전 XML에서 뽑은 것.
     동음이의어가 전부 들어 있어 '실제로 흔한 뜻'을 고를 수 있다. 이게 기준이다.
  2) means_legacy.tsv  — 예전에 쓰던 소스. 표제어당 뜻이 하나뿐이고 그 하나를
     잘못 고른 경우가 많아 신뢰도가 낮지만, 1)에 없는 낱말을 꽤 메워 준다.

용례(예문)는 어느 소스에서 왔든 버린다. 문학작품·기사에서 따온 것이 섞여 있어
사전 본문과 저작권이 따로 놀기 때문이다.

빈칸은 여기서 채우지 않는다. 그건 gen_means.py 가 이어서 한다.

    python tools/build_means.py <stdict.tsv> <legacy.tsv>
"""
import io
import os
import re
import struct
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets')
MAX_BYTES = 160


def strip_examples(g):
    """용례와 유의어 목록을 걷어낸다."""
    g = g.split('❖')[0]
    g = g.split('≒')[0]
    return re.sub(r'\s+', ' ', g).strip()


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


def load(path):
    out = {}
    if not path or not os.path.exists(path):
        return out
    for line in open(path, encoding='utf-8'):
        w, _, g = line.rstrip('\n').partition('\t')
        g = strip_examples(g)
        if w and g and w not in out:
            out[w] = cut(g)
    return out


def main():
    sources = sys.argv[1:]
    if not sources:
        print('소스 tsv를 하나 이상 줘야 한다')
        return 1

    tables = []
    for p in sources:
        t = load(p)
        tables.append((os.path.basename(p), t))
        print('%-22s %s개' % (os.path.basename(p), f'{len(t):,}'))

    words = [w.strip() for w in open(os.path.join(ASSETS, 'dict_all.txt'),
                                     encoding='utf-8') if w.strip()]

    hits = [0] * len(tables)
    parts, offs, total = [], [0], 0
    for w in words:
        chunk = b''
        for i, (_, t) in enumerate(tables):
            g = t.get(w)
            if g:
                chunk = g.encode('utf-8')
                hits[i] += 1
                break
        parts.append(chunk)
        total += len(chunk)
        offs.append(total)

    assert total < 2 ** 31, 'means.bin 이 2GB를 넘었다 — 오프셋이 4바이트다'
    with open(os.path.join(ASSETS, 'means.bin'), 'wb') as f:
        for p in parts:
            f.write(p)
    with open(os.path.join(ASSETS, 'means.idx'), 'wb') as f:
        f.write(struct.pack('<%dI' % len(offs), *offs))

    print('\n단어 %s개' % f'{len(words):,}')
    for (name, _), n in zip(tables, hits):
        print('  %-22s %s개 (%.1f%%)' % (name, f'{n:,}', 100.0 * n / len(words)))
    empty = len(words) - sum(hits)
    print('  %-22s %s개 (%.1f%%)  ← gen_means.py 가 채운다'
          % ('빈칸', f'{empty:,}', 100.0 * empty / len(words)))
    print('means.bin %.1fMB · means.idx %.1fMB' % (total / 1e6, len(offs) * 4 / 1e6))
    return 0


if __name__ == '__main__':
    sys.exit(main())
