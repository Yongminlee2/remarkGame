package com.kkeutmal.game

import android.content.Context
import android.media.MediaPlayer

class AudioManager(val context: Context) {
    private val players = mutableMapOf<String, MediaPlayer>()
    private var bgm: MediaPlayer? = null
    private var bgmActive = false
    private var lastTrack = -1

    /** raw 리소스에서 bgm_01..bgm_20 수집 */
    private val bgmIds: List<Int> by lazy {
        (1..40).mapNotNull { i ->
            val rid = context.resources.getIdentifier(String.format("bgm_%02d", i), "raw", context.packageName)
            if (rid != 0) rid else null
        }
    }

    fun preload() {
        listOf("sfx_ok", "sfx_ai", "sfx_error", "sfx_win", "sfx_lose", "sfx_tick").forEach { name ->
            val rid = context.resources.getIdentifier(name, "raw", context.packageName)
            if (rid != 0) {
                MediaPlayer.create(context, rid)?.let { mp ->
                    mp.setVolume(0.7f, 0.7f)
                    players[name] = mp
                }
            }
        }
    }

    fun play(name: String) {
        players[name]?.apply { if (isPlaying) seekTo(0) else start() }
    }

    /** 20곡 중 랜덤 재생, 곡이 끝나면 다음 랜덤 곡으로 이어짐 */
    fun startBgm() {
        if (bgmActive) return
        bgmActive = true
        playNextBgm()
    }

    private fun playNextBgm() {
        if (!bgmActive || bgmIds.isEmpty()) return
        bgm?.release()
        var pick = bgmIds.indices.random()
        if (bgmIds.size > 1 && pick == lastTrack) pick = (pick + 1) % bgmIds.size // 같은 곡 연속 방지
        lastTrack = pick
        bgm = MediaPlayer.create(context, bgmIds[pick])?.apply {
            setVolume(0.35f, 0.35f)
            setOnCompletionListener { playNextBgm() }
            start()
        }
    }

    fun stopBgm() {
        bgmActive = false
        bgm?.release()
        bgm = null
    }

    fun cleanup() {
        stopBgm()
        players.values.forEach { it.release() }
        players.clear()
    }
}
