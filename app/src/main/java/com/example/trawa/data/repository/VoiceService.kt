package com.example.trawa.data.repository

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.trawa.domain.model.VoiceSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

interface VoiceService {
  val voiceState: StateFlow<VoiceSessionState>
  val liveTranscript: StateFlow<String>
  val lastSpokenResponse: StateFlow<String>
  val isAudioAvailable: Boolean

  fun startListening(onResult: (String) -> Unit)
  fun stopListening()
  fun speak(text: String, onDone: (() -> Unit)? = null)
  fun stopSpeaking()
  fun interrupt()
  fun destroy()
}

class TrawaVoiceService(
  private val context: Context
) : VoiceService {

  private val _voiceState = MutableStateFlow(VoiceSessionState.IDLE)
  override val voiceState: StateFlow<VoiceSessionState> = _voiceState.asStateFlow()

  private val _liveTranscript = MutableStateFlow("")
  override val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

  private val _lastSpokenResponse = MutableStateFlow("")
  override val lastSpokenResponse: StateFlow<String> = _lastSpokenResponse.asStateFlow()

  private var speechRecognizer: SpeechRecognizer? = null
  private var tts: TextToSpeech? = null
  private var isTtsReady = false

  override val isAudioAvailable: Boolean
    get() = SpeechRecognizer.isRecognitionAvailable(context)

  init {
    initTts()
  }

  private fun initTts() {
    try {
      tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
          isTtsReady = true
          // Default language
          tts?.language = Locale.getDefault()
        }
      }
    } catch (e: Exception) {
      Log.e("TrawaVoice", "Failed to initialize TTS", e)
    }
  }

  override fun startListening(onResult: (String) -> Unit) {
    if (_voiceState.value == VoiceSessionState.SPEAKING) {
      interrupt()
    }

    if (!isAudioAvailable) {
      _voiceState.value = VoiceSessionState.ERROR
      return
    }

    try {
      speechRecognizer?.destroy()
      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            _voiceState.value = VoiceSessionState.LISTENING
          }

          override fun onBeginningOfSpeech() {}

          override fun onRmsChanged(rmsdB: Float) {}

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {
            _voiceState.value = VoiceSessionState.PROCESSING
          }

          override fun onError(error: Int) {
            Log.w("TrawaVoice", "SpeechRecognizer error: $error")
            _voiceState.value = VoiceSessionState.IDLE
          }

          override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull().orEmpty()
            _liveTranscript.value = recognizedText
            _voiceState.value = VoiceSessionState.IDLE
            if (recognizedText.isNotBlank()) {
              onResult(recognizedText)
            }
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            partial?.firstOrNull()?.let {
              _liveTranscript.value = it
            }
          }

          override fun onEvent(eventType: Int, params: Bundle?) {}
        })
      }

      // Universal multilingual recognizer configuration (Arabic, English, and all locales)
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        // Set Arabic preference while supporting bilingual recognition
        val defaultLocale = Locale.getDefault()
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, defaultLocale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar,en")
        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ar", "en", "ar-EG", "ar-SA", "en-US", "en-GB"))
      }

      speechRecognizer?.startListening(intent)
      _voiceState.value = VoiceSessionState.LISTENING
    } catch (e: Exception) {
      Log.e("TrawaVoice", "Error starting listening", e)
      _voiceState.value = VoiceSessionState.ERROR
    }
  }

  override fun stopListening() {
    try {
      speechRecognizer?.stopListening()
      _voiceState.value = VoiceSessionState.IDLE
    } catch (_: Exception) {}
  }

  override fun speak(text: String, onDone: (() -> Unit)?) {
    if (!isTtsReady || text.isBlank()) return
    interrupt()

    _voiceState.value = VoiceSessionState.SPEAKING
    _lastSpokenResponse.value = text

    // Dynamic language detection for high quality speech in Arabic, English, or any language
    val isArabic = text.any { it in '\u0600'..'\u06FF' }
    try {
      if (isArabic) {
        val arLocale = Locale("ar")
        if (tts?.isLanguageAvailable(arLocale) != TextToSpeech.LANG_NOT_SUPPORTED) {
          tts?.language = arLocale
        }
      } else {
        val enLocale = Locale.ENGLISH
        if (tts?.isLanguageAvailable(enLocale) != TextToSpeech.LANG_NOT_SUPPORTED) {
          tts?.language = enLocale
        } else {
          tts?.language = Locale.getDefault()
        }
      }
    } catch (e: Exception) {
      Log.w("TrawaVoice", "Error setting dynamic TTS language", e)
    }

    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) {}

      override fun onDone(utteranceId: String?) {
        _voiceState.value = VoiceSessionState.IDLE
        onDone?.invoke()
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) {
        _voiceState.value = VoiceSessionState.IDLE
      }
    })

    // Clean markdown characters and code blocks for smooth, natural speech
    val speechCleanText = text
      .replace(Regex("```[\\s\\S]*?```"), "كود برمجي")
      .replace(Regex("[#*`_\\[\\]()]"), "")
      .trim()

    val utteranceId = "trawa_tts_${System.currentTimeMillis()}"
    tts?.speak(speechCleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
  }

  override fun stopSpeaking() {
    tts?.stop()
    if (_voiceState.value == VoiceSessionState.SPEAKING) {
      _voiceState.value = VoiceSessionState.IDLE
    }
  }

  override fun interrupt() {
    stopSpeaking()
    stopListening()
    _voiceState.value = VoiceSessionState.IDLE
  }

  override fun destroy() {
    speechRecognizer?.destroy()
    speechRecognizer = null
    tts?.stop()
    tts?.shutdown()
    tts = null
  }
}
