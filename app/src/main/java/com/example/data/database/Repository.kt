package com.example.data.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PdfRepository(
    private val documentDao: DocumentDao,
    private val highlightDao: HighlightDao,
    private val deviceDao: DeviceDao
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val allHighlights: Flow<List<HighlightEntity>> = highlightDao.getAllHighlights()
    val allDevices: Flow<List<DeviceEntity>> = deviceDao.getAllDevices()

    fun getHighlightsForDocument(docId: Long): Flow<List<HighlightEntity>> {
        return highlightDao.getHighlightsByDocument(docId)
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun insertDocument(document: DocumentEntity): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document)
        highlightDao.deleteByDocumentId(document.id)
    }

    suspend fun insertHighlight(highlight: HighlightEntity): Long {
        return highlightDao.insertHighlight(highlight)
    }

    suspend fun deleteHighlight(highlight: HighlightEntity) {
        highlightDao.deleteHighlight(highlight)
    }

    suspend fun insertDevice(device: DeviceEntity): Long {
        return deviceDao.insertDevice(device)
    }

    suspend fun updateDevice(device: DeviceEntity) {
        deviceDao.updateDevice(device)
    }

    suspend fun prepopulateDatabaseIfEmpty() {
        val currentDocs = documentDao.getAllDocuments().first()
        if (currentDocs.isEmpty()) {
            // Populate sample documents representing rich educational texts & research papers
            val sampleDocs = listOf(
                DocumentEntity(
                    title = "Attention Is All You Need (Transformer Research)",
                    description = "The landmark paper introducing the Transformer neural network architecture, utilizing self-attention mechanisms to revolutionize sequence transduction tasks.",
                    sourcePath = "attention_all_you_need.pdf",
                    fileSizeBytes = 2210432,
                    totalPages = 15,
                    tags = "Deep Learning, AI, NLP",
                    aiSummary = "This breakthrough research proposes a novel sequence-to-sequence neural network model called the Transformer. Unlike traditional architectures (RNNs, LSTMs, and GRUs) that process text sequentially, the Transformer relies entirely on attention mechanisms to model global dependencies. This allows for dramatically more parallelization during training, resulting in state-of-the-art translation quality achieved in a fraction of the training time.",
                    keyTakeaways = "1. Replaces Recurrent Neural Networks with Self-Attention blocks to establish parallelized training.\n2. Introduces Multi-Head Attention, enabling the model to jointly attend to information from different representation subspaces.\n3. Sets a new record for translation metrics (BLEU) on English-to-German and English-to-French translation challenges.\n4. Establishes the foundations for modern generative LLMs, including GPT and Gemini."
                ),
                DocumentEntity(
                    title = "Decentralized Finance (DeFi) & Market Liquidity",
                    description = "A deep analytical paper examining automated market makers (AMMs), decentralized exchanges, yield farm mechanics, and multi-chain liquidity parameters.",
                    sourcePath = "defi_liquidity_report.pdf",
                    fileSizeBytes = 1845920,
                    totalPages = 10,
                    tags = "Finance, Blockchain",
                    aiSummary = "An empirical research analysis on how Decentralized Finance systems have redesigned global market liquidity. It focuses on the shift from centralized limit order books to automated constant-product market maker pools, explaining how algorithmic liquidity depth mitigates trading slippage but exposes liquidity providers to impermanent loss.",
                    keyTakeaways = "1. Traditional order books are replaced by AMMs using the constant-product formula (x * y = k).\n2. Liquidity Providers earn fees but are exposed to risk parameters known as Impermanent Loss.\n3. Decentralized protocols allow permissionless lending, borrowing, and high-velocity arbitrage trading across isolated networks.\n4. Cross-chain bridges introduce significant cybersecurity and systemic structural risks."
                ),
                DocumentEntity(
                    title = "Global Climate Solutions & Carbon Capture Tech",
                    description = "A scientific study on Direct Air Capture (DAC) systems, marine carbon sequestration, policy frameworks, and carbon credit economic viability studies.",
                    sourcePath = "climate_carbon_capture.pdf",
                    fileSizeBytes = 3402324,
                    totalPages = 22,
                    tags = "Ecology, Science",
                    aiSummary = "An overview of active negative-emission technologies designed to help achieve net-zero targets. It contrasts mechanical Direct Air Capture (DAC) with nature-based biological sequestration pathways, analyzing the energy budgets, financial costs per ton, and geopolitical policies required to render atmospheric capture scalable.",
                    keyTakeaways = "1. Direct Air Capture is currently energy-intensive, and requires up to 2.5 MWh per ton of carbon captured.\n2. Permanent sequestration under deep basalt geological structures guarantees long-term storage safety for centuries.\n3. Policy frameworks like taxation and high-quality carbon credits are required to motivate private capital investments.\n4. Scalability constraints suggest DAC must be combined with global emission reduction efforts, not act as a substitute."
                )
            )

            for (doc in sampleDocs) {
                documentDao.insertDocument(doc)
            }
        }

        val currentDevices = deviceDao.getAllDevices().first()
        if (currentDevices.isEmpty()) {
            val sampleDevices = listOf(
                DeviceEntity(name = "MacBook Pro Desktop", deviceType = "Desktop Editor", status = "Connected", isMainDevice = false),
                DeviceEntity(name = "Pixel Tablet Dashboard", deviceType = "Tablet Viewer", status = "Connected", isMainDevice = false),
                DeviceEntity(name = "Pixel 9 Pro Fold (This Device)", deviceType = "Mobile Slate", status = "Syncing", isMainDevice = true),
                DeviceEntity(name = "Web Editor Console", deviceType = "Web Reader", status = "Offline", isMainDevice = false)
            )
            for (dev in sampleDevices) {
                deviceDao.insertDevice(dev)
            }
        }
    }
}
