# 개인정보처리방침 · Privacy Policy

> ⚠️ **이것은 보관용 사본이다. 효력 있는 원본은 legal 저장소에 있다.**
>
> https://yongminlee2.github.io/legal/wordchain/privacy.html
>
> 플레이 콘솔과 앱 안 「정보」 화면이 가리키는 곳도 위 주소다.
> 이 저장소는 언제든 private 으로 돌릴 수 있어서, 방침 원본을 여기 두면
> 링크가 죽는다. **고칠 일이 생기면 legal 쪽을 고칠 것.**
> 양쪽 문구가 어긋나 보이면 legal 쪽이 맞다.

**앱 이름:** 끝말잇기 (WordChain)
**패키지명:** `com.kkeutmal.game`
**최종 수정:** 2026년 7월

---

## 한 줄 요약

**이 앱은 어떤 개인정보도 수집하지 않고, 어디로도 전송하지 않습니다.**
인터넷 접속 권한 자체가 없습니다.

---

## 1. 수집하는 개인정보

**없습니다.**

이 앱은 이름, 이메일, 전화번호, 위치, 연락처, 사진, 기기 식별자 등
어떠한 개인정보도 수집하지 않습니다. 회원가입이나 로그인 기능도 없습니다.

## 2. 인터넷 전송

**없습니다.**

이 앱은 안드로이드 인터넷 권한(`android.permission.INTERNET`)을 **선언하지 않았습니다.**
기술적으로 외부와 통신할 수 없으므로, 어떤 데이터도 외부로 나가지 않습니다.
광고, 분석 도구(Analytics), 크래시 리포트 SDK를 일절 사용하지 않습니다.

## 3. 기기에 저장되는 정보

게임 진행에 필요한 아래 정보가 **기기 안에만** 저장됩니다.
외부로 전송되지 않으며, 앱을 삭제하면 함께 지워집니다.

- 게임 점수·최고 기록·플레이한 라운드 수
- 레벨·경험치·랭크
- 보유 코인·아이템·아바타
- 모험 모드 진행 스테이지
- 일일 미션 진행도, 연속 출석 일수
- 선택한 난이도 등 게임 설정

저장 위치는 안드로이드 표준 저장소(SharedPreferences)이며 앱 전용 영역입니다.

## 4. 권한 사용 목적

| 권한 | 목적 | 처리 방식 |
|---|---|---|
| **마이크** (`RECORD_AUDIO`) | 단어를 말로 입력하는 음성 인식 기능 | 안드로이드 시스템 음성 인식기에 전달되어 **텍스트로 변환**됩니다. 앱은 **음성을 녹음하거나 저장하지 않습니다.** 변환된 글자만 즉시 사용하고 버립니다. 이 권한은 **선택 사항**이며, 허용하지 않아도 키보드로 게임을 즐길 수 있습니다. |
| **진동** (`VIBRATE`) | 정답·오답·승패 시 촉각 피드백 | 개인정보와 무관합니다. |

> **음성 인식 관련 안내**: 음성을 문자로 바꾸는 처리는 사용자의 기기에 설치된
> 안드로이드 음성 인식 서비스가 수행합니다. 해당 서비스의 동작 방식은
> 기기 제조사와 사용자의 설정에 따르며, 그 부분은 이 앱이 아닌
> 각 서비스 제공자의 개인정보처리방침이 적용됩니다.

## 5. 만 14세 미만 아동

이 앱은 개인정보를 수집하지 않으므로 아동의 개인정보 역시 수집하지 않습니다.
앱 내 결제, 광고, 외부 링크, 채팅 등 다른 이용자와의 상호작용 기능이 없습니다.

## 6. 제3자 제공 및 위탁

수집하는 정보가 없으므로 제3자에게 제공하거나 처리를 위탁하는 정보도 없습니다.

## 7. 데이터 보관 및 삭제

기기에 저장된 게임 기록은 사용자가 **앱을 삭제**하거나
**설정 → 애플리케이션 → 끝말잇기 → 저장공간 → 데이터 삭제**를 실행하면 완전히 지워집니다.

## 8. 방침 변경

이 방침이 변경되면 이 문서를 갱신하고 상단의 최종 수정일을 고칩니다.

## 9. 문의

- 이메일: dydals5678@gmail.com
- 저장소: https://github.com/Yongminlee2/remarkGame

---

## English Summary

**This app collects no personal data and transmits nothing.**
It does not declare the `INTERNET` permission, so it is technically incapable of
sending data anywhere. There are no ads, no analytics, and no third-party SDKs.

Game progress (scores, level, coins, items, avatars, stage, daily missions) is stored
**only on the device** via Android SharedPreferences and is deleted when the app is uninstalled.

**Microphone** (`RECORD_AUDIO`) is optional and used solely for speech-to-text word input.
Audio is passed to the Android system speech recognizer and converted to text;
the app never records or stores audio. **Vibration** is used for haptic feedback only.

Contact: dydals5678@gmail.com
