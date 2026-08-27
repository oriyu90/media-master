package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.FileViewModel
import com.example.MediaFile
import com.example.R
import com.example.ViewMode
import com.example.ViewState
import com.example.openMediaFile
import com.example.ui.components.SortViewMenu
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentsScreen(viewModel: FileViewModel, navController: NavHostController) {
    val context = LocalContext.current
    val viewState by viewModel.documentsState.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    
    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.loadDocuments()
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedFiles.size} ${stringResource(R.string.selected)}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedFiles.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            val uris = selectedFiles.mapNotNull { path ->
                                (viewState as? ViewState.Success)?.files?.find { it.path == path }?.contentUri
                            }
                            if (uris.isNotEmpty()) {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_media)))
                            }
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.documents)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        SortViewMenu(viewModel = viewModel)
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.scan)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.document_list)) }
                )
            }
            
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> ScanView(navController, viewModel::loadDocuments)
                    1 -> DocumentListView(viewState, navController, selectedFiles, isSelectionMode, excludedFolders)
                }
            }
        }
    }
}

@Composable
fun ScanView(navController: NavHostController, onDocumentsChanged: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity ?: return
    val coroutineScope = rememberCoroutineScope()
    
    var scannedPages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var scannedPdfUri by remember { mutableStateOf<Uri?>(null) }
    
    var showAppendDialog by remember { mutableStateOf(false) }
    var appendPdfUri by remember { mutableStateOf<Uri?>(null) }
    var insertAfterPage by remember { mutableIntStateOf(-1) }
    var maxPages by remember { mutableIntStateOf(0) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scannedPages = scanResult?.pages?.map { it.imageUri } ?: emptyList()
            scannedPdfUri = scanResult?.pdf?.uri
            
            if (scannedPages.isNotEmpty()) {
                // By default, ML Kit creates a PDF. We can save it.
                // Prompt user to save as new or append.
                showAppendDialog = true
            }
        }
    }
    
    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            appendPdfUri = uri
            // Get max pages
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            val renderer = PdfRenderer(pfd)
                            maxPages = renderer.pageCount
                            renderer.close()
                            pfd.close()
                            insertAfterPage = maxPages
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.scan), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.document_scan_desc), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                    )
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, context.getString(R.string.scanner_failed, it.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.start_new_scan))
        }
    }
    
    if (showAppendDialog) {
        AlertDialog(
            onDismissRequest = { showAppendDialog = false },
            title = { Text(stringResource(R.string.save_scanned_document)) },
            text = {
                Column {
                    Text(stringResource(R.string.scanned_pages_question, scannedPages.size))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (appendPdfUri != null) {
                        Text(stringResource(R.string.selected_pdf, appendPdfUri?.lastPathSegment ?: ""))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.insert_after_page_help, maxPages))
                        Slider(
                            value = insertAfterPage.toFloat(),
                            onValueChange = { insertAfterPage = it.toInt() },
                            valueRange = 0f..maxPages.toFloat(),
                            steps = if (maxPages > 0) maxPages - 1 else 0
                        )
                        Text(stringResource(R.string.current_insert_page, insertAfterPage), style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(onClick = { pickPdfLauncher.launch("application/pdf") }) {
                            Text(stringResource(R.string.select_pdf_to_append))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        try {
                            if (appendPdfUri != null) {
                                // Append to existing
                                val destination = createPublicDocument(context, "Scanned_Appended_${System.currentTimeMillis()}.pdf", "application/pdf")
                                appendImagesToPdf(context, appendPdfUri!!, scannedPages, destination, insertAfterPage)
                                Toast.makeText(context, context.getString(R.string.appended_to_documents), Toast.LENGTH_LONG).show()
                            } else if (scannedPdfUri != null) {
                                // Save as new
                                val destination = createPublicDocument(context, "Scanned_${System.currentTimeMillis()}.pdf", "application/pdf")
                                context.contentResolver.openInputStream(scannedPdfUri!!)?.use { input ->
                                    context.contentResolver.openOutputStream(destination)?.use(input::copyTo)
                                }
                                Toast.makeText(context, context.getString(R.string.saved_new_pdf), Toast.LENGTH_LONG).show()
                            }
                            onDocumentsChanged()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.error_saving_pdf, e.message ?: ""), Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                        }
                        showAppendDialog = false
                        appendPdfUri = null
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            try {
                                saveScannedJpegs(context, scannedPages)
                                onDocumentsChanged()
                                Toast.makeText(context, context.getString(R.string.exported_jpg_pages), Toast.LENGTH_LONG).show()
                                showAppendDialog = false
                                appendPdfUri = null
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.error_saving_pdf, e.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Text(stringResource(R.string.export_as_jpg))
                    }
                    TextButton(onClick = {
                        showAppendDialog = false
                        appendPdfUri = null
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

private fun createPublicDocument(context: Context, displayName: String, mimeType: String): Uri {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOCUMENTS}/Media Master")
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Media Master")
            directory.mkdirs()
            @Suppress("DEPRECATION")
            put(MediaStore.MediaColumns.DATA, File(directory, displayName).absolutePath)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Files.getContentUri("external")
    }
    val uri = context.contentResolver.insert(collection, values)
        ?: throw IllegalStateException("Could not create document")
    return uri
}

private suspend fun saveScannedJpegs(context: Context, pages: List<Uri>) = withContext(Dispatchers.IO) {
    pages.forEachIndexed { index, pageUri ->
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Scanned_${System.currentTimeMillis()}_${index + 1}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/Media Master Scans")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Media Master Scans")
                directory.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, File(directory, "Scanned_${System.currentTimeMillis()}_${index + 1}.jpg").absolutePath)
            }
        }
        val destination = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create JPEG")
        context.contentResolver.openInputStream(pageUri)?.use { input ->
            context.contentResolver.openOutputStream(destination)?.use(input::copyTo)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(destination, values, null, null)
        }
    }
}

suspend fun appendImagesToPdf(context: Context, sourcePdfUri: Uri, imageUris: List<Uri>, destPdfUri: Uri, insertAfterPage: Int) {
    withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(sourcePdfUri, "r") ?: throw Exception(context.getString(R.string.could_not_open_source_pdf))
        val pdfRenderer = PdfRenderer(pfd)
        
        val pdfDocument = PdfDocument()
        
        val renderExistingPage = { pageIndex: Int -> 
            val page = pdfRenderer.openPage(pageIndex)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            // White background
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pdfDocument.pages.size + 1).create()
            val docPage = pdfDocument.startPage(pageInfo)
            docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(docPage)
        }
        
        val renderNewImages = {
            for (imageUri in imageUris) {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                }
                
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pdfDocument.pages.size + 1).create()
                val docPage = pdfDocument.startPage(pageInfo)
                // White background
                docPage.canvas.drawColor(android.graphics.Color.WHITE)
                docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(docPage)
            }
        }
        
        val totalPages = pdfRenderer.pageCount
        
        for (i in 0 until totalPages) {
            if (i == insertAfterPage) {
                renderNewImages()
            }
            renderExistingPage(i)
        }
        
        if (insertAfterPage >= totalPages) {
            renderNewImages()
        }
        
        pdfRenderer.close()
        pfd.close()
        
        context.contentResolver.openOutputStream(destPdfUri)?.use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }
}

@Composable
fun DocumentListView(viewState: ViewState, navController: NavHostController, selectedFiles: MutableList<String>, isSelectionMode: Boolean, excludedFolders: Set<String>) {
    val context = LocalContext.current
    when (viewState) {
        is ViewState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ViewState.Success -> {
            val documentFiles = viewState.files.filter {
                !excludedFolders.any { excluded -> it.path.startsWith(excluded) }
            }.sortedByDescending { it.dateModified }
            
            if (documentFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_pdf_documents))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(documentFiles) { index, file ->
                        val isSelected = selectedFiles.contains(file.path)
                        DocumentListRow(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onToggleSelect = { if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path) },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedFiles.remove(file.path) else selectedFiles.add(file.path)
                                } else {
                                    openMediaFile(context, file, navController)
                                }
                            }
                        )
                        if (index < documentFiles.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        is ViewState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(viewState.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListRow(file: MediaFile, isSelected: Boolean, isSelectionMode: Boolean, onToggleSelect: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(-1) }
    
    LaunchedEffect(file.path) {
        if (file.mimeType != "application/pdf" && !file.name.endsWith(".pdf", ignoreCase = true)) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                if (file.contentUri != null) {
                    val pfd = context.contentResolver.openFileDescriptor(file.contentUri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        pageCount = renderer.pageCount
                        renderer.close()
                        pfd.close()
                    }
                } else {
                    val pfd = android.os.ParcelFileDescriptor.open(File(file.path), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount
                    renderer.close()
                    pfd.close()
                }
            } catch (e: Exception) {
                // Ignore errors reading pdf
            }
        }
    }

    ListItem(
        headlineContent = { Text(file.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        supportingContent = { 
            val dateString = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(file.dateModified))
            val pageString = if (pageCount > 0) stringResource(R.string.page_count, pageCount) else stringResource(R.string.pdf_document)
            Text("$dateString • $pageString") 
        },
        leadingContent = { 
            Icon(
                if (file.mimeType == "application/pdf" || file.name.endsWith(".pdf", ignoreCase = true)) Icons.Default.PictureAsPdf else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onToggleSelect() }
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    )
}
