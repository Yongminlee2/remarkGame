package com.kkeutmal.game

import android.content.Context
import android.content.SharedPreferences

/** 코인·아바타·아이템 보유 관리 (SharedPreferences) */
object Wallet {
    const val DEFAULT_AVATAR = "🙂"

    data class Avatar(val emoji: String, val price: Int)
    data class Item(val id: String, val emoji: String, val nameLabel: String, val descLabel: String, val price: Int)

    val AVATARS = listOf(
        Avatar("🙂", 0), Avatar("😎", 100), Avatar("🐱", 150), Avatar("🐶", 150),
        Avatar("🦊", 200), Avatar("🐼", 250), Avatar("🦁", 300), Avatar("👻", 350),
        Avatar("🤖", 400), Avatar("🐲", 500), Avatar("👑", 800)
    )

    val ITEMS = listOf(
        Item("item_time", "⏰", "시간 +15초", "타이머에 15초를 더해요 (제한시간 모드 전용)", 60),
        Item("item_hint", "💡", "힌트", "낼 수 있는 단어를 하나 알려줘요", 80),
        Item("item_pass", "🔄", "단어 바꾸기", "AI가 낸 단어를 다른 단어로 바꿔요", 120)
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

    fun ownedAvatars(ctx: Context): Set<String> =
        (p(ctx).getStringSet("owned_avatars", emptySet()) ?: emptySet()) + DEFAULT_AVATAR

    fun ownAvatar(ctx: Context, emoji: String) {
        val cur = ownedAvatars(ctx) + emoji
        p(ctx).edit().putStringSet("owned_avatars", cur).apply()
    }

    fun selectedAvatar(ctx: Context): String =
        p(ctx).getString("sel_avatar", DEFAULT_AVATAR) ?: DEFAULT_AVATAR

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
}
