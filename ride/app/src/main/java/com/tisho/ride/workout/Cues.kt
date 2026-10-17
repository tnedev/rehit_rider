package com.tisho.ride.workout

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Beeps for countdowns and spoken coaching cues at step changes. */
class Cues(context: Context) {
    var voice = true
    var sounds = true
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    private var ttsReady = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
    }.also { it.language = Locale.US }

    fun beep(short: Boolean) {
        if (!sounds) return
        tone?.startTone(if (short) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2, if (short) 120 else 350)
    }

    fun say(text: String) {
        if (!voice || !ttsReady || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }
}
