# -*- coding: utf-8 -*-
"""
플레이스토어 등록 이미지 생성.

  - 앱 아이콘 512x512 PNG (스토어 등록용, 알파 없음)
  - 피처 그래픽 1024x500 PNG

앱 안의 적응형 아이콘(res/drawable/ic_launcher_fg.xml)과 **같은 도형**을 그린다.
스토어 아이콘과 런처 아이콘이 다르면 설치 전후로 딴 앱처럼 보인다.
그래서 좌표를 벡터와 같은 108 기준으로 두고 배율만 바꾼다.

    python tools/make_store_assets.py [출력폴더]
"""
import io
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'docs', 'store')

SS = 4          # 슈퍼샘플링 배율 — 원을 매끈하게 뽑으려면 크게 그려서 줄인다

# ic_launcher_fg.xml 과 같은 값 (viewport 108 기준)
RING_L = (43, 54)
RING_R = (65, 54)
R_OUT = 20
R_IN = 11
# 두 고리가 겹치는 자리는 위·아래 두 군데다. 위쪽에서만 노랑을 되살리면
# 위는 노랑이 앞, 아래는 민트가 앞이 되어 사슬처럼 엮인 모양이 나온다.
SEAM_Y = 54

YELLOW = (255, 197, 51)
MINT = (89, 212, 153)
BG_TOP = (139, 123, 255)
BG_MID = (91, 76, 214)
BG_BOT = (42, 32, 100)

INK = (244, 244, 246)
DIM = (156, 159, 184)


def korean_font(size, bold=True):
    """윈도우 기본 한글 글꼴. 없으면 기본 글꼴로 떨어진다."""
    for name in (('malgunbd.ttf', 'malgun.ttf') if bold else ('malgun.ttf',)):
        path = os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts', name)
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def diagonal_gradient(size, top, mid, bot):
    """좌상 → 우하 대각 그라데이션. 아이콘 배경(angle 315)과 같은 방향."""
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
    return img


def ring(draw, center, r_out, r_in, color, scale, dy=0):
    """가운데가 뚫린 고리. 안쪽을 투명으로 도려내려고 마스크에 그린다."""
    cx, cy = center
    cx, cy = cx * scale, (cy + dy) * scale
    ro, ri = r_out * scale, r_in * scale
    draw.ellipse([cx - ro, cy - ro, cx + ro, cy + ro], fill=color)
    draw.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=(0, 0, 0, 0))


def draw_logo(size_px, pad_ratio=0.0):
    """맞물린 고리 두 개를 RGBA 이미지로 그린다. 배경은 투명."""
    scale = size_px * (1 - pad_ratio * 2) / 108.0
    off = size_px * pad_ratio
    layer = Image.new('RGBA', (size_px, size_px), (0, 0, 0, 0))

    def stamp(color, seam_clip=None, dy=0, alpha=255):
        tmp = Image.new('RGBA', (size_px, size_px), (0, 0, 0, 0))
        d = ImageDraw.Draw(tmp)
        center = RING_L if color == 'L' else RING_R
        col = (YELLOW if color == 'L' else MINT) + (alpha,)
        cx, cy = center
        cx = cx * scale + off
        cy = (cy + dy) * scale + off
        ro, ri = R_OUT * scale, R_IN * scale
        d.ellipse([cx - ro, cy - ro, cx + ro, cy + ro], fill=col)
        d.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=(0, 0, 0, 0))
        if seam_clip is not None:
            mask = Image.new('L', (size_px, size_px), 0)
            ImageDraw.Draw(mask).rectangle(
                [0, 0, size_px, SEAM_Y * scale + off], fill=255)
            tmp.putalpha(Image.composite(tmp.getchannel('A'),
                                         Image.new('L', (size_px, size_px), 0), mask))
        layer.alpha_composite(tmp)

    # 그림자 → 노랑 → 민트 → (겹침 왼쪽만) 노랑
    for c in ('L', 'R'):
        sh = Image.new('RGBA', (size_px, size_px), (0, 0, 0, 0))
        d = ImageDraw.Draw(sh)
        center = RING_L if c == 'L' else RING_R
        cx = center[0] * scale + off
        cy = (center[1] + 3) * scale + off
        ro, ri = R_OUT * scale, R_IN * scale
        d.ellipse([cx - ro, cy - ro, cx + ro, cy + ro], fill=(0, 0, 0, 60))
        d.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=(0, 0, 0, 0))
        layer.alpha_composite(sh)

    stamp('L')
    stamp('R')
    stamp('L', seam_clip=True)
    return layer


def make_icon(path, size=512):
    big = size * SS
    bg = diagonal_gradient((big, big), BG_TOP, BG_MID, BG_BOT)
    logo = draw_logo(big)
    bg = bg.convert('RGBA')
    bg.alpha_composite(logo)
    out = bg.convert('RGB').resize((size, size), Image.LANCZOS)
    out.save(path, 'PNG')
    print('아이콘   %s  (%dx%d, 알파 없음)' % (os.path.basename(path), size, size))


def make_feature(path, w=1024, h=500):
    """
    피처 그래픽. 스토어 상단에 넓게 깔리고 기기에 따라 가운데가 잘릴 수 있어
    글자를 가운데 몰아 두지 않고, 로고와 문구를 좌우로 나눠 배치한다.
    """
    big_w, big_h = w * 2, h * 2
    img = diagonal_gradient((big_w, big_h), BG_TOP, BG_MID, BG_BOT).convert('RGBA')

    # draw_logo 는 108 캔버스 안에 고리를 그리므로 실제로 보이는 폭은 그 60% 남짓이다.
    # 눈에 차게 하려면 캔버스를 화면보다 크게 잡아야 한다.
    logo_size = int(big_h * 0.95)
    logo = draw_logo(logo_size)
    img.alpha_composite(logo, (int(big_w * 0.015), (big_h - logo_size) // 2))

    d = ImageDraw.Draw(img)
    x = int(big_w * 0.45)
    f_title = korean_font(int(big_h * 0.21))
    f_sub = korean_font(int(big_h * 0.088), bold=False)

    d.text((x, int(big_h * 0.315)), '끝말잇기', font=f_title, fill=INK, anchor='ls')
    d.text((x, int(big_h * 0.455)), 'AI와 두뇌 한판 승부',
           font=f_sub, fill=(226, 226, 240), anchor='ls')

    f_tag = korean_font(int(big_h * 0.066), bold=False)
    # 광고 관련 문구는 넣지 않는다 — 나중에 광고를 붙일 계획이라
    # "광고 없음"은 지키지 못할 약속이 된다.
    tags = ['43만 단어 사전 내장', '단어마다 사전 뜻풀이', '무한 스테이지와 보스전']
    for i, t in enumerate(tags):
        d.text((x, int(big_h * (0.62 + i * 0.095))), '· ' + t,
               font=f_tag, fill=(190, 231, 216), anchor='ls')

    out = img.convert('RGB').resize((w, h), Image.LANCZOS)
    out.save(path, 'PNG')
    print('피처그래픽 %s  (%dx%d)' % (os.path.basename(path), w, h))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    make_icon(os.path.join(OUT_DIR, 'play_icon_512.png'))
    make_feature(os.path.join(OUT_DIR, 'play_feature_1024x500.png'))
    print('저장 위치: %s' % os.path.abspath(OUT_DIR))


if __name__ == '__main__':
    main()
