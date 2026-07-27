# 끝말잇기 확장 구현 계획


**Goal:** 기존 끝말잇기 앱에 성장(레벨·랭크)·수집(아바타 48종 도감)·도전(모험 모드와 보스)·습관(일일 미션·연속 출석·도전과제) 네 시스템을 추가해 반복 플레이 동기를 만든다.

**Architecture:** 새 로직은 전부 **화면과 분리된 순수 Kotlin 파일**로 만들고 JUnit으로 검증한다. 화면(Activity)은 이 순수 로직을 소비하기만 한다. 기존 자유 대전 경로는 건드리지 않고, 모험 모드는 `GameActivity`에 모드 분기를 추가해 재사용한다.

**Tech Stack:** Kotlin, AGP 9.2.1 내장 Kotlin, Gradle 9.4.1, ViewBinding, Material Components, JUnit 4. 외부 네트워크 라이브러리 없음.

## Global Constraints

- compileSdk 36 / minSdk 26 / targetSdk 36, Java 11 (`sourceCompatibility`/`targetCompatibility`)
- 앱은 **완전 오프라인**이다. INTERNET 권한을 추가하지 않는다. 모든 에셋은 앱에 번들한다.
- 빌드: `gradlew.bat :app:assembleDebug` (JDK 경로는 `gradle.properties`의 `org.gradle.java.home`이 지정하므로 JAVA_HOME 불필요)
- **단위 테스트는 반드시 `GRADLE_USER_HOME=C:/gradle-home` 을 붙여 실행한다:**
  `GRADLE_USER_HOME=C:/gradle-home ./gradlew.bat :app:testDebugUnitTest`
  이 PC의 사용자 홈이 `C:\Users\사용자`(한글)라서 기본 Gradle 홈을 쓰면 테스트 워커 JVM이
  클래스패스를 읽지 못하고 `ClassNotFoundException: ...GradleWorkerMain` 으로 죽는다.
  `C:/gradle-home`에 Gradle 배포본 사본이 이미 있다. 빌드(`assembleDebug`)는 이 변수 없이도 된다.
- 테스트 결과는 `app/build/test-results/testDebugUnitTest/*.xml` 의 `tests=`/`failures=` 로 확인한다.
  "BUILD SUCCESSFUL" 만으로는 테스트가 실제로 돌았는지 알 수 없다.
- 실기기 설치: `"C:/Users/사용자/AppData/Local/Android/Sdk/platform-tools/adb.exe" install -r "C:/workAndroid/WordChain/끝말잇기-v1.0.apk"`
- 실기기 스크린샷은 **bash에서** 찍는다. PowerShell 리다이렉트는 바이너리를 깨뜨린다:
  `"$ADB" exec-out screencap -p > out.png`
- `app/src/main/assets/dict_all.txt`는 **LC_ALL=C 정렬**을 전제로 한다(`WordDict`가 이진 탐색). 사전 파일을 재생성할 일이 있으면 정렬 순서를 반드시 유지한다.
- 색상은 새로 만들지 말고 `app/src/main/res/values/colors.xml`의 기존 토큰을 쓴다: `surface`, `surface_stroke`, `input_bg`, `chip_bg`, `accent`(#7C6CFF), `accent2`(#59D499), `warn`(#FFC533), `error`(#FF6161), `text_primary`, `text_dim`, `cta_bg`, `cta_text`. 랭크·등급 색만 새로 추가한다.
- 패키지는 전부 `com.kkeutmal.game`.
- 스펙 원본: `docs/specs/game-expansion-design.md`

---

## 파일 구조

### 새로 만드는 파일

| 파일 | 책임 | 테스트 |
|---|---|---|
| `Progress.kt` | XP → 레벨, 레벨 → 랭크 (순수) | `ProgressTest.kt` |
| `BossRule.kt` | 보스 규칙 판정 (순수 predicate) | `BossRuleTest.kt` |
| `Stage.kt` | 스테이지 n → 제한시간·AI레벨·목표라운드·보스 (순수) | `StageTest.kt` |
| `AvatarCatalog.kt` | 아바타 48종 정의·이름·등급·해금조건 (순수) | `AvatarCatalogTest.kt` |
| `Missions.kt` | 일일 미션 풀·진행도·연속 출석 (순수부 + Prefs) | `MissionsTest.kt` |
| `Achievements.kt` | 도전과제 판정 (순수) | `AchievementsTest.kt` |
| `AvatarView.kt` | 파츠를 겹쳐 그리는 커스텀 뷰 | 실기기 |
| `AdventureActivity.kt` | 모험 화면 | 실기기 |
| `CollectionActivity.kt` | 도감 화면 | 실기기 |
| `GameResult.kt` | 게임 종료 후 보상 계산·저장 (순수부 분리) | `GameResultTest.kt` |

### 수정하는 파일

| 파일 | 변경 |
|---|---|
| `gradle/libs.versions.toml` | JUnit 추가 |
| `app/build.gradle.kts` | `testImplementation` 추가 |
| `Wallet.kt` | 아바타 저장을 이모지 → ID로, 마이그레이션, 아이템 2종 추가 |
| `GameEngine.kt` | 보스 규칙 주입, `validate`/`hasAnyCandidate`/`pickAiWord`에 규칙 적용 |
| `GameActivity.kt` | 모험/자유 모드 분기, 목표 라운드 표시, 결과 처리 위임 |
| `MainActivity.kt` | 홈 재구성 |
| `ShopActivity.kt` | 아바타 탭을 `AvatarView` 기반으로 교체 |
| `res/values/colors.xml` | 랭크·등급 색 추가 |

---

## Task 1: 테스트 인프라와 Progress (XP·레벨·랭크)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/kkeutmal/game/Progress.kt`
- Test: `app/src/test/java/com/kkeutmal/game/ProgressTest.kt`

**Interfaces:**
- Consumes: 없음 (첫 작업)
- Produces:
  - `enum class Rank(val label: String, val minLevel: Int)` — `BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER, GRANDMASTER`
  - `object Progress`
    - `const val MAX_LEVEL: Int = 99`
    - `fun xpForNextLevel(level: Int): Int`
    - `fun totalXpForLevel(level: Int): Int`
    - `fun levelForTotalXp(totalXp: Int): Int`
    - `fun xpIntoLevel(totalXp: Int): Int`
    - `fun rankOf(level: Int): Rank`
    - `fun freeMatchXp(score: Int): Int`
    - `fun stageXp(stage: Int, isBoss: Boolean): Int`

- [ ] **Step 1: 테스트 의존성 추가**

`gradle/libs.versions.toml`의 `[versions]`에 추가:

```toml
junit = "4.13.2"
```

`[libraries]`에 추가:

```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
```

`app/build.gradle.kts`의 `dependencies` 블록 마지막에 추가:

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 2: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/ProgressTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    @Test
    fun `레벨별 필요 XP는 50 곱하기 레벨의 1_2제곱`() {
        assertEquals(50, Progress.xpForNextLevel(1))
        assertEquals(114, Progress.xpForNextLevel(2))
        assertEquals(186, Progress.xpForNextLevel(3))
        assertEquals(792, Progress.xpForNextLevel(10))
    }

    @Test
    fun `누적 필요 XP는 이전 레벨들의 합`() {
        assertEquals(0, Progress.totalXpForLevel(1))
        assertEquals(50, Progress.totalXpForLevel(2))
        assertEquals(164, Progress.totalXpForLevel(3))
        assertEquals(350, Progress.totalXpForLevel(4))
    }

    @Test
    fun `누적 XP로 레벨을 역산한다`() {
        assertEquals(1, Progress.levelForTotalXp(0))
        assertEquals(1, Progress.levelForTotalXp(49))
        assertEquals(2, Progress.levelForTotalXp(50))
        assertEquals(2, Progress.levelForTotalXp(163))
        assertEquals(3, Progress.levelForTotalXp(164))
    }

    @Test
    fun `레벨은 99에서 멈춘다`() {
        assertEquals(99, Progress.levelForTotalXp(Int.MAX_VALUE))
    }

    @Test
    fun `현재 레벨 안에서의 XP를 구한다`() {
        assertEquals(0, Progress.xpIntoLevel(50))
        assertEquals(10, Progress.xpIntoLevel(60))
        assertEquals(0, Progress.xpIntoLevel(164))
    }

    @Test
    fun `랭크 경계가 스펙과 일치한다`() {
        assertEquals(Rank.BRONZE, Progress.rankOf(1))
        assertEquals(Rank.BRONZE, Progress.rankOf(9))
        assertEquals(Rank.SILVER, Progress.rankOf(10))
        assertEquals(Rank.SILVER, Progress.rankOf(19))
        assertEquals(Rank.GOLD, Progress.rankOf(20))
        assertEquals(Rank.GOLD, Progress.rankOf(34))
        assertEquals(Rank.PLATINUM, Progress.rankOf(35))
        assertEquals(Rank.PLATINUM, Progress.rankOf(49))
        assertEquals(Rank.DIAMOND, Progress.rankOf(50))
        assertEquals(Rank.DIAMOND, Progress.rankOf(69))
        assertEquals(Rank.MASTER, Progress.rankOf(70))
        assertEquals(Rank.MASTER, Progress.rankOf(89))
        assertEquals(Rank.GRANDMASTER, Progress.rankOf(90))
        assertEquals(Rank.GRANDMASTER, Progress.rankOf(99))
    }

    @Test
    fun `모험 XP가 자유 대전보다 후하다`() {
        // 자유 대전 점수 300점 = 60 XP, 모험 10스테이지 = 150 XP
        assertEquals(60, Progress.freeMatchXp(300))
        assertEquals(150, Progress.stageXp(10, isBoss = false))
        assertEquals(450, Progress.stageXp(10, isBoss = true))
        assertTrue(Progress.stageXp(10, false) > Progress.freeMatchXp(300))
    }
}
```

- [ ] **Step 3: 테스트를 돌려 실패를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.ProgressTest"`
Expected: 컴파일 실패 — `Unresolved reference: Progress`

- [ ] **Step 4: 최소 구현 작성**

`app/src/main/java/com/kkeutmal/game/Progress.kt`:

```kotlin
package com.kkeutmal.game

import kotlin.math.pow

enum class Rank(val label: String, val minLevel: Int) {
    BRONZE("브론즈", 1),
    SILVER("실버", 10),
    GOLD("골드", 20),
    PLATINUM("플래티넘", 35),
    DIAMOND("다이아", 50),
    MASTER("마스터", 70),
    GRANDMASTER("그랜드마스터", 90)
}

/** XP·레벨·랭크 계산. 저장소를 모르는 순수 로직. */
object Progress {
    const val MAX_LEVEL = 99

    /** level 에서 level+1 로 가는 데 필요한 XP */
    fun xpForNextLevel(level: Int): Int =
        (50.0 * level.toDouble().pow(1.2)).toInt()

    /** level 에 도달하기까지 필요한 누적 XP */
    fun totalXpForLevel(level: Int): Int {
        var sum = 0
        for (l in 1 until level) sum += xpForNextLevel(l)
        return sum
    }

    fun levelForTotalXp(totalXp: Int): Int {
        var level = 1
        var need = xpForNextLevel(1)
        var remain = totalXp
        while (level < MAX_LEVEL && remain >= need) {
            remain -= need
            level++
            need = xpForNextLevel(level)
        }
        return level
    }

    fun xpIntoLevel(totalXp: Int): Int =
        totalXp - totalXpForLevel(levelForTotalXp(totalXp))

    fun rankOf(level: Int): Rank =
        Rank.entries.last { level >= it.minLevel }

    fun freeMatchXp(score: Int): Int = score / 5

    fun stageXp(stage: Int, isBoss: Boolean): Int {
        val base = stage * 10 + 50
        return if (isBoss) base * 3 else base
    }
}
```

- [ ] **Step 5: 테스트를 돌려 통과를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.ProgressTest"`
Expected: PASS (7 tests)

`levelForTotalXp(Int.MAX_VALUE)` 테스트가 오래 걸리면 안 된다 — 99에서 루프가 멈추므로 즉시 끝나야 한다.

- [ ] **Step 6: 커밋**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/kkeutmal/game/Progress.kt app/src/test/java/com/kkeutmal/game/ProgressTest.kt
git commit -m "레벨·랭크 계산 로직과 단위 테스트 환경 추가"
```

---

## Task 2: BossRule (보스 규칙 판정)

**Files:**
- Create: `app/src/main/java/com/kkeutmal/game/BossRule.kt`
- Test: `app/src/test/java/com/kkeutmal/game/BossRuleTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `enum class BossRule(val label: String)` — `MIN_LEN_3, MIN_LEN_4, ENDS_WITH_JONGSEONG, TIME_8, AI_HANBANG`
    - `fun accepts(word: String): Boolean` — 단어에 거는 제약. 단어와 무관한 규칙(`TIME_8`, `AI_HANBANG`)은 항상 true
  - `fun List<BossRule>.acceptsWord(word: String): Boolean` — 모든 규칙을 AND
  - `fun List<BossRule>.rejectionMessage(): String` — 사용자에게 보여줄 안내 문구

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/BossRuleTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossRuleTest {

    @Test
    fun `세 글자 이상 규칙`() {
        assertFalse(BossRule.MIN_LEN_3.accepts("사과"))
        assertTrue(BossRule.MIN_LEN_3.accepts("자동차"))
        assertTrue(BossRule.MIN_LEN_3.accepts("고등학교"))
    }

    @Test
    fun `네 글자 이상 규칙`() {
        assertFalse(BossRule.MIN_LEN_4.accepts("자동차"))
        assertTrue(BossRule.MIN_LEN_4.accepts("고등학교"))
    }

    @Test
    fun `받침으로 끝나는 단어 규칙`() {
        assertFalse(BossRule.ENDS_WITH_JONGSEONG.accepts("사과"))   // 과: 받침 없음
        assertTrue(BossRule.ENDS_WITH_JONGSEONG.accepts("사람"))    // 람: 받침 ㅁ
        assertTrue(BossRule.ENDS_WITH_JONGSEONG.accepts("책상"))    // 상: 받침 ㅇ
    }

    @Test
    fun `단어와 무관한 규칙은 항상 통과`() {
        assertTrue(BossRule.TIME_8.accepts("사과"))
        assertTrue(BossRule.AI_HANBANG.accepts("사과"))
    }

    @Test
    fun `규칙 목록은 모두 만족해야 통과`() {
        val rules = listOf(BossRule.MIN_LEN_3, BossRule.ENDS_WITH_JONGSEONG)
        assertFalse(rules.acceptsWord("사람"))       // 2글자라 탈락
        assertFalse(rules.acceptsWord("자동차"))     // 차: 받침 없음
        assertTrue(rules.acceptsWord("고등학생"))    // 4글자 + 생: 받침 ㅇ
    }

    @Test
    fun `빈 규칙 목록은 모두 통과`() {
        assertTrue(emptyList<BossRule>().acceptsWord("사과"))
    }

    @Test
    fun `안내 문구는 단어 제약만 모아 보여준다`() {
        assertEquals("3글자 이상", listOf(BossRule.MIN_LEN_3).rejectionMessage())
        assertEquals(
            "3글자 이상 · 받침으로 끝나는 단어",
            listOf(BossRule.MIN_LEN_3, BossRule.ENDS_WITH_JONGSEONG).rejectionMessage()
        )
        assertEquals("", listOf(BossRule.TIME_8).rejectionMessage())
    }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.BossRuleTest"`
Expected: 컴파일 실패 — `Unresolved reference: BossRule`

- [ ] **Step 3: 최소 구현 작성**

`app/src/main/java/com/kkeutmal/game/BossRule.kt`:

```kotlin
package com.kkeutmal.game

/**
 * 보스가 거는 특수 규칙.
 * accepts 가 단어에 거는 제약이고, TIME_8·AI_HANBANG 처럼 단어와 무관한 규칙은 항상 true 를 준다.
 */
enum class BossRule(val label: String, val wordConstraint: Boolean) {
    MIN_LEN_3("3글자 이상", true),
    MIN_LEN_4("4글자 이상", true),
    ENDS_WITH_JONGSEONG("받침으로 끝나는 단어", true),
    TIME_8("제한시간 8초", false),
    AI_HANBANG("AI가 한방단어를 노림", false);

    fun accepts(word: String): Boolean = when (this) {
        MIN_LEN_3 -> word.length >= 3
        MIN_LEN_4 -> word.length >= 4
        ENDS_WITH_JONGSEONG -> hasJongseong(word.last())
        TIME_8, AI_HANBANG -> true
    }

    private fun hasJongseong(c: Char): Boolean {
        val code = c.code - 0xAC00
        if (code < 0 || code > 11171) return false
        return code % 28 != 0
    }
}

fun List<BossRule>.acceptsWord(word: String): Boolean =
    word.isNotEmpty() && all { it.accepts(word) }

fun List<BossRule>.rejectionMessage(): String =
    filter { it.wordConstraint }.joinToString(" · ") { it.label }
```

- [ ] **Step 4: 테스트를 돌려 통과를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.BossRuleTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/BossRule.kt app/src/test/java/com/kkeutmal/game/BossRuleTest.kt
git commit -m "보스 규칙 판정 로직 추가"
```

---

## Task 3: Stage (스테이지 파라미터와 보스 배치)

**Files:**
- Create: `app/src/main/java/com/kkeutmal/game/Stage.kt`
- Test: `app/src/test/java/com/kkeutmal/game/StageTest.kt`

**Interfaces:**
- Consumes: `BossRule`(Task 2), `AiLevel`(기존 `GameEngine.kt`의 `VERY_EASY/EASY/NORMAL/HARD`)
- Produces:
  - `data class Boss(val name: String, val rules: List<BossRule>)`
  - `data class StageConfig(val stage: Int, val timerSec: Int, val aiLevel: AiLevel, val targetRounds: Int, val allowHanbang: Boolean, val boss: Boss?)`
  - `object Stage { fun configFor(n: Int): StageConfig; fun isBossStage(n: Int): Boolean; fun stagesToNextBoss(n: Int): Int }`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/StageTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTest {

    @Test
    fun `제한시간은 30초에서 시작해 8초까지 줄어든다`() {
        assertEquals(30, Stage.configFor(1).timerSec)
        assertEquals(20, Stage.configFor(30).timerSec)
        assertEquals(10, Stage.configFor(60).timerSec)
        assertEquals(8, Stage.configFor(66).timerSec)
        assertEquals(8, Stage.configFor(200).timerSec)
    }

    @Test
    fun `AI 레벨이 구간별로 승격된다`() {
        assertEquals(AiLevel.VERY_EASY, Stage.configFor(1).aiLevel)
        assertEquals(AiLevel.VERY_EASY, Stage.configFor(5).aiLevel)
        assertEquals(AiLevel.EASY, Stage.configFor(6).aiLevel)
        assertEquals(AiLevel.EASY, Stage.configFor(15).aiLevel)
        assertEquals(AiLevel.NORMAL, Stage.configFor(16).aiLevel)
        assertEquals(AiLevel.NORMAL, Stage.configFor(30).aiLevel)
        assertEquals(AiLevel.HARD, Stage.configFor(31).aiLevel)
    }

    @Test
    fun `목표 라운드는 3에서 시작해 3스테이지마다 하나씩 는다`() {
        assertEquals(3, Stage.configFor(1).targetRounds)
        assertEquals(4, Stage.configFor(3).targetRounds)
        assertEquals(13, Stage.configFor(30).targetRounds)
        assertEquals(23, Stage.configFor(60).targetRounds)
    }

    @Test
    fun `한방단어는 31스테이지부터 허용된다`() {
        assertFalse(Stage.configFor(30).allowHanbang)
        assertTrue(Stage.configFor(31).allowHanbang)
    }

    @Test
    fun `보스는 5스테이지마다 나온다`() {
        assertNull(Stage.configFor(4).boss)
        assertNotNull(Stage.configFor(5).boss)
        assertNull(Stage.configFor(6).boss)
        assertNotNull(Stage.configFor(10).boss)
        assertTrue(Stage.isBossStage(25))
        assertFalse(Stage.isBossStage(26))
    }

    @Test
    fun `지정된 보스는 스펙과 일치한다`() {
        assertEquals(listOf(BossRule.MIN_LEN_3), Stage.configFor(5).boss!!.rules)
        assertEquals(listOf(BossRule.ENDS_WITH_JONGSEONG), Stage.configFor(10).boss!!.rules)
        assertEquals(listOf(BossRule.TIME_8), Stage.configFor(15).boss!!.rules)
        assertEquals(listOf(BossRule.AI_HANBANG), Stage.configFor(20).boss!!.rules)
        assertEquals(listOf(BossRule.MIN_LEN_4), Stage.configFor(25).boss!!.rules)
        assertEquals("세글자 도깨비", Stage.configFor(5).boss!!.name)
    }

    @Test
    fun `시간 도둑 보스는 제한시간을 8초로 덮어쓴다`() {
        // 15스테이지의 기본 제한시간은 25초지만 보스 규칙이 8초로 고정한다
        assertEquals(8, Stage.configFor(15).timerSec)
    }

    @Test
    fun `30스테이지 이상 보스는 서로 다른 규칙 두 개를 갖는다`() {
        for (n in listOf(30, 35, 40, 55, 100)) {
            val rules = Stage.configFor(n).boss!!.rules
            assertEquals("스테이지 $n", 2, rules.size)
            assertEquals("스테이지 $n 규칙 중복", 2, rules.toSet().size)
        }
    }

    @Test
    fun `혼합 보스는 같은 스테이지면 항상 같은 규칙이 나온다`() {
        repeat(5) {
            assertEquals(Stage.configFor(30).boss!!.rules, Stage.configFor(30).boss!!.rules)
            assertEquals(Stage.configFor(75).boss!!.rules, Stage.configFor(75).boss!!.rules)
        }
    }

    @Test
    fun `다음 보스까지 남은 스테이지 수`() {
        assertEquals(4, Stage.stagesToNextBoss(1))
        assertEquals(1, Stage.stagesToNextBoss(4))
        assertEquals(0, Stage.stagesToNextBoss(5))
        assertEquals(3, Stage.stagesToNextBoss(12))
    }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.StageTest"`
Expected: 컴파일 실패 — `Unresolved reference: Stage`

- [ ] **Step 3: 최소 구현 작성**

`app/src/main/java/com/kkeutmal/game/Stage.kt`:

```kotlin
package com.kkeutmal.game

import kotlin.random.Random

data class Boss(val name: String, val rules: List<BossRule>)

data class StageConfig(
    val stage: Int,
    val timerSec: Int,
    val aiLevel: AiLevel,
    val targetRounds: Int,
    val allowHanbang: Boolean,
    val boss: Boss?
)

/** 스테이지 번호만으로 난이도를 계산한다. 수제 데이터 없음. */
object Stage {
    private const val BOSS_EVERY = 5

    private val MIXED_POOL = listOf(
        BossRule.MIN_LEN_3,
        BossRule.ENDS_WITH_JONGSEONG,
        BossRule.TIME_8,
        BossRule.AI_HANBANG,
        BossRule.MIN_LEN_4
    )

    fun isBossStage(n: Int): Boolean = n > 0 && n % BOSS_EVERY == 0

    fun stagesToNextBoss(n: Int): Int = (BOSS_EVERY - n % BOSS_EVERY) % BOSS_EVERY

    fun configFor(n: Int): StageConfig {
        val boss = bossFor(n)
        val baseTimer = maxOf(8, 30 - n / 3)
        val timer = if (boss != null && BossRule.TIME_8 in boss.rules) 8 else baseTimer
        return StageConfig(
            stage = n,
            timerSec = timer,
            aiLevel = aiLevelFor(n),
            targetRounds = 3 + n / 3,
            allowHanbang = n >= 31,
            boss = boss
        )
    }

    private fun aiLevelFor(n: Int): AiLevel = when {
        n <= 5 -> AiLevel.VERY_EASY
        n <= 15 -> AiLevel.EASY
        n <= 30 -> AiLevel.NORMAL
        else -> AiLevel.HARD
    }

    private fun bossFor(n: Int): Boss? {
        if (!isBossStage(n)) return null
        return when (n) {
            5 -> Boss("세글자 도깨비", listOf(BossRule.MIN_LEN_3))
            10 -> Boss("받침 지킴이", listOf(BossRule.ENDS_WITH_JONGSEONG))
            15 -> Boss("시간 도둑", listOf(BossRule.TIME_8))
            20 -> Boss("한방 마왕", listOf(BossRule.AI_HANBANG))
            25 -> Boss("긴말 여왕", listOf(BossRule.MIN_LEN_4))
            else -> Boss("혼돈의 문지기", mixedRules(n))
        }
    }

    /** 스테이지 번호를 시드로 써서 같은 스테이지는 항상 같은 규칙이 나오게 한다. */
    private fun mixedRules(n: Int): List<BossRule> =
        MIXED_POOL.shuffled(Random(n.toLong())).take(2)
}
```

- [ ] **Step 4: 테스트를 돌려 통과를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.StageTest"`
Expected: PASS (10 tests)

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/Stage.kt app/src/test/java/com/kkeutmal/game/StageTest.kt
git commit -m "스테이지 난이도 곡선과 보스 배치 로직 추가"
```

---

## Task 4: AvatarCatalog (아바타 48종 정의)

**Files:**
- Create: `app/src/main/java/com/kkeutmal/game/AvatarCatalog.kt`
- Test: `app/src/test/java/com/kkeutmal/game/AvatarCatalogTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `enum class AvatarShape(val id: String, val label: String, val price: Int)` — `SQUARE("square","네모",150), CIRCLE("circle","동글이",200), SQUIRCLE("squircle","모난동글",250), RHOMBUS("rhombus","마름모",300)`
  - `enum class AvatarColor(val id: String, val label: String)` — `RED, PURPLE, GREEN, BLUE, PINK, YELLOW`
  - `enum class AvatarFace(val id: String, val label: String, val eyeAsset: String, val mouthAsset: String)` — `BASIC, SPECIAL`
  - `enum class AvatarGrade(val label: String)` — `COMMON, RARE, EPIC, LEGENDARY`
  - `sealed class Unlock { data class Coin(val price: Int); data class Level(val level: Int); data class BossClear(val stage: Int); data class Achieve(val achievementId: String) }`
  - `data class AvatarDef(val id: String, val name: String, val shape: AvatarShape, val color: AvatarColor, val face: AvatarFace, val grade: AvatarGrade, val unlock: Unlock)`
    - `val bodyAsset: String` — `"${color.id}_body_${shape.id}"`
  - `object AvatarCatalog { val ALL: List<AvatarDef>; const val DEFAULT_ID = "square_blue_basic"; fun byId(id: String): AvatarDef?; fun unlockDescription(def: AvatarDef): String }`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/AvatarCatalogTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCatalogTest {

    @Test
    fun `아바타는 형태 4 곱하기 색 6 곱하기 표정 2 로 48종`() {
        assertEquals(48, AvatarCatalog.ALL.size)
        assertEquals(48, AvatarCatalog.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `등급별 개수가 스펙과 일치한다`() {
        val byGrade = AvatarCatalog.ALL.groupingBy { it.grade }.eachCount()
        assertEquals(24, byGrade[AvatarGrade.COMMON])
        assertEquals(12, byGrade[AvatarGrade.RARE])
        assertEquals(8, byGrade[AvatarGrade.EPIC])
        assertEquals(4, byGrade[AvatarGrade.LEGENDARY])
    }

    @Test
    fun `기본 표정은 전부 일반 등급이고 코인으로 산다`() {
        val basics = AvatarCatalog.ALL.filter { it.face == AvatarFace.BASIC }
        assertEquals(24, basics.size)
        assertTrue(basics.all { it.grade == AvatarGrade.COMMON })
        assertTrue(basics.all { it.unlock is Unlock.Coin })
    }

    @Test
    fun `일반 등급 가격은 몸 형태로 정해진다`() {
        val square = AvatarCatalog.ALL.first { it.shape == AvatarShape.SQUARE && it.face == AvatarFace.BASIC }
        val rhombus = AvatarCatalog.ALL.first { it.shape == AvatarShape.RHOMBUS && it.face == AvatarFace.BASIC }
        assertEquals(150, (square.unlock as Unlock.Coin).price)
        assertEquals(300, (rhombus.unlock as Unlock.Coin).price)
    }

    @Test
    fun `아이디와 이름은 규칙적으로 생성된다`() {
        val def = AvatarCatalog.byId("square_blue_basic")
        assertNotNull(def)
        assertEquals("방긋 파랑 네모", def!!.name)
        assertEquals("blue_body_square", def.bodyAsset)
    }

    @Test
    fun `기본 아바타는 카탈로그에 있고 가장 싸다`() {
        val def = AvatarCatalog.byId(AvatarCatalog.DEFAULT_ID)
        assertNotNull(def)
        assertEquals(AvatarGrade.COMMON, def!!.grade)
    }

    @Test
    fun `희귀는 레벨 12개 영웅은 보스 8개 전설은 도전과제 4개로 해금된다`() {
        val rare = AvatarCatalog.ALL.filter { it.grade == AvatarGrade.RARE }
        assertEquals(
            listOf(5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 80),
            rare.map { (it.unlock as Unlock.Level).level }.sorted()
        )
        val epic = AvatarCatalog.ALL.filter { it.grade == AvatarGrade.EPIC }
        assertEquals(
            listOf(5, 10, 15, 20, 25, 30, 35, 40),
            epic.map { (it.unlock as Unlock.BossClear).stage }.sorted()
        )
        val legendary = AvatarCatalog.ALL.filter { it.grade == AvatarGrade.LEGENDARY }
        assertEquals(4, legendary.size)
        assertTrue(legendary.all { it.unlock is Unlock.Achieve })
    }

    @Test
    fun `해금 안내 문구가 사람이 읽을 수 있게 나온다`() {
        val rare = AvatarCatalog.ALL.first { it.grade == AvatarGrade.RARE && (it.unlock as Unlock.Level).level == 5 }
        assertEquals("레벨 5 달성", AvatarCatalog.unlockDescription(rare))
        val epic = AvatarCatalog.ALL.first { it.grade == AvatarGrade.EPIC && (it.unlock as Unlock.BossClear).stage == 15 }
        assertEquals("15스테이지 보스 클리어", AvatarCatalog.unlockDescription(epic))
    }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.AvatarCatalogTest"`
Expected: 컴파일 실패 — `Unresolved reference: AvatarCatalog`

- [ ] **Step 3: 최소 구현 작성**

`app/src/main/java/com/kkeutmal/game/AvatarCatalog.kt`:

```kotlin
package com.kkeutmal.game

enum class AvatarShape(val id: String, val label: String, val price: Int) {
    SQUARE("square", "네모", 150),
    CIRCLE("circle", "동글이", 200),
    SQUIRCLE("squircle", "모난동글", 250),
    RHOMBUS("rhombus", "마름모", 300)
}

enum class AvatarColor(val id: String, val label: String) {
    RED("red", "빨강"),
    PURPLE("purple", "보라"),
    GREEN("green", "초록"),
    BLUE("blue", "파랑"),
    PINK("pink", "분홍"),
    YELLOW("yellow", "노랑")
}

enum class AvatarFace(val id: String, val label: String, val eyeAsset: String, val mouthAsset: String) {
    BASIC("basic", "방긋", "facial_part_eye_open", "facial_part_mouth_happy"),
    SPECIAL("special", "새침", "facial_part_eye_half_top", "facial_part_mouth_smirk")
}

enum class AvatarGrade(val label: String) {
    COMMON("일반"), RARE("희귀"), EPIC("영웅"), LEGENDARY("전설")
}

sealed class Unlock {
    data class Coin(val price: Int) : Unlock()
    data class Level(val level: Int) : Unlock()
    data class BossClear(val stage: Int) : Unlock()
    data class Achieve(val achievementId: String) : Unlock()
}

data class AvatarDef(
    val id: String,
    val name: String,
    val shape: AvatarShape,
    val color: AvatarColor,
    val face: AvatarFace,
    val grade: AvatarGrade,
    val unlock: Unlock
) {
    val bodyAsset: String get() = "${color.id}_body_${shape.id}"
}

object AvatarCatalog {
    const val DEFAULT_ID = "square_blue_basic"

    private val RARE_LEVELS = listOf(5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 80)
    private val EPIC_BOSS_STAGES = listOf(5, 10, 15, 20, 25, 30, 35, 40)
    private val LEGENDARY_ACHIEVEMENTS = listOf("ach_rounds_20", "ach_stage_50", "ach_collect_30", "ach_streak_7")

    val ALL: List<AvatarDef> = buildList {
        // 기본 표정 24종 = 일반 등급
        for (shape in AvatarShape.entries) {
            for (color in AvatarColor.entries) {
                add(make(shape, color, AvatarFace.BASIC, AvatarGrade.COMMON, Unlock.Coin(shape.price)))
            }
        }
        // 특수 표정 24종 = 희귀 12 + 영웅 8 + 전설 4
        var index = 0
        for (shape in AvatarShape.entries) {
            for (color in AvatarColor.entries) {
                val (grade, unlock) = when {
                    index < 12 -> AvatarGrade.RARE to Unlock.Level(RARE_LEVELS[index])
                    index < 20 -> AvatarGrade.EPIC to Unlock.BossClear(EPIC_BOSS_STAGES[index - 12])
                    else -> AvatarGrade.LEGENDARY to Unlock.Achieve(LEGENDARY_ACHIEVEMENTS[index - 20])
                }
                add(make(shape, color, AvatarFace.SPECIAL, grade, unlock))
                index++
            }
        }
    }

    private val byId: Map<String, AvatarDef> = ALL.associateBy { it.id }

    fun byId(id: String): AvatarDef? = byId[id]

    fun unlockDescription(def: AvatarDef): String = when (val u = def.unlock) {
        is Unlock.Coin -> "${u.price}코인"
        is Unlock.Level -> "레벨 ${u.level} 달성"
        is Unlock.BossClear -> "${u.stage}스테이지 보스 클리어"
        is Unlock.Achieve -> Achievements.labelOf(u.achievementId)
    }

    private fun make(
        shape: AvatarShape,
        color: AvatarColor,
        face: AvatarFace,
        grade: AvatarGrade,
        unlock: Unlock
    ) = AvatarDef(
        id = "${shape.id}_${color.id}_${face.id}",
        name = "${face.label} ${color.label} ${shape.label}",
        shape = shape,
        color = color,
        face = face,
        grade = grade,
        unlock = unlock
    )
}
```

`unlockDescription`이 `Achievements.labelOf`를 부르므로 Task 6과 순환처럼 보이지만, Task 6에서 만들 `Achievements`는 `AvatarCatalog`를 참조하지 않으므로 순환 참조가 아니다. Task 6을 먼저 끝내지 않았다면 이 줄만 임시로 `u.achievementId`를 반환하게 두고, Task 6에서 되돌린다.

- [ ] **Step 4: 테스트를 돌려 통과를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.AvatarCatalogTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/AvatarCatalog.kt app/src/test/java/com/kkeutmal/game/AvatarCatalogTest.kt
git commit -m "아바타 48종 카탈로그와 등급·해금 조건 정의"
```

---

## Task 5: Achievements (도전과제 판정)

**Files:**
- Create: `app/src/main/java/com/kkeutmal/game/Achievements.kt`
- Test: `app/src/test/java/com/kkeutmal/game/AchievementsTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `data class PlayerStats(val bestRounds: Int, val bestStage: Int, val ownedAvatarCount: Int, val bestStreak: Int)`
  - `enum class Achievement(val id: String, val label: String)` — `ROUNDS_20("ach_rounds_20", "한 판에서 20라운드 버티기")`, `STAGE_50("ach_stage_50", "50스테이지 도달")`, `COLLECT_30("ach_collect_30", "아바타 30종 수집")`, `STREAK_7("ach_streak_7", "7일 연속 출석")`
    - `fun isMet(stats: PlayerStats): Boolean`
  - `object Achievements { fun labelOf(id: String): String; fun metBy(stats: PlayerStats): List<Achievement> }`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/AchievementsTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private val none = PlayerStats(bestRounds = 0, bestStage = 0, ownedAvatarCount = 0, bestStreak = 0)

    @Test
    fun `도전과제는 4개이고 아바타 카탈로그의 전설 해금 아이디와 일치한다`() {
        assertEquals(4, Achievement.entries.size)
        val fromCatalog = AvatarCatalog.ALL
            .filter { it.grade == AvatarGrade.LEGENDARY }
            .map { (it.unlock as Unlock.Achieve).achievementId }
            .toSet()
        assertEquals(Achievement.entries.map { it.id }.toSet(), fromCatalog)
    }

    @Test
    fun `아무것도 안 했으면 달성한 게 없다`() {
        assertTrue(Achievements.metBy(none).isEmpty())
    }

    @Test
    fun `20라운드 도전과제`() {
        assertFalse(Achievement.ROUNDS_20.isMet(none.copy(bestRounds = 19)))
        assertTrue(Achievement.ROUNDS_20.isMet(none.copy(bestRounds = 20)))
    }

    @Test
    fun `50스테이지 도전과제`() {
        assertFalse(Achievement.STAGE_50.isMet(none.copy(bestStage = 49)))
        assertTrue(Achievement.STAGE_50.isMet(none.copy(bestStage = 50)))
    }

    @Test
    fun `30종 수집 도전과제`() {
        assertFalse(Achievement.COLLECT_30.isMet(none.copy(ownedAvatarCount = 29)))
        assertTrue(Achievement.COLLECT_30.isMet(none.copy(ownedAvatarCount = 30)))
    }

    @Test
    fun `7일 연속 출석 도전과제`() {
        assertFalse(Achievement.STREAK_7.isMet(none.copy(bestStreak = 6)))
        assertTrue(Achievement.STREAK_7.isMet(none.copy(bestStreak = 7)))
    }

    @Test
    fun `달성한 것만 골라 준다`() {
        val stats = PlayerStats(bestRounds = 25, bestStage = 10, ownedAvatarCount = 30, bestStreak = 2)
        assertEquals(
            listOf(Achievement.ROUNDS_20, Achievement.COLLECT_30),
            Achievements.metBy(stats)
        )
    }

    @Test
    fun `아이디로 라벨을 찾는다`() {
        assertEquals("50스테이지 도달", Achievements.labelOf("ach_stage_50"))
    }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.AchievementsTest"`
Expected: 컴파일 실패 — `Unresolved reference: Achievement`

- [ ] **Step 3: 최소 구현 작성**

`app/src/main/java/com/kkeutmal/game/Achievements.kt`:

```kotlin
package com.kkeutmal.game

/** 도전과제 판정에 쓰는 누적 기록 묶음. 저장소를 모르는 값 객체. */
data class PlayerStats(
    val bestRounds: Int,
    val bestStage: Int,
    val ownedAvatarCount: Int,
    val bestStreak: Int
)

enum class Achievement(val id: String, val label: String) {
    ROUNDS_20("ach_rounds_20", "한 판에서 20라운드 버티기"),
    STAGE_50("ach_stage_50", "50스테이지 도달"),
    COLLECT_30("ach_collect_30", "아바타 30종 수집"),
    STREAK_7("ach_streak_7", "7일 연속 출석");

    fun isMet(stats: PlayerStats): Boolean = when (this) {
        ROUNDS_20 -> stats.bestRounds >= 20
        STAGE_50 -> stats.bestStage >= 50
        COLLECT_30 -> stats.ownedAvatarCount >= 30
        STREAK_7 -> stats.bestStreak >= 7
    }
}

object Achievements {
    fun labelOf(id: String): String =
        Achievement.entries.firstOrNull { it.id == id }?.label ?: id

    fun metBy(stats: PlayerStats): List<Achievement> =
        Achievement.entries.filter { it.isMet(stats) }
}
```

- [ ] **Step 4: 테스트를 돌려 통과를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.AchievementsTest"`
Expected: PASS (8 tests)

Task 4에서 `unlockDescription`을 임시 처리했다면 이제 `Achievements.labelOf(u.achievementId)`로 되돌리고 `AvatarCatalogTest`도 다시 통과하는지 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/Achievements.kt app/src/test/java/com/kkeutmal/game/AchievementsTest.kt app/src/main/java/com/kkeutmal/game/AvatarCatalog.kt
git commit -m "도전과제 판정 로직 추가"
```

---

## Task 6: Missions (일일 미션과 연속 출석)

**Files:**
- Create: `app/src/main/java/com/kkeutmal/game/Missions.kt`
- Test: `app/src/test/java/com/kkeutmal/game/MissionsTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `enum class Aggregate { SUM, MAX }`
  - `enum class Mission(val id: String, val label: String, val target: Int, val reward: Int, val aggregate: Aggregate)` — 7종
  - `data class StreakResult(val days: Int, val reward: Int, val isNewDay: Boolean)`
  - `object Missions`
    - `const val ALL_CLEAR_BONUS = 50`
    - `fun pickDaily(dateKey: String): List<Mission>`
    - `fun applyProgress(mission: Mission, current: Int, amount: Int): Int`
    - `fun isComplete(mission: Mission, progress: Int): Boolean`
    - `fun streakRewardFor(days: Int): Int`
    - `fun advanceStreak(lastDateKey: String?, todayKey: String, currentDays: Int): StreakResult`

날짜 문자열은 `yyyy-MM-dd` 형식을 쓴다. 저장은 Task 8(Wallet)에서 연결한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/MissionsTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionsTest {

    @Test
    fun `미션 풀은 7종이고 아이디가 겹치지 않는다`() {
        assertEquals(7, Mission.entries.size)
        assertEquals(7, Mission.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `하루에 서로 다른 미션 3개를 뽑는다`() {
        val picked = Missions.pickDaily("2026-07-26")
        assertEquals(3, picked.size)
        assertEquals(3, picked.toSet().size)
    }

    @Test
    fun `같은 날짜면 항상 같은 미션이 나온다`() {
        assertEquals(Missions.pickDaily("2026-07-26"), Missions.pickDaily("2026-07-26"))
    }

    @Test
    fun `날짜가 다르면 조합이 달라진다`() {
        val week = listOf(
            "2026-07-26", "2026-07-27", "2026-07-28", "2026-07-29",
            "2026-07-30", "2026-07-31", "2026-08-01"
        ).map { Missions.pickDaily(it) }
        // 7일치가 전부 같은 조합이면 시드가 날짜를 반영하지 못한 것이다
        assertTrue(week.toSet().size > 1)
    }

    @Test
    fun `누적형 미션은 값을 더한다`() {
        val m = Mission.PLAY_3
        assertEquals(Aggregate.SUM, m.aggregate)
        assertEquals(2, Missions.applyProgress(m, current = 1, amount = 1))
    }

    @Test
    fun `최대형 미션은 더 큰 값만 남긴다`() {
        val m = Mission.ROUNDS_5
        assertEquals(Aggregate.MAX, m.aggregate)
        assertEquals(7, Missions.applyProgress(m, current = 7, amount = 3))
        assertEquals(9, Missions.applyProgress(m, current = 7, amount = 9))
    }

    @Test
    fun `진행도가 목표에 닿으면 완료다`() {
        assertFalse(Missions.isComplete(Mission.PLAY_3, 2))
        assertTrue(Missions.isComplete(Mission.PLAY_3, 3))
        assertTrue(Missions.isComplete(Mission.PLAY_3, 5))
    }

    @Test
    fun `연속 출석 보상은 2일차부터 7일차까지 커진다`() {
        assertEquals(0, Missions.streakRewardFor(1))
        assertEquals(20, Missions.streakRewardFor(2))
        assertEquals(30, Missions.streakRewardFor(3))
        assertEquals(60, Missions.streakRewardFor(6))
        assertEquals(100, Missions.streakRewardFor(7))
    }

    @Test
    fun `연속 출석 보상은 7일 주기로 반복된다`() {
        assertEquals(20, Missions.streakRewardFor(9))   // 9 = 7 + 2일차
        assertEquals(100, Missions.streakRewardFor(14))
    }

    @Test
    fun `첫 플레이는 1일차로 시작한다`() {
        val r = Missions.advanceStreak(lastDateKey = null, todayKey = "2026-07-26", currentDays = 0)
        assertEquals(1, r.days)
        assertTrue(r.isNewDay)
    }

    @Test
    fun `어제 했으면 연속이 이어진다`() {
        val r = Missions.advanceStreak("2026-07-25", "2026-07-26", 3)
        assertEquals(4, r.days)
        assertTrue(r.isNewDay)
    }

    @Test
    fun `같은 날 또 하면 연속이 안 늘어난다`() {
        val r = Missions.advanceStreak("2026-07-26", "2026-07-26", 4)
        assertEquals(4, r.days)
        assertFalse(r.isNewDay)
        assertEquals(0, r.reward)
    }

    @Test
    fun `하루를 건너뛰면 1일차로 초기화된다`() {
        val r = Missions.advanceStreak("2026-07-24", "2026-07-26", 9)
        assertEquals(1, r.days)
        assertTrue(r.isNewDay)
    }

    @Test
    fun `월을 넘겨도 연속 판정이 된다`() {
        val r = Missions.advanceStreak("2026-07-31", "2026-08-01", 2)
        assertEquals(3, r.days)
    }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.MissionsTest"`
Expected: 컴파일 실패 — `Unresolved reference: Mission`

- [ ] **Step 3: 최소 구현 작성**

`app/src/main/java/com/kkeutmal/game/Missions.kt`:

```kotlin
package com.kkeutmal.game

import java.time.LocalDate
import kotlin.random.Random

enum class Aggregate { SUM, MAX }

enum class Mission(
    val id: String,
    val label: String,
    val target: Int,
    val reward: Int,
    val aggregate: Aggregate
) {
    PLAY_3("m_play3", "게임 3판 하기", 3, 30, Aggregate.SUM),
    ROUNDS_5("m_rounds5", "5라운드 이상 버티기", 5, 40, Aggregate.MAX),
    LONG_WORD_3("m_long3", "4글자 이상 단어 3번 쓰기", 3, 40, Aggregate.SUM),
    STAGE_2("m_stage2", "모험 스테이지 2개 클리어", 2, 50, Aggregate.SUM),
    SURRENDER_1("m_surrender1", "AI 항복시키기", 1, 50, Aggregate.SUM),
    VOICE_5("m_voice5", "음성으로 단어 5번 내기", 5, 40, Aggregate.SUM),
    SCORE_300("m_score300", "누적 300점 얻기", 300, 30, Aggregate.SUM)
}

data class StreakResult(val days: Int, val reward: Int, val isNewDay: Boolean)

/** 일일 미션과 연속 출석. 날짜는 yyyy-MM-dd 문자열로 주고받아 테스트 가능하게 한다. */
object Missions {
    const val ALL_CLEAR_BONUS = 50

    private val STREAK_REWARDS = listOf(0, 20, 30, 40, 50, 60, 100) // 1~7일차

    fun pickDaily(dateKey: String): List<Mission> =
        Mission.entries.shuffled(Random(dateKey.hashCode().toLong())).take(3)

    fun applyProgress(mission: Mission, current: Int, amount: Int): Int =
        when (mission.aggregate) {
            Aggregate.SUM -> current + amount
            Aggregate.MAX -> maxOf(current, amount)
        }

    fun isComplete(mission: Mission, progress: Int): Boolean = progress >= mission.target

    fun streakRewardFor(days: Int): Int {
        if (days <= 0) return 0
        val index = (days - 1) % 7
        return STREAK_REWARDS[index]
    }

    fun advanceStreak(lastDateKey: String?, todayKey: String, currentDays: Int): StreakResult {
        if (lastDateKey == todayKey) {
            return StreakResult(days = currentDays, reward = 0, isNewDay = false)
        }
        val days = when {
            lastDateKey == null -> 1
            isYesterday(lastDateKey, todayKey) -> currentDays + 1
            else -> 1
        }
        return StreakResult(days = days, reward = streakRewardFor(days), isNewDay = true)
    }

    private fun isYesterday(lastKey: String, todayKey: String): Boolean = try {
        LocalDate.parse(lastKey).plusDays(1) == LocalDate.parse(todayKey)
    } catch (_: Exception) {
        false
    }
}
```

`java.time.LocalDate`는 minSdk 26에서 그대로 쓸 수 있다(API 26에 추가됨).

- [ ] **Step 4: 테스트를 돌려 통과를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.MissionsTest"`
Expected: PASS (14 tests)

`날짜가 다르면 조합이 달라진다` 테스트가 실패하면 `dateKey.hashCode()`가 날짜별로 충분히 흩어지지 않은 것이다. 시드를 `dateKey.replace("-","").toLong()`으로 바꾼다.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/Missions.kt app/src/test/java/com/kkeutmal/game/MissionsTest.kt
git commit -m "일일 미션 풀과 연속 출석 판정 로직 추가"
```

---

## Task 7: GameResult (보상 계산 분리)

**Files:**
- Create: `app/src/main/java/com/kkeutmal/game/GameResult.kt`
- Test: `app/src/test/java/com/kkeutmal/game/GameResultTest.kt`

**Interfaces:**
- Consumes: `Progress`(Task 1), `Stage`(Task 3)
- Produces:
  - `enum class GameMode { FREE, ADVENTURE }`
  - `data class GameOutcome(val mode: GameMode, val won: Boolean, val score: Int, val rounds: Int, val stage: Int, val isBoss: Boolean, val doubleReward: Boolean)`
  - `data class Reward(val coins: Int, val xp: Int)`
  - `object GameResult { fun rewardFor(outcome: GameOutcome): Reward }`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/GameResultTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameResultTest {

    private fun free(score: Int, won: Boolean) = GameOutcome(
        mode = GameMode.FREE, won = won, score = score, rounds = 5,
        stage = 0, isBoss = false, doubleReward = false
    )

    private fun adventure(stage: Int, won: Boolean, isBoss: Boolean = false, doubleReward: Boolean = false) =
        GameOutcome(
            mode = GameMode.ADVENTURE, won = won, score = 0, rounds = 5,
            stage = stage, isBoss = isBoss, doubleReward = doubleReward
        )

    @Test
    fun `자유 대전 보상은 점수 기반이고 승리 보너스가 붙는다`() {
        assertEquals(Reward(coins = 30, xp = 60), GameResult.rewardFor(free(300, won = false)))
        assertEquals(Reward(coins = 50, xp = 60), GameResult.rewardFor(free(300, won = true)))
    }

    @Test
    fun `모험은 클리어했을 때만 보상을 준다`() {
        assertEquals(Reward(coins = 0, xp = 0), GameResult.rewardFor(adventure(10, won = false)))
        assertEquals(Reward(coins = 30, xp = 150), GameResult.rewardFor(adventure(10, won = true)))
    }

    @Test
    fun `보스 클리어는 코인과 XP가 3배다`() {
        assertEquals(Reward(coins = 90, xp = 450), GameResult.rewardFor(adventure(10, won = true, isBoss = true)))
    }

    @Test
    fun `2배 획득 아이템은 코인과 XP를 모두 2배로 만든다`() {
        assertEquals(
            Reward(coins = 60, xp = 300),
            GameResult.rewardFor(adventure(10, won = true, doubleReward = true))
        )
    }

    @Test
    fun `모험 실패는 2배 아이템을 써도 0이다`() {
        assertEquals(
            Reward(coins = 0, xp = 0),
            GameResult.rewardFor(adventure(10, won = false, doubleReward = true))
        )
    }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.GameResultTest"`
Expected: 컴파일 실패 — `Unresolved reference: GameResult`

- [ ] **Step 3: 최소 구현 작성**

`app/src/main/java/com/kkeutmal/game/GameResult.kt`:

```kotlin
package com.kkeutmal.game

enum class GameMode { FREE, ADVENTURE }

data class GameOutcome(
    val mode: GameMode,
    val won: Boolean,
    val score: Int,
    val rounds: Int,
    val stage: Int,
    val isBoss: Boolean,
    val doubleReward: Boolean
)

data class Reward(val coins: Int, val xp: Int)

/** 한 판이 끝났을 때 줄 코인·XP 계산. 저장은 호출하는 쪽이 한다. */
object GameResult {
    fun rewardFor(outcome: GameOutcome): Reward {
        val base = when (outcome.mode) {
            GameMode.FREE -> Reward(
                coins = outcome.score / 10 + if (outcome.won) 20 else 0,
                xp = Progress.freeMatchXp(outcome.score)
            )
            GameMode.ADVENTURE -> {
                if (!outcome.won) Reward(0, 0)
                else {
                    val coins = 10 + outcome.stage * 2
                    Reward(
                        coins = if (outcome.isBoss) coins * 3 else coins,
                        xp = Progress.stageXp(outcome.stage, outcome.isBoss)
                    )
                }
            }
        }
        return if (outcome.doubleReward) Reward(base.coins * 2, base.xp * 2) else base
    }
}
```

- [ ] **Step 4: 테스트를 돌려 통과를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.GameResultTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/GameResult.kt app/src/test/java/com/kkeutmal/game/GameResultTest.kt
git commit -m "게임 종료 보상 계산 로직 분리"
```

---

## Task 8: Wallet v2 (아바타 ID 전환·마이그레이션·아이템 2종)

**Files:**
- Modify: `app/src/main/java/com/kkeutmal/game/Wallet.kt`
- Test: `app/src/test/java/com/kkeutmal/game/WalletMigrationTest.kt`

**Interfaces:**
- Consumes: `AvatarCatalog`(Task 4), `Progress`(Task 1), `Missions`(Task 6), `Achievement`(Task 5)
- Produces (`Wallet` 오브젝트에 추가):
  - `fun migratedAvatarIds(oldEmojis: Set<String>): Set<String>` — **순수 함수**, 테스트 대상
  - `fun ensureMigrated(ctx: Context)`
  - `fun ownedAvatarIds(ctx: Context): Set<String>` / `fun ownAvatarId(ctx: Context, id: String)`
  - `fun selectedAvatarId(ctx: Context): String` / `fun selectAvatarId(ctx: Context, id: String)`
  - `fun xp(ctx): Int` / `fun addXp(ctx, amount: Int): Int` (반환 = 오른 레벨 수)
  - `fun level(ctx): Int`
  - `fun stage(ctx): Int` / `fun setStage(ctx, n: Int)` / `fun bestStage(ctx): Int`
  - `fun bestRounds(ctx): Int` / `fun recordRounds(ctx, rounds: Int)`
  - `fun playerStats(ctx): PlayerStats`
  - `ITEMS`에 `item_revive`(🛡 부활, 200), `item_double`(🎯 2배 획득, 120) 추가

기존 `AVATARS`(이모지 목록)와 `ownedAvatars`/`selectedAvatar`는 **삭제하지 않는다**. 마이그레이션이 읽어야 한다. `@Deprecated`만 붙인다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/WalletMigrationTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletMigrationTest {

    @Test
    fun `아무것도 없으면 기본 아바타만 준다`() {
        val ids = Wallet.migratedAvatarIds(emptySet())
        assertEquals(setOf(AvatarCatalog.DEFAULT_ID), ids)
    }

    @Test
    fun `기존에 산 이모지 개수만큼 일반 아바타를 준다`() {
        val old = setOf("🙂", "😎", "🐱", "🐶")
        val ids = Wallet.migratedAvatarIds(old)
        // 기본 아바타 + 이모지 4개분 = 최소 4종 (기본이 이미 포함될 수 있어 4 이상)
        assertTrue(ids.size >= 4)
        assertTrue(AvatarCatalog.DEFAULT_ID in ids)
    }

    @Test
    fun `변환 결과는 전부 실재하는 일반 등급 아바타다`() {
        val ids = Wallet.migratedAvatarIds(setOf("🙂", "😎", "🐱", "🐶", "🦊", "🐼"))
        for (id in ids) {
            val def = AvatarCatalog.byId(id)
            assertTrue("존재하지 않는 아바타: $id", def != null)
            assertEquals(AvatarGrade.COMMON, def!!.grade)
        }
    }

    @Test
    fun `이모지가 아무리 많아도 일반 등급 24종을 넘지 않는다`() {
        val many = (1..100).map { "e$it" }.toSet()
        val ids = Wallet.migratedAvatarIds(many)
        assertEquals(24, ids.size)
    }

    @Test
    fun `같은 입력이면 항상 같은 결과가 나온다`() {
        val old = setOf("🙂", "🦊", "👑")
        assertEquals(Wallet.migratedAvatarIds(old), Wallet.migratedAvatarIds(old))
    }

    @Test
    fun `새 아이템 두 종이 추가돼 있다`() {
        val ids = Wallet.ITEMS.map { it.id }
        assertTrue("item_revive" in ids)
        assertTrue("item_double" in ids)
        assertEquals(200, Wallet.ITEMS.first { it.id == "item_revive" }.price)
        assertEquals(120, Wallet.ITEMS.first { it.id == "item_double" }.price)
    }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.WalletMigrationTest"`
Expected: FAIL — `Unresolved reference: migratedAvatarIds`

- [ ] **Step 3: 마이그레이션 순수 함수를 먼저 구현**

`Wallet.kt`에 추가:

```kotlin
    /**
     * 구버전(이모지) 아바타 보유 목록을 새 아바타 ID 집합으로 변환한다.
     * 이모지 개수만큼 일반 등급을 앞에서부터 지급하고, 기본 아바타는 항상 포함한다.
     */
    fun migratedAvatarIds(oldEmojis: Set<String>): Set<String> {
        val commons = AvatarCatalog.ALL.filter { it.grade == AvatarGrade.COMMON }.map { it.id }
        val count = oldEmojis.size.coerceIn(1, commons.size)
        return (commons.take(count) + AvatarCatalog.DEFAULT_ID).toSet()
    }
```

`AvatarCatalog.DEFAULT_ID`(`square_blue_basic`)는 일반 등급 목록의 앞쪽에 있으므로 `take(count)`에 이미 들어갈 수도 있다. `toSet()`이 중복을 흡수하므로 `이모지가 아무리 많아도 24종` 테스트가 통과한다.

- [ ] **Step 4: 테스트를 돌려 마이그레이션 부분만 통과 확인**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.WalletMigrationTest"`
Expected: 아이템 테스트만 FAIL, 나머지 5개 PASS

- [ ] **Step 5: 나머지 저장소 API와 새 아이템 추가**

`Wallet.kt`의 `ITEMS`를 다음으로 교체:

```kotlin
    val ITEMS = listOf(
        Item("item_time", "⏰", "시간 +15초", "타이머에 15초를 더해요 (제한시간 모드 전용)", 60),
        Item("item_hint", "💡", "힌트", "낼 수 있는 단어를 하나 알려줘요", 80),
        Item("item_pass", "🔄", "단어 바꾸기", "AI가 낸 단어를 다른 단어로 바꿔요", 120),
        Item("item_double", "🎯", "2배 획득", "그 판의 코인과 경험치를 2배로 받아요", 120),
        Item("item_revive", "🛡", "부활", "게임 오버를 1회 무효로 만들고 이어서 해요", 200)
    )
```

같은 파일에 저장소 API를 추가:

```kotlin
    // ---------- 마이그레이션 ----------

    fun ensureMigrated(ctx: Context) {
        val prefs = p(ctx)
        if (prefs.getBoolean("avatar_migrated_v2", false)) return
        @Suppress("DEPRECATION")
        val oldOwned = prefs.getStringSet("owned_avatars", emptySet()) ?: emptySet()
        prefs.edit()
            .putStringSet("owned_avatars_v2", migratedAvatarIds(oldOwned))
            .putString("sel_avatar_v2", AvatarCatalog.DEFAULT_ID)
            .putBoolean("avatar_migrated_v2", true)
            .apply()
    }

    // ---------- 아바타 ----------

    fun ownedAvatarIds(ctx: Context): Set<String> {
        ensureMigrated(ctx)
        return p(ctx).getStringSet("owned_avatars_v2", emptySet()) ?: emptySet()
    }

    fun ownAvatarId(ctx: Context, id: String) {
        p(ctx).edit().putStringSet("owned_avatars_v2", ownedAvatarIds(ctx) + id).apply()
    }

    fun selectedAvatarId(ctx: Context): String {
        ensureMigrated(ctx)
        return p(ctx).getString("sel_avatar_v2", AvatarCatalog.DEFAULT_ID) ?: AvatarCatalog.DEFAULT_ID
    }

    fun selectAvatarId(ctx: Context, id: String) {
        p(ctx).edit().putString("sel_avatar_v2", id).apply()
    }

    // ---------- 성장 ----------

    fun xp(ctx: Context) = p(ctx).getInt("xp", 0)

    fun level(ctx: Context) = Progress.levelForTotalXp(xp(ctx))

    /** XP를 더하고 오른 레벨 수를 돌려준다. */
    fun addXp(ctx: Context, amount: Int): Int {
        val before = level(ctx)
        p(ctx).edit().putInt("xp", xp(ctx) + amount).apply()
        return level(ctx) - before
    }

    // ---------- 모험 ----------

    fun stage(ctx: Context) = p(ctx).getInt("stage", 1)

    fun setStage(ctx: Context, n: Int) {
        p(ctx).edit()
            .putInt("stage", n)
            .putInt("stage_best", maxOf(bestStage(ctx), n))
            .apply()
    }

    fun bestStage(ctx: Context) = p(ctx).getInt("stage_best", 1)

    // ---------- 기록 ----------

    fun bestRounds(ctx: Context) = p(ctx).getInt("best_round", 0)

    fun recordRounds(ctx: Context, rounds: Int) {
        if (rounds > bestRounds(ctx)) p(ctx).edit().putInt("best_round", rounds).apply()
    }

    fun bestStreak(ctx: Context) = p(ctx).getInt("streak_best", 0)

    fun playerStats(ctx: Context) = PlayerStats(
        bestRounds = bestRounds(ctx),
        bestStage = bestStage(ctx),
        ownedAvatarCount = ownedAvatarIds(ctx).size,
        bestStreak = bestStreak(ctx)
    )
```

기존 `AVATARS`, `ownedAvatars`, `selectedAvatar`, `ownAvatar`, `selectAvatar`에는 `@Deprecated("v2 API를 쓸 것")`를 붙이되 **지우지 않는다**.

- [ ] **Step 6: 테스트와 빌드 확인**

Run: `gradlew.bat :app:testDebugUnitTest`
Expected: 전체 PASS

Run: `gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

이 시점에서 `ShopActivity`가 아직 구 API를 쓰고 있어도 `@Deprecated`는 경고일 뿐이라 빌드는 통과한다.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/Wallet.kt app/src/test/java/com/kkeutmal/game/WalletMigrationTest.kt
git commit -m "지갑에 아바타 ID 저장·마이그레이션·성장 기록 API와 새 아이템 2종 추가"
```

---

## Task 9: Kenney 에셋 도입과 AvatarView

**Files:**
- Create: `app/src/main/res/drawable-nodpi/` 아래 PNG 29개 (아래 목록)
- Create: `app/src/main/java/com/kkeutmal/game/AvatarView.kt`
- Create: `app/src/main/res/values/grades.xml`
- Modify: `README.md` (에셋 출처 표에 Kenney 두 팩 추가)

**Interfaces:**
- Consumes: `AvatarCatalog`(Task 4)
- Produces:
  - `class AvatarView(context, attrs) : FrameLayout` — `fun bind(def: AvatarDef?, locked: Boolean = false)`
  - `res/values/grades.xml` 색: `grade_common`(#6A6B6C), `grade_rare`(#57C1FF), `grade_epic`(#A05CFF), `grade_legendary`(#FFC533), `rank_bronze`(#CD7F32), `rank_silver`(#C0C0C0), `rank_gold`(#FFC533), `rank_platinum`(#4ADEBB), `rank_diamond`(#57C1FF), `rank_master`(#A05CFF), `rank_grandmaster`(#FF6161)

- [ ] **Step 1: 에셋 내려받고 필요한 파일만 복사**

```bash
SP="$TMPDIR/kenney"; mkdir -p "$SP" && cd "$SP"
curl -sL -o shape.zip "https://kenney.nl/media/pages/assets/shape-characters/c016420b08-1698339465/kenney_shape-characters.zip"
curl -sL -o icons.zip "https://kenney.nl/media/pages/assets/game-icons/1ebf9c14af-1677661579/kenney_game-icons.zip"
unzip -oq shape.zip -d shape && unzip -oq icons.zip -d icons
ls shape/PNG/Default/ | head -40
```

먼저 **실제 파일명을 눈으로 확인한다.** 몸통은 `{color}_body_{shape}.png`, 표정은 `facial_part_*.png` 형태다. 아래 복사 명령이 파일을 못 찾으면 실제 이름에 맞춰 고친다.

```bash
DEST="/c/workAndroid/WordChain/app/src/main/res/drawable-nodpi"
mkdir -p "$DEST"
for c in red purple green blue pink yellow; do
  for s in square circle squircle rhombus; do
    cp "shape/PNG/Default/${c}_body_${s}.png" "$DEST/${c}_body_${s}.png"
  done
done
cp shape/PNG/Default/facial_part_eye_open.png      "$DEST/"
cp shape/PNG/Default/facial_part_eye_half_top.png  "$DEST/"
cp shape/PNG/Default/facial_part_mouth_happy.png   "$DEST/"
cp shape/PNG/Default/facial_part_mouth_smirk.png   "$DEST/"
cp shape/PNG/Default/facial_part_mouth_angry.png   "$DEST/"
cp icons/PNG/White/2x/medal1.png  "$DEST/ic_rank_medal.png"
cp icons/PNG/White/2x/trophy.png  "$DEST/ic_rank_trophy.png"
cp icons/PNG/White/2x/star.png    "$DEST/ic_star.png"
cp icons/PNG/White/2x/locked.png  "$DEST/ic_locked.png"
ls "$DEST" | wc -l
```

Expected: 29개 파일 (몸통 24 + 표정 5 → 여기에 아이콘 4개를 더해 33개).

`icons/PNG/White/2x/`가 없으면 `ls icons/PNG/` 로 실제 경로를 확인한다(`Black`/`White` × `1x`/`2x` 구조).

- [ ] **Step 2: 파일명이 안드로이드 리소스 규칙을 지키는지 확인**

```bash
ls "$DEST" | grep -vE '^[a-z0-9_]+\.png$' && echo "규칙 위반 파일 있음" || echo "OK"
```

Expected: `OK`. 대문자나 하이픈이 있으면 소문자·언더스코어로 바꾼다.

- [ ] **Step 3: 등급·랭크 색 추가**

`app/src/main/res/values/grades.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="grade_common">#6A6B6C</color>
    <color name="grade_rare">#57C1FF</color>
    <color name="grade_epic">#A05CFF</color>
    <color name="grade_legendary">#FFC533</color>

    <color name="rank_bronze">#CD7F32</color>
    <color name="rank_silver">#C0C0C0</color>
    <color name="rank_gold">#FFC533</color>
    <color name="rank_platinum">#4ADEBB</color>
    <color name="rank_diamond">#57C1FF</color>
    <color name="rank_master">#A05CFF</color>
    <color name="rank_grandmaster">#FF6161</color>
</resources>
```

- [ ] **Step 4: AvatarView 구현**

`app/src/main/java/com/kkeutmal/game/AvatarView.kt`:

```kotlin
package com.kkeutmal.game

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat

/**
 * 몸통 · 눈 · 입 PNG 를 겹쳐 아바타 하나를 그린다.
 * 잠긴 아바타는 검은 실루엣으로 표시한다.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val body = ImageView(context)
    private val eyes = ImageView(context)
    private val mouth = ImageView(context)

    init {
        listOf(body, eyes, mouth).forEach {
            it.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    fun bind(def: AvatarDef?, locked: Boolean = false) {
        if (def == null) {
            listOf(body, eyes, mouth).forEach { it.setImageDrawable(null) }
            return
        }
        body.setImageResource(drawableId(def.bodyAsset))
        eyes.setImageResource(drawableId(def.face.eyeAsset))
        mouth.setImageResource(drawableId(def.face.mouthAsset))

        if (locked) {
            body.setColorFilter(Color.BLACK)
            eyes.setColorFilter(Color.BLACK)
            mouth.setColorFilter(Color.BLACK)
            alpha = 0.45f
        } else {
            listOf(body, eyes, mouth).forEach { it.clearColorFilter() }
            alpha = 1f
        }
    }

    /** 보스용: 화난 입으로 바꾸고 크게 보이게 한다. */
    fun bindBoss(def: AvatarDef) {
        bind(def)
        mouth.setImageResource(drawableId("facial_part_mouth_angry"))
    }

    private fun drawableId(name: String): Int =
        resources.getIdentifier(name, "drawable", context.packageName)
            .also { require(it != 0) { "드로어블을 찾을 수 없음: $name" } }
}
```

`ContextCompat`는 지금은 안 쓰지만 이후 등급 테두리에서 쓰므로 import를 남겨도 된다. 경고가 거슬리면 지운다.

- [ ] **Step 5: 빌드 확인**

Run: `gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL. 실패하면 대개 드로어블 파일명 규칙 위반이다.

- [ ] **Step 6: 에셋 출처를 README에 기록**

`README.md`의 "데이터 출처" 표에 두 줄을 추가한다:

```markdown
| 아바타 그래픽 | [Kenney — Shape Characters](https://kenney.nl/assets/shape-characters) | CC0 1.0 |
| UI 아이콘 | [Kenney — Game Icons](https://kenney.nl/assets/game-icons) | CC0 1.0 |
```

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/res/drawable-nodpi app/src/main/res/values/grades.xml app/src/main/java/com/kkeutmal/game/AvatarView.kt README.md
git commit -m "Kenney CC0 아바타 에셋 도입과 조합 렌더링 뷰 추가"
```

---

## Task 10: GameEngine에 보스 규칙 통합

**Files:**
- Modify: `app/src/main/java/com/kkeutmal/game/GameEngine.kt`
- Test: `app/src/test/java/com/kkeutmal/game/GameEngineRuleTest.kt`

**Interfaces:**
- Consumes: `BossRule`(Task 2)
- Produces:
  - `GameEngine` 생성자에 `val bossRules: List<BossRule> = emptyList()` 추가
  - `fun candidatesUnderRules(starts: Set<Char>, common: Boolean): List<String>` — 규칙을 통과하는 후보만
  - `validate`가 규칙 위반 시 `Verdict.Bad("${bossRules.rejectionMessage()} 만 낼 수 있어요")`
  - `hasAnyCandidate`가 규칙을 통과하는 후보만 센다

`GameEngine`은 `WordDict`(안드로이드 assets 의존)를 쓰므로 순수 JUnit에서 사전을 못 읽는다. 그래서 이 작업의 테스트는 **규칙 필터 자체**만 검증하고, 사전이 걸린 통합 동작은 Task 15 시뮬레이션과 실기기로 확인한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/kkeutmal/game/GameEngineRuleTest.kt`:

```kotlin
package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GameEngine 은 WordDict(안드로이드 assets)에 의존하므로 여기서는
 * 규칙 필터링 자체만 검증한다. 사전이 걸린 동작은 시뮬레이션과 실기기에서 본다.
 */
class GameEngineRuleTest {

    @Test
    fun `규칙이 없으면 모든 단어를 통과시킨다`() {
        val rules = emptyList<BossRule>()
        assertTrue(listOf("사과", "자동차", "고등학교").all { rules.acceptsWord(it) })
    }

    @Test
    fun `받침 규칙은 후보를 걸러낸다`() {
        val rules = listOf(BossRule.ENDS_WITH_JONGSEONG)
        val candidates = listOf("사과", "사람", "바다", "가방")
        assertEquals(listOf("사람", "가방"), candidates.filter { rules.acceptsWord(it) })
    }

    @Test
    fun `규칙을 통과하는 후보가 하나도 없을 수 있다`() {
        val rules = listOf(BossRule.MIN_LEN_4)
        val candidates = listOf("사과", "바다", "가방")
        assertTrue(candidates.none { rules.acceptsWord(it) })
    }

    @Test
    fun `안내 문구는 단어 제약만 담는다`() {
        assertEquals("받침으로 끝나는 단어", listOf(BossRule.ENDS_WITH_JONGSEONG).rejectionMessage())
        assertFalse(listOf(BossRule.AI_HANBANG).rejectionMessage().isNotEmpty())
    }
}
```

- [ ] **Step 2: 테스트를 돌려 통과를 확인 (Task 2 구현으로 이미 통과해야 함)**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.kkeutmal.game.GameEngineRuleTest"`
Expected: PASS (4 tests). 이 테스트는 회귀 방지용 안전망이다.

- [ ] **Step 3: GameEngine 생성자와 후보 필터 수정**

`GameEngine.kt`의 클래스 선언을 바꾼다:

```kotlin
class GameEngine(
    val level: AiLevel,
    val noTimer: Boolean,
    val bossRules: List<BossRule> = emptyList()
) {
```

`candidates` 아래에 규칙 적용 버전을 추가한다:

```kotlin
    /** 보스 규칙을 통과하는 후보만 남긴다. 규칙이 없으면 candidates 와 같다. */
    fun candidatesUnderRules(starts: Set<Char>, common: Boolean): List<String> {
        val base = candidates(starts, common)
        return if (bossRules.isEmpty()) base else base.filter { bossRules.acceptsWord(it) }
    }
```

`hasAnyCandidate`를 규칙 반영으로 교체한다:

```kotlin
    fun hasAnyCandidate(starts: Set<Char>): Boolean {
        for (c in starts) {
            for (i in WordDict.range(c)) {
                val w = WordDict.words[i]
                if (w !in used && (bossRules.isEmpty() || bossRules.acceptsWord(w))) return true
            }
        }
        return false
    }
```

- [ ] **Step 4: validate에 규칙 검사 추가**

`validate`의 `if (!WordDict.contains(word)) ...` **바로 앞**에 넣는다:

```kotlin
        if (bossRules.isNotEmpty() && !bossRules.acceptsWord(word)) {
            return Verdict.Bad("${bossRules.rejectionMessage()}만 낼 수 있어요")
        }
```

- [ ] **Step 5: AI도 같은 규칙을 지키게 한다**

`pickAiWord`의 각 분기에서 `candidates(starts, ...)`를 `candidatesUnderRules(starts, ...)`로 바꾼다. 총 6군데다(VERY_EASY 2, EASY 1, NORMAL 2, HARD 1).

`hintWord`와 `rerollAiWord`도 같은 이유로 `candidatesUnderRules`를 쓰게 바꾼다.

`AI_HANBANG` 규칙이 걸린 보스는 `HARD` 분기의 `canKill` 조건을 무조건 참으로 만든다. `pickAiWord`의 HARD 분기에서:

```kotlin
                val canKill = round >= 6 || BossRule.AI_HANBANG in bossRules
```

- [ ] **Step 6: 빌드와 전체 테스트 확인**

Run: `gradlew.bat :app:assembleDebug && gradlew.bat :app:testDebugUnitTest`
Expected: 둘 다 성공. 기존 `GameActivity`는 `GameEngine(level, noTimer)`로 부르는데 세 번째 인자에 기본값이 있으므로 수정 없이 컴파일된다.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/GameEngine.kt app/src/test/java/com/kkeutmal/game/GameEngineRuleTest.kt
git commit -m "게임 엔진에 보스 규칙 주입 — 단어 검증·후보 탐색·AI 선택에 일괄 적용"
```

---

## Task 11: GameActivity 모험 모드 지원

**Files:**
- Modify: `app/src/main/java/com/kkeutmal/game/GameActivity.kt`
- Modify: `app/src/main/res/layout/activity_game.xml`
- Modify: `app/src/main/res/layout/dialog_result.xml`

**Interfaces:**
- Consumes: `Stage`(Task 3), `GameResult`(Task 7), `Wallet` v2(Task 8), `AvatarView`(Task 9)
- Produces:
  - `GameActivity`가 받는 인텐트 엑스트라: `EXTRA_MODE`(String, `"FREE"`/`"ADVENTURE"`), `EXTRA_STAGE`(Int, 모험일 때만)
  - 기존 `EXTRA_LEVEL`, `EXTRA_NO_TIMER`는 자유 대전에서 계속 쓴다

- [ ] **Step 1: 레이아웃에 목표 라운드 표시와 보스 배너 추가**

`activity_game.xml`의 헤더 `LinearLayout` 안, `tvDifficulty` **앞**에 추가:

```xml
        <TextView
            android:id="@+id/tvGoal"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="6dp"
            android:background="@drawable/bubble_sys"
            android:paddingHorizontal="10dp"
            android:paddingVertical="6dp"
            android:textColor="@color/accent2"
            android:textSize="12sp"
            android:textStyle="bold"
            android:visibility="gone" />
```

`timerBar` **아래**, `recycler` 위에 보스 배너를 추가하고, `recycler`의 `layout_constraintTop_toBottomOf`를 `@id/bossBanner`로 바꾼다:

```xml
    <TextView
        android:id="@+id/bossBanner"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="16dp"
        android:layout_marginTop="6dp"
        android:background="@drawable/bubble_sys"
        android:gravity="center"
        android:paddingVertical="8dp"
        android:textColor="@color/error"
        android:textSize="13sp"
        android:textStyle="bold"
        android:visibility="gone"
        app:layout_constraintTop_toBottomOf="@id/tvTimer"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />
```

- [ ] **Step 2: 결과 다이얼로그에 XP·레벨업 표시 추가**

`dialog_result.xml`의 `tvCoinsEarned` **바로 아래**에 추가:

```xml
    <TextView
        android:id="@+id/tvXpEarned"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textColor="@color/accent2"
        android:textSize="14sp"
        android:textStyle="bold"
        android:visibility="gone" />

    <TextView
        android:id="@+id/tvLevelUp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textColor="@color/warn"
        android:textSize="15sp"
        android:textStyle="bold"
        android:visibility="gone" />
```

- [ ] **Step 3: GameActivity에 모드 분기 넣기**

`companion object`를 다음으로 교체:

```kotlin
    companion object {
        const val EXTRA_LEVEL = "level"
        const val EXTRA_NO_TIMER = "no_timer"
        const val EXTRA_MODE = "mode"
        const val EXTRA_STAGE = "stage"
        private const val REQ_MIC = 71
    }
```

필드를 추가:

```kotlin
    private var mode = GameMode.FREE
    private var stageNumber = 0
    private var stageConfig: StageConfig? = null
    private var voiceWordCount = 0
    private var longWordCount = 0
```

`onCreate`에서 엔진을 만드는 부분을 교체:

```kotlin
        mode = runCatching { GameMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "FREE") }
            .getOrDefault(GameMode.FREE)

        if (mode == GameMode.ADVENTURE) {
            stageNumber = intent.getIntExtra(EXTRA_STAGE, 1)
            val cfg = Stage.configFor(stageNumber)
            stageConfig = cfg
            engine = GameEngine(cfg.aiLevel, noTimer = false, bossRules = cfg.boss?.rules ?: emptyList())
            binding.tvDifficulty.text = "${stageNumber}스테이지"
            binding.tvGoal.visibility = View.VISIBLE
            binding.tvGoal.text = "목표 ${cfg.targetRounds}라운드"
            cfg.boss?.let { boss ->
                binding.bossBanner.visibility = View.VISIBLE
                val ruleText = boss.rules.rejectionMessage()
                binding.bossBanner.text =
                    if (ruleText.isEmpty()) "👹 ${boss.name}" else "👹 ${boss.name} — $ruleText"
            }
        } else {
            val level = runCatching {
                AiLevel.valueOf(intent.getStringExtra(EXTRA_LEVEL) ?: AiLevel.NORMAL.name)
            }.getOrDefault(AiLevel.NORMAL)
            val noTimer = intent.getBooleanExtra(EXTRA_NO_TIMER, false)
            engine = GameEngine(level, noTimer)
            binding.tvDifficulty.text = level.label + if (noTimer) " ∞" else ""
        }
```

`adapter.playerAvatar` 대입을 지우고, `ChatAdapter`에 아바타 ID를 넘기도록 Task 13에서 바꾼다. 지금은 `adapter.playerAvatarId = Wallet.selectedAvatarId(this)`로 둔다(`ChatAdapter` 수정은 Step 5에서 한다).

- [ ] **Step 4: 제한시간과 클리어 판정 반영**

`startTimer` 안의 `val totalMs = engine.timerSec * 1000L`을 다음으로 바꾼다:

```kotlin
        val totalMs = (stageConfig?.timerSec ?: engine.timerSec) * 1000L
```

`beginPlayerTurn()`의 `startTimer(engine.timerSec * 1000L)`도 같은 값으로 바꾼다.

`submit()`의 `is GameEngine.Verdict.Ok` 분기에서 `aiTurn()` 호출 **앞**에 클리어 판정을 넣는다:

```kotlin
                if (verdict.word.length >= 4) longWordCount++
                val cfg = stageConfig
                if (cfg != null && engine.round >= cfg.targetRounds) {
                    adapter.add(ChatItem.Sys("🎉 ${cfg.targetRounds}라운드 달성! 스테이지 클리어"))
                    scrollToEnd()
                    endGame(win = true, reason = "${stageNumber}스테이지 클리어")
                    return
                }
```

- [ ] **Step 5: ChatAdapter가 아바타 뷰를 쓰게 바꾸기**

`ChatAdapter.kt`에서 `var playerAvatar: String` 을 `var playerAvatarId: String = AvatarCatalog.DEFAULT_ID` 로 바꾸고, `is ChatItem.Player` 바인딩의 `b.tvAvatar.text = playerAvatar` 를 다음으로 교체:

```kotlin
                b.avatarView.bind(AvatarCatalog.byId(playerAvatarId))
```

`item_chat_player.xml`의 `tvAvatar` TextView를 다음으로 교체:

```xml
    <com.kkeutmal.game.AvatarView
        android:id="@+id/avatarView"
        android:layout_width="34dp"
        android:layout_height="34dp"
        android:layout_marginStart="8dp" />
```

- [ ] **Step 6: 보상 지급을 GameResult에 위임**

`endGame`의 코인 계산 부분(`val coinsEarned = ...` 부터 `Wallet.addCoins` 까지)을 다음으로 교체:

```kotlin
        val outcome = GameOutcome(
            mode = mode,
            won = win,
            score = engine.score,
            rounds = engine.round,
            stage = stageNumber,
            isBoss = stageConfig?.boss != null,
            doubleReward = false
        )
        val reward = GameResult.rewardFor(outcome)
        if (reward.coins > 0) Wallet.addCoins(this, reward.coins)
        val levelsGained = if (reward.xp > 0) Wallet.addXp(this, reward.xp) else 0
        Wallet.recordRounds(this, engine.round)
        if (mode == GameMode.ADVENTURE && win) {
            Wallet.setStage(this, stageNumber + 1)
        }
```

`showResultDialog` 시그니처를 `(win, reason, newBest, reward: Reward, levelsGained: Int)`로 바꾸고 본문에 추가:

```kotlin
        if (reward.xp > 0) {
            b.tvXpEarned.visibility = View.VISIBLE
            b.tvXpEarned.text = "✨ +${reward.xp} XP"
        }
        if (levelsGained > 0) {
            b.tvLevelUp.visibility = View.VISIBLE
            b.tvLevelUp.text = "🎊 레벨 ${Wallet.level(this)} 달성!"
        }
        if (reward.coins > 0) {
            b.tvCoinsEarned.visibility = View.VISIBLE
            b.tvCoinsEarned.text = "🪙 +${reward.coins} 코인"
        } else {
            b.tvCoinsEarned.visibility = View.GONE
        }
```

`b.btnRetry.setOnClickListener`의 `recreate()`는 모험 모드에서도 같은 스테이지를 다시 하므로 그대로 둔다.

- [ ] **Step 7: 빌드하고 실기기에서 자유 대전이 깨지지 않았는지 확인**

```bash
cd /c/workAndroid/WordChain && ./gradlew.bat :app:assembleDebug
ADB="/c/Users/사용자/AppData/Local/Android/Sdk/platform-tools/adb.exe"
cp app/build/outputs/apk/debug/app-debug.apk 끝말잇기-v1.0.apk
"$ADB" install -r 끝말잇기-v1.0.apk
"$ADB" shell am start -n com.kkeutmal.game/.MainActivity
sleep 4 && "$ADB" exec-out screencap -p > /tmp/free.png
```

Expected: 자유 대전이 예전처럼 동작하고, 내 말풍선 옆 아바타가 이모지가 아니라 도형 캐릭터로 보인다. 결과 화면에 `+XP`가 뜬다.

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/GameActivity.kt app/src/main/java/com/kkeutmal/game/ChatAdapter.kt app/src/main/res/layout/activity_game.xml app/src/main/res/layout/dialog_result.xml app/src/main/res/layout/item_chat_player.xml
git commit -m "게임 화면에 모험 모드 분기와 보스 배너·목표 라운드·XP 보상 표시 추가"
```

---

## Task 12: AdventureActivity (모험 화면)

**Files:**
- Create: `app/src/main/java/com/kkeutmal/game/AdventureActivity.kt`
- Create: `app/src/main/res/layout/activity_adventure.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `Stage`(Task 3), `Wallet` v2(Task 8), `GameActivity`(Task 11)
- Produces: 홈에서 `Intent(this, AdventureActivity::class.java)`로 진입

- [ ] **Step 1: 레이아웃 작성**

`app/src/main/res/layout/activity_adventure.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="20dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnBack"
            style="?attr/borderlessButtonStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:minWidth="0dp"
            android:text="←"
            android:textColor="@color/text_primary"
            android:textSize="22sp" />

        <TextView
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="wrap_content"
            android:layout_marginStart="6dp"
            android:text="모험"
            android:textColor="@color/text_primary"
            android:textSize="22sp"
            android:fontFamily="sans-serif-black" />

        <TextView
            android:id="@+id/tvBestStage"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/bubble_sys"
            android:paddingHorizontal="14dp"
            android:paddingVertical="8dp"
            android:textColor="@color/warn"
            android:textSize="14sp"
            android:textStyle="bold" />
    </LinearLayout>

    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="28dp"
        app:cardBackgroundColor="@color/surface"
        app:cardCornerRadius="24dp"
        app:strokeColor="@color/surface_stroke"
        app:strokeWidth="1dp"
        app:cardElevation="0dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center_horizontal"
            android:padding="24dp">

            <com.kkeutmal.game.AvatarView
                android:id="@+id/avatarBoss"
                android:layout_width="88dp"
                android:layout_height="88dp"
                android:visibility="gone" />

            <TextView
                android:id="@+id/tvStageNo"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@color/text_primary"
                android:textSize="34sp"
                android:fontFamily="sans-serif-black" />

            <TextView
                android:id="@+id/tvStageInfo"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:gravity="center"
                android:lineSpacingExtra="4dp"
                android:textColor="@color/text_dim"
                android:textSize="14sp" />

            <TextView
                android:id="@+id/tvBossInfo"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:gravity="center"
                android:textColor="@color/error"
                android:textSize="14sp"
                android:textStyle="bold"
                android:visibility="gone" />
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>

    <TextView
        android:id="@+id/tvNextBoss"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:gravity="center"
        android:textColor="@color/text_dim"
        android:textSize="13sp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnChallenge"
        android:layout_width="match_parent"
        android:layout_height="62dp"
        android:layout_marginTop="24dp"
        android:text="도전하기"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="@color/cta_text"
        app:backgroundTint="@color/cta_bg"
        app:cornerRadius="31dp" />
</LinearLayout>
```

- [ ] **Step 2: 액티비티 구현**

`app/src/main/java/com/kkeutmal/game/AdventureActivity.kt`:

```kotlin
package com.kkeutmal.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.kkeutmal.game.databinding.ActivityAdventureBinding

class AdventureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdventureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdventureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnChallenge.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_MODE, GameMode.ADVENTURE.name)
                    .putExtra(GameActivity.EXTRA_STAGE, Wallet.stage(this))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val n = Wallet.stage(this)
        val cfg = Stage.configFor(n)

        binding.tvStageNo.text = "${n}스테이지"
        binding.tvBestStage.text = "🏔 최고 ${Wallet.bestStage(this)}"
        binding.tvStageInfo.text = buildString {
            append("목표 ${cfg.targetRounds}라운드 버티기\n")
            append("제한시간 ${cfg.timerSec}초 · AI ${cfg.aiLevel.label}")
            if (cfg.allowHanbang) append("\n⚠ AI가 한방단어를 씁니다")
        }

        val boss = cfg.boss
        if (boss != null) {
            binding.tvBossInfo.visibility = View.VISIBLE
            val ruleText = boss.rules.rejectionMessage()
            binding.tvBossInfo.text =
                if (ruleText.isEmpty()) "👹 보스 ${boss.name}" else "👹 보스 ${boss.name}\n$ruleText"
            binding.avatarBoss.visibility = View.VISIBLE
            AvatarCatalog.byId("rhombus_red_special")?.let { binding.avatarBoss.bindBoss(it) }
            binding.tvNextBoss.text = "지금이 보스 스테이지입니다"
        } else {
            binding.tvBossInfo.visibility = View.GONE
            binding.avatarBoss.visibility = View.GONE
            binding.tvNextBoss.text = "다음 보스까지 ${Stage.stagesToNextBoss(n)}스테이지"
        }
    }
}
```

- [ ] **Step 3: 매니페스트에 등록**

`AndroidManifest.xml`의 `ShopActivity` 선언 아래에 추가:

```xml
        <activity
            android:name=".AdventureActivity"
            android:exported="false"
            android:screenOrientation="portrait" />
```

- [ ] **Step 4: 빌드와 실기기 확인**

임시로 홈의 `btnStart`를 길게 눌러 진입하게 하거나, 아래 명령으로 직접 띄워 확인한다:

```bash
cd /c/workAndroid/WordChain && ./gradlew.bat :app:assembleDebug
ADB="/c/Users/사용자/AppData/Local/Android/Sdk/platform-tools/adb.exe"
cp app/build/outputs/apk/debug/app-debug.apk 끝말잇기-v1.0.apk && "$ADB" install -r 끝말잇기-v1.0.apk
"$ADB" shell am start -n com.kkeutmal.game/.AdventureActivity
sleep 3 && "$ADB" exec-out screencap -p > /tmp/adventure.png
```

Expected: 1스테이지 카드가 뜨고 "목표 3라운드 · 제한시간 30초 · AI 매우쉬움", "다음 보스까지 4스테이지"가 보인다. `도전하기`를 누르면 게임이 시작되고 헤더에 "1스테이지 / 목표 3라운드"가 보인다.

- [ ] **Step 5: 5스테이지 보스가 제대로 뜨는지 확인**

```bash
"$ADB" shell "run-as com.kkeutmal.game sh -c 'echo skip'" 2>/dev/null
```

`run-as`가 막혀 있으면 자유롭게 3~4판을 클리어해 5스테이지까지 진행한 뒤 화면을 확인한다. 보스 배너와 "3글자 이상" 규칙 문구, 2글자 단어 거절이 보이면 통과다.

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/AdventureActivity.kt app/src/main/res/layout/activity_adventure.xml app/src/main/AndroidManifest.xml
git commit -m "모험 화면 추가 — 스테이지 정보·보스 예고·도전 진입"
```

---

## Task 13: CollectionActivity (도감 화면)

**Files:**
- Create: `app/src/main/java/com/kkeutmal/game/CollectionActivity.kt`
- Create: `app/src/main/res/layout/activity_collection.xml`
- Create: `app/src/main/res/drawable/bg_grade_border.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AvatarCatalog`(Task 4), `AvatarView`(Task 9), `Wallet` v2(Task 8)
- Produces: 홈에서 진입하는 도감 화면

- [ ] **Step 1: 등급 테두리 드로어블 작성**

`app/src/main/res/drawable/bg_grade_border.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/input_bg" />
    <stroke android:width="2dp" android:color="@color/grade_common" />
    <corners android:radius="16dp" />
</shape>
```

등급별 색은 코드에서 `GradientDrawable.setStroke`로 덮어쓴다.

- [ ] **Step 2: 레이아웃 작성**

`app/src/main/res/layout/activity_collection.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    android:overScrollMode="never">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnBack"
                style="?attr/borderlessButtonStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:minWidth="0dp"
                android:text="←"
                android:textColor="@color/text_primary"
                android:textSize="22sp" />

            <TextView
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:layout_marginStart="6dp"
                android:text="도감"
                android:textColor="@color/text_primary"
                android:textSize="22sp"
                android:fontFamily="sans-serif-black" />

            <TextView
                android:id="@+id/tvCollected"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="@drawable/bubble_sys"
                android:paddingHorizontal="14dp"
                android:paddingVertical="8dp"
                android:textColor="@color/accent2"
                android:textSize="14sp"
                android:textStyle="bold" />
        </LinearLayout>

        <GridLayout
            android:id="@+id/grid"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:columnCount="4" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 3: 액티비티 구현**

`app/src/main/java/com/kkeutmal/game/CollectionActivity.kt`:

```kotlin
package com.kkeutmal.game

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kkeutmal.game.databinding.ActivityCollectionBinding

class CollectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun gradeColor(grade: AvatarGrade) = ContextCompat.getColor(
        this,
        when (grade) {
            AvatarGrade.COMMON -> R.color.grade_common
            AvatarGrade.RARE -> R.color.grade_rare
            AvatarGrade.EPIC -> R.color.grade_epic
            AvatarGrade.LEGENDARY -> R.color.grade_legendary
        }
    )

    private fun render() {
        val owned = Wallet.ownedAvatarIds(this)
        val selected = Wallet.selectedAvatarId(this)
        binding.tvCollected.text = "${owned.size} / ${AvatarCatalog.ALL.size} 수집"

        binding.grid.removeAllViews()
        for (def in AvatarCatalog.ALL) {
            val isOwned = def.id in owned
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
                background = (ContextCompat.getDrawable(
                    this@CollectionActivity, R.drawable.bg_grade_border
                ) as GradientDrawable).apply {
                    mutate()
                    setStroke(dp(if (def.id == selected) 3 else 2), gradeColor(def.grade))
                }
            }
            cell.addView(AvatarView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                bind(def, locked = !isOwned)
            })
            cell.addView(TextView(this).apply {
                text = when {
                    def.id == selected -> "사용 중"
                    isOwned -> def.grade.label
                    else -> "잠김"
                }
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(
                    if (def.id == selected) ContextCompat.getColor(this@CollectionActivity, R.color.accent2)
                    else gradeColor(def.grade)
                )
            })
            cell.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            cell.setOnClickListener { onTap(def, isOwned) }
            binding.grid.addView(cell)
        }
    }

    private fun onTap(def: AvatarDef, isOwned: Boolean) {
        if (isOwned) {
            Wallet.selectAvatarId(this, def.id)
            render()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(def.name)
            .setMessage("${def.grade.label} 등급\n획득 방법: ${AvatarCatalog.unlockDescription(def)}")
            .setPositiveButton("확인", null)
            .show()
    }
}
```

- [ ] **Step 4: 매니페스트에 등록**

```xml
        <activity
            android:name=".CollectionActivity"
            android:exported="false"
            android:screenOrientation="portrait" />
```

- [ ] **Step 5: 빌드와 실기기 확인**

```bash
cd /c/workAndroid/WordChain && ./gradlew.bat :app:assembleDebug
ADB="/c/Users/사용자/AppData/Local/Android/Sdk/platform-tools/adb.exe"
cp app/build/outputs/apk/debug/app-debug.apk 끝말잇기-v1.0.apk && "$ADB" install -r 끝말잇기-v1.0.apk
"$ADB" shell am start -n com.kkeutmal.game/.CollectionActivity
sleep 3 && "$ADB" exec-out screencap -p > /tmp/collection.png
```

Expected: 48칸이 4열 그리드로 보이고, 미보유는 검은 실루엣, 등급별로 테두리 색이 다르다. 잠긴 칸을 누르면 획득 방법 다이얼로그가 뜬다.

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/CollectionActivity.kt app/src/main/res/layout/activity_collection.xml app/src/main/res/drawable/bg_grade_border.xml app/src/main/AndroidManifest.xml
git commit -m "아바타 도감 화면 추가 — 48칸 그리드·등급 테두리·해금 안내"
```

---

## Task 14: MainActivity 홈 재구성과 미션·출석 연결

**Files:**
- Modify: `app/src/main/java/com/kkeutmal/game/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/kkeutmal/game/ShopActivity.kt`

**Interfaces:**
- Consumes: 앞선 모든 작업
- Produces: 최종 홈 화면

- [ ] **Step 1: 홈 레이아웃에 프로필·미션·모드 버튼 추가**

`activity_main.xml`의 최고기록 카드(`MaterialCardView`) **위**에 프로필 카드를 넣는다:

```xml
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            app:cardBackgroundColor="@color/surface"
            app:cardCornerRadius="20dp"
            app:strokeColor="@color/surface_stroke"
            app:strokeWidth="1dp"
            app:cardElevation="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:padding="16dp">

                <com.kkeutmal.game.AvatarView
                    android:id="@+id/avatarMe"
                    android:layout_width="52dp"
                    android:layout_height="52dp" />

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="14dp"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/tvLevelRank"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:textStyle="bold" />

                    <com.google.android.material.progressindicator.LinearProgressIndicator
                        android:id="@+id/xpBar"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:max="1000"
                        app:trackThickness="6dp"
                        app:trackCornerRadius="3dp"
                        app:trackColor="@color/chip_bg"
                        app:indicatorColor="@color/accent" />

                    <TextView
                        android:id="@+id/tvXp"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:textColor="@color/text_dim"
                        android:textSize="11sp" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
```

`btnStart` **위**에 모험 버튼을, `btnShop` 옆에 도감 버튼을 넣는다:

```xml
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnAdventure"
            android:layout_width="match_parent"
            android:layout_height="62dp"
            android:layout_marginTop="20dp"
            android:text="🗺 모험 떠나기"
            android:textSize="18sp"
            android:textStyle="bold"
            app:backgroundTint="@color/accent"
            app:cornerRadius="31dp" />
```

```xml
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnCollection"
                        android:layout_width="wrap_content"
                        android:layout_height="40dp"
                        android:layout_marginStart="6dp"
                        android:minWidth="0dp"
                        android:paddingHorizontal="16dp"
                        android:text="📖 도감"
                        android:textSize="13sp"
                        app:backgroundTint="@color/chip_bg"
                        app:cornerRadius="20dp" />
```

미션 배너를 `btnAdventure` 아래에 넣는다:

```xml
        <LinearLayout
            android:id="@+id/missionBox"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="vertical"
            android:background="@drawable/bg_input"
            android:padding="14dp" />
```

`btnStart`의 텍스트를 `⚔ 자유 대전`으로 바꾼다.

- [ ] **Step 2: MainActivity에 프로필·미션·출석 렌더링 추가**

`onCreate`에 버튼 연결을 추가:

```kotlin
        binding.btnAdventure.setOnClickListener {
            startActivity(Intent(this, AdventureActivity::class.java))
        }
        binding.btnCollection.setOnClickListener {
            startActivity(Intent(this, CollectionActivity::class.java))
        }
```

`onResume`을 다음으로 교체:

```kotlin
    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("kkeutmal", MODE_PRIVATE)

        val bestScore = prefs.getInt("best_score", 0)
        val bestRound = prefs.getInt("best_round", 0)
        binding.tvBest.text =
            if (bestScore == 0 && bestRound == 0) "아직 기록 없음"
            else "${bestScore}점 · ${bestRound}라운드"
        binding.tvCoins.text = "🪙 ${Wallet.coins(this)} 코인"

        // 프로필
        val level = Wallet.level(this)
        val rank = Progress.rankOf(level)
        binding.avatarMe.bind(AvatarCatalog.byId(Wallet.selectedAvatarId(this)))
        binding.tvLevelRank.text = "Lv.$level · ${rank.label}"
        val into = Progress.xpIntoLevel(Wallet.xp(this))
        val need = Progress.xpForNextLevel(level)
        binding.xpBar.progress = if (need > 0) (into * 1000 / need) else 1000
        binding.tvXp.text = "$into / $need XP"

        renderMissions()
    }

    private fun renderMissions() {
        val today = java.time.LocalDate.now().toString()
        val prefs = getSharedPreferences("kkeutmal", MODE_PRIVATE)

        // 날짜가 바뀌었으면 오늘의 미션을 새로 뽑고 진행도를 초기화한다
        if (prefs.getString("missions_date", null) != today) {
            val picked = Missions.pickDaily(today)
            val editor = prefs.edit()
                .putString("missions_date", today)
                .putString("missions_ids", picked.joinToString(",") { it.id })
            for (m in Mission.entries) editor.putInt("mission_progress_${m.id}", 0)
            editor.putBoolean("missions_bonus_paid", false).apply()
        }

        val ids = prefs.getString("missions_ids", "")!!.split(",").filter { it.isNotEmpty() }
        val missions = ids.mapNotNull { id -> Mission.entries.firstOrNull { it.id == id } }

        binding.missionBox.removeAllViews()
        binding.missionBox.addView(TextView(this).apply {
            text = "📋 오늘의 미션"
            textSize = 13f
            setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.text_dim))
        })
        for (m in missions) {
            val progress = prefs.getInt("mission_progress_${m.id}", 0)
            val done = Missions.isComplete(m, progress)
            binding.missionBox.addView(TextView(this).apply {
                text = if (done) "✅ ${m.label}" else "・${m.label}  ($progress/${m.target})"
                textSize = 13f
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, 0)
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        this@MainActivity,
                        if (done) R.color.accent2 else R.color.text_primary
                    )
                )
            })
        }
    }
```

`import android.widget.TextView`를 추가한다.

- [ ] **Step 3: 출석 보상을 홈 진입 시 처리**

`onResume` 맨 앞(프로필 렌더 전)에 추가:

```kotlin
        val todayKey = java.time.LocalDate.now().toString()
        val lastKey = prefs.getString("streak_last_date", null)
        if (lastKey != todayKey) {
            val r = Missions.advanceStreak(lastKey, todayKey, prefs.getInt("streak_days", 0))
            prefs.edit()
                .putString("streak_last_date", todayKey)
                .putInt("streak_days", r.days)
                .putInt("streak_best", maxOf(prefs.getInt("streak_best", 0), r.days))
                .apply()
            if (r.reward > 0) {
                Wallet.addCoins(this, r.reward)
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("🔥 ${r.days}일 연속 출석!")
                    .setMessage("🪙 +${r.reward} 코인을 받았어요")
                    .setPositiveButton("확인", null)
                    .show()
            }
        }
```

출석 판정은 홈에 들어올 때 한 번만 돌아야 하므로 `lastKey != todayKey` 가드를 반드시 유지한다.

- [ ] **Step 4: ShopActivity의 아바타 탭을 AvatarView로 교체**

`ShopActivity.buildAvatars()`의 `Wallet.AVATARS` 순회를 `AvatarCatalog.ALL.filter { it.grade == AvatarGrade.COMMON }`로 바꾸고, 이모지 `TextView` 대신 `AvatarView`를 넣는다. 구매 처리는 `Unlock.Coin`의 `price`를 쓰고 `Wallet.ownAvatarId` / `Wallet.selectAvatarId`를 부른다.

`onAvatarTap`의 시그니처를 `(def: AvatarDef, owned: Boolean)`로 바꾸고 가격은 `(def.unlock as Unlock.Coin).price`로 읽는다.

- [ ] **Step 5: 빌드와 실기기 전체 확인**

```bash
cd /c/workAndroid/WordChain && ./gradlew.bat :app:assembleDebug && ./gradlew.bat :app:testDebugUnitTest
ADB="/c/Users/사용자/AppData/Local/Android/Sdk/platform-tools/adb.exe"
cp app/build/outputs/apk/debug/app-debug.apk 끝말잇기-v1.0.apk && "$ADB" install -r 끝말잇기-v1.0.apk
"$ADB" shell am start -n com.kkeutmal.game/.MainActivity
sleep 4 && "$ADB" exec-out screencap -p > /tmp/home.png
```

Expected: 홈에 아바타·레벨·XP바·랭크, 오늘의 미션 3개, 모험/자유대전 버튼, 상점·도감 버튼이 모두 보인다. **기존에 산 아바타가 사라지지 않았는지** 상점과 도감에서 확인한다(마이그레이션 검증).

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/MainActivity.kt app/src/main/java/com/kkeutmal/game/ShopActivity.kt app/src/main/res/layout/activity_main.xml
git commit -m "홈 화면 재구성 — 프로필·XP·일일 미션·출석 보상과 모험/도감 진입"
```

---

## Task 15: 밸런스 시뮬레이션 검증

**Files:**
- Create: `tools/BalanceSim.java`
- Modify: `docs/specs/game-expansion-design.md` (검증 결과 기록)

**Interfaces:**
- Consumes: 없음 (Kotlin 로직을 Java로 옮겨 심은 독립 하네스)
- Produces: 콘솔 표 출력

스펙의 "수용 기준"을 실제로 확인한다. `Sim.java`와 같은 방식이다.

- [ ] **Step 1: 시뮬레이션 하네스 작성**

`tools/BalanceSim.java`:

```java
import java.util.*;

/** 레벨 곡선·스테이지 곡선·코인 경제가 스펙의 수용 기준을 만족하는지 확인한다. */
public class BalanceSim {

    static int xpForNextLevel(int level) {
        return (int) (50.0 * Math.pow(level, 1.2));
    }

    static int levelForTotalXp(int totalXp) {
        int level = 1, remain = totalXp, need = xpForNextLevel(1);
        while (level < 99 && remain >= need) {
            remain -= need;
            level++;
            need = xpForNextLevel(level);
        }
        return level;
    }

    static int stageTimer(int n) { return Math.max(8, 30 - n / 3); }
    static int stageTarget(int n) { return 3 + n / 3; }
    static String stageAi(int n) {
        if (n <= 5) return "매우쉬움";
        if (n <= 15) return "쉬움";
        if (n <= 30) return "보통";
        return "어려움";
    }
    static int stageXp(int n, boolean boss) {
        int base = n * 10 + 50;
        return boss ? base * 3 : base;
    }
    static int stageCoins(int n, boolean boss) {
        int c = 10 + n * 2;
        return boss ? c * 3 : c;
    }

    public static void main(String[] args) {
        System.out.println("=== 스테이지 곡선 ===");
        System.out.println("스테이지\t제한시간\tAI\t목표라운드\t보스");
        for (int n : new int[]{1, 5, 10, 15, 20, 25, 30, 31, 40, 50, 60, 66, 80, 100}) {
            System.out.printf("%d\t%d초\t%s\t%d\t%s%n",
                n, stageTimer(n), stageAi(n), stageTarget(n), n % 5 == 0 ? "O" : "");
        }

        System.out.println("\n=== 레벨 곡선 (모험만 플레이했을 때) ===");
        System.out.println("도달레벨\t누적판수\t그때의스테이지");
        int xp = 0, games = 0, stage = 1, shownLevel = 1;
        int[] milestones = {10, 20, 30, 50, 70, 99};
        int mi = 0;
        while (mi < milestones.length && games < 100000) {
            boolean boss = stage % 5 == 0;
            xp += stageXp(stage, boss);
            games++;
            stage++;
            int lv = levelForTotalXp(xp);
            while (mi < milestones.length && lv >= milestones[mi]) {
                System.out.printf("Lv%d\t%d판\t%d스테이지%n", milestones[mi], games, stage);
                mi++;
            }
        }

        System.out.println("\n=== 코인 경제 (하루 5판 x 30일, 모험 진행) ===");
        int coins = 0; stage = 1;
        for (int day = 1; day <= 30; day++) {
            for (int g = 0; g < 5; g++) {
                boolean boss = stage % 5 == 0;
                coins += stageCoins(stage, boss);
                stage++;
            }
            coins += 120; // 일일 미션 3개 + 보너스 대략치
            if (day % 7 == 0) coins += 100; // 주간 출석
            if (day == 1 || day == 7 || day == 14 || day == 30) {
                System.out.printf("%d일차: %d코인 (%d스테이지) — 일반아바타 %d개 살 수 있음%n",
                    day, coins, stage, coins / 200);
            }
        }
    }
}
```

- [ ] **Step 2: 실행해서 수용 기준과 대조**

```bash
cd /c/workAndroid/WordChain
"/c/Program Files/Android/Android Studio/jbr/bin/java.exe" -Dstdout.encoding=UTF-8 tools/BalanceSim.java
```

확인할 것:
- 제한시간이 1스테이지 30초에서 시작해 66스테이지에서 8초에 닿는가
- AI 레벨이 단조 증가하는가
- **레벨 10이 15판 이내, 레벨 20이 40판 이내, 레벨 50이 200판 이내**인가 (스펙 수용 기준)
- 30일차에 일반 아바타를 여러 개 살 수 있는가 (너무 빡빡하지 않은가)

기준을 벗어나면 `Progress.xpForNextLevel`의 계수 50 또는 지수 1.2를 조정하고, `ProgressTest`의 기대값도 함께 고친다.

- [ ] **Step 3: 보스 규칙 안전성 확인**

사전에서 "규칙을 통과하는 단어가 없는 시작 글자"가 몇 개인지 센다. `tools/BossSafetySim.java`를 만들어 `app/src/main/assets/dict_all.txt`를 읽고, 각 시작 글자별로 `3글자 이상` / `받침으로 끝남` / `4글자 이상` 조건을 통과하는 단어 수를 센다.

```bash
"/c/Program Files/Android/Android Studio/jbr/bin/java.exe" -Dstdout.encoding=UTF-8 tools/BossSafetySim.java
```

통과 단어가 0인 시작 글자가 많으면(전체 글자의 5% 이상) 해당 규칙을 완화한다. 다만 `GameEngine.hasAnyCandidate`가 이미 규칙을 반영하므로 **후보가 0이면 AI 항복으로 처리**돼 플레이어가 억울하게 지지는 않는다. 이 시뮬레이션은 "보스가 너무 자주 그냥 항복하는지"를 보기 위한 것이다.

- [ ] **Step 4: 결과를 스펙 문서에 기록**

스펙의 "8. 검증 방법" 절 끝에 실제 측정값을 적는다. 예:

```markdown
### 측정 결과 (2026-07-26)

- 레벨 10: N판 / 레벨 20: N판 / 레벨 50: N판 → 수용 기준 충족 여부
- 제한시간 8초 도달: 66스테이지
- 30일차 누적 코인: N — 일반 아바타 N개 구매 가능
- 보스 규칙별 후보 0인 시작 글자 수: 3글자 N개 / 받침 N개 / 4글자 N개
```

- [ ] **Step 5: 커밋**

```bash
git add tools/ docs/specs/game-expansion-design.md
git commit -m "밸런스 시뮬레이션 하네스 추가와 측정 결과 기록"
```

---

## Task 16: 보상 지급 배선 (미션 기록·아바타 해금·아이템 2종 사용)

**Files:**
- Modify: `app/src/main/java/com/kkeutmal/game/GameActivity.kt`
- Modify: `app/src/main/res/layout/activity_game.xml`

**Interfaces:**
- Consumes: `Missions`(Task 6), `AvatarCatalog`(Task 4), `Achievements`(Task 5), `Wallet` v2(Task 8), `GameResult`(Task 7)
- Produces: 없음 (배선 작업)

Task 1~15가 각 시스템을 만들었지만, 게임이 끝났을 때 **미션 진행도를 올리고 해금된 아바타를 실제로 지급하는 코드**와 **부활·2배 아이템을 쓰는 버튼**이 아직 없다. 이 작업이 그 연결을 담당한다.

- [ ] **Step 1: 아이템 바에 버튼 2개 추가**

`activity_game.xml`의 아이템 바(`btnItemPass`가 있는 `LinearLayout`) 안, `btnItemPass` 뒤에 추가:

```xml
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnItemDouble"
                style="?attr/borderlessButtonStyle"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:layout_marginStart="6dp"
                android:minWidth="0dp"
                android:paddingHorizontal="8dp"
                android:text="🎯×0"
                android:textColor="@color/text_primary"
                android:textSize="13sp"
                android:background="@drawable/bubble_sys" />
```

부활은 게임 오버 시점에 다이얼로그로 물어보므로 버튼이 필요 없다.

- [ ] **Step 2: 2배 획득 아이템 사용 처리**

`GameActivity`에 필드를 추가:

```kotlin
    private var doubleRewardActive = false
```

`refreshItemBar()`에 한 줄 추가:

```kotlin
        binding.btnItemDouble.text = label("item_double", "🎯")
```

`onCreate`의 아이템 버튼 연결 옆에 추가:

```kotlin
        binding.btnItemDouble.setOnClickListener { useDoubleItem() }
```

메서드를 추가:

```kotlin
    private fun useDoubleItem() {
        if (!playerTurnActive()) return
        if (doubleRewardActive) { showInfo("🎯 이미 적용 중이에요"); return }
        if (Wallet.itemCount(this, "item_double") <= 0) {
            showError("🎯 아이템이 없어요. 상점에서 구매하세요"); return
        }
        Wallet.useItem(this, "item_double")
        doubleRewardActive = true
        audio.play("sfx_ok")
        showInfo("🎯 이번 판 보상 2배!")
        refreshItemBar()
    }
```

`setInputEnabled`의 아이템 버튼 비활성화 목록에 `binding.btnItemDouble`도 추가한다.

- [ ] **Step 3: 부활 아이템 처리**

`endGame`의 맨 앞(`if (gameOver) return` 바로 뒤)에 넣는다:

```kotlin
        if (!win && Wallet.itemCount(this, "item_revive") > 0) {
            stopTimer()
            MaterialAlertDialogBuilder(this)
                .setTitle("🛡 부활할까요?")
                .setMessage("부활 아이템을 써서 이어서 할 수 있어요 (보유 ${Wallet.itemCount(this, "item_revive")}개)")
                .setPositiveButton("부활") { _, _ ->
                    Wallet.useItem(this, "item_revive")
                    refreshItemBar()
                    adapter.add(ChatItem.Sys("🛡 부활했어요!"))
                    scrollToEnd()
                    beginPlayerTurn()
                }
                .setNegativeButton("포기") { _, _ -> finishGame(win, reason) }
                .setCancelable(false)
                .show()
            return
        }
        finishGame(win, reason)
    }

    private fun finishGame(win: Boolean, reason: String) {
```

즉, 기존 `endGame` 본문을 `finishGame`으로 옮기고 `endGame`은 부활 여부만 판단하게 나눈다. `gameOver = true` 대입은 `finishGame` 안에 남긴다. 부활을 고르면 `gameOver`가 false로 유지돼 게임이 계속된다.

한방단어로 진 경우에는 부활해도 낼 단어가 없으므로, 부활 제안은 `reason`이 `"한방단어"`를 포함하지 않을 때만 한다:

```kotlin
        if (!win && !reason.contains("한방단어") && Wallet.itemCount(this, "item_revive") > 0) {
```

- [ ] **Step 4: 미션 진행도 기록**

`voice.onResult`에서 인식 성공 시 `voiceWordCount++`를 추가한다.
Task 11 Step 4에서 이미 `longWordCount++`를 넣었다.

`finishGame` 안, 보상 계산 **뒤**에 추가:

```kotlin
        val prefs = getSharedPreferences("kkeutmal", MODE_PRIVATE)
        fun bump(m: Mission, amount: Int) {
            if (amount <= 0) return
            val key = "mission_progress_${m.id}"
            prefs.edit().putInt(key, Missions.applyProgress(m, prefs.getInt(key, 0), amount)).apply()
        }
        bump(Mission.PLAY_3, 1)
        bump(Mission.ROUNDS_5, engine.round)
        bump(Mission.LONG_WORD_3, longWordCount)
        bump(Mission.VOICE_5, voiceWordCount)
        bump(Mission.SCORE_300, engine.score)
        if (win && mode == GameMode.ADVENTURE) bump(Mission.STAGE_2, 1)
        if (win && reason.contains("항복")) bump(Mission.SURRENDER_1, 1)
```

- [ ] **Step 5: 해금된 아바타 실제 지급**

`finishGame` 안, `Wallet.addXp` 호출 **뒤**에 추가:

```kotlin
        val newLevel = Wallet.level(this)
        AvatarCatalog.ALL.forEach { def ->
            val u = def.unlock
            val unlocked = when (u) {
                is Unlock.Level -> u.level <= newLevel
                is Unlock.BossClear -> win && stageConfig?.boss != null && u.stage == stageNumber
                else -> false
            }
            if (unlocked) Wallet.ownAvatarId(this, def.id)
        }
        // 도전과제는 이번 판 기록이 반영된 뒤에 판정한다
        Achievements.metBy(Wallet.playerStats(this)).forEach { ach ->
            AvatarCatalog.ALL
                .filter { it.unlock is Unlock.Achieve && (it.unlock as Unlock.Achieve).achievementId == ach.id }
                .forEach { Wallet.ownAvatarId(this, it.id) }
        }
```

`Wallet.recordRounds`와 `Wallet.setStage` 호출이 이 코드보다 **먼저** 와야 도전과제 판정에 이번 판 기록이 들어간다.

- [ ] **Step 6: GameOutcome에 doubleReward 반영**

`finishGame`의 `GameOutcome` 생성에서 `doubleReward = false`를 `doubleReward = doubleRewardActive`로 바꾼다.

- [ ] **Step 7: 빌드·테스트·실기기 확인**

```bash
cd /c/workAndroid/WordChain && ./gradlew.bat :app:assembleDebug && ./gradlew.bat :app:testDebugUnitTest
ADB="/c/Users/사용자/AppData/Local/Android/Sdk/platform-tools/adb.exe"
cp app/build/outputs/apk/debug/app-debug.apk 끝말잇기-v1.0.apk && "$ADB" install -r 끝말잇기-v1.0.apk
"$ADB" shell am start -n com.kkeutmal.game/.MainActivity
```

확인 항목:
1. 한 판 하고 홈에 돌아오면 **오늘의 미션 진행도가 올라가 있다**
2. 상점에서 🛡 부활을 산 뒤 일부러 시간 초과하면 **부활 다이얼로그가 뜨고, 부활하면 게임이 이어진다**
3. 🎯 2배를 쓰고 판을 끝내면 결과 화면의 코인·XP가 두 배다
4. 레벨이 5를 넘으면 도감에서 **희귀 아바타 하나가 해금**돼 있다

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/java/com/kkeutmal/game/GameActivity.kt app/src/main/res/layout/activity_game.xml
git commit -m "보상 배선 완성 — 미션 진행도 기록·아바타 해금 지급·부활/2배 아이템 사용"
```

---

## Self-Review

**1. 스펙 커버리지**

| 스펙 항목 | 담당 작업 |
|---|---|
| 경험치·레벨·랭크 | Task 1 |
| 보스 규칙 정의 | Task 2 |
| 스테이지 파라미터·보스 배치 | Task 3 |
| 아바타 48종·등급·해금 | Task 4 |
| 도전과제 4개 | Task 5 |
| 일일 미션·연속 출석 | Task 6 |
| 코인 경제 계산 | Task 7 |
| 데이터 모델·마이그레이션·새 아이템 2종 | Task 8 |
| Kenney 에셋·아바타 렌더링 | Task 9 |
| 보스 규칙을 한방단어 판정에도 적용 | Task 10 (Step 3) |
| 모험 모드 게임 진행·클리어 판정 | Task 11 |
| 모험 화면 | Task 12 |
| 도감 화면 | Task 13 |
| 홈 재구성·미션 표시·출석 보상 | Task 14 |
| 검증 방법 5가지 | Task 15 |
| 미션 진행도 기록·아바타 해금 지급·아이템 2종 사용 | Task 16 |

빠진 항목 없음.

**2. 검토에서 발견해 고친 것**

처음 초안에는 각 시스템을 **만드는** 작업만 있고 **연결하는** 코드가 어느 작업에도 없었다. 구체적으로 세 가지가 비어 있었다.

1. 일일 미션을 화면에 그리기(Task 14)는 했지만 게임이 끝났을 때 진행도를 올리는 코드가 없었다
2. 아바타 해금 조건(Task 4)은 정의했지만 조건을 만족했을 때 실제로 지급하는 코드가 없었다
3. 부활·2배 아이템을 정의(Task 8)하고 보상 계산에 반영(Task 7)까지 했지만 게임 화면에서 쓸 방법이 없었다

셋 다 **Task 16**으로 묶어 정식 작업으로 추가했다. 이런 배선 누락은 시스템별로 작업을 나눌 때 생기기 쉬우므로, Task 16의 Step 7 확인 항목 4개를 반드시 실기기에서 확인한다.

**3. 타입 일관성**

- `Wallet.selectedAvatarId` / `ownAvatarId` / `ownedAvatarIds` — Task 8에서 정의, Task 11·13·14에서 같은 이름으로 사용 ✓
- `AvatarCatalog.byId(id): AvatarDef?` — nullable 반환을 Task 11·13·14 모두 `?.let` 또는 `!!`로 처리 ✓
- `Stage.configFor(n): StageConfig` — Task 11·12에서 동일 사용 ✓
- `GameResult.rewardFor(outcome): Reward` — Task 7 정의, Task 11 사용 ✓
- `List<BossRule>.rejectionMessage()` — Task 2 정의, Task 10·11·12 사용 ✓
- `AvatarView.bind(def, locked)` / `bindBoss(def)` — Task 9 정의, Task 11·12·13·14 사용 ✓
- `Missions.applyProgress(mission, current, amount)` — Task 6 정의, Self-Review의 `bump`에서 사용 ✓

---

## 실행 순서 요약

```
Task 1  Progress          ← 테스트 환경 구축
Task 2  BossRule
Task 3  Stage             ← Task 2 필요
Task 4  AvatarCatalog
Task 5  Achievements      ← Task 4와 상호 참조 (Task 4 Step 3 주의)
Task 6  Missions
Task 7  GameResult        ← Task 1, 3 필요
Task 8  Wallet v2         ← Task 1, 4, 5, 6 필요
Task 9  Kenney 에셋       ← Task 4 필요
Task 10 GameEngine 통합   ← Task 2 필요
Task 11 GameActivity      ← Task 3, 7, 8, 9, 10 필요
Task 12 AdventureActivity ← Task 11 필요
Task 13 CollectionActivity← Task 8, 9 필요
Task 14 MainActivity      ← 전부 필요
Task 16 보상 배선          ← Task 14 필요 (미션·해금·아이템 연결)
Task 15 밸런스 검증        ← 독립 (언제든 가능)
```

Task 1~7은 순수 로직이라 **서로 독립적으로 병렬 진행이 가능**하다(Task 3←2, 5↔4, 7←1·3 의존만 지키면 된다).
Task 9(에셋)도 독립적이다. Task 8부터는 순서대로 진행한다.
