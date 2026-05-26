package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.local.NexusDatabase
import com.example.data.model.ChatMessage
import com.example.data.repository.NexusRepository
import android.net.Uri
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class NexusForegroundService : Service(), TextToSpeech.OnInitListener {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isProcessing = false
    private var isRecognizerPausedForTTS = false

    private lateinit var database: NexusDatabase
    private lateinit var repository: NexusRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        val notification = buildServiceNotification("NEXUS Core Listening background matrix...")
        
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        database = NexusDatabase.getDatabase(this)
        repository = NexusRepository(database.nexusDao())

        // Initializing TTS Speech Synthesis Engine
        try {
            textToSpeech = TextToSpeech(this, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize and trigger Background Voice Engine
        initializeSpeechEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        isServiceRunning = true
        startListeningLoop()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NEXUS Core Process Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps NEXUS operating brain active continuously in the background"
                enableLights(true)
                lightColor = Color.CYAN
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildServiceNotification(statusText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val stopIntent = Intent(this, NexusForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NEXUS Background Agent Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "SHUTDOWN NEXUS", stopPendingIntent)
            .setColor(Color.parseColor("#00FFFF"))
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildServiceNotification(statusText))
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(1.05f)
            textToSpeech?.setSpeechRate(1.0f)

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isRecognizerPausedForTTS = true
                }

                override fun onDone(utteranceId: String?) {
                    isRecognizerPausedForTTS = false
                    serviceScope.launch {
                        delay(600)
                        startListeningLoop()
                    }
                }

                override fun onError(utteranceId: String?) {
                    isRecognizerPausedForTTS = false
                    serviceScope.launch {
                        delay(600)
                        startListeningLoop()
                    }
                }
            })

            speak("NEXUS offline background agent has gone online.")
        }
    }

    private fun speak(text: String) {
        if (textToSpeech != null) {
            val containsHindi = text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI }
            if (containsHindi) {
                textToSpeech?.language = Locale("hi", "IN")
            } else {
                textToSpeech?.language = Locale.US
            }
            textToSpeech?.speak(
                text.replace(Regex("[#*`_-]"), ""),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "nexus_bg_tts"
            )
        }
    }

    private fun initializeSpeechEngine() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            updateNotification("NEXUS Status: Listening for voice intake...")
                        }

                        override fun onBeginningOfSpeech() {
                            updateNotification("NEXUS Status: Ingesting audio waves...")
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListening = false
                        }

                        override fun onError(error: Int) {
                            isListening = false
                            // If recognizer is busy or experiences other errors, reclaim and revive it
                            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                                serviceScope.launch {
                                    try {
                                        speechRecognizer?.cancel()
                                        speechRecognizer?.destroy()
                                    } catch (e: Exception) {}
                                    delay(1000)
                                    initializeSpeechEngine()
                                    startListeningLoop()
                                }
                                return
                            }
                            if (!isProcessing && !isRecognizerPausedForTTS) {
                                serviceScope.launch {
                                    delay(1500)
                                    startListeningLoop()
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val speech = matches[0]
                                processBackgroundVoiceRequest(speech)
                            } else {
                                startListeningLoop()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startListeningLoop() {
        if (isListening || isProcessing || isRecognizerPausedForTTS) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processBackgroundVoiceRequest(query: String) {
        if (query.isBlank()) return
        isProcessing = true
        updateNotification("NEXUS Status: AI engine resolving request...")

        serviceScope.launch {
            try {
                val userMsg = ChatMessage(role = "user", text = query, hasCode = false)
                repository.saveMessage(userMsg)

                val systemContext = """
                    You are NEXUS, an advanced online background AI assistant with deep expertise in coding, website creation, software engineering, databases, and AI models across all languages.
                    You are equipped with Siri and Google Assistant-like system features. 
                    If the user asks technical, logic, or programming questions, offer highly expert and comprehensive guidance.
                    Speak very concisely, naturally, and warmly. If the user talks in Hindi or Hinglish, respond in warm Hindi or Hinglish.
                    Keep vocal sentences short (ideally under 25 words) for optimal TTS speech output.

                    Always include the specific tag when users want you to open system controls or applications:
                    - Open YouTube (e.g., 'youtube open karo', 'open youtube'): Include '[ACTION:OPEN_YOUTUBE]' and say 'Haan sir, ho gaya, YouTube open ho gaya hai.' or 'Sure sir, YouTube is open.'.
                    - Open Camera (e.g., 'camera open', 'take photo'): Include '[ACTION:OPEN_CAM]' and say 'Haan sir, camera open ho gaya hai' or similar.
                    - Dial / Call someone (e.g., 'call karo', 'nexus call'): Include '[ACTION:DIAL_PHONE]' and say 'Haan sir, dialer screen open ho gaya hai' or similar.
                    - Send a SMS / Message (e.g., 'message karo', 'massage/sms send'): Include '[ACTION:SEND_MESSAGE]' and say 'Haan sir, message compose open ho gaya hai' or similar.
                    - Change WiFi (e.g., 'wifi settings', 'toggle wifi'): Include '[ACTION:TOGGLE_WIFI]' and say 'Haan sir, WiFi screen open ho gaya hai' or similar.
                """.trimIndent()
                val apiKey = BuildConfig.GEMINI_API_KEY
                
                val reply: String = if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
                    makeGeminiCall(systemContext, query)
                } else {
                    compileOfflineFallback(query)
                }

                executeLocalActionIfEmbedded(query, reply)

                val aiMsg = ChatMessage(role = "model", text = reply, modelUsed = "gemini-3.5-flash", hasCode = false)
                repository.saveMessage(aiMsg)

                speak(reply)
                updateNotification("NEXUS Status: AI processed recent cue successfully.")
            } catch (e: Exception) {
                e.printStackTrace()
                updateNotification("NEXUS Status: AI error recovery state.")
                isProcessing = false
                startListeningLoop()
            }
        }
    }

    private suspend fun makeGeminiCall(sysInst: String, prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val systemPart = Content(parts = listOf(Part(text = sysInst)))
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                systemInstruction = systemPart,
                generationConfig = GenerationConfig(temperature = 0.5f)
            )
            val baseModel = "gemini-3.5-flash"
            val key = BuildConfig.GEMINI_API_KEY
            val response = RetrofitClient.service.generateContent(baseModel, key, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Nexus backup active."
        } catch (e: Exception) {
            compileOfflineFallback(prompt)
        }
    }

    private fun compileOfflineFallback(input: String): String {
        val query = input.lowercase()
        return when {
            query.contains("youtube") -> "[ACTION:OPEN_YOUTUBE] Haan sir, YouTube open ho gaya hai."
            query.contains("camera") || query.contains("photo") || query.contains("picture") -> "[ACTION:OPEN_CAM] Haan sir, camera open ho gaya hai."
            query.contains("wifi") || query.contains("wi-fi") -> "[ACTION:TOGGLE_WIFI] Haan sir, Wi-Fi screen open ho gaya hai."
            query.contains("call") || query.contains("phone") || query.contains("dialer") -> "[ACTION:DIAL_PHONE] Haan sir, dialer screen open ho gaya hai."
            query.contains("message") || query.contains("sms") || query.contains("massage") -> "[ACTION:SEND_MESSAGE] Haan sir, message screen open ho gaya hai."
            query.contains("hello") || query.contains("nexus") -> "Standing by. Active background setup ready."
            else -> "Offline background prompt captured: $input."
        }
    }

    private fun executeLocalActionIfEmbedded(userText: String, replyText: String) {
        val comb = (userText + " " + replyText).uppercase()
        when {
            comb.contains("[ACTION:OPEN_YOUTUBE]") || comb.contains("OPEN YOUTUBE") || comb.contains("YOUTUBE OPEN") -> {
                try {
                    val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            comb.contains("[ACTION:OPEN_CAM]") || comb.contains("OPEN CAMERA") || comb.contains("CAMERA OPEN") -> {
                try {
                    val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            comb.contains("[ACTION:TOGGLE_WIFI]") || comb.contains("TOGGLE WIFI") || comb.contains("WIFI") -> {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            comb.contains("[ACTION:DIAL_PHONE]") || comb.contains("CALL KARO") || comb.contains("MAKE A CALL") || comb.contains("NEXUS CALL") -> {
                try {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            comb.contains("[ACTION:SEND_MESSAGE]") || comb.contains("MESSAGE KARO") || comb.contains("MASSAGE KARO") || comb.contains("SEND SMS") || comb.contains("SEND MESSAGE") -> {
                try {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_MESSAGING)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(fallbackIntent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        try {
            serviceJob.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val CHANNEL_ID = "NEXUS_BG_MONITORING_CHANNEL"
        const val NOTIFICATION_ID = 404
        const val ACTION_STOP = "com.example.service.STOP_FOREGROUND"

        var isServiceRunning = false
            private set
    }
}
