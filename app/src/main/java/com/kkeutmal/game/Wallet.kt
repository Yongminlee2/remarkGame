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

    /** 첫 실행 때 지급하는 힌트 아이템 수 */
    const val STARTER_HINTS = 50

    /**
     * 처음 시작하는 플레이어에게 힌트 아이템을 넉넉히 지급한다.
     * `starter_granted` 플래그로 한 번만 실행되며, 이미 갖고 있던 개수에 더한다.
     */
    fun ensureStarterGrant(ctx: Context) {
        val prefs = p(ctx)
        if (prefs.getBoolean("starter_granted", false)) return
        prefs.edit()
            .putInt("item_hint", itemCount(ctx, "item_hint") + STARTER_HINTS)
            .putBoolean("starter_granted", true)
            .apply()
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

    fun playerStats(ctx: Context) = PlayerStats(
        bestRounds = bestRounds(ctx),
        bestStage = bestStage(ctx),
        ownedAvatarCount = ownedAvatarIds(ctx).size,
        bestStreak = bestStreak(ctx)
    )
}
