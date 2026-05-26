package com.example.ui.screens

import android.os.Build
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.example.BuildConfig
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntry
import com.example.service.NexusAccessibilityService
import com.example.service.NexusOverlayService
import com.example.ui.NexusViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MainDashboard(
    viewModel: NexusViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianBlack,
                tonalElevation = 8.dp
            ) {
                val tabs = listOf(
                    Triple("COSMOS", Icons.Default.AutoAwesome, 0),
                    Triple("CHAT", Icons.Default.Chat, 1),
                    Triple("MEMORY", Icons.Default.Memory, 2),
                    Triple("ROBO", Icons.Default.PlayArrow, 3),
                    Triple("SHELL", Icons.Default.Terminal, 4),
                    Triple("PLUG", Icons.Default.Tune, 5)
                )

                tabs.forEach { (title, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            keyboardController?.hide()
                        },
                        icon = { Icon(icon, contentDescription = title) },
                        label = {
                            Text(
                                text = title,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(250),
                label = "dashboard_tabs"
            ) { tab ->
                when (tab) {
                    0 -> CosmosScreen(viewModel)
                    1 -> ChatConsoleScreen(viewModel)
                    2 -> MemoryVaultScreen(viewModel)
                    3 -> AutomationRulesScreen(viewModel)
                    4 -> DeveloperShellScreen(viewModel)
                    5 -> SettingsPluginsScreen(viewModel)
                }
            }
        }
    }
}

// ---------------- COSMOS CORE SCREEN ----------------
@Composable
fun CosmosScreen(viewModel: NexusViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val lastLog = terminalLogs.lastOrNull() ?: "All systems nominal."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Futuristic Status Bar Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NEXUS PLATFORM",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = NeonCyan,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "OS INTEL ASSISTANT V3.12",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (NexusAccessibilityService.isServiceConnected) ActiveGreen.copy(alpha = 0.15f) else NeonCoral.copy(alpha = 0.15f))
                    .border(1.dp, if (NexusAccessibilityService.isServiceConnected) ActiveGreen else NeonCoral, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (NexusAccessibilityService.isServiceConnected) "AUTO CONNECTED" else "AUTO DETACHED",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (NexusAccessibilityService.isServiceConnected) ActiveGreen else NeonCoral,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active Agent Badge Indicator
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CosmicSlate)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ActiveGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = viewModel.activeAgent.uppercase(),
                    color = TextLight,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Pulse Holographic Orb Viewports
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HolographicOrb(
                    modifier = Modifier.size(190.dp),
                    isThinking = viewModel.isThinking,
                    isActiveListening = viewModel.isListeningSpeech
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (viewModel.isListeningSpeech) "NEXUS IS LISTENING..." 
                           else if (viewModel.isThinking) "NEXUS COGNITIVE CORE THINKING..."
                           else "TAP TO VOICE COMM",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = if (viewModel.isListeningSpeech) ActiveGreen else if (viewModel.isThinking) GlowingPurple else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Real-time Sound Oscillation Graph Layout
        AnimatedVisibility(
            visible = viewModel.isListeningSpeech,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            InfiniteVoiceWave(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .background(Color.Transparent),
                isActive = true
            )
        }

        // Suggestion Chips Grid and Quick Toggles Drawer
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "HOLOGRAPHIC CONTEXT: $lastLog",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val context = LocalContext.current
                
                Button(
                    onClick = {
                        if (viewModel.isListeningSpeech) {
                            viewModel.stopVoiceListening()
                        } else {
                            viewModel.startVoiceListening()
                        }
                    },
                    modifier = Modifier.weight(1.3f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.isListeningSpeech) ActiveGreen else NeonCyan,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (viewModel.isListeningSpeech) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Voice Activation"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (viewModel.isListeningSpeech) "STOP INTAKE" else "VOICE INGEST",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                val isOverlayActive = NexusOverlayService.isOverlayShowing
                Button(
                    onClick = { viewModel.toggleFloatingBubble(context) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOverlayActive) GlowingPurple else CosmicSlate,
                        contentColor = TextLight
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Overlay")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOverlayActive) "DISMISS OVER" else "DEPLOY BUBBLE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable context automation recipes suggestions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "Simulate Camera" to "open camera",
                    "Morning Briefing" to "Morning Coffee Briefing",
                    "Simulate dialer" to "make call"
                )

                suggestions.forEach { (label, command) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmicSlate)
                            .border(1.dp, NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.logTerminal("Suggestion Toggled: \"$command\"")
                                viewModel.handleUserRequest(command)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------------- CHAT CONSOLE SCREEN ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatConsoleScreen(viewModel: NexusViewModel) {
    val chatHistory by viewModel.chatHistory.collectAsState()
    val scope = rememberCoroutineScope()
    var inputQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current

    // Scroll chat history to bottom automatically on new entries
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Chat Header with Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CHAT HISTORY BUFFER",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Code block highlighter icon status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.isThinking) ActiveGreen else CosmicSlate)
                )
            }

            IconButton(onClick = { viewModel.clearHistory() }) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Clear History", tint = NeonCoral)
            }
        }

        // Scrolling Message Array Viewports
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatHistory, key = { it.id }) { message ->
                val isUser = message.role == "user"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .background(if (isUser) CosmicSlate else SurfaceGlass)
                            .border(
                                1.dp,
                                if (isUser) NeonCyan.copy(alpha = 0.25f) else GlowingPurple.copy(alpha = 0.2f),
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            // Speaker Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUser) "USER INTENT" else "NEXUS COGNITION",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = if (isUser) NeonCyan else GlowingPurple
                                )
                                
                                if (!isUser) {
                                    IconButton(
                                        modifier = Modifier.size(16.dp),
                                        onClick = { clipboardManager.setText(AnnotatedString(message.text)) }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextMuted, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Text Render body supporting monospace for custom coding assistants
                            if (message.hasCode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ObsidianBlack)
                                        .border(1.dp, ActiveGreen.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = message.text,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ActiveGreen
                                        )
                                    )
                                }
                            } else {
                                Text(
                                    text = message.text,
                                    fontSize = 13.sp,
                                    color = TextLight,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                        fontSize = 8.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (viewModel.isThinking) {
                item {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.3.dp, color = NeonCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Orchestrating AI response matrix...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Text Ingress Core Keyboard Container
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                textStyle = TextStyle(color = TextLight, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                placeholder = { Text("Query system...", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputQuery.isNotBlank()) {
                        viewModel.handleUserRequest(inputQuery)
                        inputQuery = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CosmicSlate,
                    cursorColor = NeonCyan
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonCyan)
                    .clickable {
                        if (inputQuery.isNotBlank()) {
                            viewModel.handleUserRequest(inputQuery)
                            inputQuery = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, contentDescription = "Transmit query", tint = ObsidianBlack)
            }
        }
    }
}

// ---------------- MEMORY VAULT SCREEN ----------------
@Composable
fun MemoryVaultScreen(viewModel: NexusViewModel) {
    val memories by viewModel.memoryBank.collectAsState()
    var inputKey by remember { mutableStateOf("") }
    var inputValue by remember { mutableStateOf("") }
    var categorySelection by remember { mutableStateOf("preference") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        HolographicHeading(text = "MEMORY STORAGE VAULT", accentColor = NeonCyan)
        Spacer(modifier = Modifier.height(12.dp))

        // Create Memory Node Glassy Card
        GlassyCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SEED MEMORY VECTOR NODE",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    textStyle = TextStyle(color = TextLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    placeholder = { Text("Concept Key (e.g., Default Name)", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = CosmicSlate)
                )

                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    textStyle = TextStyle(color = TextLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    placeholder = { Text("Recall Payload value (e.g., Arthur)", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = CosmicSlate)
                )

                // Category selector chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf("preference", "habit", "note")
                    categories.forEach { cat ->
                        val isSelected = categorySelection == cat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) NeonCyan else CosmicSlate)
                                .clickable { categorySelection = cat }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.uppercase(),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) ObsidianBlack else TextLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (inputKey.isNotBlank() && inputValue.isNotBlank()) {
                            viewModel.createMemory(inputKey, inputValue, categorySelection)
                            inputKey = ""
                            inputValue = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "COMMIT TO LONG-TERM MEMORY", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Persistent database output list
        Text(
            text = "SEMANTIC MEMORY DATA INDEX",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(memories) { memo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceGlass)
                        .border(1.dp, CosmicSlate, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = memo.category.uppercase(),
                                    fontSize = 7.sp,
                                    color = NeonCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = memo.contentKey,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = memo.contentValue, color = TextMuted, fontSize = 11.sp)
                    }

                    IconButton(onClick = { viewModel.removeMemory(memo.id) }) {
                        Icon(Icons.Default.RemoveCircle, contentDescription = "Erase Node", tint = NeonCoral, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ---------------- AUTOMATION MATRIX SCREEN ----------------
@Composable
fun AutomationRulesScreen(viewModel: NexusViewModel) {
    val workflows by viewModel.automationWorkflows.collectAsState()
    var title by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("TIME") }
    var triggerVal by remember { mutableStateOf("08:00 AM") }
    var actionType by remember { mutableStateOf("LAUNCH_APP") }
    var actionVal by remember { mutableStateOf("com.google.android.youtube") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        HolographicHeading(text = "AUTOMATION ROBOTIC MATRIX", accentColor = GlowingPurple)
        Spacer(modifier = Modifier.height(10.dp))

        // Add Rule Card
        GlassyCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = GlowingPurple.copy(alpha = 0.25f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "COMPILE MACRO INTENT RULE",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = GlowingPurple,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(color = TextLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    placeholder = { Text("Workflow Title (e.g. Headphones on launch)", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GlowingPurple, unfocusedBorderColor = CosmicSlate)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Trigger selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TRIGGER", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                        val triggerList = listOf("TIME", "APP_LAUNCH", "HEADPHONES")
                        triggerList.forEach { trig ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { triggerType = trig }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = triggerType == trig, onClick = { triggerType = trig }, colors = RadioButtonDefaults.colors(selectedColor = GlowingPurple))
                                Text(trig, fontSize = 10.sp, color = TextLight, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Action selector
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("ACTION RESULT", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                        val actionList = listOf("LAUNCH_APP", "TOGGLE_WIFI", "SAY", "OPEN_CAM")
                        actionList.forEach { act ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { actionType = act }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = actionType == act, onClick = { actionType = act }, colors = RadioButtonDefaults.colors(selectedColor = GlowingPurple))
                                Text(act, fontSize = 10.sp, color = TextLight, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = triggerVal,
                        onValueChange = { triggerVal = it },
                        textStyle = TextStyle(color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        label = { Text("Trigger Value (e.g. 10:30 PM)", fontSize = 9.sp, color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GlowingPurple, unfocusedBorderColor = CosmicSlate)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = actionVal,
                        onValueChange = { actionVal = it },
                        textStyle = TextStyle(color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        label = { Text("Action Payload (e.g. pkg_name or speaker text)", fontSize = 9.sp, color = TextMuted) },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GlowingPurple, unfocusedBorderColor = CosmicSlate)
                    )
                }

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.createWorkflow(title, triggerType, triggerVal, actionType, actionVal)
                            title = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlowingPurple, contentColor = ObsidianBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "DEPLOY ACTIVE RECIPE WORKFLOW", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Recipes queue
        Text(
            text = "DEPLOYED MACROS QUEUE",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(workflows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceGlass)
                        .border(1.dp, CosmicSlate, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(text = row.title, fontWeight = FontWeight.Bold, color = TextLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "IF [${row.triggerType}: ${row.triggerValue}] THEN [${row.actionType}: ${row.actionValue}]", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        
                        if (row.lastTriggered > 0) {
                            Text(
                                text = "Last run: " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(row.lastTriggered)),
                                fontSize = 8.sp,
                                color = ActiveGreen,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = row.isActive,
                            onCheckedChange = { viewModel.toggleWorkflow(row, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlowingPurple, checkedTrackColor = GlowingPurple.copy(alpha = 0.5f))
                        )
                        IconButton(onClick = { viewModel.removeWorkflow(row.id) }) {
                            Icon(Icons.Default.RemoveCircle, contentDescription = "Erase Recipe", tint = NeonCoral, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- DEVELOPER SHELL SCREEN ----------------
@Composable
fun DeveloperShellScreen(viewModel: NexusViewModel) {
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    var devCommand by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val consoleListState = rememberLazyListState()

    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            consoleListState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        HolographicHeading(text = "NEXUS COGNITION CORE CONSOLE", accentColor = ActiveGreen)
        Spacer(modifier = Modifier.height(10.dp))

        // Console screen viewports
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianBlack)
                .border(1.dp, ActiveGreen.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                state = consoleListState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(terminalLogs) { log ->
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (log.contains("Error")) NeonCoral else if (log.contains("Voice")) GlowingPurple else if (log.contains("Memory")) NeonCyan else ActiveGreen,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Console prompt controller
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NEXUS_SYS:~$ ",
                fontFamily = FontFamily.Monospace,
                color = ActiveGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            OutlinedTextField(
                value = devCommand,
                onValueChange = { devCommand = it },
                textStyle = TextStyle(color = ActiveGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                placeholder = { Text("Type custom system script commands...", color = ActiveGreen.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (devCommand.isNotBlank()) {
                        executeTerminalCommand(devCommand, viewModel)
                        devCommand = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ActiveGreen,
                    unfocusedBorderColor = ActiveGreen.copy(alpha = 0.3f),
                    cursorColor = ActiveGreen
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (devCommand.isNotBlank()) {
                        executeTerminalCommand(devCommand, viewModel)
                        devCommand = ""
                    }
                }
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run command", tint = ActiveGreen)
            }
        }
    }
}

private fun executeTerminalCommand(command: String, viewModel: NexusViewModel) {
    viewModel.logTerminal("Ingesting manual Command: \"$command\"")
    val query = command.lowercase().trim()
    when {
        query.contains("clear") || query == "cls" -> {
            viewModel.clearHistory()
            viewModel.logTerminal("Shell: Consoles purged.")
        }
        query.contains("trigger") || query.contains("run") -> {
            viewModel.logTerminal("Intent Dispatcher: Triggering coffee workflow.")
            viewModel.executeDeviceAction("LAUNCH_APP", "com.google.android.youtube")
        }
        query.contains("help") -> {
            viewModel.logTerminal("Core Command Manual:")
            viewModel.logTerminal("  cls / clear  - Flush conversation nodes")
            viewModel.logTerminal("  trigger      - Simulate automated morning actions")
            viewModel.logTerminal("  model pro    - Set AI LLM back-grid to Pro")
            viewModel.logTerminal("  model flash  - Set AI LLM back-grid to Swift")
        }
        query.contains("model pro") -> {
            viewModel.activeModel = "gemini-3.1-pro-preview"
            viewModel.logTerminal("Config: Core AI mapped to PRO.")
        }
        query.contains("model flash") -> {
            viewModel.activeModel = "gemini-3.5-flash"
            viewModel.logTerminal("Config: Swift Core AI activated.")
        }
        else -> {
            // Treat as general intent trigger
            viewModel.handleUserRequest(command)
        }
    }
}

// ---------------- SETTINGS PLUGINS SCREEN ----------------
@Composable
fun SettingsPluginsScreen(viewModel: NexusViewModel) {
    val currentKey = BuildConfig.GEMINI_API_KEY
    var inputKey by remember { mutableStateOf(if (currentKey.length > 5 && currentKey != "MY_GEMINI_API_KEY") "••••••••••••••••" else "") }
    var userTtsEnabled by remember { mutableStateOf(viewModel.ttsEnabled) }
    val scope = rememberCoroutineScope()
    var isKeySavedMessageVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        HolographicHeading(text = "ONBOARDING CREDENTIALS & PLUGINS", accentColor = NeonCyan)
        Spacer(modifier = Modifier.height(14.dp))

        // API Setup Section
        GlassyCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = "API Security", tint = NeonCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "GEMINI SECURE API ROUTING", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }

                Text(
                    text = "Any API key injected from the AI Studio Secrets panel is securely mapped. If you want to supply or test a custom override key, enter it below.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    textStyle = TextStyle(color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    placeholder = { Text("Enter Gemini API secure key here...", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = CosmicSlate)
                )

                Button(
                    onClick = {
                        if (inputKey.isNotBlank()) {
                            viewModel.createMemory("user_key_override", inputKey, "preference")
                            viewModel.logTerminal("Secure Storage: Saved custom override key.")
                            isKeySavedMessageVisible = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SAVE OVERRIDE CREDENTIAL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                if (isKeySavedMessageVisible) {
                    Text(
                        text = "Custom override registered in local database memory nodes.",
                        fontSize = 10.sp,
                        color = ActiveGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Config section
        Text(
            text = "AI AGENT PLUGINS CONFIG",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceGlass)
                .border(1.dp, CosmicSlate, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("INTELLIGENT MODEL ENGINE", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("Select cognitive back-grid version", color = TextMuted, fontSize = 10.sp)
            }

            var expandedModelDropdown by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { expandedModelDropdown = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSlate, contentColor = NeonCyan),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(viewModel.activeModel.substringAfterLast("-").uppercase(), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                DropdownMenu(
                    expanded = expandedModelDropdown,
                    onDismissRequest = { expandedModelDropdown = false },
                    modifier = Modifier.background(CosmicSlate).border(1.dp, CosmicSlate, RoundedCornerShape(6.dp))
                ) {
                    val models = listOf("gemini-3.5-flash" to "Swift Flash Engine", "gemini-3.1-pro-preview" to "Strong Analytical Pro")
                    models.forEach { (id, desc) ->
                        DropdownMenuItem(
                            text = { Text("$id ($desc)", color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            onClick = {
                                viewModel.activeModel = id
                                viewModel.logTerminal("System Mode: Configured model to -> $id")
                                expandedModelDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceGlass)
                .border(1.dp, CosmicSlate, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("VOICE SYNTHESIS (TTS)", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("Speak AI answers automatically", color = TextMuted, fontSize = 10.sp)
            }

            Switch(
                checked = userTtsEnabled,
                onCheckedChange = {
                    userTtsEnabled = it
                    viewModel.ttsEnabled = it
                    viewModel.logTerminal("Settings: Voice feedback set to $it")
                },
                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        var backgroundServiceActive by remember { mutableStateOf(com.example.service.NexusForegroundService.isServiceRunning) }
        val context = LocalContext.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceGlass)
                .border(1.dp, CosmicSlate, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PERSISTENT BACKGROUND ASSISTANT", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("NEXUS runs & listens to voice commands in background", color = TextMuted, fontSize = 10.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = backgroundServiceActive,
                onCheckedChange = { active ->
                    backgroundServiceActive = active
                    val serviceIntent = Intent(context, com.example.service.NexusForegroundService::class.java)
                    if (active) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            viewModel.logTerminal("Continuous Background Mode: Service Started")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            viewModel.logTerminal("Error activating service: ${e.localizedMessage}")
                        }
                    } else {
                        context.stopService(serviceIntent)
                        viewModel.logTerminal("Continuous Background Mode: Service Suspended")
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permissions Status Overview (Self-Onboarding Sandbox)
        Text(
            text = "SYSTEM PERMISSIONS STATUS",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val overlayGranted = NexusOverlayService.isOverlayShowing
        val accessibilityActive = NexusAccessibilityService.isServiceConnected

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceGlass)
                .border(1.dp, CosmicSlate, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Accessibility Service", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("Enables task automation", fontSize = 10.sp, color = TextMuted)
            }

            Text(
                if (accessibilityActive) "ACTIVE" else "NOT CONNECTED",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (accessibilityActive) ActiveGreen else NeonCoral,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
