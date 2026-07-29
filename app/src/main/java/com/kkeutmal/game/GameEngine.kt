package com.kkeutmal.game

import kotlin.random.Random

/**
 * 제한시간은 단계마다 대략 1.5배씩 벌어지게 잡았다.
 *
 * 예전에는 60/45/20/12 였는데 쉬움에서 보통으로 갈 때 45초가 20초로 뚝 떨어져,
 * 한 단계만 올렸는데도 갑자기 쫓기는 느낌이 났다. 전체적으로도 짧았다 —
 * 끝말잇기는 '이 글자로 시작하는 말'을 떠올려야 해서 생각할 틈이 있어야 한다.
 */
enum class AiLevel(val label: String, val timerSec: Int, val desc: String) {
    VERY_EASY("매우쉬움", 90, "AI가 이어가기 가장 쉬운 단어만 골라줘요"),
    EASY("쉬움", 60, "AI가 잇기 좋은 단어를 골라줘요"),
    NORMAL("보통", 45, "AI가 무난하게 받아쳐요 (가끔 한방단어!)"),
    HARD("어려움", 30, "AI가 사전 전체로 압박해요 (한방단어 주의!)")
}

/**
 * @param allowHanbang AI 가 한방단어(이을 말이 없는 단어)를 써도 되는가.
 *   사용자가 고르는 값이 아니라 **판을 만들 때 코드가 정해서 넘기는 값**이다.
 *   자유 대전은 늘 허용하고(실제로 쓰는 건 보통·어려움뿐), 모험은 스테이지가 정한다 —
 *   같은 '보통' 이라도 모험 16~30스테이지에서는 한방단어가 나오면 안 되기 때문이다.
 */
class GameEngine(
    val level: AiLevel,
    val noTimer: Boolean,
    val bossRules: List<BossRule> = emptyList(),
    val chainMode: ChainMode = ChainMode.TAIL,
    val allowHanbang: Boolean = true
) {

    sealed class Verdict {
        data class Ok(val word: String) : Verdict()
        data class Bad(val message: String) : Verdict()
    }

    val used = HashSet<String>()
    var lastWord: String? = null
        private set
    private var prevWord: String? = null // lastWord 직전 단어 (아이템 '단어 바꾸기'용)
    var round = 0
        private set
    var score = 0
        private set

    private val rng = Random(System.nanoTime())

    val timerSec: Int get() = level.timerSec

    /** 앞 단어에서 이어야 할 음절들. 아직 첫 단어 전이면 null */
    fun allowedStarts(): Set<Char>? = lastWord?.let { chainMode.linkSyllables(it) }

    fun allowedStartsLabel(): String {
        val starts = allowedStarts() ?: return ""
        val list = starts.toList()
        return if (list.size == 1) "'${list[0]}'"
        else "'${list[0]}' (또는 '${list.drop(1).joinToString("', '")}')"
    }

    /** 화면에 그대로 띄울 안내 문구. 이어가기 방식에 따라 문장이 달라진다. */
    fun linkPrompt(): String = allowedStarts()?.let { chainMode.promptFor(it) } ?: ""

    /**
     * 이어야 할 음절 하나에 해당하는 사전 인덱스 범위.
     * 끝말잇기·같은글자 모드는 첫 글자 기준(정렬 배열의 접두 범위),
     * 앞말잇기는 끝 글자 기준(역방향 색인)이라 조회 방법 자체가 다르다.
     */
    private inline fun forEachWordLinkedTo(c: Char, action: (String) -> Unit) {
        if (chainMode == ChainMode.HEAD) {
            for (i in WordDict.indicesEndingWith(c)) action(WordDict.words[i])
        } else {
            for (i in WordDict.range(c)) action(WordDict.words[i])
        }
    }

    fun candidates(starts: Set<Char>, common: Boolean): List<String> {
        val out = ArrayList<String>()
        if (common) {
            // 상용 단어 풀은 첫 글자로만 묶여 있어서, 앞말잇기에서는 끝 글자로 직접 걸러낸다
            if (chainMode == ChainMode.HEAD) {
                for (w in WordDict.commonList) {
                    if (w !in used && chainMode.anchorOf(w) in starts) out.add(w)
                }
            } else {
                for (c in starts) WordDict.commonByFirst[c]?.forEach { if (it !in used) out.add(it) }
            }
        } else {
            for (c in starts) forEachWordLinkedTo(c) { w -> if (w !in used) out.add(w) }
        }
        return out
    }

    /** 보스 규칙을 통과하는 후보만 남긴다. 규칙이 없으면 candidates 와 같다. */
    fun candidatesUnderRules(starts: Set<Char>, common: Boolean): List<String> {
        val base = candidates(starts, common)
        return if (bossRules.isEmpty()) base else base.filter { bossRules.acceptsWord(it) }
    }

    fun hasAnyCandidate(starts: Set<Char>): Boolean {
        for (c in starts) {
            var found = false
            forEachWordLinkedTo(c) { w ->
                if (!found && w !in used && (bossRules.isEmpty() || bossRules.acceptsWord(w))) found = true
            }
            if (found) return true
        }
        return false
    }

    /** word 뒤에 이을 수 있는(아직 안 쓴, 보스 규칙도 통과하는) 단어 수. cap 에서 세기를 멈춘다. */
    fun followUpCount(word: String, cap: Int = 60): Int {
        val starts = chainMode.linkSyllables(word)
        var n = 0
        for (c in starts) {
            forEachWordLinkedTo(c) { w ->
                if (n < cap && w != word && w !in used &&
                    (bossRules.isEmpty() || bossRules.acceptsWord(w))
                ) n++
            }
            if (n >= cap) return n
        }
        return n
    }

    fun validate(raw: String): Verdict {
        val word = raw.trim()
        if (word.isEmpty()) return Verdict.Bad("단어를 입력해 주세요")
        if (!Dueum.isHangul(word) || word.length < 2) return Verdict.Bad("두 글자 이상 한글 단어만 낼 수 있어요")
        val starts = allowedStarts()
        // 맞춰야 하는 자리가 방식마다 다르다 — 끝말잇기는 첫 글자, 앞말잇기는 끝 글자
        if (starts != null && chainMode.anchorOf(word) !in starts) {
            val label = allowedStartsLabel()
            return Verdict.Bad(
                when (chainMode) {
                    ChainMode.HEAD -> "${label}(으)로 끝나야 해요"
                    ChainMode.SAME_HEAD -> "${label}(으)로 시작해야 해요 (같은 글자로 계속!)"
                    ChainMode.TAIL -> "${label}(으)로 시작해야 해요"
                }
            )
        }
        if (word in used) return Verdict.Bad("이미 나온 단어예요")
        if (bossRules.isNotEmpty() && !bossRules.acceptsWord(word)) {
            return Verdict.Bad("${bossRules.rejectionMessage()}만 낼 수 있어요")
        }
        if (!WordDict.contains(word)) return Verdict.Bad("사전에 없는 단어예요")
        return Verdict.Ok(word)
    }

    fun acceptPlayer(word: String, secondsLeft: Int): Int {
        applyWord(word)
        round++
        val points = 10 + word.length * 2 + (if (noTimer) 0 else secondsLeft)
        score += points
        return points
    }

    fun applyWord(word: String) {
        used.add(word)
        prevWord = lastWord
        lastWord = word
    }

    /**
     * AI 첫 단어. 난이도와 무관하게 **한방단어를 절대 내지 않는다.**
     *
     * 첫 단어부터 막히면 손도 못 써 보고 지는 셈이라 어느 난이도에서도 허용하지 않는다.
     * 기준을 단계적으로 낮추되 **어느 갈래에서도 이어갈 단어가 있는지 반드시 확인한다.**
     * 예전에는 규칙을 통과하는 아무 단어나 돌려주는 갈래가 있어서 한방단어가 새어 나갔다.
     *
     * @param avoidLinks 이 글자들로 이어지는 단어는 피한다 (단어 바꾸기·부활에서 쓴다)
     */
    fun openingWord(avoidLinks: Set<Char> = emptySet()): String {
        fun ok(w: String) = w !in used && (bossRules.isEmpty() || bossRules.acceptsWord(w))
        fun fresh(w: String) =
            avoidLinks.isEmpty() || chainMode.linkSyllables(w).none { it in avoidLinks }

        // 1) 이어갈 곳이 아주 넉넉한 짧은 상용 명사 — 평소에는 여기서 끝난다
        repeat(80) {
            val w = WordDict.commonList[rng.nextInt(WordDict.commonList.size)]
            if (w.length in 2..3 && ok(w) && fresh(w) && followUpCount(w, 40) >= 30) return w
        }
        // 2) 길이 조건은 풀되 이어갈 곳은 여전히 넉넉해야 한다
        val roomy = WordDict.commonList.filter { ok(it) && fresh(it) && followUpCount(it, 20) >= 10 }
        if (roomy.isNotEmpty()) return roomy[rng.nextInt(roomy.size)]

        // 3) 상용 풀이 규칙에 다 걸리면 사전 전체에서 찾는다. 여기서도 후속은 확인한다.
        val wide = ArrayList<String>()
        for (w in WordDict.words) {
            if (ok(w) && fresh(w) && followUpCount(w, 8) >= 5) {
                wide.add(w)
                if (wide.size >= 200) break
            }
        }
        if (wide.isNotEmpty()) return wide[rng.nextInt(wide.size)]

        // 4) avoidLinks 때문에 다 막힌 것이라면 그 조건만 버리고 다시 본다
        if (avoidLinks.isNotEmpty()) return openingWord()

        // 5) 이 사전·규칙 조합으로는 이어갈 수 있는 단어가 아예 없다.
        //    그래도 후속이 가장 많은 것을 골라 최선을 다한다.
        return WordDict.commonList.filter { ok(it) }.maxByOrNull { followUpCount(it, 60) }
            ?: WordDict.words.firstOrNull { ok(it) }
            ?: "사과"
    }

    /** 아이템: 낼 수 있는 단어 힌트 */
    fun hintWord(): String? = hintWords(1).firstOrNull()

    /**
     * 아이템: 이어서 낼 수 있는 단어를 최대 n개 추천한다.
     * 뒤가 막히지 않는 상용 단어를 먼저 채우고, 모자라면 사전 전체에서 보충한다.
     * 중복 없이 돌려주고, 후보가 없으면 빈 목록을 준다.
     */
    fun hintWords(n: Int = 3): List<String> {
        if (n <= 0) return emptyList()
        val starts = allowedStarts() ?: return emptyList()
        val picked = LinkedHashSet<String>()

        val good = candidatesUnderRules(starts, common = true).filter { followUpCount(it, 4) >= 2 }
        for (w in sample(good, n * 3)) {
            if (picked.size >= n) break
            picked.add(w)
        }
        if (picked.size < n) {
            for (w in sample(candidatesUnderRules(starts, common = false), n * 3)) {
                if (picked.size >= n) break
                picked.add(w)
            }
        }
        return picked.toList()
    }

    /**
     * 아이템·부활: AI가 방금 낸 단어를 다른 단어로 교체. null = 교체 불가.
     *
     * 바꿨는데 **내가 이어야 할 글자가 그대로면 바꾼 보람이 없다.** 그래서 지금 단어가
     * 요구하는 글자와 겹치지 않는 단어를 우선으로 고르고, 그런 단어가 없을 때만
     * 겹치는 것으로 물러선다. 어느 경우든 이어갈 단어가 있는 것만 고른다 —
     * 돈 주고 쓴 아이템이 한방단어를 물어다 주면 안 된다.
     *
     * 이어가기 방식(끝말/같은글자/앞말)도 그대로 따른다. 예전에는 끝 글자 기준으로
     * 굳어 있어서 앞말잇기 판에서 엉뚱한 단어가 나왔다.
     */
    fun rerollAiWord(): String? {
        val current = lastWord
        val avoid = current?.let { chainMode.linkSyllables(it) }.orEmpty()
        val base = prevWord

        val newWord = if (base == null) {
            // 아직 AI 첫 단어뿐이다 — 이어야 할 제약이 없으니 새로 뽑는다.
            // openingWord 는 사전이 바닥나면 막다른 단어라도 돌려주므로 여기서 한 번 더 본다.
            openingWord(avoidLinks = avoid)
                .takeIf { it != current && followUpCount(it, 1) >= 1 }
        } else {
            pickRerollWord(chainMode.linkSyllables(base), avoid)
        } ?: return null

        // 기존 단어는 used 에 남겨 두고(재사용 금지) lastWord 만 교체한다
        used.add(newWord)
        lastWord = newWord
        return newWord
    }

    /**
     * 바꿔치기용 단어 고르기.
     * @param starts 앞 단어에서 이어야 할 글자들
     * @param avoid  이 글자들로 이어지는 단어는 되도록 피한다
     */
    private fun pickRerollWord(starts: Set<Char>, avoid: Set<Char>): String? {
        val pool = candidatesUnderRules(starts, common = true)
            .ifEmpty { candidatesUnderRules(starts, common = false) }
        if (pool.isEmpty()) return null

        val sampled = sample(pool, 120)
        // 이어갈 곳이 있는 것만 남긴다. 그중 요구 글자가 바뀌는 쪽을 먼저 본다.
        val alive = sampled.filter { it != lastWord && followUpCount(it, 4) >= 1 }
        val changed = alive.filter { w -> chainMode.linkSyllables(w).none { it in avoid } }

        if (changed.isNotEmpty()) return changed[rng.nextInt(changed.size)]
        if (alive.isNotEmpty()) return alive[rng.nextInt(alive.size)]

        // 표본이 전멸했으면 풀 전체에서 이어갈 곳이 가장 많은 것을 찾는다.
        // 그마저 막다른 단어면 **바꿔 주지 않는다** — null 을 돌려주면 아이템도
        // 소모되지 않는다. 돈 주고 쓴 아이템이 한방단어를 물어다 주는 것보다 낫다.
        val best = pool.filter { it != lastWord }.maxByOrNull { followUpCount(it, 20) }
        return best?.takeIf { followUpCount(it, 1) >= 1 }
    }

    /** AI 응수. null = 항복 */
    fun aiMove(): String? {
        val starts = allowedStarts() ?: return null
        // 항복 판정(난이도별 성향)
        when (level) {
            AiLevel.VERY_EASY -> if (round >= 5 && rng.nextInt(100) < 15) return null
            AiLevel.EASY -> if (round >= 8 && rng.nextInt(100) < 12) return null
            else -> {}
        }
        return pickAiWord(starts)
    }

    /**
     * 매우쉬움·쉬움·보통이 공통으로 쓰는 고르기.
     *
     * 후보를 '이어갈 수 있는 말이 많은 순'으로 줄 세운 뒤 난이도에 맞는 구간에서 뽑는다.
     * 예전에는 난이도마다 기준(followUpCount >= 8, >= 5, >= 3)이 따로 놀아서
     * 한 단계 올릴 때 얼마나 어려워지는지 가늠이 안 됐다. 같은 자를 쓰면
     * **뒤쪽 구간을 볼수록 어려워진다**가 눈에 보인다.
     *
     * 후속이 0인 단어(한방단어)는 여기서 절대 고르지 않는다.
     *
     * @param band 줄 세운 목록에서 볼 구간. 0.0 이 가장 이어가기 쉬운 쪽.
     */
    private fun pickFromBand(starts: Set<Char>, band: ClosedFloatingPointRange<Double>): String? {
        var pool = candidatesUnderRules(starts, common = true)
        // 상용 단어가 모자라면(보스 규칙 등) 사전 전체에서 짧은 말로 보충한다
        if (pool.size < 8) {
            pool = pool + candidatesUnderRules(starts, common = false).filter { it.length <= 4 }
        }
        if (pool.isEmpty()) return null

        // followUpCount 는 사전을 훑어서 무겁다. 표본만 줄 세운다.
        val ranked = sample(pool, 24)
            .map { it to followUpCount(it, 60) }
            .filter { it.second > 0 }          // 한방단어 제외
            .sortedByDescending { it.second }
        if (ranked.isEmpty()) return null

        val from = (ranked.size * band.start).toInt().coerceIn(0, ranked.size - 1)
        val to = (ranked.size * band.endInclusive).toInt().coerceIn(from + 1, ranked.size)
        val slice = ranked.subList(from, to)
        return slice[rng.nextInt(slice.size)].first
    }

    /** 이 판에서 AI 가 한방단어를 노려도 되는 때인가 */
    private fun canUseHanbang(): Boolean =
        allowHanbang || BossRule.AI_HANBANG in bossRules

    /** 이을 말이 없는 단어(한방단어) 하나. 없으면 null */
    private fun findHanbang(starts: Set<Char>): String? =
        sample(candidatesUnderRules(starts, common = false), 40)
            .firstOrNull { followUpCount(it, 1) == 0 }

    private fun pickAiWord(starts: Set<Char>): String? {
        return when (level) {
            // 이어갈 곳이 가장 많은 쪽에서 고른다
            AiLevel.VERY_EASY -> pickFromBand(starts, 0.0..0.15)
            AiLevel.EASY -> pickFromBand(starts, 0.15..0.45)
            AiLevel.NORMAL -> {
                // 중간 구간이 기본. 다만 판이 좀 진행되면 가끔 한방단어로 찌른다.
                if (canUseHanbang() && round >= 8 && rng.nextInt(100) < 18) {
                    findHanbang(starts)?.let { return it }
                }
                pickFromBand(starts, 0.45..0.75)
            }
            // 어려움은 예전 그대로 — 플레이어 선택지를 좁히고 6라운드부터 한방단어를 노린다
            AiLevel.HARD -> {
                val pool = candidatesUnderRules(starts, common = false)
                if (pool.isEmpty()) return null
                val sampled = sample(pool, 40)
                val canKill = canUseHanbang() && round >= 6
                val scored = sampled.map { it to followUpCount(it, 30) }
                val safe = scored.filter { it.second > 0 }
                val killers = scored.filter { it.second == 0 }
                when {
                    canKill && killers.isNotEmpty() -> killers[rng.nextInt(killers.size)].first
                    safe.isNotEmpty() -> safe.minByOrNull { it.second }!!.first
                    else -> killers[rng.nextInt(killers.size)].first
                }
            }
        }
    }

    private fun sample(pool: List<String>, n: Int): List<String> {
        if (pool.size <= n) return pool
        val picked = HashSet<Int>()
        val out = ArrayList<String>(n)
        while (out.size < n) {
            val i = rng.nextInt(pool.size)
            if (picked.add(i)) out.add(pool[i])
        }
        return out
    }
}
