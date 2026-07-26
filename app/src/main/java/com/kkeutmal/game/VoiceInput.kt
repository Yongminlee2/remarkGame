package com.kkeutmal.game

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent

/** 한국어 음성 인식 → 단어 텍스트. android.speech.SpeechRecognizer 래퍼 */
class VoiceInput(private val activity: Activity) {
    private var recognizer: android.speech.SpeechRecognizer? = null
    var listening = false
        private set
    var onResult: (String) -> Unit = {}
    var onStateChange: (Boolean) -> Unit = {}   // true=듣기 시작, false=끝
    var onFail: (String) -> Unit = {}

    fun isAvailable(): Boolean =
        android.speech.SpeechRecognizer.isRecognitionAvailable(activity)

    fun start() {
        if (listening) return
        if (!isAvailable()) { onFail("이 폰에서 음성 인식을 지원하지 않아요"); return }
        if (recognizer == null) {
            recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(activity).apply {
                setRecognitionListener(listener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        listening = true
        onStateChange(true)
        recognizer?.startListening(intent)
    }

    fun cancel() {
        if (!listening) return
        recognizer?.cancel()
        end()
    }

    private fun end() {
        if (listening) {
            listening = false
            onStateChange(false)
        }
    }

    fun cleanup() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onResults(b: Bundle?) {
            end()
            val list = b?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
            val word = list?.firstOrNull()?.replace(Regex("[^가-힣]"), "")
            if (word.isNullOrEmpty()) onFail("무슨 말인지 못 알아들었어요") else onResult(word)
        }

        override fun onError(e: Int) {
            end()
            when (e) {
                android.speech.SpeechRecognizer.ERROR_NO_MATCH,
                android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    onFail("무슨 말인지 못 알아들었어요. 다시 말해보세요")
                android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    onFail("마이크 권한이 필요해요")
                else -> onFail("음성 인식 오류 (코드 $e)")
            }
        }

        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(p: Float) {}
        override fun onBufferReceived(p: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(p1: Int, p2: Bundle?) {}
    }
}
