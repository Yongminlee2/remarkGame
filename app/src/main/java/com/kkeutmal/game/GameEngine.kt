package com.kkeutmal.game

import kotlin.random.Random

enum class AiLevel(val label: String, val timerSec: Int, val desc: String) {
    VERY_EASY("매우쉬움", 60, "AI가 쉬운 단어만 내고 한방단어는 절대 안 써요"),
    EASY("쉬움", 45, "AI가 잇기 좋은 단어를 골라줘요"),
    NORMAL("보통", 20, "AI가 무난하게 받아쳐요"),
    HARD("어려움", 12, "AI가 사전 전체로 압박해요 (한방단어 주의!)")
}

class GameEngine(
    val level: AiLevel,
    val noTimer: Boolean,
    val bossRules: List<BossRule> = emptyList(),
    val chainMode: ChainMode = ChainMode.TAIL
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

    /** AI 첫 단어: 잇기 여지가 넉넉한 짧은 상용 명사. 보스 규칙이 있으면 그 규칙도 지킨다. */
    fun openingWord(): String {
        repeat(60) {
            val w = WordDict.commonList[rng.nextInt(WordDict.commonList.size)]
            if (w.length in 2..3 && w !in used && followUpCount(w, 40) >= 30 &&
                (bossRules.isEmpty() || bossRules.acceptsWord(w))
            ) return w
        }
        if (bossRules.isNotEmpty()) {
            val fallback = WordDict.commonList.filter { it !in used && bossRules.acceptsWord(it) }
            if (fallback.isNotEmpty()) return fallback[rng.nextInt(fallback.size)]
        }
        if (bossRules.isNotEmpty() && !bossRules.acceptsWord("사과")) {
            for (w in WordDict.words) {
                if (w !in used && bossRules.acceptsWord(w)) return w
            }
        }
        return "사과"
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

    /** 아이템: AI가 방금 낸 단어를 다른 단어로 교체. null=교체 불가 */
    fun rerollAiWord(): String? {
        val base = prevWord
        val starts = if (base == null) null else Dueum.variants(base.last())
        val newWord = if (starts == null) {
            openingWord().takeIf { it != lastWord }
        } else {
            pickAiWord(starts)
        } ?: return null
        // 기존 단어는 used 에 남겨두고(재사용 금지) lastWord 만 교체
        used.add(newWord)
        lastWord = newWord
        return newWord
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

    private fun pickAiWord(starts: Set<Char>): String? {
        return when (level) {
            AiLevel.VERY_EASY -> {
                // 한방단어(이을 단어 0개) 절대 금지 + 플레이어가 잇기 아주 좋은 단어만
                var pool = candidatesUnderRules(starts, common = true).filter { followUpCount(it, 12) >= 8 }
                if (pool.isEmpty()) pool = candidatesUnderRules(starts, common = true).filter { followUpCount(it, 2) >= 1 }
                if (pool.isEmpty()) null
                else sample(pool, 10).maxByOrNull { followUpCount(it, 60) }
            }
            AiLevel.EASY -> {
                val pool = candidatesUnderRules(starts, common = true).filter { followUpCount(it, 8) >= 5 }
                if (pool.isEmpty()) null
                else sample(pool, 10).maxByOrNull { followUpCount(it, 60) }
            }
            AiLevel.NORMAL -> {
                var pool = candidatesUnderRules(starts, common = true).filter { followUpCount(it, 4) >= 3 }
                if (pool.isEmpty()) {
                    pool = candidatesUnderRules(starts, common = false).filter { it.length <= 4 && followUpCount(it, 4) >= 3 }
                }
                if (pool.isEmpty()) null else pool[rng.nextInt(pool.size)]
            }
            AiLevel.HARD -> {
                val pool = candidatesUnderRules(starts, common = false)
                if (pool.isEmpty()) return null
                val sampled = sample(pool, 40)
                val canKill = round >= 6 || BossRule.AI_HANBANG in bossRules
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
