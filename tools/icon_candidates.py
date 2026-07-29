# -*- coding: utf-8 -*-
"""
앱 아이콘 후보를 여러 개 그려서 비교표로 뽑는다.

런처 아이콘은 48dp 로도 뭔지 알아볼 수 있어야 한다. 512 로 크게 보면 다 그럴싸한데
작게 줄이면 뭉개지는 도안이 많아서, 후보마다 **큰 것과 48px 짜리를 나란히** 놓는다.

    python tools/icon_candidates.py [출력폴더]
"""
import io
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'docs', 'store', 'icon_candidates')

SS = 4                      # 슈퍼샘플링
YELLOW = (255, 197, 51)
MINT = (89, 212, 153)
CORAL = (255, 122, 122)
SKY = (110, 180, 255)
INK = (26, 27, 45)
WHITE = (255, 255, 255)


def font(size, bold=True):
    for n in (('malgunbd.ttf', 'malgun.ttf') if bold else ('malgun.ttf',)):
        p = os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts', n)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def grad(size, top, mid, bot, diagonal=True):
    w, h = size
    img = Image.new('RGB', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x / max(w - 1, 1) + y / max(h - 1, 1)) / 2) if diagonal else (y / max(h - 1, 1))
            if t < 0.5:
                k = t / 0.5
                c = tuple(int(top[i] + (mid[i] - top[i]) * k) for i in range(3))
            else:
                k = (t - 0.5) / 0.5
                c = tuple(int(mid[i] + (bot[i] - mid[i]) * k) for i in range(3))
            px[x, y] = c
    return img.convert('RGBA')


def ring(d, cx, cy, ro, ri, color):
    d.ellipse([cx - ro, cy - ro, cx + ro, cy + ro], fill=color)
    d.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=(0, 0, 0, 0))


def clip_top(layer, y):
    """위쪽 y 까지만 남긴다 — 고리를 엮인 것처럼 보이게 할 때 쓴다."""
    w, h = layer.size
    mask = Image.new('L', (w, h), 0)
    ImageDraw.Draw(mask).rectangle([0, 0, w, y], fill=255)
    layer.putalpha(Image.composite(layer.getchannel('A'), Image.new('L', (w, h), 0), mask))
    return layer


# ────────────────────────── 후보들 ──────────────────────────
# 각 함수는 S 픽셀짜리 RGBA 를 돌려준다. 좌표는 전부 S 기준 비율로 잡는다.

def a_rings(S):
    """A. 맞물린 고리 두 개 (현재 쓰는 것)"""
    img = grad((S, S), (139, 123, 255), (91, 76, 214), (42, 32, 100))
    u = S / 108.0
    for cx, col, seam in ((43, YELLOW, False), (65, MINT, False), (43, YELLOW, True)):
        lay = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(lay)
        ring(d, cx * u, 54 * u, 20 * u, 11 * u, col + (255,))
        if seam:
            lay = clip_top(lay, int(54 * u))
        img.alpha_composite(lay)
    return img


def b_tiles(S):
    """B. 낱말 타일 두 장 — '끝' '말'. 낱말 게임임이 바로 읽힌다"""
    img = grad((S, S), (58, 62, 120), (33, 35, 72), (18, 19, 42))
    u = S / 108.0
    for dx, col, ch, rot in ((-12, YELLOW, '끝', 8), (12, MINT, '말', -8)):
        t = Image.new('RGBA', (int(46 * u), int(46 * u)), (0, 0, 0, 0))
        dt = ImageDraw.Draw(t)
        dt.rounded_rectangle([0, 0, 46 * u - 1, 46 * u - 1], radius=10 * u, fill=col + (255,))
        f = font(int(30 * u))
        dt.text((23 * u, 24 * u), ch, font=f, fill=INK, anchor='mm')
        t = t.rotate(rot, resample=Image.BICUBIC, expand=True)
        img.alpha_composite(t, (int(54 * u + dx * u - t.width / 2),
                                int(54 * u - t.height / 2)))
    return img


def c_bubbles(S):
    """C. 말풍선 두 개가 겹친 모양"""
    img = grad((S, S), (139, 123, 255), (91, 76, 214), (42, 32, 100))
    u = S / 108.0
    for cx, cy, col, flip in ((38, 44, YELLOW, False), (66, 62, MINT, True)):
        lay = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(lay)
        d.rounded_rectangle([(cx - 24) * u, (cy - 17) * u, (cx + 24) * u, (cy + 17) * u],
                            radius=12 * u, fill=col + (255,))
        if flip:
            d.polygon([((cx + 10) * u, (cy + 16) * u), ((cx + 22) * u, (cy + 28) * u),
                       ((cx + 20) * u, (cy + 14) * u)], fill=col + (255,))
        else:
            d.polygon([((cx - 10) * u, (cy + 16) * u), ((cx - 22) * u, (cy + 28) * u),
                       ((cx - 20) * u, (cy + 14) * u)], fill=col + (255,))
        img.alpha_composite(lay)
    return img


def d_glyph(S):
    """D. 큰 '끝' 한 글자 — 한글 사용자에게 가장 직관적"""
    img = grad((S, S), (255, 143, 112), (233, 78, 119), (146, 42, 130))
    u = S / 108.0
    d = ImageDraw.Draw(img)
    f = font(int(62 * u))
    d.text((54 * u, 52 * u + 3 * u), '끝', font=f, fill=(0, 0, 0, 60), anchor='mm')
    d.text((54 * u, 52 * u), '끝', font=f, fill=WHITE, anchor='mm')
    return img


def e_chain3(S):
    """E. 고리 세 개가 이어진 사슬"""
    img = grad((S, S), (46, 196, 182), (28, 122, 140), (16, 48, 76))
    u = S / 108.0
    specs = [(30, YELLOW), (54, WHITE), (78, CORAL)]
    for cx, col in specs:
        lay = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(lay)
        ring(d, cx * u, 54 * u, 17 * u, 9 * u, col + (255,))
        img.alpha_composite(lay)
    # 가운데 고리를 위쪽만 다시 덮어 엮인 느낌
    lay = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    ring(ImageDraw.Draw(lay), 54 * u, 54 * u, 17 * u, 9 * u, WHITE + (255,))
    img.alpha_composite(clip_top(lay, int(54 * u)))
    return img


def f_stacked(S):
    """F. 둥근 타일 한 장에 '끝말' 두 글자"""
    img = grad((S, S), (124, 108, 255), (74, 60, 190), (30, 24, 74))
    u = S / 108.0
    lay = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle([22 * u, 22 * u, 86 * u, 86 * u], radius=18 * u, fill=YELLOW + (255,))
    # 두 글자를 세로로 넣으므로 글자 크기를 줄여야 겹치지 않는다
    f = font(int(25 * u))
    d.text((54 * u, 41 * u), '끝', font=f, fill=INK, anchor='mm')
    d.text((54 * u, 68 * u), '말', font=f, fill=INK, anchor='mm')
    img.alpha_composite(lay)
    return img


def g_ring_glyph(S):
    """G. 고리 안에 '끝' — 도형과 글자를 함께"""
    img = grad((S, S), (255, 209, 102), (240, 150, 60), (176, 74, 40))
    u = S / 108.0
    lay = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    ring(d, 54 * u, 54 * u, 34 * u, 25 * u, (28, 26, 60, 255))
    img.alpha_composite(lay)
    d2 = ImageDraw.Draw(img)
    f = font(int(34 * u))
    d2.text((54 * u, 54 * u), '끝', font=f, fill=(28, 26, 60), anchor='mm')
    return img


def h_wave(S):
    """H. 이어지는 물결 — 말이 흘러 이어지는 느낌"""
    img = grad((S, S), (36, 40, 84), (24, 26, 58), (14, 15, 34))
    u = S / 108.0
    d = ImageDraw.Draw(img)
    for i, col in enumerate((YELLOW, MINT, SKY)):
        y = 36 + i * 18
        d.line([(20 * u, y * u), (38 * u, (y - 9) * u), (54 * u, y * u),
                (70 * u, (y + 9) * u), (88 * u, y * u)],
               fill=col + (255,), width=int(7 * u), joint='curve')
    return img


CANDIDATES = [
    ('A', '맞물린 고리 (현재)', a_rings),
    ('B', '낱말 타일 끝·말', b_tiles),
    ('C', '말풍선 두 개', c_bubbles),
    ('D', '큰 글자 끝', d_glyph),
    ('E', '사슬 세 고리', e_chain3),
    ('F', '타일에 끝말', f_stacked),
    ('G', '고리 안에 끝', g_ring_glyph),
    ('H', '이어지는 물결', h_wave),
]


def rounded_mask(size, radius_ratio=0.22):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1],
                                        radius=int(size * radius_ratio), fill=255)
    return m


def main():
    os.makedirs(OUT, exist_ok=True)
    big = 512

    # 개별 파일 저장
    icons = {}
    for key, name, fn in CANDIDATES:
        img = fn(big * SS // 2).convert('RGB').resize((big, big), Image.LANCZOS)
        img.save(os.path.join(OUT, 'icon_%s.png' % key), 'PNG')
        icons[key] = img
        print('  icon_%s.png  %s' % (key, name))

    # 비교표: 큰 미리보기 + 실제 런처 크기(48dp≈144px)
    cell_w, cell_h = 420, 300
    cols, rows = 4, 2
    pad = 24
    sheet = Image.new('RGB', (cols * cell_w + pad, rows * cell_h + pad + 60), (18, 19, 34))
    d = ImageDraw.Draw(sheet)
    d.text((pad, 18), '아이콘 후보 — 왼쪽은 크게, 오른쪽 작은 것이 실제 런처에서 보이는 크기',
           font=font(22, bold=False), fill=(220, 220, 235))

    for i, (key, name, _) in enumerate(CANDIDATES):
        cx = pad + (i % cols) * cell_w
        cy = 60 + (i // cols) * cell_h
        img = icons[key]

        shown = 190
        big_i = img.resize((shown, shown), Image.LANCZOS)
        big_i.putalpha(rounded_mask(shown))
        sheet.paste(big_i, (cx, cy + 30), big_i)

        small = 96          # 실제 런처에서 보이는 정도
        sm = img.resize((small, small), Image.LANCZOS)
        sm.putalpha(rounded_mask(small))
        sheet.paste(sm, (cx + shown + 24, cy + 30 + (shown - small) // 2), sm)

        d.text((cx, cy + 4), '%s. %s' % (key, name), font=font(20), fill=(240, 240, 250))

    sheet.save(os.path.join(OUT, '_compare.png'), 'PNG')
    print('\n비교표: %s' % os.path.abspath(os.path.join(OUT, '_compare.png')))


if __name__ == '__main__':
    main()
