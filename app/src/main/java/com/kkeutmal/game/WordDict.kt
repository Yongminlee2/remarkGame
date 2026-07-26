package com.kkeutmal.game

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile

/**
 * 사전: dict_all.txt (표준국어대사전+끄투 DB 병합, LC_ALL=C 정렬) 를 정렬 배열로 들고
 * 이진 탐색으로 검증/범위 조회. 뜻풀이는 means.bin(원문)+means.idx(오프셋)로 필요할 때만 읽는다.
 */
object WordDict {
    @Volatile
    var ready = false
        private set

    lateinit var words: Array<String>
        private set
    val commonList = ArrayList<String>(3_200)
    val commonByFirst = HashMap<Char, MutableList<String>>()

    private var meanIdx: IntArray? = null
    private var meansFile: RandomAccessFile? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ArrayList<() -> Unit>()
    private var loading = false

    @Synchronized
    fun preload(context: Context, onReady: (() -> Unit)? = null) {
        if (ready) {
            onReady?.let { mainHandler.post(it) }
            return
        }
        onReady?.let { pending.add(it) }
        if (loading) return
        loading = true
        val app = context.applicationContext
        Thread({
            loadFromAssets(app)
            synchronized(this) {
                ready = true
                val callbacks = pending.toList()
                pending.clear()
                mainHandler.post { callbacks.forEach { it() } }
            }
        }, "dict-loader").start()
    }

    private fun loadFromAssets(context: Context) {
        val list = ArrayList<String>(440_000)
        context.assets.open("dict_all.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val w = line.trim()
                if (w.length >= 2) list.add(w)
            }
        }
        words = list.toTypedArray()

        context.assets.open("dict_common.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val w = line.trim()
                if (w.length < 2 || !contains(w)) continue
                commonList.add(w)
                commonByFirst.getOrPut(w[0]) { ArrayList() }.add(w)
            }
        }

        // 뜻풀이(없어도 게임은 동작)
        try {
            val idxBytes = context.assets.open("means.idx").readBytes()
            val n = idxBytes.size / 4
            val idx = IntArray(n)
            for (i in 0 until n) {
                val b = i * 4
                idx[i] = (idxBytes[b].toInt() and 0xFF) or
                        ((idxBytes[b + 1].toInt() and 0xFF) shl 8) or
                        ((idxBytes[b + 2].toInt() and 0xFF) shl 16) or
                        ((idxBytes[b + 3].toInt() and 0xFF) shl 24)
            }
            if (n != words.size + 1) throw IllegalStateException("idx size mismatch")
            val f = File(context.filesDir, "means.bin")
            val expected = idx[n - 1].toLong()
            if (!f.exists() || f.length() != expected) {
                context.assets.open("means.bin").use { input ->
                    f.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                }
            }
            meanIdx = idx
            meansFile = RandomAccessFile(f, "r")
        } catch (_: Exception) {
            meanIdx = null
            meansFile = null
        }
    }

    fun indexOf(w: String): Int {
        var lo = 0
        var hi = words.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = words[mid].compareTo(w)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    fun contains(w: String): Boolean = indexOf(w) >= 0

    /** c 로 시작하는 단어들의 인덱스 범위 */
    fun range(c: Char): IntRange {
        val lo = lowerBound(c.toString())
        val hi = lowerBound((c + 1).toString())
        return lo until hi
    }

    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** 단어 뜻풀이. 없으면 null */
    @Synchronized
    fun meaning(word: String): String? {
        val idx = meanIdx ?: return null
        val f = meansFile ?: return null
        val i = indexOf(word)
        if (i < 0) return null
        val start = idx[i]
        val end = idx[i + 1]
        if (end <= start) return null
        return try {
            val buf = ByteArray(end - start)
            f.seek(start.toLong())
            f.readFully(buf)
            String(buf, Charsets.UTF_8).trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }
}
