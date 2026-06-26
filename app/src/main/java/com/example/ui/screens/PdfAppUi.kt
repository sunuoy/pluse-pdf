package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.DocumentEntity
import com.example.data.database.HighlightEntity
import com.example.data.model.PdfDocumentLines
import com.example.ui.viewmodel.ChatRole
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.SyncState
import kotlinx.coroutines.launch

enum class ScreenType {
    Dashboard, Reader, OCRScanner, SyncConsole
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulsePdfApp(viewModel: PdfViewModel) {
    val selectedDoc by viewModel.selectedDocument.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf(ScreenType.Dashboard) }

    // Auto-navigate to Reader when a document is selected
    LaunchedEffect(selectedDoc) {
        if (selectedDoc != null) {
            currentScreen = ScreenType.Reader
        } else if (currentScreen == ScreenType.Reader) {
            currentScreen = ScreenType.Dashboard
        }
    }

    Scaffold(
        bottomBar = {
            if (selectedDoc == null) {
                NavigationBar(
                    containerColor = Color(0xFFF3EDF7),
                    contentColor = Color(0xFF1D1B20),
                    modifier = Modifier.testTag("app_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentScreen == ScreenType.Dashboard,
                        onClick = { currentScreen = ScreenType.Dashboard },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Dashboard") },
                        label = { Text("Library") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1D192B),
                            selectedTextColor = Color(0xFF1D192B),
                            unselectedIconColor = Color(0xFF49454F),
                            unselectedTextColor = Color(0xFF49454F),
                            indicatorColor = Color(0xFFE8DEF8)
                        ),
                        modifier = Modifier.testTag("nav_library_tab")
                    )
                    NavigationBarItem(
                        selected = currentScreen == ScreenType.OCRScanner,
                        onClick = { currentScreen = ScreenType.OCRScanner },
                        icon = { Icon(Icons.Default.Camera, contentDescription = "OCR Scanner") },
                        label = { Text("OCR Scanner") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1D192B),
                            selectedTextColor = Color(0xFF1D192B),
                            unselectedIconColor = Color(0xFF49454F),
                            unselectedTextColor = Color(0xFF49454F),
                            indicatorColor = Color(0xFFE8DEF8)
                        ),
                        modifier = Modifier.testTag("nav_ocr_tab")
                    )
                    NavigationBarItem(
                        selected = currentScreen == ScreenType.SyncConsole,
                        onClick = { currentScreen = ScreenType.SyncConsole },
                        icon = { Icon(Icons.Default.Sync, contentDescription = "Cloud Sync") },
                        label = { Text("Cloud Sync") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1D192B),
                            selectedTextColor = Color(0xFF1D192B),
                            unselectedIconColor = Color(0xFF49454F),
                            unselectedTextColor = Color(0xFF49454F),
                            indicatorColor = Color(0xFFE8DEF8)
                        ),
                        modifier = Modifier.testTag("nav_sync_tab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFEF7FF))
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        )
                    ) { it } togetherWith slideOutVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { -it }
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    ScreenType.Dashboard -> DashboardScreen(
                        viewModel = viewModel,
                        onOpenOcr = { currentScreen = ScreenType.OCRScanner },
                        onOpenSync = { currentScreen = ScreenType.SyncConsole }
                    )
                    ScreenType.Reader -> ReaderScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.closeDocument()
                            currentScreen = ScreenType.Dashboard
                        }
                    )
                    ScreenType.OCRScanner -> OcrScannerScreen(viewModel = viewModel)
                    ScreenType.SyncConsole -> SyncConsoleScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: PdfViewModel,
    onOpenOcr: () -> Unit,
    onOpenSync: () -> Unit
) {
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val syncState by viewModel.syncingState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // App Custom Brand Intro with Bold Editorial Typography
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "PULSE PDF",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    color = Color(0xFF1D1B20)
                )
                Text(
                    text = "Research Engine & Cloud Sync",
                    fontSize = 14.sp,
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF3EDF7))
                    .clickable { onOpenSync() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Cloud Status",
                    tint = if (syncState == SyncState.Syncing) Color(0xFF6750A4) else Color(0xFF1D1B20)
                )
            }
        }

        // Feature shortcuts in dynamic bouncing shapes!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick OCR Card - Live OCR theme color EADDFF / 21005D
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEADDFF))
                    .clickable { onOpenOcr() }
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Scan icon",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("OCR Cam Scanner", color = Color(0xFF21005D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Extract paper text", color = Color(0xFF49454F), fontSize = 11.sp)
                    }
                }
            }

            // Cloud Sync Console Status - Secondary border card styling
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Color(0xFFCAC4D0)), RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFFFFF))
                    .clickable { onOpenSync() }
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Sync Console",
                        tint = Color(0xFF49454F),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Cloud Sync", color = Color(0xFF1D1B20), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("4 Devices linked", color = Color(0xFF49454F), fontSize = 11.sp)
                    }
                }
            }
        }

        // Section Title
        Text(
            text = "Your Workspace Documents",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B20),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (docs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF3EDF7)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Empty", tint = Color(0xFF49454F), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No documents available. Pulling sample reports...", color = Color(0xFF49454F))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                docs.forEach { doc ->
                    DocumentCardItem(document = doc, onSelect = { viewModel.selectDocument(doc) })
                }
            }
        }
    }
}

@Composable
fun DocumentCardItem(
    document: DocumentEntity,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doc_item_${document.id}")
            .clickable { onSelect() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, if (!document.isSynced) Color(0xFFEF4444) else Color(0xFFCAC4D0))
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // PDF Tag badge - styled dynamically to match Material 3 Expressive shapes & badges
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEADDFF))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("PDF DOCUMENT", color = Color(0xFF21005D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Sync status icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (document.isSynced) Icons.Default.CheckCircle else Icons.Default.SyncProblem,
                        contentDescription = "Sync state",
                        tint = if (document.isSynced) Color(0xFF0F5132) else Color(0xFF842029),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (document.isSynced) "Synced" else "Changes Pending",
                        fontSize = 11.sp,
                        color = if (document.isSynced) Color(0xFF0F5132) else Color(0xFF842029)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Editorial Heading
            Text(
                text = document.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1D1B20),
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = document.description,
                fontSize = 12.sp,
                color = Color(0xFF49454F),
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(color = Color(0xFFCAC4D0), thickness = 1.dp)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Topic chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    document.tags.split(",").take(2).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color(0xFFF3EDF7))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(tag.trim(), color = Color(0xFF49454F), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Page weight info
                Text(
                    text = "Page ${document.lastReadPage} of ${document.totalPages}",
                    color = Color(0xFF49454F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Custom Interactive PDF Reader Screen
@Composable
fun ReaderScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit
) {
    val document by viewModel.selectedDocument.collectAsStateWithLifecycle()
    val activePage by viewModel.currentPage.collectAsStateWithLifecycle()
    val allHighlights by viewModel.highlights.collectAsStateWithLifecycle()

    val currentDoc = document ?: return
    val pages = remember(currentDoc) { PdfDocumentLines.getPagesForDocument(currentDoc.sourcePath) }
    val pageContent = pages.find { it.pageNumber == activePage } ?: pages.first()

    val pageHighlights = remember(allHighlights, activePage) {
        allHighlights.filter { it.documentId == currentDoc.id && it.pageNumber == activePage }
    }

    var selectedSentenceText by remember { mutableStateOf("") }
    var showTranslatorPane by remember { mutableStateOf(false) }
    var showResearchDrawer by remember { mutableStateOf(false) }
    var isAreaOcrModeActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3EDF7))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("reader_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1D1B20))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = currentDoc.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Page $activePage of ${currentDoc.totalPages}",
                    fontSize = 11.sp,
                    color = Color(0xFF6750A4)
                )
            }

            // Area OCR Select Tool Toggle (Crop Icon)
            IconButton(
                onClick = { isAreaOcrModeActive = !isAreaOcrModeActive },
                modifier = Modifier.testTag("area_ocr_crop_toggle")
            ) {
                Icon(
                    imageVector = if (isAreaOcrModeActive) Icons.Default.Close else Icons.Default.Crop,
                    contentDescription = "Area OCR Selector",
                    tint = if (isAreaOcrModeActive) Color(0xFF842029) else Color(0xFF6750A4)
                )
            }

            // Sync Badge and Research Engine Drawer Toggle
            IconButton(onClick = { showResearchDrawer = !showResearchDrawer }) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Research Engine",
                    tint = Color(0xFF6750A4)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isAreaOcrModeActive) {
                AreaOcrSelectorView(
                    viewModel = viewModel,
                    pageContent = pageContent,
                    onClose = { isAreaOcrModeActive = false }
                )
            } else {
                // Main Rendered PDF Page View
                Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                // PDF Sheet replica style card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Header metadata replica
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PULSE DIGITAL READER v2.4",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "PAGE 0$activePage",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = Color.LightGray, modifier = Modifier.padding(bottom = 18.dp))

                        // Rendered paragraphs containing text lines
                        pageContent.paragraphs.forEachIndexed { index, paragraphText ->
                            val isSelected = selectedSentenceText == paragraphText
                            // Check if this paragraph is already highlighted in database
                            val itemHighlight = pageHighlights.find { it.textHighlighted == paragraphText }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            itemHighlight != null -> Color(android.graphics.Color.parseColor(itemHighlight.colorHex)).copy(alpha = 0.35f)
                                            isSelected -> Color(0xFFEADDFF)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable {
                                        selectedSentenceText = if (isSelected) "" else paragraphText
                                        viewModel.textSelectedRange = selectedSentenceText
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp)
                            ) {
                                Text(
                                    text = paragraphText,
                                    color = Color(0xFF1E293B),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    fontFamily = FontFamily.Serif
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        HorizontalDivider(color = Color.LightGray, modifier = Modifier.padding(bottom = 12.dp))

                        // Footer replica
                        Text(
                            text = "Proprietary Pulsar Academic Sync, 2026. All highlights encrypted locally.",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))
            }

            // Highlighting and Real-time Translator FLOATING ACTIONS Menu
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedSentenceText.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                HighlightTranslatorToolbar(
                    viewModel = viewModel,
                    highlightExists = pageHighlights.any { it.textHighlighted == selectedSentenceText },
                    onHighlightColorSelected = { colorHex ->
                        viewModel.addHighlight(colorHex)
                        selectedSentenceText = ""
                    },
                    onRemoveHighlight = {
                        val hl = pageHighlights.find { it.textHighlighted == selectedSentenceText }
                        if (hl != null) viewModel.removeHighlight(hl)
                        selectedSentenceText = ""
                    },
                    onTriggerTranslate = {
                        showTranslatorPane = true
                    }
                )
            }

            // Real-Time Translator Shelf
            if (showTranslatorPane) {
                TranslatorSheet(
                    viewModel = viewModel,
                    onDismiss = { showTranslatorPane = false }
                )
            }

            // AI summary drawer panel
            if (showResearchDrawer) {
                ResearchEnginePanel(
                    viewModel = viewModel,
                    doc = currentDoc,
                    allDocHighlights = allHighlights.filter { it.documentId == currentDoc.id },
                    onDismiss = { showResearchDrawer = false }
                )
            }
            }
        }

        // Bottom Reader Navigation Bar (Next/Prev buttons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(vertical = 12.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { viewModel.changePage(activePage - 1) },
                enabled = activePage > 1,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E5FF))
            ) {
                Row {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev")
                    Text("PREVIOUS")
                }
            }

            Text(
                "PAGE $activePage of ${currentDoc.totalPages}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            TextButton(
                onClick = { viewModel.changePage(activePage + 1) },
                enabled = activePage < currentDoc.totalPages,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E5FF))
            ) {
                Row {
                    Text("NEXT")
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        }
    }
}

// Toolbar popup with bouncier Material shapes
@Composable
fun HighlightTranslatorToolbar(
    viewModel: PdfViewModel,
    highlightExists: Boolean,
    onHighlightColorSelected: (String) -> Unit,
    onRemoveHighlight: () -> Unit,
    onTriggerTranslate: () -> Unit
) {
    val highlightColors = listOf(
        "#FFEB3B", // Neon Yellow
        "#00E5FF", // Cyan Light
        "#10B981", // Emerald Green
        "#EC4899"  // Hot Pink
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
        elevation = CardDefaults.cardElevation(8.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!highlightExists) {
                // Color Selectors
                highlightColors.forEach { color ->
                    val javaColor = android.graphics.Color.parseColor(color)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(javaColor))
                            .border(1.5.dp, Color.White, CircleShape)
                            .clickable { onHighlightColorSelected(color) }
                    )
                }
            } else {
                IconButton(onClick = onRemoveHighlight) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Highlight", tint = Color(0xFFEF4444))
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Action dividers
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color(0xFFCAC4D0))
            )

            // Real-Time Translate Action Button - Professional Purple background (0xFF6750A4)
            Button(
                onClick = onTriggerTranslate,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Translate, contentDescription = "Translate", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Translate", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Bouncier real-time Translator Drawer sheet
@Composable
fun TranslatorSheet(
    viewModel: PdfViewModel,
    onDismiss: () -> Unit
) {
    val languages = listOf("Spanish", "French", "Japanese", "German", "Hindi", "Arabic")
    var selectedLanguage by remember { mutableStateOf("Spanish") }

    val translation by viewModel.translationResult.collectAsStateWithLifecycle()
    val loading by viewModel.translationLoading.collectAsStateWithLifecycle()

    // Trigger initial translation
    LaunchedEffect(selectedLanguage) {
        viewModel.translateSelectedText(selectedLanguage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(enabled = false) {}
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFCAC4D0))
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "On-Screen Omni Translator",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1D1B20)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1D1B20))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selector chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languages.forEach { lang ->
                        val isSelected = selectedLanguage == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFF6750A4) else Color.White)
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF6750A4) else Color(0xFFCAC4D0)
                                    ),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedLanguage = lang }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = lang,
                                color = if (isSelected) Color.White else Color(0xFF49454F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Original Text Reference
                Text("SELECTED SOURCE TEXT:", color = Color(0xFF49454F), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = viewModel.textSelectedRange,
                    color = Color(0xFF1D1B20),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(color = Color(0xFFCAC4D0))

                Spacer(modifier = Modifier.height(14.dp))

                // Translated Output result
                Text("TRANSLATED OUTPUT ($selectedLanguage):", color = Color(0xFF6750A4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6750A4), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Translating onscreen characters...", color = Color(0xFF49454F), fontSize = 12.sp)
                    }
                } else {
                    Text(
                        text = translation,
                        color = Color(0xFF1D1B20),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color(0xFFCAC4D0)), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// Right panel drawer representing scientific summaries & interactive context chats
@Composable
fun ResearchEnginePanel(
    viewModel: PdfViewModel,
    doc: DocumentEntity,
    allDocHighlights: List<HighlightEntity>,
    onDismiss: () -> Unit
) {
    var chatInputText by remember { mutableStateOf("") }
    val chatMessages by viewModel.aiChatMessages.collectAsStateWithLifecycle()
    val chatLoading by viewModel.chatLoading.collectAsStateWithLifecycle()

    var activeTabIsSummary by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.85f)
                .align(Alignment.CenterEnd)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Research Co-Pilot", color = Color(0xFF1D1B20), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1D1B20))
                    }
                }

                // Selector Tabs with dynamic shape - styled with active EADDFF/6750A4 & inactive White/CAC4D0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEADDFF))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeTabIsSummary = true }
                            .background(if (activeTabIsSummary) Color(0xFF6750A4) else Color.Transparent)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Summary & Facts", color = if (activeTabIsSummary) Color.White else Color(0xFF21005D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeTabIsSummary = false }
                            .background(if (!activeTabIsSummary) Color(0xFF6750A4) else Color.Transparent)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ask Document", color = if (!activeTabIsSummary) Color.White else Color(0xFF21005D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (activeTabIsSummary) {
                    // Summary and highlights list
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("AI POWERED RESEARCH SUMMARY", color = Color(0xFF6750A4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = doc.aiSummary.ifEmpty { "Generating AI abstract on demand..." },
                            color = Color(0xFF1D1B20),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text("CRITICAL DISCOVERY POINTS", color = Color(0xFF6750A4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = doc.keyTakeaways.ifEmpty { "1. Parsing academic facts...\n2. Extracting formulas." },
                            color = Color(0xFF49454F),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text("LOCAL RESEARCH HIGHLIGHTS (${allDocHighlights.size})", color = Color(0xFF6750A4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        if (allDocHighlights.isEmpty()) {
                            Text("No highlights captured yet. Tap text in the document viewer to highlight your research.", color = Color(0xFF49454F), fontSize = 12.sp)
                        } else {
                            allDocHighlights.forEach { highlight ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "\"${highlight.textHighlighted}\"",
                                            color = Color(0xFF1D1B20),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Page ${highlight.pageNumber} • Highlighted with color ${highlight.colorHex}",
                                            color = Color(0xFF49454F),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // AI Interactive Chat inside PDF context
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(chatMessages) { msg ->
                                val isUser = msg.role == ChatRole.User
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isUser) Color(0xFF6750A4) else Color.White)
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (isUser) Color(0xFF6750A4) else Color(0xFFCAC4D0)
                                                ),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(12.dp)
                                            .fillMaxWidth(0.85f)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = if (isUser) Color.White else Color(0xFF1D1B20),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }

                            if (chatLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .border(BorderStroke(1.dp, Color(0xFFCAC4D0)), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                            .fillMaxWidth(0.5f)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF6750A4))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Thinking...", color = Color(0xFF49454F), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Chat textfield input with light mode styles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInputText,
                                onValueChange = { chatInputText = it },
                                placeholder = { Text("Ask anything on doc...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1D1B20),
                                    unfocusedTextColor = Color(0xFF49454F),
                                    focusedBorderColor = Color(0xFF6750A4),
                                    unfocusedBorderColor = Color(0xFFCAC4D0),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_input_field"),
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (chatInputText.isNotEmpty()) {
                                        viewModel.sendChatMessage(chatInputText)
                                        chatInputText = ""
                                    }
                                },
                                modifier = Modifier.testTag("chat_send_button")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF6750A4))
                            }
                        }
                    }
                }
            }
        }
    }
}

// OCR Scan Tool Screen with Live Scanner replica & Dynamic text recognizer
@Composable
fun OcrScannerScreen(viewModel: PdfViewModel) {
    val ocrText by viewModel.ocrText.collectAsStateWithLifecycle()
    val loading by viewModel.ocrLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasRequestedScan by remember { mutableStateOf(false) }
    var inputDocName by remember { mutableStateOf("Scanned Academic Summary") }

    // Academic Paper content for beautiful, high-fidelity mock recognition fallback!
    val mockDacTextResult = """
        TECHNICAL MEMORANDUM ON ATMOSPHERIC DIRECT AIR CAPTURE (DAC)
        Section 4.1 Thermodynamic Sorbent Desorption Cycles
        
        Using solid amine-functionalized chemical contactors, CO2 captures ambient humidity and isolates atmospheric CO2 coordinates through a selective chemical chemisorption process. Desorption occurs under steam validation temperature blocks of approximately 100°C.
        
        Key Calculations & Efficiency metrics:
        - Absolute heat coefficient: Q = 1.42 GJ per ton CO2 captured.
        - Power-to-carbon sequestration efficiency: η_c = 78.4%.
        - Desorption kinetic limits follow the Arrhenius thermodynamic curve: k = A * exp(-Ea / RT), facilitating high purity collection.
    """.trimIndent()

    var customCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPermissionDialog by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                "Mobile OCR character Recognition",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1D1B20)
            )
            Text(
                "Snap real academic articles to read, edit & sync to cloud instantly.",
                fontSize = 13.sp,
                color = Color(0xFF49454F),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Live Camera viewfinder simulation framework
            if (!hasRequestedScan) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFF3EDF7))
                        .border(2.dp, Color(0xFFCAC4D0), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Viewfinder corners - Custom Professional Purple style
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val thickness = 4.dp.toPx()
                        val length = 32.dp.toPx()
                        val color = Color(0xFF6750A4)

                        // Top-left
                        drawLine(color, Offset(24.dp.toPx(), 24.dp.toPx()), Offset(24.dp.toPx() + length, 24.dp.toPx()), thickness)
                        drawLine(color, Offset(24.dp.toPx(), 24.dp.toPx()), Offset(24.dp.toPx(), 24.dp.toPx() + length), thickness)

                        // Top-right
                        drawLine(color, Offset(size.width - 24.dp.toPx(), 24.dp.toPx()), Offset(size.width - 24.dp.toPx() - length, 24.dp.toPx()), thickness)
                        drawLine(color, Offset(size.width - 24.dp.toPx(), 24.dp.toPx()), Offset(size.width - 24.dp.toPx(), 24.dp.toPx() + length), thickness)

                        // Bottom-left
                        drawLine(color, Offset(24.dp.toPx(), size.height - 24.dp.toPx()), Offset(24.dp.toPx() + length, size.height - 24.dp.toPx()), thickness)
                        drawLine(color, Offset(24.dp.toPx(), size.height - 24.dp.toPx()), Offset(24.dp.toPx(), size.height - 24.dp.toPx() - length), thickness)

                        // Bottom-right
                        drawLine(color, Offset(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()), Offset(size.width - 24.dp.toPx() - length, size.height - 24.dp.toPx()), thickness)
                        drawLine(color, Offset(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()), Offset(size.width - 24.dp.toPx(), size.height - 24.dp.toPx() - length), thickness)
                    }

                    // OCR Center illustration
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Scanner",
                            tint = Color(0xFF6750A4).copy(alpha = 0.8f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "ALIGN ACADEMIC PAGE INSIDE SHIELD",
                            color = Color(0xFF49454F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons triggers SCAN! - Styled in rich violet
                Button(
                    onClick = {
                        hasRequestedScan = true
                        // Draw a custom simple text on mock bitmap to simulate image analysis
                        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        val paint = Paint()
                        paint.color = android.graphics.Color.WHITE
                        canvas.drawRect(0f, 0f, 400f, 400f, paint)
                        paint.color = android.graphics.Color.BLACK
                        paint.textSize = 24f
                        canvas.drawText("TECHNICAL MEMORANDUM OCR", 50f, 200f, paint)
                        customCapturedBitmap = bmp

                        viewModel.performOcr(bmp, mockDacTextResult)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_ocr_button"),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = "Capture Snap", tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("CAPTURE & RECOGNISE TEXT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                // OCR Output display - restyled in Professional Polish Light theme colors
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Task, contentDescription = null, tint = Color(0xFF0F5132))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Extracted Character Output", color = Color(0xFF1D1B20), fontWeight = FontWeight.Bold)
                                }

                                TextButton(onClick = {
                                    hasRequestedScan = false
                                    customCapturedBitmap = null
                                }) {
                                    Text("Snap Again", color = Color(0xFF6750A4))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (loading) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF6750A4))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Running Gemini Multimodal OCR Extraction...", color = Color(0xFF49454F), fontSize = 12.sp)
                                }
                            } else {
                                Text(
                                    text = ocrText,
                                    color = Color(0xFF1D1B20),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF3EDF7))
                                        .border(BorderStroke(1.dp, Color(0xFFCAC4D0)), RoundedCornerShape(8.dp))
                                        .padding(14.dp)
                                        .testTag("ocr_result_text")
                                )
                            }
                        }
                    }

                    // Sync & Import options
                    if (!loading && ocrText.isNotEmpty()) {
                        Text(
                            text = "Save as Synced Document",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = inputDocName,
                            onValueChange = { inputDocName = it },
                            placeholder = { Text("Appended Folder/Doc name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1D1B20),
                                unfocusedTextColor = Color(0xFF49454F),
                                focusedBorderColor = Color(0xFF6750A4),
                                unfocusedBorderColor = Color(0xFFCAC4D0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.importScannedTextAsDoc(inputDocName, ocrText)
                                Toast.makeText(context, "Successfully imported Doc to Cloud Sync Database!", Toast.LENGTH_LONG).show()
                                hasRequestedScan = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("import_ocr_doc_button"),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Cloud Upload")
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("SAVE TO CLOUD & SYNC DEVICES", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Modern sync hub monitoring local vs remote logs of mobile + desktop
@Composable
fun SyncConsoleScreen(viewModel: PdfViewModel) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val syncState by viewModel.syncingState.collectAsStateWithLifecycle()

    var showSyncSuccessCelebration by remember { mutableStateOf(false) }

    LaunchedEffect(syncState) {
        if (syncState == SyncState.Success) {
            showSyncSuccessCelebration = true
        } else if (syncState == SyncState.Idle) {
            showSyncSuccessCelebration = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            // Dashboard header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Pulse Sync Hub",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        "Cross-platform Sync Monitor Console",
                        fontSize = 12.sp,
                        color = Color(0xFF49454F)
                    )
                }

                // Beautiful glowing sync dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (syncState == SyncState.Syncing) Color(0xFF6750A4) else Color(0xFF0F5132))
                )
            }

            // Sync controller card - Crisp White layout with subtle shadow & border
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (syncState == SyncState.Syncing) Icons.Default.Cyclone else Icons.Default.CloudCircle,
                        contentDescription = "Syncing logo",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier
                            .size(72.dp)
                            .rotate(
                                if (syncState == SyncState.Syncing) {
                                    val rotation = rememberInfiniteTransition()
                                    val angle by rotation.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1200, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        ),
                                        label = "rotate"
                                    )
                                    angle
                                } else 0f
                            )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when (syncState) {
                            SyncState.Idle -> "All Files Synced Successfully"
                            SyncState.Syncing -> "Synchronizing connected device stacks..."
                            SyncState.Success -> "Synchronization Completed (Green Level)"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Encrypted TLS Tunnel v2.1 • Multi-device validation",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F),
                        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                    )

                    Button(
                        onClick = { viewModel.syncCloudDocuments() },
                        enabled = syncState == SyncState.Idle,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("trigger_sync_button"),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            text = if (syncState == SyncState.Syncing) "COMMUNICATING WITH SERVERS..." else "FORCE CLOUD SYNC NOW",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Sync successful popup banner - styled with pleasant soothing colors
            AnimatedVisibility(
                visible = showSyncSuccessCelebration,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDEF7EC)),
                    border = BorderStroke(1.dp, Color(0xFF31C48D))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = "Verified Sync", tint = Color(0xFF03543F))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Express Sync Successful! Desktop & Tablet nodes updated safely.",
                            color = Color(0xFF03543F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Peer Connected Status list
            Text(
                "Connected Ecosystem Members (${devices.size})",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D1B20),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                devices.forEach { device ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Device icon based on tag
                            Icon(
                                imageVector = when (device.deviceType) {
                                    "Desktop Editor" -> Icons.Default.LaptopMac
                                    "Tablet Viewer" -> Icons.Default.TabletMac
                                    "Web Reader" -> Icons.Default.Language
                                    else -> Icons.Default.PhoneIphone
                                },
                                contentDescription = "DeviceType",
                                tint = if (device.isMainDevice) Color(0xFF6750A4) else Color(0xFF49454F),
                                modifier = Modifier.size(28.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    color = Color(0xFF1D1B20),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${device.deviceType} • Last Sync: just now",
                                    color = Color(0xFF49454F),
                                    fontSize = 11.sp
                                )
                            }

                            // Connection status dynamic badge - beautiful high-fidelity designs
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (device.status) {
                                            "Offline" -> Color(0xFFFDE8E8)
                                            "Syncing" -> Color(0xFFFEF08A)
                                            else -> Color(0xFFDEF7EC)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = device.status.uppercase(),
                                    color = when (device.status) {
                                        "Offline" -> Color(0xFF9B1C1C)
                                        "Syncing" -> Color(0xFF713F12)
                                        else -> Color(0xFF03543F)
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AreaOcrSelectorView(
    viewModel: PdfViewModel,
    pageContent: com.example.data.model.PdfPageModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val areaResult by viewModel.areaOcrResult.collectAsStateWithLifecycle()
    val areaLoading by viewModel.areaOcrLoading.collectAsStateWithLifecycle()

    var topSliderValue by remember { mutableFloatStateOf(0.18f) }
    var bottomSliderValue by remember { mutableFloatStateOf(0.68f) }
    var leftSliderValue by remember { mutableFloatStateOf(0.08f) }
    var rightSliderValue by remember { mutableFloatStateOf(0.92f) }

    val rectTop = minOf(topSliderValue, bottomSliderValue).coerceIn(0f, 1f)
    val rectBottom = maxOf(topSliderValue, bottomSliderValue).coerceIn(0f, 1f)
    val rectLeft = minOf(leftSliderValue, rightSliderValue).coerceIn(0f, 1f)
    val rectRight = maxOf(leftSliderValue, rightSliderValue).coerceIn(0f, 1f)

    val rectHeight = (rectBottom - rectTop).coerceAtLeast(0.05f)
    val rectWidth = (rectRight - rectLeft).coerceAtLeast(0.05f)

    var showResultDialog by remember { mutableStateOf(false) }
    var currentTextResult by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "ScannerBeam")
    val scannerLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScannerProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scanned Page Area Selector",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        text = "Telescope crop filters to retrieve text blocks and equations instantly.",
                        fontSize = 10.sp,
                        color = Color(0xFF49454F)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1D1B20))
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFCAC4D0).copy(alpha = 0.25f))
                .border(2.dp, Color(0xFFCAC4D0), RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val tapXRatio = offset.x / size.width
                            val tapYRatio = offset.y / size.height
                            val currentWidth = rectWidth
                            val currentHeight = rectHeight

                            val newLeft = (tapXRatio - currentWidth / 2f).coerceIn(0f, 1f - currentWidth)
                            val newTop = (tapYRatio - currentHeight / 2f).coerceIn(0f, 1f - currentHeight)

                            leftSliderValue = newLeft
                            rightSliderValue = newLeft + currentWidth
                            topSliderValue = newTop
                            bottomSliderValue = newTop + currentHeight
                        }
                    }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF6EE)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("PAGE SCANNED IMAGE v1.0", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("SCANNED CAMERA NODE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEADDFF).copy(alpha = 0.5f))
                                    .border(1.dp, Color(0xFF6750A4).copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val graphColor = Color(0xFF6750A4)
                                    val secondaryGraphColor = Color(0xFF0F5132)

                                    val p1 = Offset(w * 0.15f, h * 0.5f)
                                    val p2 = Offset(w * 0.40f, h * 0.2f)
                                    val p3 = Offset(w * 0.40f, h * 0.8f)
                                    val p4 = Offset(w * 0.65f, h * 0.5f)
                                    val p5 = Offset(w * 0.85f, h * 0.5f)

                                    drawLine(graphColor, p1, p2, 2.dp.toPx())
                                    drawLine(graphColor, p1, p3, 2.dp.toPx())
                                    drawLine(graphColor, p2, p4, 2.dp.toPx())
                                    drawLine(graphColor, p3, p4, 2.dp.toPx())
                                    drawLine(secondaryGraphColor, p4, p5, 3.dp.toPx())

                                    drawCircle(graphColor, 10.dp.toPx(), p1)
                                    drawCircle(graphColor, 8.dp.toPx(), p2)
                                    drawCircle(graphColor, 8.dp.toPx(), p3)
                                    drawCircle(graphColor, 10.dp.toPx(), p4)
                                    drawCircle(secondaryGraphColor, 12.dp.toPx(), p5)
                                }
                                Text(
                                    "[Figure 1.1: Multi-head Optical Node Projection Mapping]",
                                    color = Color(0xFF6750A4),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            pageContent.paragraphs.forEachIndexed { _, text ->
                                Text(
                                    text = text,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = Color(0xFF2C251C),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val leftPx = rectLeft * w
                            val rightPx = rectRight * w
                            val topPx = rectTop * h
                            val bottomPx = rectBottom * h

                            drawRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                size = androidx.compose.ui.geometry.Size(w, topPx)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                topLeft = Offset(0f, bottomPx),
                                size = androidx.compose.ui.geometry.Size(w, h - bottomPx)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                topLeft = Offset(0f, topPx),
                                size = androidx.compose.ui.geometry.Size(leftPx, bottomPx - topPx)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                topLeft = Offset(rightPx, topPx),
                                size = androidx.compose.ui.geometry.Size(w - rightPx, bottomPx - topPx)
                            )

                            drawRect(
                                color = Color(0xFF6750A4),
                                topLeft = Offset(leftPx, topPx),
                                size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                            )

                            val bracketLen = 16.dp.toPx()
                            val bracketThickness = 6.dp.toPx()
                            val bracketColor = Color(0xFF6750A4)

                            drawLine(bracketColor, Offset(leftPx, topPx), Offset(leftPx + bracketLen, topPx), bracketThickness)
                            drawLine(bracketColor, Offset(leftPx, topPx), Offset(leftPx, topPx + bracketLen), bracketThickness)

                            drawLine(bracketColor, Offset(rightPx, topPx), Offset(rightPx - bracketLen, topPx), bracketThickness)
                            drawLine(bracketColor, Offset(rightPx, topPx), Offset(rightPx, topPx + bracketLen), bracketThickness)

                            drawLine(bracketColor, Offset(leftPx, bottomPx), Offset(leftPx + bracketLen, bottomPx), bracketThickness)
                            drawLine(bracketColor, Offset(leftPx, bottomPx), Offset(leftPx, bottomPx - bracketLen), bracketThickness)

                            drawLine(bracketColor, Offset(rightPx, bottomPx), Offset(rightPx - bracketLen, bottomPx), bracketThickness)
                            drawLine(bracketColor, Offset(rightPx, bottomPx), Offset(rightPx, bottomPx - bracketLen), bracketThickness)

                            val beamY = topPx + (bottomPx - topPx) * scannerLineProgress
                            drawLine(
                                color = Color(0xFF6750A4),
                                start = Offset(leftPx + 4.dp.toPx(), beamY),
                                end = Offset(rightPx - 4.dp.toPx(), beamY),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "PRECISION COORDINATES ADJUSTER DECK",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6750A4),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Top Clip Bound: ${(rectTop * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF49454F))
                        Slider(
                            value = topSliderValue,
                            onValueChange = { topSliderValue = it },
                            valueRange = 0f..0.9f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6750A4),
                                activeTrackColor = Color(0xFF6750A4),
                                inactiveTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bottom Clip Bound: ${(rectBottom * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF49454F))
                        Slider(
                            value = bottomSliderValue,
                            onValueChange = { bottomSliderValue = it },
                            valueRange = 0.1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6750A4),
                                activeTrackColor = Color(0xFF6750A4),
                                inactiveTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Left Clip Bound: ${(rectLeft * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF49454F))
                        Slider(
                            value = leftSliderValue,
                            onValueChange = { leftSliderValue = it },
                            valueRange = 0f..0.9f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6750A4),
                                activeTrackColor = Color(0xFF6750A4),
                                inactiveTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Right Clip Bound: ${(rectRight * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF49454F))
                        Slider(
                            value = rightSliderValue,
                            onValueChange = { rightSliderValue = it },
                            valueRange = 0.1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6750A4),
                                activeTrackColor = Color(0xFF6750A4),
                                inactiveTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                modifier = Modifier
                    .weight(0.40f)
                    .height(48.dp)
            ) {
                Text("Cancel", color = Color(0xFF49454F), fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val calculatedText = buildString {
                        val paraCount = pageContent.paragraphs.size
                        if (paraCount > 0) {
                            val indexStart = (rectTop * paraCount).toInt().coerceIn(0, paraCount - 1)
                            val indexEnd = (rectBottom * paraCount).toInt().coerceIn(0, paraCount - 1)

                            val selectedParas = pageContent.paragraphs.filterIndexed { index, _ ->
                                index in indexStart..indexEnd
                            }

                            selectedParas.forEach { originalParaText ->
                                val words = originalParaText.split(" ")
                                if (words.isNotEmpty()) {
                                    val leftIndex = (rectLeft * words.size).toInt().coerceIn(0, words.size - 1)
                                    val rightIndex = (rectRight * words.size).toInt().coerceIn(0, words.size)
                                    val finalWords = words.subList(leftIndex, rightIndex)
                                    append(finalWords.joinToString(" "))
                                    append("\n\n")
                                } else {
                                    append(originalParaText)
                                    append("\n\n")
                                }
                            }
                        } else {
                            append("No text could be extracted.")
                        }
                    }.trim()

                    currentTextResult = calculatedText
                    viewModel.performAreaOcr(calculatedText)
                    showResultDialog = true
                },
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                modifier = Modifier
                    .weight(0.60f)
                    .height(48.dp)
                    .testTag("extract_area_ocr_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("RUN CROP AREA OCR", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }

    if (showResultDialog) {
        AreaOcrResultDialog(
            viewModel = viewModel,
            initialText = currentTextResult,
            onDismiss = {
                showResultDialog = false
                viewModel.clearAreaOcr()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaOcrResultDialog(
    viewModel: PdfViewModel,
    initialText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val areaResult by viewModel.areaOcrResult.collectAsStateWithLifecycle()
    val areaLoading by viewModel.areaOcrLoading.collectAsStateWithLifecycle()

    var editedText by remember(initialText) { mutableStateOf(initialText) }

    LaunchedEffect(areaResult) {
        if (areaResult.isNotEmpty()) {
            editedText = areaResult
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TaskAlt, contentDescription = null, tint = Color(0xFF0F5132))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Area OCR Text Extraction",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1D1B20)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    text = "Edit or export the character nodes captured from the dynamic selection box.",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (areaLoading) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6750A4), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Formulating OCR structure via Gemini API...", color = Color(0xFF49454F), fontSize = 12.sp)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("ocr_editable_text_input"),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        placeholder = { Text("Enter extracted text here") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6750A4),
                            unfocusedBorderColor = Color(0xFFCAC4D0),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = Color(0xFF1D1B20),
                            unfocusedTextColor = Color(0xFF1D1B20)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "EXPORT CAPABILITIES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipData = android.content.ClipData.newPlainText("Extracted PDF Area Text", editedText)
                            clipboardManager.setPrimaryClip(clipData)
                            Toast.makeText(context, "Copied to device Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE8DEF8)),
                        modifier = Modifier.weight(1f).testTag("dialog_copy_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            if (editedText.trim().isEmpty()) {
                                Toast.makeText(context, "Text is empty! Please write or extract content.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.importScannedTextAsDoc("Snippet: ${System.currentTimeMillis() % 10000}", editedText)
                                Toast.makeText(context, "Successfully exported Snapped Text as local PDF Document!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFD0BCFF)),
                        modifier = Modifier.weight(1f).testTag("dialog_export_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Import", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Doc", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(16.dp)
            .background(Color.White, shape = RoundedCornerShape(28.dp))
    )
}
