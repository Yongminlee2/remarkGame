# -*- coding: utf-8 -*-
"""
끄투 db.sql 의 kkutu_ko 테이블에서 단어별 품사·분야 꼬리표를 뽑는다.

공개 덤프의 mean 칸은 저작권 때문에 비워져 있지만(전부 ＂1＂［1］（1） 자리표),
품사(type)와 분야(theme) 칸은 살아 있다. 뜻풀이를 끝내 못 찾은 낱말이라도
"부사." "《식물》 분야의 말." 정도는 알려 줄 수 있다.

품사 번호는 표준국어대사전 차례를 따른다(1 명사 … 8 부사 …).
분야 코드 이름은 끄투 ko_KR.json 의 theme_* 항목에서 가져왔다.

    python tools/extract_kkutu_meta.py <db.sql> <출력 tsv>
"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

POS = {
    '1': '명사', '2': '대명사', '3': '수사', '4': '조사', '5': '동사',
    '6': '형용사', '7': '관형사', '8': '부사', '9': '감탄사', '10': '접사',
}

# 분야 코드 → 이름 (끄투 ko_KR.json theme_*)
THEME = {
    '10': '가톨릭', '20': '건설', '30': '경제', '40': '고적', '50': '고유',
    '60': '공업', '70': '광업', '80': '교육', '90': '교통', '100': '군사',
    '110': '기계', '120': '기독교', '130': '논리', '140': '농업', '150': '문학',
    '160': '물리', '170': '미술', '180': '민속', '190': '동물', '200': '법률',
    '210': '불교', '220': '사회', '230': '생물', '240': '수학', '250': '수산',
    '260': '수공', '270': '식물', '280': '심리', '290': '약학', '300': '언론',
    '310': '언어', '320': '역사', '330': '연영', '340': '예술', '350': '운동',
    '360': '음악', '370': '의학', '380': '인명', '390': '전기', '400': '정치',
    '410': '종교', '420': '지리', '430': '지명', '440': '책명', '450': '천문',
    '460': '철학', '470': '출판', '480': '통신', '490': '컴퓨터', '500': '한의학',
    '510': '항공', '520': '해양', '530': '화학', '1001': '나라 이름과 수도',
}

# 작품·게임에서 따온 이름들. 사전 낱말이 아니라 '끄투 인정 낱말'이다.
WORKS = {
    'IMS': 'THE iDOLM@STER', 'VOC': 'VOCALOID', 'KRR': '개구리 중사 케로로',
    'KTV': '국내 방송 프로그램', 'NSK': '니세코이', 'KOT': '대한민국 철도역',
    'DOT': '도타 2', 'DRR': '듀라라라!!', 'DGM': '디지몬', 'RAG': '라면/과자',
    'LVL': '러브 라이브!', 'LOL': '리그 오브 레전드',
    'MRN': '마법소녀 리리컬 나노하', 'MMM': '마법소녀 마도카☆마기카',
    'MAP': '메이플스토리', 'MKK': '메카쿠시티 액터즈', 'MNG': '모노가타리 시리즈',
    'MOB': '모바일 게임', 'HYK': '빙과', 'CYP': '사이퍼즈', 'HRH': '스즈미야 하루히',
    'STA': '스타크래프트', 'OIJ': '신조어', 'KGR': '아지랑이 프로젝트',
    'ESB': '앙상블 스타즈!', 'ELW': '엘소드', 'OIM': '오레이모', 'OVW': '오버워치',
    'NEX': '온라인 게임', 'WMV': '외국 영화', 'WOW': '월드 오브 워크래프트',
    'YRY': '유루유리', 'KPO': '유명인', 'JLN': '라이트 노벨',
    'JAN': '만화/애니메이션', 'ZEL': '젤다의 전설', 'POK': '포켓몬스터',
    'HAI': '하이큐!!', 'HSS': '하스스톤', 'KMV': '한국 영화', 'HDC': '함대 컬렉션',
    'HOS': '히어로즈 오브 더 스톰',
}


def unescape(s):
    out, i = [], 0
    while i < len(s):
        if s[i] == '\\' and i + 1 < len(s):
            out.append({'n': '\n', 't': '\t', 'r': '\r', '\\': '\\'}.get(s[i + 1], s[i + 1]))
            i += 2
        else:
            out.append(s[i])
            i += 1
    return ''.join(out)


def pick(codes, table):
    """쉼표로 이어진 코드 목록에서 표에 있는 첫 이름을 고른다."""
    for c in codes.split(','):
        c = c.strip()
        if c in table:
            return table[c]
    return ''


def main():
    sql_path, out_path = sys.argv[1], sys.argv[2]
    rows = kept = 0
    with open(sql_path, encoding='utf-8', errors='replace') as f, \
            open(out_path, 'w', encoding='utf-8', newline='\n') as out:
        inside = False
        for line in f:
            if not inside:
                inside = line.startswith('COPY kkutu_ko (')
                continue
            if line.startswith('\\.'):
                break
            rows += 1
            cols = line.rstrip('\n').split('\t')
            if len(cols) < 6:
                continue
            word = unescape(cols[0]).strip()
            if not word:
                continue
            pos = pick(cols[1], POS)
            theme_codes = cols[5]
            work = pick(theme_codes, WORKS)
            field = pick(theme_codes, THEME)
            if not (pos or work or field):
                continue
            out.write('%s\t%s\t%s\t%s\n' % (word, pos, field, work))
            kept += 1
    print('kkutu_ko %s행 · 꼬리표 있는 낱말 %s개' % (f'{rows:,}', f'{kept:,}'))


if __name__ == '__main__':
    main()
