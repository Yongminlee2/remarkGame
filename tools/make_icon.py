# -*- coding: utf-8 -*-
"""
앱 아이콘(낱말 타일 '끝'·'말')을 그려 런처용·스토어용 이미지를 한꺼번에 만든다.

**여기가 아이콘 도안의 유일한 출처다.** 런처 아이콘과 스토어 아이콘이 다르면
설치 전후로 딴 앱처럼 보이므로, 두 곳 모두 이 파일의 함수로 그린다.

한글 글자가 들어가서 벡터(VectorDrawable)로는 만들 수 없다 — 벡터에는 글자 요소가
없고 글리프를 경로로 바꿔야 한다. 그래서 적응형 아이콘의 전경만 PNG 로 굽고
배경은 그라데이션 XML 로 둔다(minSdk 26 이라 적응형 아이콘은 항상 쓸 수 있다).

    python tools/make_icon.py           # 이미지 생성
    python tools/make_icon.py --preview # 런처 마스크별로 어떻게 잘리는지만 확인
"""
import io
import os
import sys

from PIL import Image, ImageDraw, ImageFont

# 콘솔이 UTF-8 이 아니면 한글이 깨진다. 다른 스크립트가 이 파일을 불러다 쓰기도 해서
# **이미 UTF-8 이면 다시 감싸지 않는다** — 두 번 감싸면 먼저 만든 래퍼가 정리되면서
# 밑에 깔린 버퍼까지 닫혀 버린다.
if (sys.stdout.encoding or '').lower().replace('-', '') != 'utf8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'app', 'src', 'main', 'res')
STORE = os.path.join(HERE, '..', 'docs', 'store')

SS = 4                              # 슈퍼샘플링 배율

YELLOW = (255, 197, 51)
MINT = (89, 212, 153)
INK = (26, 27, 45)

BG_TOP = (58, 62, 120)
BG_MID = (33, 35, 72)
BG_BOT = (18, 19, 42)

# 108 기준 좌표. 적응형 아이콘은 가운데 72(=18~90)만 안 잘린다고 보장한다.
# 원형 마스크까지 버티려면 중심에서 36 안에 들어와야 해서 타일을 그만큼 줄였다.
TILE = 38
OFFSET = 10.5
ROT = 8

# 런처 아이콘 밀도별 크기 (적응형 아이콘 전경은 108dp)
DENSITIES = [
    ('mdpi', 108), ('hdpi', 162), ('xhdpi', 216),
    ('xxhdpi', 324), ('xxxhdpi', 432),
]


def font(size):
    for n in ('malgunbd.ttf', 'malgun.ttf'):
        p = os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts', n)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    raise SystemExit('한글 글꼴을 찾지 못했다 (malgun.ttf)')


def gradient(size, top, mid, bot):
    """좌상 → 우하 대각 그라데이션"""
    w, h = size
    img = Image.new('RGB', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / max(w - 1, 1) + y / max(h - 1, 1)) / 2
            if t < 0.5:
                k = t / 0.5
                c = tuple(int(top[i] + (mid[i] - top[i]) * k) for i in range(3))
            else:
                k = (t - 0.5) / 0.5
                c = tuple(int(mid[i] + (bot[i] - mid[i]) * k) for i in range(3))
            px[x, y] = c
    return img.convert('RGBA')


def draw_tiles(size_px, scale=1.0):
    """낱말 타일 두 장만 그린다(배경 투명). scale 로 전체 크기를 조절한다."""
    u = size_px / 108.0 * scale
    off = size_px * (1 - scale) / 2
    layer = Image.new('RGBA', (size_px, size_px), (0, 0, 0, 0))

    for dx, color, ch, rot in ((-OFFSET, YELLOW, '끝', ROT), (OFFSET, MINT, '말', -ROT)):
        side = int(TILE * u)
        tile = Image.new('RGBA', (side, side), (0, 0, 0, 0))
        d = ImageDraw.Draw(tile)
        d.rounded_rectangle([0, 0, side - 1, side - 1],
                            radius=int(TILE * 0.22 * u), fill=color + (255,))
        f = font(int(TILE * 0.66 * u))
        # 한글은 글자 상자 안에서 살짝 위로 뜨는 편이라 조금 내려 앉힌다
        d.text((side / 2, side / 2 + side * 0.03), ch, font=f, fill=INK, anchor='mm')
        tile = tile.rotate(rot, resample=Image.BICUBIC, expand=True)
        layer.alpha_composite(tile, (int(54 * u + dx * u - tile.width / 2 + off),
                                     int(54 * u - tile.height / 2 + off)))
    return layer


def draw_icon(size_px):
    """배경까지 포함한 완성 아이콘"""
    img = gradient((size_px, size_px), BG_TOP, BG_MID, BG_BOT)
    img.alpha_composite(draw_tiles(size_px))
    return img


def mask_preview(path):
    """런처 마스크(원형·스퀘어클·둥근네모)별로 잘리는 모습을 확인한다."""
    n = 256
    icon = draw_icon(n * SS).resize((n, n), Image.LANCZOS)
    sheet = Image.new('RGB', (n * 3 + 80, n + 70), (20, 21, 38))
    d = ImageDraw.Draw(sheet)
    d.text((20, 16), '런처 마스크별로 잘리는 모습 확인', font=font(20), fill=(230, 230, 240))

    shapes = [('원형', 0.5), ('스퀘어클', 0.30), ('둥근 네모', 0.18)]
    for i, (name, r) in enumerate(shapes):
        m = Image.new('L', (n, n), 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, n - 1, n - 1],
                                            radius=int(n * r), fill=255)
        cell = icon.copy()
        cell.putalpha(m)
        x = 20 + i * (n + 20)
        sheet.paste(cell, (x, 50), cell)
        d.text((x, 26), name, font=font(18), fill=(200, 200, 220))
    sheet.save(path, 'PNG')
    print('마스크 확인표: %s' % os.path.abspath(path))


def main():
    if '--preview' in sys.argv:
        os.makedirs(STORE, exist_ok=True)
        mask_preview(os.path.join(STORE, '_icon_mask_preview.png'))
        return

    # 1) 런처 적응형 아이콘 전경 (배경은 ic_launcher_bg.xml 이 그린다)
    for name, px in DENSITIES:
        d = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(d, exist_ok=True)
        img = draw_tiles(px * SS).resize((px, px), Image.LANCZOS)
        img.save(os.path.join(d, 'ic_launcher_fg.png'), 'PNG')
    print('런처 전경 PNG %d개 (mipmap-*/ic_launcher_fg.png)' % len(DENSITIES))

    # 2) 스토어 아이콘 512x512 (알파 없이)
    os.makedirs(STORE, exist_ok=True)
    icon = draw_icon(512 * SS // 2).resize((512, 512), Image.LANCZOS)
    icon.convert('RGB').save(os.path.join(STORE, 'play_icon_512.png'), 'PNG')
    print('스토어 아이콘 play_icon_512.png (512x512, 알파 없음)')

    mask_preview(os.path.join(STORE, '_icon_mask_preview.png'))


if __name__ == '__main__':
    main()
