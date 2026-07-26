package com.kkeutmal.game

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kkeutmal.game.databinding.ItemChatAiBinding
import com.kkeutmal.game.databinding.ItemChatPlayerBinding
import com.kkeutmal.game.databinding.ItemChatSysBinding
import com.kkeutmal.game.databinding.ItemChatTypingBinding

sealed class ChatItem {
    data class Ai(val word: String, val meaning: String?) : ChatItem()
    data class Player(val word: String, val points: Int, val meaning: String?) : ChatItem()
    data class Sys(val text: String) : ChatItem()
    data object Typing : ChatItem()
}

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = ArrayList<ChatItem>()
    var playerAvatarId: String = AvatarCatalog.DEFAULT_ID

    fun add(item: ChatItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun removeLastIfTyping() {
        val i = items.size - 1
        if (i >= 0 && items[i] is ChatItem.Typing) {
            items.removeAt(i)
            notifyItemRemoved(i)
        }
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ChatItem.Ai -> 0
        is ChatItem.Player -> 1
        is ChatItem.Sys -> 2
        is ChatItem.Typing -> 3
    }

    private class AiHolder(val b: ItemChatAiBinding) : RecyclerView.ViewHolder(b.root)
    private class PlayerHolder(val b: ItemChatPlayerBinding) : RecyclerView.ViewHolder(b.root)
    private class SysHolder(val b: ItemChatSysBinding) : RecyclerView.ViewHolder(b.root)
    private class TypingHolder(b: ItemChatTypingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> AiHolder(ItemChatAiBinding.inflate(inf, parent, false))
            1 -> PlayerHolder(ItemChatPlayerBinding.inflate(inf, parent, false))
            2 -> SysHolder(ItemChatSysBinding.inflate(inf, parent, false))
            else -> TypingHolder(ItemChatTypingBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ChatItem.Ai -> {
                val b = (holder as AiHolder).b
                b.tvWord.text = item.word
                bindMeaning(b.tvMeaning, item.meaning)
            }
            is ChatItem.Player -> {
                val b = (holder as PlayerHolder).b
                b.tvWord.text = item.word
                b.tvPoints.text = "+${item.points}점"
                b.avatarView.bind(AvatarCatalog.byId(playerAvatarId))
                bindMeaning(b.tvMeaning, item.meaning)
            }
            is ChatItem.Sys -> (holder as SysHolder).b.tvText.text = item.text
            is ChatItem.Typing -> Unit
        }
    }

    private fun bindMeaning(tv: android.widget.TextView, meaning: String?) {
        if (meaning.isNullOrBlank()) {
            tv.visibility = View.GONE
        } else {
            tv.visibility = View.VISIBLE
            tv.text = meaning
        }
    }
}
