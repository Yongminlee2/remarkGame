# 끝말잇기 (WordChain)

AI와 1:1로 대결하는 한국어 끝말잇기 안드로이드 게임. 채팅하듯 말풍선으로 주고받는 UI에,
표준국어대사전+끄투 DB 기반 **43만 단어 사전**과 **뜻풀이 35만 건**을 완전 오프라인으로 내장했다.

기획→디자인→데이터 수집·가공→코딩→효과음/BGM 합성→실기기 테스트까지 전 과정을 직접 진행한 개인 프로젝트다.

## 주요 기능

| 기능 | 설명 |
|---|---|
| AI 대전 | 난이도 4단계 (매우쉬움 / 쉬움 / 보통 / 어려움) |
| 제한시간 분리 | 난이도와 별개로 "제한시간 없이 느긋하게" 스위치 (무제한 × 어려움 조합 가능) |
| 두음법칙 | 력→역, 라→나, 뉴→유 등 자동 인정 (초성 ㄹ/ㄴ 완화 규칙을 유니코드 분해로 계산) |
| 한방단어 | 어려움만 6라운드 이후 사용, 매우쉬움~보통은 **절대 금지** (시뮬레이션으로 검증) |
| 단어 뜻풀이 | AI든 나든 단어를 내면 말풍선 안에 사전 뜻이 작게 표시 (82% 커버) |
| 음성 입력 | 마이크 버튼 → 한국어 STT → 자동 입력·전송 (SpeechRecognizer ko-KR) |
| 효과음·진동 | 정답/오답/승리/패배/카운트다운 효과음 6종 + 상황별 진동 패턴 |
| BGM 20곡 | 곡마다 조성·템포·코드진행·악기가 다른 신스 루프, 랜덤 연속 재생 |
| 상점 | 게임 점수로 코인 획득(점수÷10, 승리+20) → 아바타 11종·아이템 3종 구매 |
| 아이템 | ⏰ 시간+15초(60🪙) · 💡 힌트(80🪙) · 🔄 AI 단어 바꾸기(120🪙) |
| 기록 | 최고 점수·라운드 저장, 결과 화면에 갱신 표시 |

## 빌드

```
# Windows, JDK 17+ (gradle.properties에 Android Studio JBR 경로 지정돼 있음)
gradlew.bat :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

- AGP 9.2.1 (built-in Kotlin) / Gradle 9.4.1 / compileSdk 36 / minSdk 26
- 외부 라이브러리는 AndroidX + Material Components뿐. 네트워크 권한 없음(완전 오프라인)

## 데이터 출처 (모두 공개 데이터)

| 데이터 | 출처 | 라이선스 |
|---|---|---|
| 명사 목록(기초) | [han-dle/pd-korean-noun-list-for-wordles](https://github.com/han-dle/pd-korean-noun-list-for-wordles) — 표준국어대사전 추출 | CC0 |
| 단어 DB(확장) | [JJoriping/KKuTu](https://github.com/JJoriping/KKuTu) db.sql 의 kkutu_ko 테이블 (끄투 계열 게임 사전) | 단어 목록은 저작권 비보호 |
| 뜻풀이 | [acidsound/korean_wordlist](https://github.com/acidsound/korean_wordlist) korean_dictionary1/2.json | 표준국어대사전 기반 |
| 효과음·BGM | 전부 자체 신스 합성 (`AudioGen.java`/`BgmGen.java` → ffmpeg ogg 인코딩) | 자작 |
| 디자인 참고 | [getdesign.md](https://getdesign.md) / [VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md) Raycast DESIGN.md | 참고 |

## 아키텍처

```
app/src/main/java/com/kkeutmal/game/
├── MainActivity.kt    # 홈: 난이도·제한시간 선택, 최고기록, 코인, 상점 진입
├── GameActivity.kt    # 게임: 채팅 UI, 타이머, 아이템 바, 음성입력, 결과 다이얼로그
├── ShopActivity.kt    # 상점: 아바타 그리드·아이템 목록 (코드로 동적 생성)
├── GameEngine.kt      # 규칙·AI 전략 (AiLevel × noTimer), 힌트/단어교체 아이템 로직
├── WordDict.kt        # 사전 로더: 정렬 배열 + 이진탐색, 뜻풀이 오프셋 조회
├── Dueum.kt           # 두음법칙 (유니코드 초성/중성 분해 기반, 단방향)
├── ChatAdapter.kt     # RecyclerView 4종 말풍선 (AI/플레이어/시스템/생각중)
├── AudioManager.kt    # SFX 6종 + BGM 20곡 랜덤 연속 재생
├── VoiceInput.kt      # SpeechRecognizer 래퍼 (ko-KR, 한글만 추출)
└── Wallet.kt          # 코인·아바타·아이템 영속화 (SharedPreferences)

app/src/main/assets/
├── dict_all.txt       # 429,961 단어, LC_ALL=C 정렬 (이진탐색 전제!)
├── dict_common.txt    # 상용 명사 3,099 (AI가 주로 쓰는 풀)
├── means.bin          # 뜻풀이 원문 (UTF-8 연결, 구분자 없음, 30MB)
└── means.idx          # 단어 i의 뜻 = means.bin[idx[i]..idx[i+1]] (4B LE × n+1)
```

### 설계 포인트

- **사전 자료구조**: 43만 단어를 HashSet 대신 **정렬 String 배열 + 이진탐색**으로 보관.
  단어 검증 O(log n), 첫 글자 범위 조회(lower bound)로 AI 후보 탐색. 뜻풀이는 메모리에 올리지 않고
  `means.idx` 오프셋으로 필요할 때만 파일에서 읽는다(첫 실행 시 filesDir로 복사 후 RandomAccessFile).
- **AI 전략**(GameEngine.pickAiWord):
  - 매우쉬움: 상용 단어 중 플레이어가 잇기 좋은 것만(followUp≥8), 한방단어 금지, 5라운드부터 항복 확률
  - 쉬움: 상용 단어, followUp≥5, 8라운드부터 항복 확률
  - 보통: 상용 단어 위주 무작위, 부족하면 전체 사전에서 짧은 단어
  - 어려움: 전체 사전에서 플레이어 후보를 최소화하는 단어, 6라운드부터 한방단어 허용
- **BGM 합성**: 5가지 스타일(패드/플럭/오르골/마림바/베이스그루브) × 7가지 코드진행 × 조성 ±6 × 템포 70~109를
  시드 고정 난수로 조합해 20곡 생성, libvorbis q2로 전곡 1.6MB.
- **음향 밸런스 검증**: 데스크톱 시뮬레이션(무작위 유효단어 플레이어 × 난이도별 150판)으로
  "AI 규칙위반 0건 / 매우쉬움~보통 한방단어 0건"을 확인하고 출시.

## 개발 일지 (2026-07-26)

1. **v1.0 — 기본 게임**
   - 환경 조사(기존 HomeCam 프로젝트의 AGP 9.2.1 체인 재사용, 독립 Gradle 루트 신설)
   - CC0 명사 193,050개 수집·가공, 두음법칙·게임엔진·채팅 UI·타이머·최고기록 구현
   - 상용 명사에만 있던 합성어 230개(고속도로 등)가 검증 사전에 빠진 버그를 병합으로 수정
   - 난이도별 150판 시뮬레이션 검증 후 실기기(갤럭시 S20 Ultra) 설치, 스크린샷으로 UI 확인
2. **v1.1 — 사운드·음성·끄투 사전**
   - 끄투 오픈소스 db.sql(42MB)에서 428,326단어 추출 → 사전 429,961단어로 확장
   - 효과음 6종 + BGM을 코드로 신스 합성(외부 음원 없음 → 저작권 무風)
   - BGM 요청이 "20곡 랜덤"으로 커져 파라메트릭 제너레이터로 20곡 생성, ffmpeg ogg 인코딩
   - 마이크 음성 입력(권한 요청 → 듣는 중 UI → 인식 단어 자동 제출)
   - 진동 피드백, 무제한(제한시간 없음) 모드
3. **v1.2 — 상점·뜻풀이·난이도 개편**
   - 난이도(매우쉬움 신설)와 제한시간을 직교 옵션으로 분리
   - 매우쉬움은 한방단어 절대 금지 — 시뮬레이션 assert로 검증
   - 코인 경제 + 상점(아바타 11종, 아이템 3종: 시간연장/힌트/AI단어교체)
   - 뜻풀이 데이터 수집(120MB JSON → 30MB 바이너리, 82% 커버), 말풍선에 뜻 표시
   - 사전 자료구조를 정렬배열+이진탐색+오프셋 파일로 리팩터링 (메모리 절약)
4. **v1.2.1 — 디자인 폴리시**
   - getdesign.md 카탈로그의 Raycast DESIGN.md 참고: 근흑 서피스 사다리, 헤어라인 보더,
     화이트 CTA 필 버튼, 채도 액센트의 15% 소프트 컨테이너 패턴 적용
   - 액센트 통일: green #59D499 / yellow #FFC533 / red #FF6161

## 크레딧

- 개발: [@Yongminlee2](https://github.com/Yongminlee2) — 기획·코드·디자인·사운드 합성·테스트 전 과정
- 사전/뜻풀이 데이터: 위 "데이터 출처" 표 참고 (모두 공개 데이터)
