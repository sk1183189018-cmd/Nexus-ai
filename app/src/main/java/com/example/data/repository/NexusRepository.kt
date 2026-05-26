package com.example.data.repository

import com.example.data.local.NexusDao
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntry
import kotlinx.coroutines.flow.Flow

class NexusRepository(private val nexusDao: NexusDao) {

    val allMessages: Flow<List<ChatMessage>> = nexusDao.getAllMessagesFlow()
    val allMemory: Flow<List<MemoryEntry>> = nexusDao.getAllMemoryFlow()
    val allWorkflows: Flow<List<AutomationWorkflow>> = nexusDao.getAllWorkflowsFlow()
    val activeWorkflows: Flow<List<AutomationWorkflow>> = nexusDao.getActiveWorkflowsFlow()

    // Message Operations
    suspend fun saveMessage(message: ChatMessage): Long {
        return nexusDao.insertMessage(message)
    }

    suspend fun clearHistory() {
        nexusDao.clearChatHistory()
    }

    // Memory Operations
    suspend fun addMemory(entry: MemoryEntry): Long {
        return nexusDao.insertMemory(entry)
    }

    suspend fun deleteMemory(id: Long) {
        nexusDao.deleteMemoryById(id)
    }

    suspend fun searchMemoryTerms(query: String): List<MemoryEntry> {
        return nexusDao.searchMemory(query)
    }

    // Workflow Operations
    suspend fun addWorkflow(workflow: AutomationWorkflow): Long {
        return nexusDao.insertWorkflow(workflow)
    }

    suspend fun deleteWorkflow(id: Long) {
        nexusDao.deleteWorkflowById(id)
    }

    suspend fun updateWorkflowActiveStatus(workflow: AutomationWorkflow, active: Boolean) {
        nexusDao.updateWorkflow(workflow.copy(isActive = active))
    }

    suspend fun markWorkflowTriggered(workflow: AutomationWorkflow) {
        nexusDao.updateWorkflow(workflow.copy(lastTriggered = System.currentTimeMillis()))
    }

    // Default seed data for initial setup
    suspend fun seedInitialDataIfEmpty(currentWorkflows: List<AutomationWorkflow>, currentMemory: List<MemoryEntry>) {
        if (currentWorkflows.isEmpty()) {
            val defaults = listOf(
                AutomationWorkflow(
                    title = "Morning Coffee Briefing",
                    triggerType = "TIME",
                    triggerValue = "08:00 AM",
                    actionType = "SAY",
                    actionValue = "Good morning! Today, I checked your feed. The weather is sunny with a clear sky, and you have no urgent alarms."
                ),
                AutomationWorkflow(
                    title = "Zen Mode on Youtube Launch",
                    triggerType = "APP_LAUNCH",
                    triggerValue = "com.google.android.youtube",
                    actionType = "TOGGLE_WIFI",
                    actionValue = "ON"
                ),
                AutomationWorkflow(
                    title = "Camera Capture Assist",
                    triggerType = "HEADPHONES",
                    triggerValue = "connected",
                    actionType = "LAUNCH_APP",
                    actionValue = "com.android.camera"
                )
            )
            for (wf in defaults) {
                nexusDao.insertWorkflow(wf)
            }
        }

        if (currentMemory.isEmpty()) {
            val initialMemories = listOf(
                MemoryEntry(category = "preference", contentKey = "Default LLM Model", contentValue = "gemini-3.5-flash"),
                MemoryEntry(category = "note", contentKey = "Core Project Directives", contentValue = "NEXUS Core AI Operating System Assistant designed for premium automated device interaction."),
                MemoryEntry(category = "habit", contentKey = "Favorite Assistant Wake Word", contentValue = "Hey Nexus")
            )
            for (mem in initialMemories) {
                nexusDao.insertMemory(mem)
            }
        }
    }
}
