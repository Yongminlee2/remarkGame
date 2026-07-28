# -*- coding: utf-8 -*-
"""
실기기 스크린샷을 플레이스토어 규격에 맞게 다듬는다.

두 가지를 손본다.

1. **가로세로 비.** 요즘 폰은 1080x2400(1:2.22)인데 플레이스토어는 2:1 을 넘으면
   받아 주지 않는다. 위아래를 잘라 1080x2160(정확히 1:2)으로 맞춘다.
2. **상태 표시줄과 내비게이션 바.** 통신사·배터리·시계가 그대로 보이면 지저분하다.
   마침 이 둘을 잘라내면 위의 비율 문제도 같이 풀린다.

원본은 건드리지 않고 out 폴더에 새로 쓴다.

    python tools/make_screenshots.py <원본폴더> [출력폴더]
"""
import io
import os
import sys

from PIL import Image

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, '..', 'docs', 'store', 'screenshots')

# 갤럭시 S20 Ultra(1080x2400) 기준. 상태 표시줄과 제스처 바 높이.
CROP_TOP = 74
CROP_BOTTOM = 96

TARGET_RATIO = 2.0      # 세로/가로 상한


def process(src, dst):
    img = Image.open(src).convert('RGB')
    w, h = img.size

    top = CROP_TOP if h > CROP_TOP + CROP_BOTTOM + 200 else 0
    bottom = h - (CROP_BOTTOM if top else 0)
    img = img.crop((0, top, w, bottom))
    w, h = img.size

    # 아직도 2:1 보다 길쭉하면 위아래를 고르게 더 잘라 낸다
    max_h = int(w * TARGET_RATIO)
    if h > max_h:
        extra = h - max_h
        # 아래쪽(입력창·버튼)이 더 중요하므로 위를 조금 더 잘라 낸다
        cut_top = extra * 2 // 3
        img = img.crop((0, cut_top, w, cut_top + max_h))

    img.save(dst, 'PNG')
    return img.size


def main():
    if len(sys.argv) < 2:
        print('사용법: python tools/make_screenshots.py <원본폴더> [출력폴더]')
        return 1
    src_dir = sys.argv[1]
    out_dir = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUT
    os.makedirs(out_dir, exist_ok=True)

    names = sorted(f for f in os.listdir(src_dir) if f.lower().endswith('.png'))
    if not names:
        print('PNG 를 못 찾음: %s' % src_dir)
        return 1

    for n in names:
        size = process(os.path.join(src_dir, n), os.path.join(out_dir, n))
        ratio = size[1] / size[0]
        flag = 'OK' if ratio <= TARGET_RATIO + 1e-6 else '비율 초과!'
        print('  %-28s %dx%d  (1:%.2f) %s' % (n, size[0], size[1], ratio, flag))
    print('저장 위치: %s' % os.path.abspath(out_dir))
    return 0


if __name__ == '__main__':
    sys.exit(main())
