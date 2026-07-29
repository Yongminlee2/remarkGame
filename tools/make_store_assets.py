# -*- coding: utf-8 -*-
"""
플레이스토어 피처 그래픽(1024x500) 생성.

아이콘 도안은 tools/make_icon.py 한 곳에만 있고 여기서는 가져다 쓴다.
스토어 안에서 아이콘과 피처 그래픽이 따로 놀면 같은 앱으로 안 읽힌다.

앱 아이콘 512x512 는 make_icon.py 가 만든다. 둘 다 필요하면:

    python tools/make_icon.py
    python tools/make_store_assets.py
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_icon  # noqa: E402  (경로를 넣은 뒤에 불러와야 한다)

# 한글 출력 설정은 make_icon 이 이미 해 뒀다. 여기서 또 감싸면 버퍼가 닫힌다.

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'docs', 'store')

INK = (244, 244, 246)


def make_feature(path, w=1024, h=500):
    """
    스토어 상단에 넓게 깔리고 기기에 따라 가장자리가 잘릴 수 있어
    글자를 가운데 몰지 않고 로고와 문구를 좌우로 나눠 배치한다.
    """
    big_w, big_h = w * 2, h * 2
    img = make_icon.gradient((big_w, big_h),
                             make_icon.BG_TOP, make_icon.BG_MID, make_icon.BG_BOT)

    # draw_tiles 는 108 캔버스 안에 타일을 그리므로 실제로 보이는 폭은 그보다 작다.
    # 눈에 차게 하려면 캔버스를 화면 높이보다 크게 잡아야 한다.
    logo_size = int(big_h * 1.05)
    logo = make_icon.draw_tiles(logo_size)
    img.alpha_composite(logo, (int(big_w * 0.005), (big_h - logo_size) // 2))

    d = ImageDraw.Draw(img)
    x = int(big_w * 0.46)
    f_title = make_icon.font(int(big_h * 0.21))
    f_sub = make_icon.font(int(big_h * 0.088))

    d.text((x, int(big_h * 0.315)), '끝말잇기', font=f_title, fill=INK, anchor='ls')
    d.text((x, int(big_h * 0.455)), 'AI와 두뇌 한판 승부',
           font=f_sub, fill=(226, 226, 240), anchor='ls')

    # 광고 관련 문구는 넣지 않는다 — 나중에 광고를 붙일 계획이라
    # "광고 없음"은 지키지 못할 약속이 된다.
    f_tag = make_icon.font(int(big_h * 0.066))
    tags = ['43만 단어 사전 내장', '단어마다 사전 뜻풀이', '무한 스테이지와 보스전']
    for i, t in enumerate(tags):
        d.text((x, int(big_h * (0.62 + i * 0.095))), '· ' + t,
               font=f_tag, fill=(190, 231, 216), anchor='ls')

    out = img.convert('RGB').resize((w, h), Image.LANCZOS)
    out.save(path, 'PNG')
    print('피처그래픽 %s (%dx%d)' % (os.path.basename(path), w, h))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    make_feature(os.path.join(OUT_DIR, 'play_feature_1024x500.png'))
    print('저장 위치: %s' % os.path.abspath(OUT_DIR))


if __name__ == '__main__':
    main()
