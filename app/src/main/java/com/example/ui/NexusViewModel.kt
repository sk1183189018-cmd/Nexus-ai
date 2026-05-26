package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.InlineData
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.local.NexusDatabase
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntry
import com.example.data.repository.NexusRepository
import com.example.service.NexusAccessibilityService
import com.example.service.NexusOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class NexusViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val db = NexusDatabase.getDatabase(application)
    private val repository = NexusRepository(db.nexusDao())

    // Expose Room flows reactively to Jetpack Compose
    val chatHistory: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoryBank: StateFlow<List<MemoryEntry>> = repository.allMemory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automationWorkflows: StateFlow<List<AutomationWorkflow>> = repository.allWorkflows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Interactive States
    var isThinking by mutableStateOf(false)
        private set

    var activeAgent by mutableStateOf("Assistant Agent")
        private set

    var activeModel by mutableStateOf("gemini-3.5-flash")

    var voiceModeActive by mutableStateOf(false)

    var isListeningSpeech by mutableStateOf(false)
        private set

    var recognizedSpeechText by mutableStateOf("")
        private set

    var ttsEnabled by mutableStateOf(true)

    // Developer terminal simulator logs
    private val _terminalLogs = MutableStateFlow<List<String>>(
        listOf("NEXUS Core Shell initialized.", "AI System: Ready.", "Listening context: ENABLED")
    )
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    // Speech-To-Text & Text-To-Speech Components
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        try {
            textToSpeech = TextToSpeech(application, this)
        } catch (e: Exception) {
            e.printStackTrace()
            logTerminal("Voice Module: TTS Synthesizer initialization failed: ${e.localizedMessage}")
        }
        initializeSpeechRecognizer(application)

        // Seed initial recipes & preferences
        viewModelScope.launch {
            delay(1000)
            repository.seedInitialDataIfEmpty(automationWorkflows.value, memoryBank.value)
        }

        // Start background workflow scheduler checking simulated triggers every 15 seconds
        startSimulatedWorkflowScheduler()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(1.05f)
            textToSpeech?.setSpeechRate(1.0f)
            logTerminal("Voice Module: TTS Synthesizer configured (US-English).")
        } else {
            logTerminal("Voice Module Error: TTS initialization failed.")
        }
    }

    private fun initializeSpeechRecognizer(context: Context) {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListeningSpeech = true
                            recognizedSpeechText = "Listening..."
                        }

                        override fun onBeginningOfSpeech() {
                            recognizedSpeechText = "Decoding voice audio..."
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListeningSpeech = false
                        }

                        override fun onError(error: Int) {
                            isListeningSpeech = false
                            recognizedSpeechText = ""
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NETWORK -> "Network issue"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission missing"
                                else -> "Could not recognize"
                            }
                            logTerminal("Speech Recognition: $msg")
                            speak("I couldn't hear that clearly. Could you repeat?")
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val speech = matches[0]
                                recognizedSpeechText = speech
                                logTerminal("Speech Resolved: \"$speech\"")
                                // Automatically submit recognized query as request
                                handleUserRequest(speech)
                            } else {
                                recognizedSpeechText = ""
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            } else {
                logTerminal("Speech Recognizer unavailable on this system configuration.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logTerminal("Speech Recognizer setup failed: ${e.localizedMessage}")
        }
    }

    fun startVoiceListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            logTerminal("Speech Recognition error: ${e.localizedMessage}")
        }
    }

    fun stopVoiceListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isListeningSpeech = false
    }

    fun speak(text: String) {
        if (ttsEnabled) {
            textToSpeech?.speak(text.replace(Regex("[#*`_-]"), ""), TextToSpeech.QUEUE_FLUSH, null, "nexus_tts")
        }
    }

    fun handleUserRequest(userInput: String, attachedImage: Bitmap? = null) {
        if (userInput.isBlank() && attachedImage == null) return

        viewModelScope.launch {
            // 1. Persist user request to local DB
            val userMsg = ChatMessage(role = "user", text = userInput, hasCode = containsCodeKeywords(userInput))
            repository.saveMessage(userMsg)

            isThinking = true

            // 2. Multimodal context and Multi-agent resolution
            val selectedAgent = determineBestAgent(userInput, attachedImage)
            activeAgent = selectedAgent
            logTerminal("Orchestration Matrix: Routing to Agent -> [$selectedAgent]")

            // 3. Formulate system context and query history
            val systemContext = buildSystemContext(selectedAgent)

            // 4. Retrieve Gemini Response
            val responseString: String
            val isOnline = isNetworkConnected()
            val apiKey = BuildConfig.GEMINI_API_KEY

            if (isOnline && apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
                responseString = makeGeminiApiCall(systemContext, userInput, attachedImage)
            } else {
                // FALLBACK OFFLINE AI INTENT COMPILER
                responseString = compileOfflineFallback(userInput)
                logTerminal("Offline Engine: Response resolved locally.")
            }

            // 5. Check if the resolved response suggests a physical device action
            checkForEmbeddedDeviceAction(userInput, responseString)

            // 6. Persist response in local database
            val aiMsg = ChatMessage(role = "model", text = responseString, modelUsed = activeModel, hasCode = containsCodeKeywords(responseString))
            repository.saveMessage(aiMsg)

            isThinking = false

            // Speak response if voice assistant mode or speech enabled
            speak(responseString)
        }
    }

    private fun determineBestAgent(input: String, image: Bitmap?): String {
        return when {
            image != null -> "Vision Agent"
            input.contains("code", ignoreCase = true) || input.contains("function", ignoreCase = true) || input.contains("kotlin", ignoreCase = true) || input.contains("python", ignoreCase = true) || input.contains("script", ignoreCase = true) -> "Coding Agent"
            input.contains("search", ignoreCase = true) || input.contains("latest", ignoreCase = true) || input.contains("news", ignoreCase = true) || input.contains("weather", ignoreCase = true) -> "Search Agent"
            input.contains("rule", ignoreCase = true) || input.contains("automation", ignoreCase = true) || input.contains("workflow", ignoreCase = true) || input.contains("trigger", ignoreCase = true) -> "Automation Agent"
            input.contains("remember", ignoreCase = true) || input.contains("preference", ignoreCase = true) || input.contains("memory", ignoreCase = true) -> "Memory Agent"
            else -> "Assistant Agent"
        }
    }

    private fun buildSystemContext(agentName: String): String {
        val base = "You are NEXUS, an intelligent operating system companion. Today is 2026-05-26. Current active app package is: ${NexusAccessibilityService.activeAppPackage}.\n"
        val memoryContext = buildString {
            append("Long Term Core Memories:\n")
            memoryBank.value.forEach {
                append("- [${it.category}] Key: ${it.contentKey} = ${it.contentValue}\n")
            }
        }
        
        val agentSpecific = when (agentName) {
            "Coding Agent" -> "You are an expert Google Senior Architect. Supply clean markdown formatting, specify programming languages, utilize concise modern design patterns, and output complete code blocks."
            "Search Agent" -> "You represent the web crawler and research processor. Respond to real-time events, structure answers in chronological ordering, and cite simulated search sources clearly."
            "Automation Agent" -> "You are the operating system's macro coordinator. Output triggers and command scripts to configure rules. If the user wants to configure a workflow, you should state that you have successfully created an automation rule."
            "Vision Agent" -> "You are the visual observer of the system, extracting OCR details and indexing photo streams."
            "Memory Agent" -> "You organize state information, habits, and user requests in our semantic data cache."
            else -> "You are the direct interface of the system. Speak like a friendly personal JARVIS — highly intelligent, swift, professional, and slightly futuristic."
        }

        return base + memoryContext + "\n" + agentSpecific
    }

    private suspend fun makeGeminiApiCall(systemInstruction: String, prompt: String, image: Bitmap?): String = withContext(Dispatchers.IO) {
        try {
            val systemPart = Content(parts = listOf(Part(text = systemInstruction)))
            
            val contentParts = ArrayList<Part>()
            contentParts.add(Part(text = prompt))
            
            if (image != null) {
                // Convert bitmap to Base64 to supply inline multimodal prompt parameters as mandated
                val base64Data = convertBitmapToBase64(image)
                contentParts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data)))
            }

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = contentParts)),
                systemInstruction = systemPart,
                generationConfig = GenerationConfig(temperature = 0.5f)
            )

            val key = BuildConfig.GEMINI_API_KEY
            val apiModel = if (image != null) "gemini-2.5-flash-image" else activeModel
            
            val response = RetrofitClient.service.generateContent(apiModel, key, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Nexus AI is silent."
        } catch (e: Exception) {
            logTerminal("API Error Exception: ${e.localizedMessage}")
            val fallback = compileOfflineFallback(prompt)
            "System offline fallback triggered. ${fallback}"
        }
    }

    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // fallback rule compiler for offline triggers or when API key is missing
    private fun compileOfflineFallback(input: String): String {
        val query = input.lowercase()
        return when {
            query.contains("open camera") -> {
                "[ACTION:OPEN_CAM] Instantly launching the system camera viewfinder locally."
            }
            query.contains("wifi") -> {
                "[ACTION:TOGGLE_WIFI] Toggling WiFi radio controller."
            }
            query.contains("alarm") -> {
                "[ACTION:SET_ALARM] Setting automated alarm."
            }
            query.contains("call") || query.contains("phone") -> {
                "[ACTION:MAKE_CALL] Opening core dialing portal."
            }
            query.contains("sms") || query.contains("text") -> {
                "[ACTION:SEND_SMS] Preparing messaging composer."
            }
            query.contains("open youtube") -> {
                "[ACTION:LAUNCH_APP] Opening YouTube media network."
            }
            query.contains("help") || query.contains("system") -> {
                "NEXUS Offline OS Module Active. Speak commands like 'open camera', 'wifi settings', or 'make call' to control the system locally without internet configuration."
            }
            else -> {
                "Hello! NEXUS is running in local backup mode. Let me know which system features you'd like to automate or control."
            }
        }
    }

    private fun checkForEmbeddedDeviceAction(userInput: String, aiResponse: String) {
        val text = (userInput + " " + aiResponse).uppercase()
        
        when {
            text.contains("[ACTION:OPEN_CAM]") || text.contains("OPEN CAMERA") -> {
                executeDeviceAction("OPEN_CAM", "")
            }
            text.contains("[ACTION:TOGGLE_WIFI]") || text.contains("TOGGLE WIFI") || text.contains("TURN OFF WIFI") -> {
                executeDeviceAction("TOGGLE_WIFI", "OFF")
            }
            text.contains("[ACTION:SET_ALARM]") || text.contains("SET ALARM") -> {
                executeDeviceAction("SET_ALARM", "08:00 AM")
            }
            text.contains("[ACTION:MAKE_CALL]") || text.contains("MAKE CALL") || text.contains("DIAL") -> {
                executeDeviceAction("MAKE_CALL", "911")
            }
            text.contains("[ACTION:SEND_SMS]") || text.contains("SEND SMS") || text.contains("TEXT") -> {
                executeDeviceAction("SEND_SMS", "Hello from Nexus AI Operating System Core!")
            }
            text.contains("[ACTION:LAUNCH_APP]") || text.contains("LAUNCH APP") -> {
                executeDeviceAction("LAUNCH_APP", "com.google.android.youtube")
            }
        }
    }

    fun executeDeviceAction(actionType: String, actionValue: String) {
        val context = getApplication<Application>()
        logTerminal("Executing intent action: $actionType (Value: $actionValue)")

        viewModelScope.launch {
            try {
                when (actionType) {
                    "OPEN_CAM" -> {
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    "TOGGLE_WIFI" -> {
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Opening WiFi controls", Toast.LENGTH_SHORT).show()
                    }
                    "LAUNCH_APP" -> {
                        val pkg = if (actionValue.isEmpty()) "com.google.android.youtube" else actionValue
                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } else {
                            // General search app intent if package not installed
                            val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$pkg")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(searchIntent)
                        }
                    }
                    "SET_ALARM" -> {
                        val intent = Intent(AlarmManagerIntentAction).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        // Fallback settings if clock package is secure
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        }
                    }
                    "MAKE_CALL" -> {
                        val dialerIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${actionValue.ifEmpty { "555-0199" }}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(dialerIntent)
                    }
                    "SEND_SMS" -> {
                        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${actionValue.ifEmpty { "555-0199" }}")).apply {
                            putExtra("sms_body", "Nexus Assistant Auto SMS notification pipeline.")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(smsIntent)
                    }
                }
            } catch (e: Exception) {
                logTerminal("Intent Launch Error: ${e.localizedMessage}")
            }
        }
    }

    // Automation Workflows Coordinator
    private fun startSimulatedWorkflowScheduler() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(20000) // check every 20s
                
                val activeList = automationWorkflows.value.filter { it.isActive }
                if (activeList.isNotEmpty()) {
                    // Evaluate triggers matching simulated timings or foreground app transitions
                    val currentHour = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                    
                    for (workflow in activeList) {
                        var triggered = false
                        when (workflow.triggerType) {
                            "TIME" -> {
                                // Simulate if current hour matches trigger
                                triggered = true // trigger in simulation automatically occasionally
                            }
                            "APP_LAUNCH" -> {
                                if (NexusAccessibilityService.activeAppPackage == workflow.triggerValue) {
                                    triggered = true
                                }
                            }
                            "HEADPHONES" -> {
                                triggered = true // simulate attachment trigger
                            }
                        }

                        if (triggered) {
                            withContext(Dispatchers.Main) {
                                logTerminal("Scheduler: Trigger event matched [${workflow.title}]. Executing action: ${workflow.actionType}")
                                executeDeviceAction(workflow.actionType, workflow.actionValue)
                                repository.markWorkflowTriggered(workflow)
                                speak("Automation rule triggered. Running workflow: ${workflow.title}.")
                            }
                            break // Limit single trigger sweep for stability
                        }
                    }
                }
            }
        }
    }

    // Long Term Memory modifiers
    fun createMemory(key: String, value: String, category: String = "preference") {
        viewModelScope.launch {
            val entry = MemoryEntry(category = category, contentKey = key, contentValue = value)
            repository.addMemory(entry)
            logTerminal("Memory Bank: Added new vector node [$key = $value]")
        }
    }

    fun removeMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
            logTerminal("Memory Bank: Removed node $id.")
        }
    }

    // Automation modifications
    fun createWorkflow(title: String, trigger: String, tValue: String, action: String, aValue: String) {
        viewModelScope.launch {
            val wf = AutomationWorkflow(
                title = title,
                triggerType = trigger,
                triggerValue = tValue,
                actionType = action,
                actionValue = aValue
            )
            repository.addWorkflow(wf)
            logTerminal("Automation Core: Created recipe [$title]")
        }
    }

    fun toggleWorkflow(workflow: AutomationWorkflow, active: Boolean) {
        viewModelScope.launch {
            repository.updateWorkflowActiveStatus(workflow, active)
            logTerminal("Automation Core: Workflow \"${workflow.title}\" is now ${if (active) "ACTIVE" else "DISABLED"}")
        }
    }

    fun removeWorkflow(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkflow(id)
            logTerminal("Automation Core: Workflow removed.")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            logTerminal("Orchestrator Matrix: Cleared history index.")
        }
    }

    fun toggleFloatingBubble(context: Context) {
        val intent = Intent(context, NexusOverlayService::class.java)
        if (NexusOverlayService.isOverlayShowing) {
            context.stopService(intent)
            logTerminal("System Overlay: Suspended floating bubble.")
        } else {
            context.startService(intent)
            logTerminal("System Overlay: Deployed active holographic bubble.")
        }
    }

    fun logTerminal(msg: String) {
        val current = _terminalLogs.value.toMutableList()
        current.add("[${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}] $msg")
        if (current.size > 25) current.removeAt(0)
        _terminalLogs.value = current
    }

    private fun containsCodeKeywords(text: String): Boolean {
        val keywords = listOf("fun ", "class ", "def ", "import ", "const ", "</html>", "package ", "val ", "var ")
        return keywords.any { text.contains(it) }
    }

    private fun isNetworkConnected(): Boolean {
        // Simple fallback
        return true
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }

    companion object {
        private const val AlarmManagerIntentAction = "android.intent.action.SET_ALARM"
    }
}
