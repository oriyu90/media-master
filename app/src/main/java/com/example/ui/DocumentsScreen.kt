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
import android.provider.MediaStore
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
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    
    val selectedFiles = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedFiles.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.loadAllMedia()
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
                    0 -> ScanView(navController)
                    1 -> DocumentListView(viewState, navController, selectedFiles, isSelectionMode, excludedFolders)
                }
            }
        }
    }
}

@Composable
fun ScanView(navController: NavHostController) {
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
                                val destFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "Scanned_Appended_${System.currentTimeMillis()}.pdf")
                                appendImagesToPdf(context, appendPdfUri!!, scannedPages, Uri.fromFile(destFile), insertAfterPage)
                                Toast.makeText(context, context.getString(R.string.appended_to_documents), Toast.LENGTH_LONG).show()
                            } else if (scannedPdfUri != null) {
                                // Save as new
                                val destFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "Scanned_${System.currentTimeMillis()}.pdf")
                                context.contentResolver.openInputStream(scannedPdfUri!!)?.use { input ->
                                    FileOutputStream(destFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Toast.makeText(context, context.getString(R.string.saved_new_pdf), Toast.LENGTH_LONG).show()
                            }
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
                TextButton(onClick = { 
                    showAppendDialog = false 
                    appendPdfUri = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
            // Find all PDFs
            val pdfFiles = viewState.files.filter { 
                it.mimeType == "application/pdf" && 
                !excludedFolders.any { excluded -> it.path.startsWith(excluded) }
            }.sortedByDescending { it.dateModified }
            
            if (pdfFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_pdf_documents))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(pdfFiles) { index, file ->
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
                                    // Open in viewer
                                    navController.navigate("viewer/${Uri.encode(file.path)}")
                                }
                            }
                        )
                        if (index < pdfFiles.lastIndex) {
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
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
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
