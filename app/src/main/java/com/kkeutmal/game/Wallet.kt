package com.kkeutmal.game

import android.content.Context
import android.content.SharedPreferences

/** 코인·아바타·아이템 보유 관리 (SharedPreferences) */
object Wallet {
    const val DEFAULT_AVATAR = "🙂"

    data class Avatar(val emoji: String, val price: Int)
    data class Item(val id: String, val emoji: String, val nameLabel: String, val descLabel: String, val price: Int)

    @Deprecated("v2 API를 쓸 것")
    val AVATARS = listOf(
        Avatar("🙂", 0), Avatar("😎", 100), Avatar("🐱", 150), Avatar("🐶", 150),
        Avatar("🦊", 200), Avatar("🐼", 250), Avatar("🦁", 300), Avatar("👻", 350),
        Avatar("🤖", 400), Avatar("🐲", 500), Avatar("👑", 800)
    )

    val ITEMS = listOf(
        Item("item_time", "⏰", "시간 +15초", "타이머에 15초를 더해요 (제한시간 모드 전용)", 60),
        Item("item_hint", "💡", "힌트", "이어서 낼 수 있는 단어를 3개 알려줘요", 80),
        Item("item_pass", "🔄", "단어 바꾸기", "AI가 낸 단어를 다른 단어로 바꿔요", 120),
        Item("item_double", "🎯", "2배 획득", "그 판의 코인과 경험치를 2배로 받아요", 120),
        Item("item_revive", "🛡", "부활", "게임 오버를 1회 무효로 만들고 이어서 해요", 200)
    )

    /**
     * 광고 하나를 보고 받는 개수. 값이 싼 아이템일수록 여러 개 준다.
     *
     * 개수를 하나로 고정하면 **부활(200코인)을 광고로 받는 것이 시간(60코인)보다
     * 세 배 이득**이라 아무도 싼 아이템을 광고로 받지 않는다. 광고 한 편의 값어치를
     * 대략 맞춰 두면 필요한 것을 고르게 된다.
     */
    fun adRewardCount(price: Int): Int = when {
        price <= 60 -> 3
        price <= 120 -> 2
        else -> 1
    }

    private fun p(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("kkeutmal", Context.MODE_PRIVATE)

    fun coins(ctx: Context) = p(ctx).getInt("coins", 0)

    fun addCoins(ctx: Context, amount: Int) {
        p(ctx).edit().putInt("coins", coins(ctx) + amount).apply()
    }

    fun spendCoins(ctx: Context, amount: Int): Boolean {
        val c = coins(ctx)
        if (c < amount) return false
        p(ctx).edit().putInt("coins", c - amount).apply()
        return true
    }

    @Deprecated("v2 API를 쓸 것")
    fun ownedAvatars(ctx: Context): Set<String> =
        (p(ctx).getStringSet("owned_avatars", emptySet()) ?: emptySet()) + DEFAULT_AVATAR

    @Deprecated("v2 API를 쓸 것")
    fun ownAvatar(ctx: Context, emoji: String) {
        val cur = ownedAvatars(ctx) + emoji
        p(ctx).edit().putStringSet("owned_avatars", cur).apply()
    }

    @Deprecated("v2 API를 쓸 것")
    fun selectedAvatar(ctx: Context): String =
        p(ctx).getString("sel_avatar", DEFAULT_AVATAR) ?: DEFAULT_AVATAR

    @Deprecated("v2 API를 쓸 것")
    fun selectAvatar(ctx: Context, emoji: String) {
        p(ctx).edit().putString("sel_avatar", emoji).apply()
    }

    fun itemCount(ctx: Context, id: String) = p(ctx).getInt(id, 0)

    fun addItem(ctx: Context, id: String, delta: Int) {
        p(ctx).edit().putInt(id, (itemCount(ctx, id) + delta).coerceAtLeast(0)).apply()
    }

    /** 아이템 1개 사용. 없으면 false */
    fun useItem(ctx: Context, id: String): Boolean {
        val c = itemCount(ctx, id)
        if (c <= 0) return false
        p(ctx).edit().putInt(id, c - 1).apply()
        return true
    }

    /**
     * 구버전(이모지) 아바타 보유 목록을 새 아바타 ID 집합으로 변환한다.
     * 이모지 개수만큼 일반 등급을 앞에서부터 지급하고, 기본 아바타는 항상 포함한다.
     */
    fun migratedAvatarIds(oldEmojis: Set<String>): Set<String> {
        val commons = AvatarCatalog.ALL.filter { it.grade == AvatarGrade.COMMON }.map { it.id }
        val count = oldEmojis.size.coerceIn(0, commons.size)
        return (commons.take(count) + AvatarCatalog.DEFAULT_ID).toSet()
    }

    /** 첫 실행 때 아이템 종류마다 지급하는 개수 */
    const val STARTER_ITEM_COUNT = 3

    /**
     * 지급 플래그 키. 지급 내용을 바꾸면 이 키의 뒤 숫자도 함께 올려서
     * 이미 한 번 받은 사람도 새 지급을 받게 한다.
     */
    private const val STARTER_FLAG = "starter_granted_v3"

    /**
     * 처음에 그냥 주지 않는 아이템.
     *
     * 부활을 3개 쥐고 시작하면 초반 몇 판은 져도 지는 게 아니어서 긴장이 없다.
     * 사거나, 광고를 보거나, 보상으로 얻게 한다.
     *
     * **STARTER_FLAG 를 올리면 안 된다** — 올리면 이미 받은 사람에게 전부 다시 지급된다.
     * 이 제외 목록은 앞으로 새로 시작하는 사람에게만 적용된다.
     */
    private val STARTER_EXCLUDED = setOf("item_revive")

    /**
     * 처음 시작하는 플레이어에게 모든 아이템을 종류별로 지급한다.
     * 플래그로 한 번만 실행되며, 이미 갖고 있던 개수에 더한다.
     */
    fun ensureStarterGrant(ctx: Context) {
        val prefs = p(ctx)
        if (prefs.getBoolean(STARTER_FLAG, false)) return
        val editor = prefs.edit()
        for (item in ITEMS) {
            if (item.id in STARTER_EXCLUDED) continue
            editor.putInt(item.id, itemCount(ctx, item.id) + STARTER_ITEM_COUNT)
        }
        editor.putBoolean(STARTER_FLAG, true).apply()
    }

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

    /**
     * 카탈로그에서 사라진 아바타 아이디를 정리한다.
     *
     * 표정 종류를 늘리면서 아이디 체계가 바뀌면(예: `circle_red_special` → `circle_red_wink`)
     * 저장돼 있던 아이디가 어디에도 없는 유령이 된다. 그대로 두면 프로필 아바타가 빈칸이 되고
     * 도감 수집 개수도 어긋난다. 보유 목록에서 유령을 걷어내고, 착용 중이던 것이 유령이면
     * 같은 몸형태·색의 아바타로 옮겨준다(없으면 기본 아바타).
     */
    fun pruneStaleAvatars(ctx: Context) {
        val prefs = p(ctx)
        val owned = prefs.getStringSet("owned_avatars_v2", emptySet()) ?: emptySet()
        val valid = owned.filter { AvatarCatalog.byId(it) != null }.toMutableSet()

        // 유령이 있었다면 같은 몸형태·색의 아바타로 최대한 살려준다
        for (stale in owned - valid) {
            val parts = stale.split("_")
            if (parts.size < 2) continue
            AvatarCatalog.ALL.firstOrNull { it.shape.id == parts[0] && it.color.id == parts[1] }
                ?.let { valid.add(it.id) }
        }
        valid.add(AvatarCatalog.DEFAULT_ID)

        val selected = prefs.getString("sel_avatar_v2", AvatarCatalog.DEFAULT_ID)
        val selectedOk = selected != null && AvatarCatalog.byId(selected) != null

        if (valid != owned || !selectedOk) {
            val newSelected = if (selectedOk) selected else {
                val parts = selected?.split("_").orEmpty()
                if (parts.size >= 2) {
                    AvatarCatalog.ALL.firstOrNull { it.shape.id == parts[0] && it.color.id == parts[1] }?.id
                        ?: AvatarCatalog.DEFAULT_ID
                } else AvatarCatalog.DEFAULT_ID
            }
            prefs.edit()
                .putStringSet("owned_avatars_v2", valid)
                .putString("sel_avatar_v2", newSelected)
                .apply()
        }
    }

    // ---------- 아바타 ----------

    fun ownedAvatarIds(ctx: Context): Set<String> {
        ensureMigrated(ctx)
        pruneStaleAvatars(ctx)
        return p(ctx).getStringSet("owned_avatars_v2", emptySet()) ?: emptySet()
    }

    fun ownAvatarId(ctx: Context, id: String) {
        p(ctx).edit().putStringSet("owned_avatars_v2", ownedAvatarIds(ctx) + id).apply()
    }

    fun selectedAvatarId(ctx: Context): String {
        ensureMigrated(ctx)
        pruneStaleAvatars(ctx)
        return p(ctx).getString("sel_avatar_v2", AvatarCatalog.DEFAULT_ID) ?: AvatarCatalog.DEFAULT_ID
    }

    fun selectAvatarId(ctx: Context, id: String) {
        ensureMigrated(ctx)
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

    // ---------- 승패 전적 ----------
    //
    // 자유 대전과 모험을 가리지 않고 끝난 판을 모두 센다.
    // **항복도 패배로 센다** — 스스로 그만둔 것도 못 이긴 것이니, 그래야 기록이 정직하다.
    // 부활로 이어서 한 판은 아직 안 끝난 판이라 세지 않는다(끝나는 시점에 한 번만 센다).

    fun wins(ctx: Context) = p(ctx).getInt("wins", 0)

    fun losses(ctx: Context) = p(ctx).getInt("losses", 0)

    /** @return 이 판까지 이어진 연승 수. 졌으면 0. */
    fun recordResult(ctx: Context, win: Boolean): Int {
        val key = if (win) "wins" else "losses"
        val streak = if (win) winStreak(ctx) + 1 else 0
        p(ctx).edit()
            .putInt(key, p(ctx).getInt(key, 0) + 1)
            .putInt("win_streak", streak)
            .putInt("win_streak_best", maxOf(bestWinStreak(ctx), streak))
            .apply()
        return streak
    }

    fun winStreak(ctx: Context) = p(ctx).getInt("win_streak", 0)

    fun bestWinStreak(ctx: Context) = p(ctx).getInt("win_streak_best", 0)

    /**
     * 패배 1회를 지운다(상점에서 광고를 보고 쓴다).
     * @return 지울 패배가 있었는가. 0패인 사람에게는 광고를 보여 주지 않는다.
     */
    fun removeOneLoss(ctx: Context): Boolean {
        val cur = losses(ctx)
        if (cur <= 0) return false
        p(ctx).edit().putInt("losses", cur - 1).apply()
        return true
    }

    // ---------- 중간에 꺼 버린 판 ----------
    //
    // 지고 있을 때 앱을 꺼서 패배를 피하는 것을 막는다. 판을 시작할 때 표시를 남기고
    // 정상적으로 끝나면 지운다. 표시가 남은 채로 앱이 다시 켜지면 그 판은 진 것으로 센다.

    fun markGameStarted(ctx: Context) {
        p(ctx).edit().putBoolean("game_in_progress", true).apply()
    }

    fun clearGameInProgress(ctx: Context) {
        p(ctx).edit().putBoolean("game_in_progress", false).apply()
    }

    /** @return 중단된 판을 패배로 처리했는가. 화면에 알려 주려고 돌려준다. */
    fun settleAbandonedGame(ctx: Context): Boolean {
        if (!p(ctx).getBoolean("game_in_progress", false)) return false
        clearGameInProgress(ctx)
        recordResult(ctx, win = false)
        return true
    }

    /**
     * 승률(%). **한 판도 안 했으면 null** — 0으로 나누기를 여기서 막는다.
     *
     * 화면에서 직접 나누면 첫 실행에 앱이 죽는데, 개발자는 이미 몇 판 해 본
     * 상태라 재현되지 않는다. 저장소를 안 타는 순수 함수라 테스트로 묶어 둔다.
     */
    fun winRatePercent(wins: Int, losses: Int): Int? {
        val total = wins + losses
        return if (total <= 0) null else wins * 100 / total
    }

    fun playerStats(ctx: Context) = PlayerStats(
        bestRounds = bestRounds(ctx),
        bestStage = bestStage(ctx),
        ownedAvatarCount = ownedAvatarIds(ctx).size,
        bestStreak = bestStreak(ctx)
    )
}
