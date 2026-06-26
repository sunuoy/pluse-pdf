package com.example.ui.viewmodel

import android.app.Application
import com.example.BuildConfig
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiRepository
import com.example.data.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class PdfViewModel(
    application: Application,
    private val repository: PdfRepository,
    private val geminiRepository: GeminiRepository
) : AndroidViewModel(application) {

    // Database UI States
    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val highlights: StateFlow<List<HighlightEntity>> = repository.allHighlights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devices: StateFlow<List<DeviceEntity>> = repository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Selection States
    private val _selectedDocument = MutableStateFlow<DocumentEntity?>(null)
    val selectedDocument: StateFlow<DocumentEntity?> = _selectedDocument.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // Interactive Translation States
    private val _translationResult = MutableStateFlow("")
    val translationResult: StateFlow<String> = _translationResult.asStateFlow()

    private val _translationLoading = MutableStateFlow(false)
    val translationLoading: StateFlow<Boolean> = _translationLoading.asStateFlow()

    // Live OCR Scanner States
    private val _ocrText = MutableStateFlow("")
    val ocrText: StateFlow<String> = _ocrText.asStateFlow()

    private val _ocrLoading = MutableStateFlow(false)
    val ocrLoading: StateFlow<Boolean> = _ocrLoading.asStateFlow()

    // Area OCR Selector states
    private val _areaOcrResult = MutableStateFlow("")
    val areaOcrResult: StateFlow<String> = _areaOcrResult.asStateFlow()

    private val _areaOcrLoading = MutableStateFlow(false)
    val areaOcrLoading: StateFlow<Boolean> = _areaOcrLoading.asStateFlow()

    fun performAreaOcr(textPayload: String) {
        _areaOcrLoading.value = true
        _areaOcrResult.value = ""
        viewModelScope.launch {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                delay(1200)
                _areaOcrResult.value = textPayload
            } else {
                try {
                    val prompt = "Correct alignment and check syntax on these OCR extracted word nodes. Output only the reconstructed, polished paragraph:\n\n$textPayload"
                    val formatted = geminiRepository.translateText(prompt, "English")
                    if (formatted.isNotEmpty() && !formatted.startsWith("API Error")) {
                        _areaOcrResult.value = formatted
                    } else {
                        _areaOcrResult.value = textPayload
                    }
                } catch (e: Exception) {
                    _areaOcrResult.value = textPayload
                }
            }
            _areaOcrLoading.value = false
        }
    }

    fun clearAreaOcr() {
        _areaOcrResult.value = ""
    }

    // Cloud Sync States
    private val _syncingState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncingState: StateFlow<SyncState> = _syncingState.asStateFlow()

    // AI Chat Assistant States
    private val _aiChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val aiChatMessages: StateFlow<List<ChatMessage>> = _aiChatMessages.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    // Current selection text for highlights & translator popup
    var textSelectedRange = ""

    init {
        // Pre-populate database
        viewModelScope.launch {
            repository.prepopulateDatabaseIfEmpty()
        }
    }

    // --- Document Operations ---
    fun selectDocument(document: DocumentEntity) {
        _selectedDocument.value = document
        _currentPage.value = document.lastReadPage
        _aiChatMessages.value = listOf(
            ChatMessage(
                role = ChatRole.AI,
                text = "Welcome to the research assistant for **${document.title}**. Ask me any conceptual question or requests for deep analysis!"
            )
        )
    }

    fun closeDocument() {
        _selectedDocument.value = null
        textSelectedRange = ""
        _translationResult.value = ""
    }

    fun changePage(page: Int) {
        val doc = _selectedDocument.value ?: return
        if (page in 1..doc.totalPages) {
            _currentPage.value = page
            viewModelScope.launch {
                repository.updateDocument(doc.copy(lastReadPage = page, lastSyncTime = System.currentTimeMillis()))
            }
        }
    }

    // --- Highlights Operations ---
    fun addHighlight(colorHex: String, customNote: String = "") {
        val doc = _selectedDocument.value ?: return
        if (textSelectedRange.isEmpty()) return

        val highlight = HighlightEntity(
            documentId = doc.id,
            pageNumber = _currentPage.value,
            textHighlighted = textSelectedRange,
            colorHex = colorHex,
            note = customNote
        )

        viewModelScope.launch {
            repository.insertHighlight(highlight)
            // Mark document unsynced on change until synced
            repository.updateDocument(doc.copy(isSynced = false))
        }
    }

    fun removeHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            repository.deleteHighlight(highlight)
            _selectedDocument.value?.let { doc ->
                repository.updateDocument(doc.copy(isSynced = false))
            }
        }
    }

    // --- Translation ---
    fun translateSelectedText(targetLanguage: String) {
        if (textSelectedRange.isEmpty()) {
            _translationResult.value = "Please select any sentence to translate."
            return
        }

        viewModelScope.launch {
            _translationLoading.value = true
            _translationResult.value = ""
            val output = geminiRepository.translateText(textSelectedRange, targetLanguage)
            _translationResult.value = output
            _translationLoading.value = false
        }
    }

    // --- OCR Text Scanning ---
    fun performOcr(bitmap: Bitmap?, fallbackText: String) {
        _ocrLoading.value = true
        _ocrText.value = ""

        viewModelScope.launch {
            if (bitmap != null) {
                // Convert bitmap to Base64
                val base64 = bitmapToBase64(bitmap)
                val recognized = geminiRepository.performOcrOnImage(base64)
                if (recognized.isNotEmpty() && !recognized.startsWith("OCR API Error")) {
                    _ocrText.value = recognized
                } else {
                    // API key missing or failed, use mock/simulated OCR content
                    delay(1500) // Simulated scan delay
                    _ocrText.value = fallbackText
                }
            } else {
                delay(1500)
                _ocrText.value = fallbackText
            }
            _ocrLoading.value = false
        }
    }

    fun importScannedTextAsDoc(title: String, content: String) {
        viewModelScope.launch {
            val doc = DocumentEntity(
                title = title,
                description = "OCR Document recognized & imported to Cloud Sync on Mobile device.",
                sourcePath = "custom_ocr_${System.currentTimeMillis()}.pdf",
                fileSizeBytes = content.length.toLong() * 2,
                totalPages = 1,
                tags = "OCR Scan, Mobile Import",
                aiSummary = "This document was captured on mobile using OCR text recognition on ${System.currentTimeMillis()}.\n\nText extracted:\n$content",
                keyTakeaways = "1. Captured via mobile OCR scanner.\n2. Synced across connected devices."
            )
            val newId = repository.insertDocument(doc)
            // Auto select document to open
            val insertedDoc = doc.copy(id = newId)
            selectDocument(insertedDoc)
        }
    }

    // --- AI Chat Assistant ---
    fun sendChatMessage(userText: String) {
        if (userText.trim().isEmpty() || _chatLoading.value) return
        val doc = _selectedDocument.value ?: return

        val userMessage = ChatMessage(role = ChatRole.User, text = userText)
        _aiChatMessages.value = _aiChatMessages.value + userMessage
        _chatLoading.value = true

        viewModelScope.launch {
            val prompt = """
                You are playing the role of an AI research assistant. The user is currently reading the PDF document titled "${doc.title}".
                
                Document Summary context:
                ${doc.aiSummary}
                
                Document key facts:
                ${doc.keyTakeaways}
                
                User question:
                $userText
                
                Explain dynamically and professionally with detailed M3-standard Markdown lists, highlights and equations where applicable. Keep the language direct and helpful.
            """.trimIndent()

            val response = geminiRepository.generateSummary(prompt)
            val aiMessage = ChatMessage(role = ChatRole.AI, text = response)
            _aiChatMessages.value = _aiChatMessages.value + aiMessage
            _chatLoading.value = false
        }
    }

    // --- Cloud Sync Simulator ---
    fun syncCloudDocuments() {
        _syncingState.value = SyncState.Syncing

        viewModelScope.launch {
            delay(2000) // Beautiful expressive sync time

            // Update all non-synced doc statuses
            val currentDocs = repository.allDocuments.first()
            for (doc in currentDocs) {
                if (!doc.isSynced) {
                    repository.updateDocument(doc.copy(isSynced = true, lastSyncTime = System.currentTimeMillis()))
                }
            }

            // Update all devices sync times
            val currentDevices = repository.allDevices.first()
            for (dev in currentDevices) {
                repository.updateDevice(
                    dev.copy(
                        lastSyncTime = System.currentTimeMillis(),
                        status = "Connected"
                    )
                )
            }

            _syncingState.value = SyncState.Success
            delay(1500)
            _syncingState.value = SyncState.Idle
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

// Sealed structures
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
}

enum class ChatRole {
    User, AI
}

data class ChatMessage(
    val role: ChatRole,
    val text: String
)

class PdfViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PdfViewModel::class.java)) {
            val database = AppDatabase.getDatabase(application)
            val pdfRepository = PdfRepository(
                database.documentDao(),
                database.highlightDao(),
                database.deviceDao()
            )
            val geminiRepository = GeminiRepository()
            @Suppress("UNCHECKED_CAST")
            return PdfViewModel(application, pdfRepository, geminiRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
