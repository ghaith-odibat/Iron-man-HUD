package com.ghaith.ironhud.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.ghaith.ironhud.ai.Brief
import java.util.Locale

/** Reads briefs aloud with the tablet's own (offline, free) text-to-speech engine. */
class Jarvis(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val british = tts.voices.orEmpty()
            .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "GB" }.thenByDescending { it.quality })
            .firstOrNull()
        if (british != null) tts.setVoice(british) else tts.setLanguage(Locale.UK)
        tts.setPitch(0.85f)
        tts.setSpeechRate(1.08f)
        ready = true
    }

    fun speak(brief: Brief) {
        val line = buildString {
            append(brief.name)
            if (brief.summary.isNotBlank()) append(". ").append(brief.summary)
        }
        say(line)
    }

    fun say(text: String) {
        if (ready && text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() {
        ready = false
        tts.shutdown()
    }
}
