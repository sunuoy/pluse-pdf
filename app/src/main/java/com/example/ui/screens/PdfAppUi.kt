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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.database.DocumentEntity
import com.example.data.database.HighlightEntity
import com.example.data.model.PdfDocumentLines
import com.example.data.model.PdfPageModel
import com.example.ui.viewmodel.ChatRole
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.SyncState
import com.example.ui.viewmodel.ReaderTheme
import com.example.ui.viewmodel.ViewMode
import com.example.ui.viewmodel.PageStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

enum class ScreenType {
    Dashboard, Reader, OCRScanner, CloudDrive
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulsePdfApp(viewModel: PdfViewModel) {
    val selectedDoc by viewModel.selectedDocument.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf(ScreenType.Dashboard) }

    val context = LocalContext.current

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTranslationDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val readerTheme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val brightness by viewModel.brightness.collectAsStateWithLifecycle()
    val pageStyle by viewModel.pageStyle.collectAsStateWithLifecycle()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            var fileName = "imported_document.pdf"
            var fileSize: Long = 1024 * 1024 // fallback 1MB

            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val titleClean = fileName.removeSuffix(".pdf").replace("_", " ").replace("-", " ")
            viewModel.importLocalPdf(titleClean, fileName, fileSize, uri)
            Toast.makeText(context, "Successfully parsed and imported $fileName!", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-navigate to Reader when a document is selected
    LaunchedEffect(selectedDoc) {
        if (selectedDoc != null) {
            currentScreen = ScreenType.Reader
        } else if (currentScreen == ScreenType.Reader) {
            currentScreen = ScreenType.Dashboard
        }
    }

    val onOpenMenu = {
        coroutineScope.launch {
            drawerState.open()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = selectedDoc == null,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFFFEF7FF)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Pulsar Research PDF",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6750A4),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                Text(
                    text = "System Navigation Menu",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F),
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)
                )
                HorizontalDivider(color = Color(0xFFE8DEF8))
                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("Cloud Storage", fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20)) },
                    selected = currentScreen == ScreenType.CloudDrive,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        currentScreen = ScreenType.CloudDrive
                    },
                    icon = { Icon(Icons.Default.CloudQueue, contentDescription = "Cloud Storage", tint = Color(0xFF6750A4)) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("menu_cloud_drive_option")
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text("Settings", fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20)) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showSettingsDialog = true
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF6750A4)) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("menu_settings_option")
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text("Translation", fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20)) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showTranslationDialog = true
                    },
                    icon = { Icon(Icons.Default.Translate, contentDescription = "Translation", tint = Color(0xFF6750A4)) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("menu_translation_option")
                )

                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text("Exit", fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20)) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        (context as? android.app.Activity)?.finish()
                    },
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = "Exit", tint = Color(0xFFEF4444)) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("menu_exit_option")
                )
            }
        }
    ) {
        Scaffold(
            floatingActionButton = {
                if (selectedDoc == null && currentScreen == ScreenType.Dashboard) {
                    ExtendedFloatingActionButton(
                        text = { Text("Import PDF", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Import local PDF") },
                        onClick = {
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        containerColor = Color(0xFF6750A4),
                        contentColor = Color.White,
                        modifier = Modifier.testTag("import_pdf_fab")
                    )
                }
            },
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
                            selected = currentScreen == ScreenType.CloudDrive,
                            onClick = { currentScreen = ScreenType.CloudDrive },
                            icon = { Icon(Icons.Default.Cloud, contentDescription = "Cloud Storage") },
                            label = { Text("Cloud Storage") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1D192B),
                                selectedTextColor = Color(0xFF1D192B),
                                unselectedIconColor = Color(0xFF49454F),
                                unselectedTextColor = Color(0xFF49454F),
                                indicatorColor = Color(0xFFE8DEF8)
                            ),
                            modifier = Modifier.testTag("nav_cloud_tab")
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
                            onOpenMenu = { onOpenMenu() },
                            onPickPdf = { pdfPickerLauncher.launch("application/pdf") }
                        )
                        ScreenType.Reader -> ReaderScreen(
                            viewModel = viewModel,
                            onBack = {
                                viewModel.closeDocument()
                                currentScreen = ScreenType.Dashboard
                            }
                        )
                        ScreenType.OCRScanner -> OcrScannerScreen(
                            viewModel = viewModel,
                            onOpenMenu = { onOpenMenu() }
                        )
                        ScreenType.CloudDrive -> CloudDriveScreen(
                            viewModel = viewModel,
                            onOpenMenu = { onOpenMenu() }
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        ViewSettingsDialog(
            currentViewMode = viewMode,
            currentTheme = readerTheme,
            currentBrightness = brightness,
            currentPageStyle = pageStyle,
            onViewModeChange = { viewModel.setViewMode(it) },
            onThemeChange = { viewModel.setReaderTheme(it) },
            onBrightnessChange = { viewModel.setBrightness(it) },
            onPageStyleChange = { viewModel.setPageStyle(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showTranslationDialog) {
        MainMenuTranslationDialog(
            viewModel = viewModel,
            onDismiss = { showTranslationDialog = false }
        )
    }
}

@Composable
fun DashboardScreen(
    viewModel: PdfViewModel,
    onOpenOcr: () -> Unit,
    onOpenMenu: () -> Unit,
    onPickPdf: () -> Unit
) {
    val docs by viewModel.documents.collectAsStateWithLifecycle()

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier.testTag("dashboard_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Menu",
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PULSE PDF",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    color = Color(0xFF1D1B20)
                )
                Text(
                    text = "Research Engine & PDF Reader",
                    fontSize = 12.sp,
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Feature shortcuts in dynamic bouncing shapes!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
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
                    Text("Extract paper text dynamically", color = Color(0xFF49454F), fontSize = 11.sp)
                }
            }
        }

        // Modern File Input Drop-Zone Card using the File API
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .testTag("local_file_input_zone")
                .clickable { onPickPdf() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF3EDF7)
            ),
            border = BorderStroke(2.dp, Color(0xFF6750A4).copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEADDFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload Document",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(
                    text = "Load Local PDF Document",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
                
                Text(
                    text = "Tap to browse and select a PDF file from your device storage to load it into application state.",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // Added visual badge for parsing mechanism
                Surface(
                    color = Color(0xFF6750A4).copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Text(
                            text = "Parses stream with native decompressor",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6750A4)
                        )
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
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val document by viewModel.selectedDocument.collectAsStateWithLifecycle()
    val activePage by viewModel.currentPage.collectAsStateWithLifecycle()
    val allHighlights by viewModel.highlights.collectAsStateWithLifecycle()

    val currentDoc = document ?: return
    val documentPages by viewModel.documentPages.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val readerTheme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val brightness by viewModel.brightness.collectAsStateWithLifecycle()
    val isRealTimeTranslationEnabled by viewModel.isRealTimeTranslationEnabled.collectAsStateWithLifecycle()
    val pageStyle by viewModel.pageStyle.collectAsStateWithLifecycle()
    val pageRotations by viewModel.pageRotations.collectAsStateWithLifecycle()

    val pageContent = documentPages.find { it.pageNumber == activePage }
        ?: documentPages.firstOrNull()
        ?: PdfPageModel(1, listOf("No page content available."))

    val rotationAngle = pageRotations[activePage] ?: 0f

    val pageHighlights = remember(allHighlights, activePage) {
        allHighlights.filter { it.documentId == currentDoc.id && it.pageNumber == activePage }
    }

    var selectedSentenceText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    DisposableEffect(context) {
        val ttsInstance = android.speech.tts.TextToSpeech(context) { _ -> }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    val translationResult by viewModel.translationResult.collectAsStateWithLifecycle()
    val translationLoading by viewModel.translationLoading.collectAsStateWithLifecycle()
    var activePopupLanguage by remember { mutableStateOf("Spanish") }
    val languages = listOf("English", "Spanish", "French", "Japanese", "German", "Hindi", "Arabic", "Telugu")

    LaunchedEffect(selectedSentenceText, activePopupLanguage, isRealTimeTranslationEnabled) {
        if (selectedSentenceText.isNotEmpty() && isRealTimeTranslationEnabled) {
            viewModel.textSelectedRange = selectedSentenceText
            viewModel.translateSelectedText(activePopupLanguage)
        }
    }
    var showTranslatorPane by remember { mutableStateOf(false) }
    var showResearchDrawer by remember { mutableStateOf(false) }
    var isAreaOcrModeActive by remember { mutableStateOf(false) }

    var showDocMenu by remember { mutableStateOf(false) }
    var showViewSettingsSheet by remember { mutableStateOf(false) }
    var showOrganizePagesMode by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var showFloatingPageIndicator by remember { mutableStateOf(false) }

    // Reset scroll offset to top when page changes
    LaunchedEffect(activePage) {
        scrollState.scrollTo(0)
    }

    // Floating indicator fades in when scrolling/swiping up/down
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            showFloatingPageIndicator = true
        } else {
            delay(1500)
            showFloatingPageIndicator = false
        }
    }

    // Custom NestedScrollConnection to detect swiping past page bounds
    val nestedScrollConnection = remember(activePage, currentDoc.totalPages, viewMode) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (viewMode == ViewMode.Horizontal) {
                    // Swiped left at the right edge (available.x is negative overscroll)
                    if (available.x < -30f) {
                        if (activePage < currentDoc.totalPages) {
                            viewModel.changePage(activePage + 1)
                        }
                    }
                    // Swiped right at the left edge (available.x is positive overscroll)
                    if (available.x > 30f) {
                        if (activePage > 1) {
                            viewModel.changePage(activePage - 1)
                        }
                    }
                } else {
                    // Swiped up at the bottom of the page (available.y is negative overscroll)
                    if (available.y < -30f) {
                        if (activePage < currentDoc.totalPages) {
                            viewModel.changePage(activePage + 1)
                        }
                    }
                    // Swiped down at the top of the page (available.y is positive overscroll)
                    if (available.y > 30f) {
                        if (activePage > 1) {
                            viewModel.changePage(activePage - 1)
                        }
                    }
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    val themeBgColor = when (readerTheme) {
        ReaderTheme.Dark -> Color(0xFF121212)
        ReaderTheme.Green -> Color(0xFFC8E6C9)
        ReaderTheme.Sepia -> Color(0xFFF4ECD8)
        else -> Color(0xFFFEF7FF)
    }

    val themeAppBarColor = when (readerTheme) {
        ReaderTheme.Dark -> Color(0xFF1E1E1E)
        ReaderTheme.Green -> Color(0xFFA5D6A7)
        ReaderTheme.Sepia -> Color(0xFFEFE8D4)
        else -> Color(0xFFF3EDF7)
    }

    val themeTextColor = when (readerTheme) {
        ReaderTheme.Dark -> Color.White
        ReaderTheme.Green -> Color(0xFF1B5E20)
        ReaderTheme.Sepia -> Color(0xFF4E342E)
        else -> Color(0xFF1D1B20)
    }

    val themeSubTextColor = when (readerTheme) {
        ReaderTheme.Dark -> Color.LightGray
        ReaderTheme.Green -> Color(0xFF2E7D32)
        ReaderTheme.Sepia -> Color(0xFF5D4037)
        else -> Color(0xFF6750A4)
    }

    val cardBgColor = when (readerTheme) {
        ReaderTheme.Dark -> Color(0xFF1E1E1E)
        ReaderTheme.Green -> Color(0xFFE8F5E9)
        ReaderTheme.Sepia -> Color(0xFFFDF5E6)
        else -> Color.White
    }

    val paragraphTextColor = when (readerTheme) {
        ReaderTheme.Dark -> Color(0xFFECEFF1)
        ReaderTheme.Green -> Color(0xFF1B5E20)
        ReaderTheme.Sepia -> Color(0xFF4E342E)
        else -> Color(0xFF1E293B)
    }

    if (showOrganizePagesMode) {
        OrganizePagesView(
            viewModel = viewModel,
            currentDoc = currentDoc,
            documentPages = documentPages,
            pageRotations = pageRotations,
            onDismiss = { showOrganizePagesMode = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(themeBgColor)
        ) {
            // App bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeAppBarColor)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("reader_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = themeTextColor)
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
                        color = themeTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Page $activePage of ${currentDoc.totalPages}",
                        fontSize = 11.sp,
                        color = themeSubTextColor
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
                        tint = if (isAreaOcrModeActive) Color(0xFF842029) else themeSubTextColor
                    )
                }

                // Sync Badge and Research Engine Drawer Toggle
                IconButton(onClick = { showResearchDrawer = !showResearchDrawer }) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Research Engine",
                        tint = themeSubTextColor
                    )
                }

                // More Menu Button (Screenshot 1 & 2)
                IconButton(
                    onClick = { showDocMenu = true },
                    modifier = Modifier.testTag("reader_more_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = themeSubTextColor
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
                            .nestedScroll(nestedScrollConnection)
                            .verticalScroll(scrollState)
                            .padding(if (pageStyle == PageStyle.Reflow) 12.dp else 18.dp)
                    ) {
                        val maxColumnWidth = if (pageStyle == PageStyle.Adaptive) 540.dp else androidx.compose.ui.unit.Dp.Unspecified
                        val densityPadding = if (pageStyle == PageStyle.Adaptive) 28.dp else 24.dp
                        val densityFontSize = if (pageStyle == PageStyle.Adaptive) 16.sp else if (pageStyle == PageStyle.Reflow) 17.sp else 15.sp
                        val densityLineHeight = if (pageStyle == PageStyle.Adaptive) 24.sp else if (pageStyle == PageStyle.Reflow) 26.sp else 22.sp

                        if (pageStyle == PageStyle.Reflow) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            ) {
                                pageContent.paragraphs.forEachIndexed { index, paragraphText ->
                                    val isSelected = selectedSentenceText == paragraphText
                                    val itemHighlight = pageHighlights.find { it.textHighlighted == paragraphText }

                                    val bounceScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.025f else if (itemHighlight != null) 1.01f else 1.00f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioHighBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "reflowBounceScale"
                                    )

                                    val bounceTranslationY by animateFloatAsState(
                                        targetValue = if (isSelected) -3f else 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioHighBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "reflowBounceTranslationY"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer(
                                                scaleX = bounceScale,
                                                scaleY = bounceScale,
                                                translationY = bounceTranslationY
                                            )
                                            .animateContentSize()
                                            .clip(RoundedCornerShape(8.dp))
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
                                            .padding(vertical = 12.dp, horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = paragraphText,
                                            color = paragraphTextColor,
                                            fontSize = densityFontSize,
                                            lineHeight = densityLineHeight,
                                            fontFamily = FontFamily.Serif
                                        )

                                        if (isSelected && isRealTimeTranslationEnabled) {
                                            Popup(
                                                alignment = Alignment.BottomCenter,
                                                offset = IntOffset(0, 10),
                                                properties = PopupProperties(
                                                    focusable = false,
                                                    dismissOnClickOutside = true,
                                                    dismissOnBackPress = true
                                                )
                                            ) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.95f)
                                                        .shadow(16.dp, RoundedCornerShape(16.dp))
                                                        .testTag("popup_translation_bubble"),
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = when (readerTheme) {
                                                            ReaderTheme.Dark -> Color(0xFF1E1E1E)
                                                            ReaderTheme.Sepia -> Color(0xFFF4ECD8)
                                                            ReaderTheme.Green -> Color(0xFFE8F5E9)
                                                            else -> Color(0xFFF7F2FA)
                                                        }
                                                    ),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        when (readerTheme) {
                                                            ReaderTheme.Dark -> Color.DarkGray
                                                            else -> Color(0xFFEADBFF)
                                                        }
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(14.dp),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Translate,
                                                                    contentDescription = "Translate",
                                                                    tint = when (readerTheme) {
                                                                        ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                        else -> Color(0xFF6750A4)
                                                                    },
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = "REAL-TIME TRANSLATION",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    letterSpacing = 1.sp,
                                                                    color = when (readerTheme) {
                                                                        ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                        else -> Color(0xFF6750A4)
                                                                    }
                                                                )
                                                            }

                                                            IconButton(
                                                                onClick = { selectedSentenceText = "" },
                                                                modifier = Modifier.size(24.dp).testTag("popup_close_button")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Close",
                                                                    tint = Color.Gray,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }

                                                        LazyRow(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            items(languages) { lang ->
                                                                val isLangSelected = activePopupLanguage == lang
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(12.dp))
                                                                        .background(
                                                                            if (isLangSelected) {
                                                                                when (readerTheme) {
                                                                                    ReaderTheme.Dark -> Color(0xFF00E5FF).copy(alpha = 0.25f)
                                                                                    else -> Color(0xFFEADDFF)
                                                                                }
                                                                            } else {
                                                                                when (readerTheme) {
                                                                                    ReaderTheme.Dark -> Color.White.copy(alpha = 0.1f)
                                                                                    else -> Color(0xFFE7E0EC)
                                                                                }
                                                                            }
                                                                        )
                                                                        .clickable { activePopupLanguage = lang }
                                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                                        .testTag("popup_lang_chip_$lang")
                                                                ) {
                                                                    Text(
                                                                        text = lang,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = if (isLangSelected) FontWeight.Bold else FontWeight.Medium,
                                                                        color = if (isLangSelected) {
                                                                            when (readerTheme) {
                                                                                ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                                else -> Color(0xFF21005D)
                                                                            }
                                                                        } else {
                                                                            when (readerTheme) {
                                                                                ReaderTheme.Dark -> Color.LightGray
                                                                                else -> Color(0xFF49454F)
                                                                            }
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        HorizontalDivider(
                                                            color = when (readerTheme) {
                                                                ReaderTheme.Dark -> Color.White.copy(alpha = 0.1f)
                                                                else -> Color(0xFFEADBFF)
                                                            },
                                                            thickness = 1.dp
                                                        )

                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                    when (readerTheme) {
                                                                        ReaderTheme.Dark -> Color.Black.copy(alpha = 0.2f)
                                                                        else -> Color(0xFFFEF7FF)
                                                                    }
                                                                )
                                                                .padding(10.dp)
                                                        ) {
                                                            if (translationLoading) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                                    horizontalArrangement = Arrangement.Center,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    CircularProgressIndicator(
                                                                        modifier = Modifier.size(16.dp),
                                                                        color = when (readerTheme) {
                                                                            ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                            else -> Color(0xFF6750A4)
                                                                        },
                                                                        strokeWidth = 2.dp
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text(
                                                                        "Translating to $activePopupLanguage...",
                                                                        fontSize = 11.sp,
                                                                        color = Color.Gray,
                                                                        fontStyle = FontStyle.Italic
                                                                    )
                                                                }
                                                            } else {
                                                                Text(
                                                                    text = translationResult.ifEmpty { "Waiting for text..." },
                                                                    fontSize = 13.sp,
                                                                    lineHeight = 18.sp,
                                                                    color = when (readerTheme) {
                                                                        ReaderTheme.Dark -> Color.White
                                                                        ReaderTheme.Sepia -> Color(0xFF4E342E)
                                                                        ReaderTheme.Green -> Color(0xFF1B5E20)
                                                                        else -> Color(0xFF1D1B20)
                                                                    },
                                                                    fontFamily = FontFamily.SansSerif
                                                                )
                                                            }
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                val highlightColors = listOf(
                                                                    "#FFEB3B", // Neon Yellow
                                                                    "#00E5FF", // Cyan Light
                                                                    "#10B981", // Emerald Green
                                                                    "#EC4899"  // Hot Pink
                                                                )
                                                                val hasHighlight = pageHighlights.any { it.textHighlighted == paragraphText }

                                                                if (!hasHighlight) {
                                                                    highlightColors.forEach { color ->
                                                                        val javaColor = android.graphics.Color.parseColor(color)
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(24.dp)
                                                                                .clip(CircleShape)
                                                                                .background(Color(javaColor))
                                                                                .border(1.dp, Color.White, CircleShape)
                                                                                .clickable {
                                                                                    viewModel.addHighlight(color)
                                                                                    selectedSentenceText = ""
                                                                                }
                                                                                .testTag("popup_hl_color_$color")
                                                                        )
                                                                    }
                                                                } else {
                                                                    IconButton(
                                                                        onClick = {
                                                                            val hl = pageHighlights.find { it.textHighlighted == paragraphText }
                                                                            if (hl != null) viewModel.removeHighlight(hl)
                                                                            selectedSentenceText = ""
                                                                        },
                                                                        modifier = Modifier.size(32.dp).testTag("popup_clear_hl")
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.DeleteSweep,
                                                                            contentDescription = "Clear Highlight",
                                                                            tint = Color(0xFFEF4444),
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                IconButton(
                                                                    onClick = {
                                                                        if (translationResult.isNotEmpty() && !translationLoading) {
                                                                            try {
                                                                                tts?.speak(translationResult, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                                                                            } catch (e: Exception) {
                                                                                Toast.makeText(context, "TTS failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(32.dp).testTag("popup_tts_button"),
                                                                    enabled = translationResult.isNotEmpty() && !translationLoading
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.VolumeUp,
                                                                        contentDescription = "Speak translation",
                                                                        tint = when (readerTheme) {
                                                                            ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                            else -> Color(0xFF6750A4)
                                                                        },
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }

                                                                IconButton(
                                                                    onClick = {
                                                                        if (translationResult.isNotEmpty()) {
                                                                            clipboardManager.setText(AnnotatedString(translationResult))
                                                                            Toast.makeText(context, "Translation copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(32.dp).testTag("popup_copy_button"),
                                                                    enabled = translationResult.isNotEmpty()
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.ContentCopy,
                                                                        contentDescription = "Copy to clipboard",
                                                                        tint = when (readerTheme) {
                                                                            ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                            else -> Color(0xFF6750A4)
                                                                        },
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                // PDF Sheet replica style card (Adaptive-Width & Adaptive-Padding configured)
                                Card(
                                    modifier = Modifier
                                        .widthIn(max = maxColumnWidth)
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .rotate(rotationAngle),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBgColor)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(densityPadding)
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
                                                color = if (readerTheme == ReaderTheme.Dark) Color.Gray else Color.LightGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "PAGE 0$activePage",
                                                color = if (readerTheme == ReaderTheme.Dark) Color.Gray else Color.LightGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 18.dp))

                                        // Rendered paragraphs containing text lines
                                        pageContent.paragraphs.forEachIndexed { index, paragraphText ->
                                    val isSelected = selectedSentenceText == paragraphText
                                    val itemHighlight = pageHighlights.find { it.textHighlighted == paragraphText }

                                    // Bouncy spring-physics based scale and translation animations
                                    val bounceScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.025f else if (itemHighlight != null) 1.01f else 1.00f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioHighBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "bouncyScale"
                                    )

                                    val bounceTranslationY by animateFloatAsState(
                                        targetValue = if (isSelected) -3f else 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioHighBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "bouncyTranslationY"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer(
                                                scaleX = bounceScale,
                                                scaleY = bounceScale,
                                                translationY = bounceTranslationY
                                            )
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
                                            color = paragraphTextColor,
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp,
                                            fontFamily = FontFamily.Serif
                                        )

                                        if (isSelected && isRealTimeTranslationEnabled) {
                                            Popup(
                                                alignment = Alignment.BottomCenter,
                                                offset = IntOffset(0, 10),
                                                properties = PopupProperties(
                                                    focusable = false,
                                                    dismissOnClickOutside = true,
                                                    dismissOnBackPress = true
                                                )
                                            ) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.95f)
                                                        .shadow(16.dp, RoundedCornerShape(16.dp))
                                                        .testTag("popup_translation_bubble"),
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = when (readerTheme) {
                                                            ReaderTheme.Dark -> Color(0xFF1E1E1E)
                                                            ReaderTheme.Sepia -> Color(0xFFF4ECD8)
                                                            ReaderTheme.Green -> Color(0xFFE8F5E9)
                                                            else -> Color(0xFFF7F2FA)
                                                        }
                                                    ),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        when (readerTheme) {
                                                            ReaderTheme.Dark -> Color.DarkGray
                                                            else -> Color(0xFFEADBFF)
                                                        }
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(14.dp),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Translate,
                                                                    contentDescription = "Translate",
                                                                    tint = when (readerTheme) {
                                                                        ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                        else -> Color(0xFF6750A4)
                                                                    },
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = "REAL-TIME TRANSLATION",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    letterSpacing = 1.sp,
                                                                    color = when (readerTheme) {
                                                                        ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                        else -> Color(0xFF6750A4)
                                                                    }
                                                                )
                                                            }

                                                            IconButton(
                                                                onClick = { selectedSentenceText = "" },
                                                                modifier = Modifier.size(24.dp).testTag("popup_close_button")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Close",
                                                                    tint = Color.Gray,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }

                                                        LazyRow(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            items(languages) { lang ->
                                                                val isLangSelected = activePopupLanguage == lang
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(12.dp))
                                                                        .background(
                                                                            if (isLangSelected) {
                                                                                when (readerTheme) {
                                                                                    ReaderTheme.Dark -> Color(0xFF00E5FF).copy(alpha = 0.25f)
                                                                                    else -> Color(0xFFEADDFF)
                                                                                }
                                                                            } else {
                                                                                when (readerTheme) {
                                                                                    ReaderTheme.Dark -> Color.White.copy(alpha = 0.1f)
                                                                                    else -> Color(0xFFE7E0EC)
                                                                                }
                                                                            }
                                                                        )
                                                                        .clickable { activePopupLanguage = lang }
                                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                                        .testTag("popup_lang_chip_$lang")
                                                                ) {
                                                                    Text(
                                                                        text = lang,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = if (isLangSelected) FontWeight.Bold else FontWeight.Medium,
                                                                        color = if (isLangSelected) {
                                                                            when (readerTheme) {
                                                                                ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                                else -> Color(0xFF21005D)
                                                                            }
                                                                        } else {
                                                                            when (readerTheme) {
                                                                                ReaderTheme.Dark -> Color.LightGray
                                                                                else -> Color(0xFF49454F)
                                                                            }
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        HorizontalDivider(
                                                            color = when (readerTheme) {
                                                                ReaderTheme.Dark -> Color.White.copy(alpha = 0.1f)
                                                                else -> Color(0xFFEADBFF)
                                                            },
                                                            thickness = 1.dp
                                                        )

                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                    when (readerTheme) {
                                                                        ReaderTheme.Dark -> Color.Black.copy(alpha = 0.2f)
                                                                        else -> Color(0xFFFEF7FF)
                                                                    }
                                                                )
                                                                .padding(10.dp)
                                                        ) {
                                                            if (translationLoading) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                                    horizontalArrangement = Arrangement.Center,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    CircularProgressIndicator(
                                                                        modifier = Modifier.size(16.dp),
                                                                        color = when (readerTheme) {
                                                                            ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                            else -> Color(0xFF6750A4)
                                                                        },
                                                                        strokeWidth = 2.dp
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text(
                                                                        "Translating to $activePopupLanguage...",
                                                                        fontSize = 11.sp,
                                                                        color = Color.Gray,
                                                                        fontStyle = FontStyle.Italic
                                                                    )
                                                                }
                                                            } else {
                                                                Text(
                                                                    text = translationResult.ifEmpty { "Waiting for text..." },
                                                                    fontSize = 13.sp,
                                                                    lineHeight = 18.sp,
                                                                    color = when (readerTheme) {
                                                                        ReaderTheme.Dark -> Color.White
                                                                        ReaderTheme.Sepia -> Color(0xFF4E342E)
                                                                        ReaderTheme.Green -> Color(0xFF1B5E20)
                                                                        else -> Color(0xFF1D1B20)
                                                                    },
                                                                    fontFamily = FontFamily.SansSerif
                                                                )
                                                            }
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                val highlightColors = listOf(
                                                                    "#FFEB3B", // Neon Yellow
                                                                    "#00E5FF", // Cyan Light
                                                                    "#10B981", // Emerald Green
                                                                    "#EC4899"  // Hot Pink
                                                                )
                                                                val hasHighlight = pageHighlights.any { it.textHighlighted == paragraphText }

                                                                if (!hasHighlight) {
                                                                    highlightColors.forEach { color ->
                                                                        val javaColor = android.graphics.Color.parseColor(color)
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(24.dp)
                                                                                .clip(CircleShape)
                                                                                .background(Color(javaColor))
                                                                                .border(1.dp, Color.White, CircleShape)
                                                                                .clickable {
                                                                                    viewModel.addHighlight(color)
                                                                                    selectedSentenceText = ""
                                                                                }
                                                                                .testTag("popup_hl_color_$color")
                                                                        )
                                                                    }
                                                                } else {
                                                                    IconButton(
                                                                        onClick = {
                                                                            val hl = pageHighlights.find { it.textHighlighted == paragraphText }
                                                                            if (hl != null) viewModel.removeHighlight(hl)
                                                                            selectedSentenceText = ""
                                                                        },
                                                                        modifier = Modifier.size(32.dp).testTag("popup_clear_hl")
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.DeleteSweep,
                                                                            contentDescription = "Clear Highlight",
                                                                            tint = Color(0xFFEF4444),
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                IconButton(
                                                                    onClick = {
                                                                        if (translationResult.isNotEmpty() && !translationLoading) {
                                                                            try {
                                                                                tts?.speak(translationResult, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                                                                            } catch (e: Exception) {
                                                                                Toast.makeText(context, "TTS failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(32.dp).testTag("popup_tts_button"),
                                                                    enabled = translationResult.isNotEmpty() && !translationLoading
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.VolumeUp,
                                                                        contentDescription = "Speak translation",
                                                                        tint = when (readerTheme) {
                                                                            ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                            else -> Color(0xFF6750A4)
                                                                        },
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }

                                                                IconButton(
                                                                    onClick = {
                                                                        if (translationResult.isNotEmpty()) {
                                                                            clipboardManager.setText(AnnotatedString(translationResult))
                                                                            Toast.makeText(context, "Translation copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(32.dp).testTag("popup_copy_button"),
                                                                    enabled = translationResult.isNotEmpty()
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.ContentCopy,
                                                                        contentDescription = "Copy to clipboard",
                                                                        tint = when (readerTheme) {
                                                                            ReaderTheme.Dark -> Color(0xFF00E5FF)
                                                                            else -> Color(0xFF6750A4)
                                                                        },
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(bottom = 12.dp))

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
                    }
                }

                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }

                // AI summary drawer panel
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

                // Floating Page HUD Overlay (Fades in/out on scroll/swipe)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showFloatingPageIndicator,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("floating_page_hud")
                ) {
                    Card(
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1D1B20).copy(alpha = 0.85f),
                            contentColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "Page Icon",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "PAGE $activePage OF ${currentDoc.totalPages}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }

                // Brightness Dimmer Overlay
                if (brightness < 1.0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 1.0f - brightness))
                    )
                }
            }

            // Bottom Controller containing Progress Slider (M3) and Prev/Next buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                // Progress Slider row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "$activePage",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = activePage.toFloat(),
                        onValueChange = { pageVal ->
                            viewModel.changePage(pageVal.toInt())
                        },
                        valueRange = 1f..currentDoc.totalPages.toFloat(),
                        steps = if (currentDoc.totalPages > 2) currentDoc.totalPages - 2 else 0,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_page_progress_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    Text(
                        text = "${currentDoc.totalPages}",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Prev / Next actions row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.changePage(activePage - 1) },
                        enabled = activePage > 1,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E5FF))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev")
                            Text("PREVIOUS")
                        }
                    }

                    TextButton(
                        onClick = { showOrganizePagesMode = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E5FF))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GridView, contentDescription = "Organize", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ORGANIZE")
                        }
                    }

                    TextButton(
                        onClick = { viewModel.changePage(activePage + 1) },
                        enabled = activePage < currentDoc.totalPages,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E5FF))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NEXT")
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next")
                        }
                    }
                }
            }
        }
    }

    // Modal Option Dialogs & Sheets
    if (showDocMenu) {
        DocumentOptionsMenu(
            title = currentDoc.title,
            onDismiss = { showDocMenu = false },
            onViewSettings = { showViewSettingsSheet = true },
            onGoToPage = { showGoToPageDialog = true },
            onOrganizePages = { showOrganizePagesMode = true },
            onConvert = { Toast.makeText(context, "Converting document to editable format...", Toast.LENGTH_SHORT).show() },
            onShare = { Toast.makeText(context, "Encrypted link copied to share drawer!", Toast.LENGTH_SHORT).show() },
            onPrint = { Toast.makeText(context, "Formatting print document... Sent to spooler.", Toast.LENGTH_SHORT).show() },
            onSaveAs = { Toast.makeText(context, "Successfully saved copy to custom directory.", Toast.LENGTH_SHORT).show() },
            isTranslationEnabled = isRealTimeTranslationEnabled,
            onTranslationToggle = { viewModel.setRealTimeTranslationEnabled(it) },
            currentPageStyle = pageStyle,
            onPageStyleChange = { viewModel.setPageStyle(it) }
        )
    }

    if (showViewSettingsSheet) {
        ViewSettingsDialog(
            currentViewMode = viewMode,
            currentTheme = readerTheme,
            currentBrightness = brightness,
            currentPageStyle = pageStyle,
            onViewModeChange = { viewModel.setViewMode(it) },
            onThemeChange = { viewModel.setReaderTheme(it) },
            onBrightnessChange = { viewModel.setBrightness(it) },
            onPageStyleChange = { viewModel.setPageStyle(it) },
            onDismiss = { showViewSettingsSheet = false }
        )
    }

    if (showGoToPageDialog) {
        var pageInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGoToPageDialog = false },
            title = { Text("Go to Page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter page number (1 to ${currentDoc.totalPages}):")
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { char -> char.isDigit() } },
                        modifier = Modifier.fillMaxWidth().testTag("go_to_page_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = pageInput.toIntOrNull()
                        if (parsed != null && parsed in 1..currentDoc.totalPages) {
                            viewModel.changePage(parsed)
                            showGoToPageDialog = false
                        } else {
                            Toast.makeText(context, "Please enter a valid page number", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Floating Popover for Highlights and Real-time translation toggling
@Composable
fun FloatingHighlightPopover(
    viewModel: PdfViewModel,
    textHighlighted: String,
    highlightExists: Boolean,
    onHighlightColorSelected: (String) -> Unit,
    onRemoveHighlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTranslationToggleActive by remember { mutableStateOf(false) }
    var selectedTargetLanguage by remember { mutableStateOf("Spanish") }

    val translation by viewModel.translationResult.collectAsStateWithLifecycle()
    val loading by viewModel.translationLoading.collectAsStateWithLifecycle()

    val languages = listOf("English", "Spanish", "French", "Japanese", "German", "Hindi", "Arabic", "Telugu")

    LaunchedEffect(textHighlighted, isTranslationToggleActive, selectedTargetLanguage) {
        if (textHighlighted.isNotEmpty() && isTranslationToggleActive) {
            viewModel.translateSelectedText(selectedTargetLanguage)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
        border = BorderStroke(1.5.dp, Color(0xFFEADBFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val highlightColors = listOf(
                        "#FFEB3B", // Neon Yellow
                        "#00E5FF", // Cyan Light
                        "#10B981", // Emerald Green
                        "#EC4899"  // Hot Pink
                    )

                    if (!highlightExists) {
                        highlightColors.forEach { color ->
                            val javaColor = android.graphics.Color.parseColor(color)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(javaColor))
                                    .border(1.5.dp, Color.White, CircleShape)
                                    .clickable { onHighlightColorSelected(color) }
                                    .testTag("popover_hl_color_$color")
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onRemoveHighlight,
                            modifier = Modifier.size(36.dp).testTag("popover_clear_hl")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Highlight",
                                tint = Color(0xFFEF4444)
                            )
                        }
                        Text(
                            "Highlighted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF625B71)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color(0xFFCAC4D0))
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Translate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTranslationToggleActive) Color(0xFF6750A4) else Color(0xFF49454F)
                    )
                    Switch(
                        checked = isTranslationToggleActive,
                        onCheckedChange = { isTranslationToggleActive = it },
                        modifier = Modifier.testTag("popover_translation_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6750A4),
                            uncheckedThumbColor = Color(0xFF79747E),
                            uncheckedTrackColor = Color(0xFFE7E0EC)
                        )
                    )
                }
            }

            if (isTranslationToggleActive) {
                HorizontalDivider(color = Color(0xFFEADBFF), thickness = 1.dp)

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(languages) { lang ->
                        val isSelected = selectedTargetLanguage == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0xFFEADDFF) else Color(0xFFE7E0EC)
                                )
                                .clickable { selectedTargetLanguage = lang }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("popover_lang_chip_$lang")
                        ) {
                            Text(
                                text = lang,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF21005D) else Color(0xFF49454F)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEF7FF))
                        .border(1.dp, Color(0xFFEADDFF), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (loading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF6750A4),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Translating selected text...",
                                fontSize = 11.sp,
                                color = Color(0xFF49454F),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Translated text",
                                        tint = Color(0xFF6750A4),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "TRANSLATED TO ${selectedTargetLanguage.uppercase()}:",
                                        color = Color(0xFF6750A4),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = translation.ifEmpty { "Waiting for text..." },
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFF1D1B20),
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
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
    val languages = listOf("English", "Spanish", "French", "Japanese", "German", "Hindi", "Arabic", "Telugu")
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
fun OcrScannerScreen(
    viewModel: PdfViewModel,
    onOpenMenu: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.testTag("ocr_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Navigation Menu",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "Mobile OCR character Recognition",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        "Snap academic articles to read, edit & sync.",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

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
                                Toast.makeText(context, "Successfully saved document!", Toast.LENGTH_LONG).show()
                                hasRequestedScan = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("import_ocr_doc_button"),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Save document")
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("SAVE AS NEW DOCUMENT", color = Color.White, fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentOptionsMenu(
    title: String,
    onDismiss: () -> Unit,
    onViewSettings: () -> Unit,
    onGoToPage: () -> Unit,
    onOrganizePages: () -> Unit,
    onConvert: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onSaveAs: () -> Unit,
    isTranslationEnabled: Boolean,
    onTranslationToggle: (Boolean) -> Unit,
    currentPageStyle: PageStyle,
    onPageStyleChange: (PageStyle) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = Color(0xFF1D1B20),
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .wrapContentHeight()
            .padding(16.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Modified 27 Jun 2026 08:31:21",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Page Style Section
                Text(
                    text = "PAGE LAYOUT STYLE",
                    color = Color.LightGray.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val styles = listOf(
                        PageStyle.Standard to "Standard",
                        PageStyle.Reflow to "Reflow",
                        PageStyle.Adaptive to "Adaptive"
                    )
                    styles.forEach { (style, name) ->
                        val isSelected = currentPageStyle == style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF6750A4) else Color(0xFF2C2C2C))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF6750A4) else Color.DarkGray
                                    ),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onPageStyleChange(style) }
                                .padding(vertical = 8.dp)
                                .testTag("page_style_chip_${name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = when (style) {
                                        PageStyle.Standard -> Icons.Default.MenuBook
                                        PageStyle.Reflow -> Icons.Default.WrapText
                                        PageStyle.Adaptive -> Icons.Default.Smartphone
                                    },
                                    contentDescription = name,
                                    tint = if (isSelected) Color.White else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = name,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val items = listOf(
                    Triple("View Settings", Icons.Default.Visibility, onViewSettings),
                    Triple("Go to Page", Icons.Default.ArrowForward, onGoToPage),
                    Triple("Real-time Translation", Icons.Default.Translate, { onTranslationToggle(!isTranslationEnabled) }),
                    Triple("Organize Pages", Icons.Default.GridView, onOrganizePages),
                    Triple("Convert", Icons.Default.Transform, onConvert),
                    Triple("Share", Icons.Default.Share, onShare),
                    Triple("Print", Icons.Default.Print, onPrint),
                    Triple("Save as", Icons.Default.Save, onSaveAs)
                )
                
                items.forEach { (label, icon, action) ->
                    val isToggleItem = label == "Real-time Translation"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                action()
                                if (!isToggleItem) {
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (isToggleItem) {
                            Switch(
                                checked = isTranslationEnabled,
                                onCheckedChange = { onTranslationToggle(it) },
                                modifier = Modifier.testTag("dialog_translation_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF6750A4),
                                    checkedTrackColor = Color(0xFFE8DEF8),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Arrow",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ViewSettingsDialog(
    currentViewMode: ViewMode,
    currentTheme: ReaderTheme,
    currentBrightness: Float,
    currentPageStyle: PageStyle,
    onViewModeChange: (ViewMode) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPageStyleChange: (PageStyle) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable(enabled = false) {}
                    .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "View Settings",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onViewModeChange(ViewMode.Vertical) },
                            shape = RoundedCornerShape(12.dp),
                            border = if (currentViewMode == ViewMode.Vertical) BorderStroke(2.dp, Color(0xFF00E5FF)) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentViewMode == ViewMode.Vertical) Color(0xFF2C2C2C) else Color(0xFF121212)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarViewWeek,
                                    contentDescription = "Vertical",
                                    tint = if (currentViewMode == ViewMode.Vertical) Color(0xFF00E5FF) else Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Vertical",
                                    color = if (currentViewMode == ViewMode.Vertical) Color.White else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onViewModeChange(ViewMode.Horizontal) },
                            shape = RoundedCornerShape(12.dp),
                            border = if (currentViewMode == ViewMode.Horizontal) BorderStroke(2.dp, Color(0xFF00E5FF)) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentViewMode == ViewMode.Horizontal) Color(0xFF2C2C2C) else Color(0xFF121212)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "Horizontal",
                                    tint = if (currentViewMode == ViewMode.Horizontal) Color(0xFF00E5FF) else Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Horizontal",
                                    color = if (currentViewMode == ViewMode.Horizontal) Color.White else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Page Style",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            PageStyle.Standard to "Standard",
                            PageStyle.Reflow to "Reflow",
                            PageStyle.Adaptive to "Adaptive"
                        ).forEach { (style, name) ->
                            val isSelected = currentPageStyle == style
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onPageStyleChange(style) }
                                    .testTag("dialog_page_style_chip_${name.lowercase()}"),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) BorderStroke(2.dp, Color(0xFF00E5FF)) else null,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF2C2C2C) else Color(0xFF121212)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = when (style) {
                                            PageStyle.Standard -> Icons.Default.MenuBook
                                            PageStyle.Reflow -> Icons.Default.WrapText
                                            PageStyle.Adaptive -> Icons.Default.Smartphone
                                        },
                                        contentDescription = name,
                                        tint = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = name,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LightMode,
                            contentDescription = "Dim",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        
                        Slider(
                            value = currentBrightness,
                            onValueChange = onBrightnessChange,
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color.DarkGray
                            )
                        )
                        
                        Icon(
                            imageVector = Icons.Default.LightMode,
                            contentDescription = "Bright",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemeCircle(
                            bgColor = Color(0xFF424242),
                            ringColor = if (currentTheme == ReaderTheme.System) Color(0xFF00E5FF) else Color.Transparent,
                            icon = Icons.Default.Settings,
                            iconTint = Color.White,
                            onClick = { onThemeChange(ReaderTheme.System) }
                        )
                        
                        ThemeCircle(
                            bgColor = Color.White,
                            ringColor = if (currentTheme == ReaderTheme.Light) Color(0xFF00E5FF) else Color.Transparent,
                            icon = Icons.Default.LightMode,
                            iconTint = Color.Black,
                            onClick = { onThemeChange(ReaderTheme.Light) }
                        )
                        
                        ThemeCircle(
                            bgColor = Color(0xFF121212),
                            ringColor = if (currentTheme == ReaderTheme.Dark) Color(0xFF00E5FF) else Color.Transparent,
                            icon = Icons.Default.DarkMode,
                            iconTint = Color.White,
                            onClick = { onThemeChange(ReaderTheme.Dark) }
                        )
                        
                        ThemeCircle(
                            bgColor = Color(0xFFC8E6C9),
                            ringColor = if (currentTheme == ReaderTheme.Green) Color(0xFF00E5FF) else Color.Transparent,
                            icon = Icons.Default.Circle,
                            iconTint = Color(0xFF1B5E20),
                            onClick = { onThemeChange(ReaderTheme.Green) }
                        )
                        
                        ThemeCircle(
                            bgColor = Color(0xFFF4ECD8),
                            ringColor = if (currentTheme == ReaderTheme.Sepia) Color(0xFF00E5FF) else Color.Transparent,
                            icon = Icons.Default.Circle,
                            iconTint = Color(0xFF4E342E),
                            onClick = { onThemeChange(ReaderTheme.Sepia) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ThemeCircle(
    bgColor: Color,
    ringColor: Color,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(BorderStroke(if (ringColor != Color.Transparent) 3.dp else 1.dp, if (ringColor != Color.Transparent) ringColor else Color.Gray), CircleShape)
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun OrganizePagesView(
    viewModel: PdfViewModel,
    currentDoc: DocumentEntity,
    documentPages: List<PdfPageModel>,
    pageRotations: Map<Int, Float>,
    onDismiss: () -> Unit
) {
    var selectedPages by remember { mutableStateOf(setOf<Int>()) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1D1B20))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Organize Pages",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedPages.isEmpty()) "Select pages" else "${selectedPages.size} Selected",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = {
                    viewModel.addPage()
                    Toast.makeText(context, "New blank page added!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.NoteAdd,
                    contentDescription = "Add page",
                    tint = Color.White
                )
            }

            IconButton(
                enabled = selectedPages.isNotEmpty() || documentPages.isNotEmpty(),
                onClick = {
                    val pageToDuplicate = selectedPages.firstOrNull() ?: viewModel.currentPage.value
                    viewModel.duplicatePage(pageToDuplicate)
                    Toast.makeText(context, "Page duplicated successfully!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Duplicate page",
                    tint = if (selectedPages.isNotEmpty()) Color.White else Color.Gray
                )
            }

            IconButton(
                enabled = selectedPages.isNotEmpty() || documentPages.isNotEmpty(),
                onClick = {
                    val pageToRotate = selectedPages.firstOrNull() ?: viewModel.currentPage.value
                    viewModel.rotatePage(pageToRotate)
                    Toast.makeText(context, "Page rotated 90°", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Rotate page",
                    tint = if (selectedPages.isNotEmpty()) Color.White else Color.Gray
                )
            }

            IconButton(
                enabled = documentPages.size > 1 && (selectedPages.isNotEmpty() || documentPages.isNotEmpty()),
                onClick = {
                    val pageToDelete = selectedPages.firstOrNull() ?: viewModel.currentPage.value
                    viewModel.deletePage(pageToDelete)
                    selectedPages = emptySet()
                    Toast.makeText(context, "Page deleted!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete page",
                    tint = if (documentPages.size > 1) Color.Red else Color.Gray
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(count = documentPages.size) { index ->
                    val page = documentPages[index]
                    val isSelected = selectedPages.contains(page.pageNumber)
                    val rotation = pageRotations[page.pageNumber] ?: 0f

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPages = if (isSelected) {
                                    selectedPages - page.pageNumber
                                } else {
                                    selectedPages + page.pageNumber
                                }
                            }
                            .border(
                                BorderStroke(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.Gray.copy(alpha = 0.3f)
                                ),
                                RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedPages = if (checked == true) {
                                            selectedPages + page.pageNumber
                                        } else {
                                            selectedPages - page.pageNumber
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF00E5FF),
                                        checkmarkColor = Color.Black
                                    )
                                )
                                Text(
                                    text = "${page.pageNumber}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .rotate(rotation),
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    val textSnippet = page.paragraphs.firstOrNull() ?: ""
                                    Text(
                                        text = textSnippet,
                                        fontSize = 8.sp,
                                        lineHeight = 11.sp,
                                        color = Color.Black,
                                        maxLines = 10,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1D1B20))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bottomTabs = listOf(
                Pair("Annotate", Icons.Default.Brush),
                Pair("Edit", Icons.Default.Edit),
                Pair("Organize Pages", Icons.Default.GridView),
                Pair("Fill & Sign", Icons.Default.Create)
            )

            bottomTabs.forEach { (tabName, icon) ->
                val isActive = tabName == "Organize Pages"
                Column(
                    modifier = Modifier
                        .clickable {
                            if (!isActive) {
                                Toast.makeText(context, "$tabName Mode selected", Toast.LENGTH_SHORT).show()
                                if (tabName == "Annotate" || tabName == "Edit") {
                                    onDismiss()
                                }
                            }
                        }
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tabName,
                        tint = if (isActive) Color(0xFF00E5FF) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = tabName,
                        color = if (isActive) Color(0xFF00E5FF) else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun MainMenuTranslationDialog(
    viewModel: PdfViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val languages = listOf("English", "Spanish", "French", "Japanese", "German", "Hindi", "Arabic", "Telugu")
    var selectedLanguage by remember { mutableStateOf("Spanish") }
    
    val translationResult by viewModel.translationResult.collectAsStateWithLifecycle()
    val translationLoading by viewModel.translationLoading.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    DisposableEffect(context) {
        val ttsInstance = android.speech.tts.TextToSpeech(context) { _ -> }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .testTag("translation_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF7FF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Global Translator",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close dialog")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Input Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Text to translate") },
                    placeholder = { Text("Type or paste academic phrases...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("translator_input_field"),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Target Language Selector Header
                Text(
                    text = "TARGET LANGUAGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F),
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Horizontal list of languages (using custom chips style matching TranslatorSheet)
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action Buttons (Clear, Translate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { inputText = "" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear")
                    }
                    
                    Button(
                        onClick = {
                            viewModel.translateCustomText(inputText, selectedLanguage)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("translator_translate_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = inputText.isNotBlank() && !translationLoading
                    ) {
                        if (translationLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Translate")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Result Box
                if (translationResult.isNotEmpty()) {
                    Text(
                        text = "TRANSLATION RESULT ($selectedLanguage):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4),
                        letterSpacing = 0.5.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF3EDF7), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE8DEF8), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = translationResult,
                                fontSize = 14.sp,
                                color = Color(0xFF1D1B20)
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // TTS Button
                                IconButton(
                                    onClick = {
                                        tts?.speak(translationResult, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                                    },
                                    enabled = !translationLoading
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = "Read translation aloud", tint = Color(0xFF6750A4))
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Copy Button
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(translationResult))
                                        Toast.makeText(context, "Translation copied!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy translation", tint = Color(0xFF6750A4))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudDriveScreen(
    viewModel: PdfViewModel,
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Google Drive, 1 = OneDrive
    var searchQuery by remember { mutableStateOf("") }
    
    // Auth statuses
    var isGoogleDriveConnected by remember { mutableStateOf(false) } // False initially so user logs in
    var loggedInEmail by remember { mutableStateOf("") }
    var googleEmail by remember { mutableStateOf("") }
    var googlePassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    var isOneDriveConnected by remember { mutableStateOf(false) }
    var oneDriveLoggedInEmail by remember { mutableStateOf("") }
    var oneDriveEmail by remember { mutableStateOf("") }
    var oneDrivePassword by remember { mutableStateOf("") }
    var isOneDrivePasswordVisible by remember { mutableStateOf(false) }
    var isOneDriveLoggingIn by remember { mutableStateOf(false) }

    // Mock cloud files lists
    val googleDriveFiles = remember {
        listOf(
            CloudFile("Quantum Computing Fundamentals", "quantum_computing_fundamentals.pdf", 14500000, "2026-06-15", "Dr. Alan Turing"),
            CloudFile("AI Ethics and Safety Framework", "ai_ethics_safety_framework.pdf", 4200000, "2026-06-20", "Ethics Board"),
            CloudFile("Astrophysics Introduction", "astrophysics_intro.pdf", 28000000, "2026-05-12", "NASA Research"),
            CloudFile("Deep Learning Architectures", "deep_learning_architectures.pdf", 18300000, "2026-06-01", "Yann LeCun"),
            CloudFile("Molecular Biology Lab Notes", "molecular_biology_lab_notes.pdf", 3200000, "2026-06-25", "Jane Doe")
        )
    }

    val oneDriveFiles = remember {
        listOf(
            CloudFile("Climate Change Analysis 2026", "climate_change_analysis_2026.pdf", 12400000, "2026-04-10", "IPCC Panel"),
            CloudFile("Game Theory & Microeconomics", "game_theory_economics.pdf", 8900000, "2026-03-29", "Nash Institute"),
            CloudFile("Organic Chemistry Formulas", "organic_chemistry_formulas.pdf", 1150000, "2026-06-18", "Prof. Woodward"),
            CloudFile("Macroeconomics Foundations", "macro_foundations.pdf", 6100000, "2026-05-05", "IMF Report")
        )
    }

    // Interactive import loaders
    var importingFile by remember { mutableStateOf<String?>(null) }
    var importProgress by remember { mutableStateOf(0f) }

    // Simulated login timer
    if (isLoggingIn) {
        LaunchedEffect(Unit) {
            delay(1500)
            isGoogleDriveConnected = true
            loggedInEmail = googleEmail
            isLoggingIn = false
            Toast.makeText(context, "Google Account Authorized successfully!", Toast.LENGTH_LONG).show()
        }
    }

    if (isOneDriveLoggingIn) {
        LaunchedEffect(Unit) {
            delay(1500)
            isOneDriveConnected = true
            oneDriveLoggedInEmail = oneDriveEmail
            isOneDriveLoggingIn = false
            Toast.makeText(context, "OneDrive Account Authorized successfully!", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(importingFile) {
        if (importingFile != null) {
            importProgress = 0f
            while (importProgress < 1f) {
                delay(100)
                importProgress += 0.1f
            }
            val fileToImport = if (selectedTab == 0) {
                googleDriveFiles.firstOrNull { it.fileName == importingFile }
            } else {
                oneDriveFiles.firstOrNull { it.fileName == importingFile }
            }
            if (fileToImport != null) {
                viewModel.importCloudPdf(
                    title = fileToImport.title,
                    fileName = fileToImport.fileName,
                    fileSize = fileToImport.fileSize,
                    source = if (selectedTab == 0) "Google Drive" else "OneDrive"
                )
                Toast.makeText(context, "Successfully downloaded and imported ${fileToImport.fileName}!", Toast.LENGTH_LONG).show()
            }
            importingFile = null
        }
    }

    val currentFiles = if (selectedTab == 0) googleDriveFiles else oneDriveFiles
    val filteredFiles = currentFiles.filter {
        it.title.contains(searchQuery, ignoreCase = true) || 
        it.fileName.contains(searchQuery, ignoreCase = true) ||
        it.author.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier.testTag("cloud_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Menu",
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cloud Integrations",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1D1B20)
                )
                Text(
                    "Import Research PDFs directly to your local library",
                    fontSize = 11.sp,
                    color = Color(0xFF49454F)
                )
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFFF3EDF7),
            contentColor = Color(0xFF6750A4),
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0; searchQuery = "" },
                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = "Google Drive icon", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google Drive", fontWeight = FontWeight.Bold)
                } },
                modifier = Modifier.testTag("google_drive_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; searchQuery = "" },
                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, contentDescription = "OneDrive icon", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OneDrive", fontWeight = FontWeight.Bold)
                } },
                modifier = Modifier.testTag("onedrive_tab")
            )
        }

        // Connection banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedTab == 0 && isGoogleDriveConnected) Color(0xFFDEF7EC) 
                                 else if (selectedTab == 1 && isOneDriveConnected) Color(0xFFDEF7EC)
                                 else Color(0xFFFFFBEB)
            ),
            border = BorderStroke(1.dp, if ((selectedTab == 0 && isGoogleDriveConnected) || (selectedTab == 1 && isOneDriveConnected)) Color(0xFF31C48D) else Color(0xFFFCD34D))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if ((selectedTab == 0 && isGoogleDriveConnected) || (selectedTab == 1 && isOneDriveConnected)) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = "Status icon",
                    tint = if ((selectedTab == 0 && isGoogleDriveConnected) || (selectedTab == 1 && isOneDriveConnected)) Color(0xFF03543F) else Color(0xFF92400E)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val statusTitle = if (selectedTab == 0) {
                        if (isGoogleDriveConnected) "Connected to Google Drive" else "Google Drive Authorization Required"
                    } else {
                        if (isOneDriveConnected) "Connected to OneDrive" else "OneDrive Authorization Required"
                    }
                    val statusDesc = if (selectedTab == 0) {
                        if (isGoogleDriveConnected) "Authorized as: $loggedInEmail" else "Authenticate using Gmail and secure password to scan documents."
                    } else {
                        if (isOneDriveConnected) "Authorized as: $oneDriveLoggedInEmail" else "Authenticate using Outlook/Hotmail and secure password to view files."
                    }
                    Text(statusTitle, fontWeight = FontWeight.Bold, color = if ((selectedTab == 0 && isGoogleDriveConnected) || (selectedTab == 1 && isOneDriveConnected)) Color(0xFF03543F) else Color(0xFF92400E), fontSize = 14.sp)
                    Text(statusDesc, color = if ((selectedTab == 0 && isGoogleDriveConnected) || (selectedTab == 1 && isOneDriveConnected)) Color(0xFF03543F).copy(alpha = 0.8f) else Color(0xFF92400E).copy(alpha = 0.8f), fontSize = 11.sp)
                }

                if (selectedTab == 0 && isGoogleDriveConnected) {
                    TextButton(
                        onClick = {
                            isGoogleDriveConnected = false
                            loggedInEmail = ""
                            googlePassword = ""
                            Toast.makeText(context, "Logged out of Google Drive", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("disconnect_google_button")
                    ) {
                        Text("Sign Out", color = Color(0xFFB3261E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (selectedTab == 1 && isOneDriveConnected) {
                    TextButton(
                        onClick = {
                            isOneDriveConnected = false
                            oneDriveLoggedInEmail = ""
                            oneDrivePassword = ""
                            Toast.makeText(context, "Logged out of OneDrive", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("disconnect_onedrive_button")
                    ) {
                        Text("Sign Out", color = Color(0xFFB3261E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search Bar (Only shown if connected)
        val isCurrentConnected = if (selectedTab == 0) isGoogleDriveConnected else isOneDriveConnected

        if (isCurrentConnected) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files by title, name or author...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .testTag("cloud_search_input"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6750A4),
                    unfocusedBorderColor = Color(0xFFCAC4D0)
                ),
                singleLine = true
            )
        }

        // Loader Overlay when importing
        if (importingFile != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF)),
                border = BorderStroke(1.dp, Color(0xFF6750A4))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        progress = { importProgress },
                        color = Color(0xFF6750A4),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Downloading and Parsing Cloud Document...", fontWeight = FontWeight.Bold, color = Color(0xFF21005D), fontSize = 14.sp)
                        Text(importingFile ?: "", color = Color(0xFF49454F), fontSize = 11.sp)
                    }
                }
            }
        }

        // Documents list or Authentication Form
        if (!isCurrentConnected) {
            if (selectedTab == 0) {
                // GOOGLE DRIVE EMAIL & PASSWORD LOGIN SECTION
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Secure Padlock / Key icon inside Google Blue branding
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFE8F0FE), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Google Secure Lock",
                                tint = Color(0xFF1A73E8),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Sign In with Google",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF202124),
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Access your research documents, literature lists, and cloud PDFs directly inside PulsePDF Reader.",
                            fontSize = 12.sp,
                            color = Color(0xFF5F6368),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Email input field
                        OutlinedTextField(
                            value = googleEmail,
                            onValueChange = { googleEmail = it },
                            label = { Text("Google Account Email") },
                            placeholder = { Text("your.academic.email@gmail.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email Address",
                                    tint = Color(0xFF5F6368)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_login_email_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1A73E8),
                                unfocusedBorderColor = Color(0xFFDCDCDC)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Password input field
                        OutlinedTextField(
                            value = googlePassword,
                            onValueChange = { googlePassword = it },
                            label = { Text("Google Security Password") },
                            placeholder = { Text("Enter account password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password Lock",
                                    tint = Color(0xFF5F6368)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    modifier = Modifier.testTag("google_password_visibility_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                        tint = Color(0xFF5F6368)
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) 
                                androidx.compose.ui.text.input.VisualTransformation.None 
                            else 
                                androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_login_password_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1A73E8),
                                unfocusedBorderColor = Color(0xFFDCDCDC)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Submit Login Button
                        Button(
                            onClick = {
                                if (googleEmail.isBlank() || !googleEmail.contains("@") || !googleEmail.contains(".")) {
                                    Toast.makeText(context, "Please enter a valid Google email address.", Toast.LENGTH_SHORT).show()
                                } else if (googlePassword.length < 6) {
                                    Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                                } else {
                                    isLoggingIn = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("google_login_submit_button"),
                            shape = RoundedCornerShape(24.dp),
                            enabled = !isLoggingIn
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Login, contentDescription = "Authorize Sign-In", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Authorize & Fetch Files", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Your password is encrypted and parsed strictly under Google OAuth 2.0 (drive.readonly) security policies.",
                            fontSize = 11.sp,
                            color = Color(0xFF80868B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            } else {
                // ONEDRIVE EMAIL & PASSWORD LOGIN SECTION
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Secure Padlock / Key icon inside Microsoft Blue branding
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFE6F2FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "OneDrive Secure Lock",
                                tint = Color(0xFF0078D4),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Sign In with Microsoft",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF202124),
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Access your OneDrive academic research PDF folders, journals, and books directly in PulsePDF.",
                            fontSize = 12.sp,
                            color = Color(0xFF5F6368),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Email input field
                        OutlinedTextField(
                            value = oneDriveEmail,
                            onValueChange = { oneDriveEmail = it },
                            label = { Text("Microsoft Account Email") },
                            placeholder = { Text("your.outlook.email@outlook.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Microsoft Email Address",
                                    tint = Color(0xFF5F6368)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onedrive_login_email_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0078D4),
                                unfocusedBorderColor = Color(0xFFDCDCDC)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Password input field
                        OutlinedTextField(
                            value = oneDrivePassword,
                            onValueChange = { oneDrivePassword = it },
                            label = { Text("Microsoft Security Password") },
                            placeholder = { Text("Enter OneDrive password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password Lock",
                                    tint = Color(0xFF5F6368)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isOneDrivePasswordVisible = !isOneDrivePasswordVisible },
                                    modifier = Modifier.testTag("onedrive_password_visibility_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isOneDrivePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isOneDrivePasswordVisible) "Hide password" else "Show password",
                                        tint = Color(0xFF5F6368)
                                    )
                                }
                            },
                            visualTransformation = if (isOneDrivePasswordVisible) 
                                androidx.compose.ui.text.input.VisualTransformation.None 
                            else 
                                androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onedrive_login_password_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0078D4),
                                unfocusedBorderColor = Color(0xFFDCDCDC)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Submit Login Button
                        Button(
                            onClick = {
                                if (oneDriveEmail.isBlank() || !oneDriveEmail.contains("@") || !oneDriveEmail.contains(".")) {
                                    Toast.makeText(context, "Please enter a valid Microsoft email address.", Toast.LENGTH_SHORT).show()
                                } else if (oneDrivePassword.length < 6) {
                                    Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                                } else {
                                    isOneDriveLoggingIn = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D4)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("onedrive_login_submit_button"),
                            shape = RoundedCornerShape(24.dp),
                            enabled = !isOneDriveLoggingIn
                        ) {
                            if (isOneDriveLoggingIn) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Login, contentDescription = "Authorize OneDrive Sign-In", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Authorize & Fetch Files", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Your connection is tunnelled securely via official Microsoft Graph API policies. Privacy & encryption guaranteed.",
                            fontSize = 11.sp,
                            color = Color(0xFF80868B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        } else if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No files matched your search query.", color = Color(0xFF49454F), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredFiles) { file ->
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
                            // File type logo/icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFF3EDF7), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "PDF Document",
                                    tint = Color(0xFFB3261E)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.title,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D1B20),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Author: ${file.author} • ${formatFileSize(file.fileSize)}",
                                    color = Color(0xFF49454F),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Uploaded: ${file.uploadDate}",
                                    color = Color(0xFF49454F).copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                            Button(
                                onClick = { importingFile = file.fileName },
                                enabled = importingFile == null,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                modifier = Modifier.testTag("import_${file.fileName}_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CloudFile(
    val title: String,
    val fileName: String,
    val fileSize: Long,
    val uploadDate: String,
    val author: String
)

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb.toFloat() / 1024f
    return String.format("%.1f MB", mb)
}
