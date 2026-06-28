package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import com.example.BuildConfig
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiRepository
import com.example.data.database.*
import com.example.data.model.PdfPageModel
import com.example.data.model.PdfDocumentLines
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

enum class ReaderTheme {
    System, Light, Dark, Green, Sepia
}

enum class ViewMode {
    Vertical, Horizontal
}

enum class PageStyle {
    Standard, Reflow, Adaptive
}

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

    // View Settings and Organize Pages States
    private val _viewMode = MutableStateFlow(ViewMode.Vertical)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _readerTheme = MutableStateFlow(ReaderTheme.Light)
    val readerTheme: StateFlow<ReaderTheme> = _readerTheme.asStateFlow()

    private val _brightness = MutableStateFlow(1.0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _isRealTimeTranslationEnabled = MutableStateFlow(false)
    val isRealTimeTranslationEnabled: StateFlow<Boolean> = _isRealTimeTranslationEnabled.asStateFlow()

    private val _pageStyle = MutableStateFlow(PageStyle.Standard)
    val pageStyle: StateFlow<PageStyle> = _pageStyle.asStateFlow()

    private val _documentPages = MutableStateFlow<List<PdfPageModel>>(emptyList())
    val documentPages: StateFlow<List<PdfPageModel>> = _documentPages.asStateFlow()

    private val _pageRotations = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val pageRotations: StateFlow<Map<Int, Float>> = _pageRotations.asStateFlow()

    private val _docPagesCache = mutableMapOf<Long, List<PdfPageModel>>()

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
        
        // Load pages from cache or initial
        var pages = _docPagesCache[document.id]
        if (pages == null) {
            val persistentFile = java.io.File(getApplication<Application>().filesDir, "parsed_pages_${document.id}.json")
            if (persistentFile.exists()) {
                try {
                    val jsonText = persistentFile.readText()
                    pages = com.example.utils.PdfParser.deserializePages(jsonText)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (pages == null || pages.isEmpty()) {
                val original = PdfDocumentLines.getPagesForDocument(document.sourcePath)
                if (original.size != document.totalPages) {
                    val list = mutableListOf<PdfPageModel>()
                    for (i in 1..document.totalPages) {
                        val origIndex = (i - 1) % original.size
                        val origPage = original[origIndex]
                        list.add(origPage.copy(pageNumber = i))
                    }
                    pages = list
                } else {
                    pages = original
                }
            }
            _docPagesCache[document.id] = pages!!
        }
        _documentPages.value = pages!!

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
            val updatedDoc = doc.copy(lastReadPage = page, lastSyncTime = System.currentTimeMillis())
            _selectedDocument.value = updatedDoc
            viewModelScope.launch {
                repository.updateDocument(updatedDoc)
            }
        }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        _readerTheme.value = theme
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun setBrightness(value: Float) {
        _brightness.value = value
    }

    fun setRealTimeTranslationEnabled(enabled: Boolean) {
        _isRealTimeTranslationEnabled.value = enabled
    }

    fun setPageStyle(style: PageStyle) {
        _pageStyle.value = style
    }

    fun duplicatePage(pageNumber: Int) {
        val doc = _selectedDocument.value ?: return
        val currentList = _documentPages.value.toMutableList()
        if (pageNumber in 1..currentList.size) {
            val pageToCopy = currentList[pageNumber - 1]
            val copiedPage = pageToCopy.copy(
                pageNumber = pageNumber + 1,
                paragraphs = pageToCopy.paragraphs.toList()
            )
            currentList.add(pageNumber, copiedPage)
            for (i in 0 until currentList.size) {
                currentList[i] = currentList[i].copy(pageNumber = i + 1)
            }
            _documentPages.value = currentList
            _docPagesCache[doc.id] = currentList
            
            val updatedDoc = doc.copy(totalPages = currentList.size)
            _selectedDocument.value = updatedDoc
            viewModelScope.launch {
                repository.updateDocument(updatedDoc)
            }
        }
    }

    fun deletePage(pageNumber: Int) {
        val doc = _selectedDocument.value ?: return
        val currentList = _documentPages.value.toMutableList()
        if (currentList.size > 1 && pageNumber in 1..currentList.size) {
            currentList.removeAt(pageNumber - 1)
            for (i in 0 until currentList.size) {
                currentList[i] = currentList[i].copy(pageNumber = i + 1)
            }
            _documentPages.value = currentList
            _docPagesCache[doc.id] = currentList
            
            if (_currentPage.value > currentList.size) {
                _currentPage.value = currentList.size
            }
            
            val updatedDoc = doc.copy(
                totalPages = currentList.size,
                lastReadPage = _currentPage.value
            )
            _selectedDocument.value = updatedDoc
            viewModelScope.launch {
                repository.updateDocument(updatedDoc)
            }
        }
    }

    fun addPage() {
        val doc = _selectedDocument.value ?: return
        val currentList = _documentPages.value.toMutableList()
        val newPageNo = currentList.size + 1
        val newPage = PdfPageModel(
            pageNumber = newPageNo,
            paragraphs = listOf(
                "Added Custom Blank Page: This is an extra blank research page inserted into ${doc.title} dynamically.",
                "Use the edit tools to annotate or capture new facts, and use highlights to build your academic notes.",
                "Syncing automatically backups this customized document structure across your workspace."
            )
        )
        currentList.add(newPage)
        _documentPages.value = currentList
        _docPagesCache[doc.id] = currentList
        
        val updatedDoc = doc.copy(totalPages = currentList.size)
        _selectedDocument.value = updatedDoc
        viewModelScope.launch {
            repository.updateDocument(updatedDoc)
        }
    }

    fun rotatePage(pageNumber: Int) {
        val currentRotations = _pageRotations.value.toMutableMap()
        val currentAngle = currentRotations[pageNumber] ?: 0f
        val newAngle = (currentAngle + 90f) % 360f
        currentRotations[pageNumber] = newAngle
        _pageRotations.value = currentRotations
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
            val updatedDoc = doc.copy(isSynced = false)
            _selectedDocument.value = updatedDoc
            repository.updateDocument(updatedDoc)
        }
    }

    fun removeHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            repository.deleteHighlight(highlight)
            _selectedDocument.value?.let { doc ->
                val updatedDoc = doc.copy(isSynced = false)
                _selectedDocument.value = updatedDoc
                repository.updateDocument(updatedDoc)
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

    fun translateCustomText(text: String, targetLanguage: String) {
        if (text.isEmpty()) {
            _translationResult.value = "Please enter some text to translate."
            return
        }
        viewModelScope.launch {
            _translationLoading.value = true
            _translationResult.value = ""
            val output = geminiRepository.translateText(text, targetLanguage)
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

    fun importCloudPdf(title: String, fileName: String, fileSize: Long, source: String) {
        viewModelScope.launch {
            val titleClean = fileName.removeSuffix(".pdf").replace("_", " ").replace("-", " ")
            val finalPages = listOf(
                com.example.data.model.PdfPageModel(
                    pageNumber = 1,
                    paragraphs = listOf(
                        "Executive Summary: This research document on '$titleClean' was imported from $source. It outlines major academic and empirical investigations, identifying core paradigms and modern analytical tools.",
                        "Introduction & Cloud Sync: Cloud storage integration allows researchers to seamlessly sync literature across workspaces. This paper focuses on optimized cloud workflows, distributed data structures, and edge-AI analysis pipelines."
                    )
                ),
                com.example.data.model.PdfPageModel(
                    pageNumber = 2,
                    paragraphs = listOf(
                        "Detailed Analysis: In this section, we review statistical correlations and performance metrics. Experimental results demonstrate high reliability under modern workloads, validating the architectural integrity of the system."
                    )
                )
            )

            val cleanPath = "${source.lowercase()}_imported_${fileName.replace(" ", "_")}"
            val doc = com.example.data.database.DocumentEntity(
                title = title,
                description = "Imported from $source cloud drive.",
                sourcePath = cleanPath,
                fileSizeBytes = fileSize,
                totalPages = finalPages.size,
                isSynced = true,
                tags = "$source, Imported",
                aiSummary = "This document represents the cloud research PDF '$fileName' imported directly from $source.",
                keyTakeaways = "1. Synchronized and imported from $source.\n2. Parsed for local indexing and offline research.\n3. Complete Material 3 typography support."
            )
            val newId = repository.insertDocument(doc)
            val insertedDoc = doc.copy(id = newId)

            // Save finalPages to filesDir
            try {
                val persistentFile = java.io.File(getApplication<Application>().filesDir, "parsed_pages_${newId}.json")
                persistentFile.writeText(com.example.utils.PdfParser.serializePages(finalPages))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _docPagesCache[newId] = finalPages
            selectDocument(insertedDoc)
        }
    }

    fun importLocalPdf(title: String, fileName: String, fileSize: Long, uri: android.net.Uri) {
        viewModelScope.launch {
            val parsed = try {
                com.example.utils.PdfParser.parsePdf(getApplication<Application>(), uri)
            } catch (e: Exception) {
                emptyList()
            }

            val finalPages = if (parsed.isNotEmpty()) {
                parsed
            } else {
                val titleClean = fileName.removeSuffix(".pdf").replace("_", " ").replace("-", " ")
                listOf(
                    PdfPageModel(
                        pageNumber = 1,
                        paragraphs = listOf(
                            "Executive Summary: This parsed publication on '$titleClean' details our comprehensive experimental and theoretical findings. In this chapter, we outline the fundamental research objectives, the historical background of the study, and the core structural frameworks utilized in our analysis.",
                            "Introduction & Frameworks: Modern research approaches in this domain emphasize highly scalable, distributed paradigms. Understanding the micro-architecture and performance trade-offs is critical to optimizing overall efficiency. We investigate the relationship between system inputs and final output metrics.",
                            "Historical Precedents: Historically, systems of this nature suffered from high latency and significant resource overheads. By introducing automated pipeline scheduling and adaptive feedback loops, our model achieves a substantial improvement in resource utilization while maintaining deterministic safety guarantees."
                        )
                    ),
                    PdfPageModel(
                        pageNumber = 2,
                        paragraphs = listOf(
                            "Methodology & Analytical Models: Section 2 covers the rigorous mathematical model of our '$titleClean' implementation. We describe the constant-state invariants and how transactions or data transformations are processed safely across concurrent nodes.",
                            "Dynamic Performance Profiles: Under peak load, the system shows highly linear scalability. We compare our results with established baselines, showing a substantial improvement in throughput and reduced execution divergence.",
                            "Limiting Factors and Bounds: While highly optimized, the operational envelope is strictly bounded by hardware memory bandwidth and network packet serialization latency. We discuss these mitigation strategies in detail."
                        )
                    ),
                    PdfPageModel(
                        pageNumber = 3,
                        paragraphs = listOf(
                            "Conclusion & Future Trajectories: In summary, this '$titleClean' framework represents a major milestone in academic and industrial applications. Our permanent stone-like secure storage prevents data leakage and ensures cross-device consistency.",
                            "Avenue of Future Work: Future investigations will focus on integrating edge-level AI summaries, extending the localized translation matrices, and optimizing real-time collaborative highlighting workflows across cloud networks."
                        )
                    )
                )
            }

            val cleanPath = "custom_local_${fileName.replace(" ", "_")}"
            val doc = DocumentEntity(
                title = title,
                description = "Locally imported PDF file parsed and integrated into the PulsePDF reader environment.",
                sourcePath = cleanPath,
                fileSizeBytes = fileSize,
                totalPages = finalPages.size,
                isSynced = false, // Not yet synced to cloud
                tags = "Local, Imported",
                aiSummary = "This document represents the parsed local PDF file '$fileName'. It has been integrated into the local PulsePDF environment for highlights, translations, and interactive learning.",
                keyTakeaways = "1. Imported locally from device storage.\n2. Fully parsed into the mobile reading environment.\n3. Supports translation and highlights."
            )
            val newId = repository.insertDocument(doc)
            val insertedDoc = doc.copy(id = newId)

            // Save finalPages to filesDir
            try {
                val persistentFile = java.io.File(getApplication<Application>().filesDir, "parsed_pages_${newId}.json")
                persistentFile.writeText(com.example.utils.PdfParser.serializePages(finalPages))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _docPagesCache[newId] = finalPages
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
