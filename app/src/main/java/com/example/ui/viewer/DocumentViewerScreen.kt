package com.example.ui.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.FileViewModel
import com.example.R
import com.example.office.DocBlock
import com.example.office.DocRun
import com.example.office.OoxmlDocumentReader
import com.example.office.OoxmlSlideReader
import com.example.office.SlideRun
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.Hallmark
import com.example.viewer.CsvParser
import com.example.viewer.HexFormatter
import com.example.viewer.HexPageReader
import com.example.viewer.JsonPretty
import com.example.viewer.LatexSourceParser
import com.example.viewer.MarkdownParser
import com.example.viewer.TexSegment
import com.example.viewer.MdBlock
import com.example.viewer.MdInline
import com.example.viewer.PdfPageRenderer
import com.example.viewer.TextCharsetReader
import com.example.viewer.ViewerKind
import com.example.viewer.ViewerKindClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal viewer for non-media documents: text/log, CSV/TSV, JSON, Markdown,
 * PDF, `.docx`/`.pptx` and legacy `.doc`/`.ppt`, and a hex/ASCII fallback for
 * anything else. Deliberately separate from [com.example.ViewerScreen] (image/
 * video/audio) so that screen's hardened pager/OCR/player code is untouched.
 *
 * Accepts a raw URI string rather than a path so it can display files Media
 * Master doesn't own the index for — e.g. a `content://` URI handed to us by
 * another app via `ACTION_VIEW`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    uriString: String?,
    path: String?,
    viewModel: FileViewModel?,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val uri = remember(uriString) { uriString?.let { runCatching { Uri.parse(it) }.getOrNull() } }
    if (uri == null) {
        EmptyState(
            icon = Icons.Default.BrokenImage,
            title = stringResource(R.string.invalid_file_path),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val displayName = remember(uri, path) { path?.let { File(it).name } ?: resolveDisplayName(context, uri) }
    val mimeType = remember(uri, displayName) {
        context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
    }
    val kind = remember(displayName, mimeType) { ViewerKindClassifier.classify(displayName, mimeType) }

    // Delete is only offered when the caller told us the real on-device path
    // (Files/Documents/Category screens pass it) — never for an arbitrary
    // external content:// URI another app handed us via ACTION_VIEW, since
    // we have no business deleting storage we don't manage.
    val canDelete = path != null && viewModel != null
    var confirmDelete by remember(uri) { mutableStateOf(false) }

    val openExternally: () -> Unit = {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType?.takeUnless { it.isBlank() } ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, displayName)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = openExternally) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.doc_open_externally))
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeType?.takeUnless { it.isBlank() } ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_media))) }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                    if (canDelete) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (confirmDelete && path != null && viewModel != null) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.delete),
                message = displayName,
                confirmLabel = stringResource(R.string.delete),
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = { confirmDelete = false },
                onConfirm = {
                    confirmDelete = false
                    coroutineScope.launch {
                        viewModel.deleteFile(path, uri)
                        navController.popBackStack()
                    }
                },
            )
        }
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (kind) {
                ViewerKind.TEXT -> TextViewerBody(context, uri)
                ViewerKind.CSV -> CsvViewerBody(context, uri)
                ViewerKind.JSON -> JsonViewerBody(context, uri)
                ViewerKind.MARKDOWN -> MarkdownViewerBody(context, uri)
                ViewerKind.LATEX_SOURCE -> LatexSourceBody(context, uri)
                ViewerKind.PDF -> PdfViewerBody(context, uri)
                ViewerKind.DOCX -> DocxViewerBody(context, uri)
                ViewerKind.PPTX -> PptxViewerBody(context, uri)
                ViewerKind.EXTERNAL_ONLY -> ExternalOnlyBody(onOpenExternally = openExternally)
                ViewerKind.HEX -> HexViewerBody(context, uri)
            }
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    if (uri.scheme == "file") return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    val queried = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull()
    return queried ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

// ---------------------------------------------------------------------------
// Shared async-load helper
// ---------------------------------------------------------------------------

private sealed class Loadable<out T> {
    data object Loading : Loadable<Nothing>()
    data class Ready<T>(val value: T) : Loadable<T>()
    data object Failed : Loadable<Nothing>()
}

@Composable
private fun <T> rememberLoadable(vararg keys: Any?, loader: suspend () -> T?): Loadable<T> {
    var state by remember(*keys) { mutableStateOf<Loadable<T>>(Loadable.Loading) }
    LaunchedEffect(*keys) {
        state = Loadable.Loading
        val result = withContext(Dispatchers.IO) { runCatching { loader() }.getOrNull() }
        state = if (result != null) Loadable.Ready(result) else Loadable.Failed
    }
    return state
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun TruncatedBanner(onLoadMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Hallmark.ContentEdge, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.doc_truncated_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onLoadMore) { Text(stringResource(R.string.doc_load_more)) }
    }
}

// ---------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------

@Composable
private fun TextViewerBody(context: Context, uri: Uri) {
    var maxBytes by remember(uri) { mutableStateOf(TextCharsetReader.DEFAULT_MAX_BYTES) }
    when (val loadable = rememberLoadable(uri, maxBytes) { TextCharsetReader.read(context, uri, maxBytes) }) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val result = loadable.value
            Column(Modifier.fillMaxSize()) {
                if (result.truncated) TruncatedBanner(onLoadMore = { maxBytes *= 4 })
                SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(
                        text = result.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(Hallmark.ContentEdge),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CSV / TSV
// ---------------------------------------------------------------------------

@Composable
private fun CsvViewerBody(context: Context, uri: Uri) {
    var maxBytes by remember(uri) { mutableStateOf(10 * 1024 * 1024) }
    when (
        val loadable = rememberLoadable(uri, maxBytes) {
            TextCharsetReader.read(context, uri, maxBytes)?.let { it to CsvParser.parse(it.text) }
        }
    ) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val (readResult, table) = loadable.value
            Column(Modifier.fillMaxSize()) {
                if (readResult.truncated || table.rowsTruncated) TruncatedBanner(onLoadMore = { maxBytes *= 4 })
                val horizontalScrollState = rememberScrollState()
                val columnCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
                if (columnCount == 0) {
                    EmptyState(icon = Icons.Default.Description, title = stringResource(R.string.doc_empty), modifier = Modifier.fillMaxSize())
                } else {
                    val columnWidth = 140.dp
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)
                            .padding(horizontal = Hallmark.ContentEdge, vertical = 8.dp),
                    ) {
                        repeat(columnCount) { col ->
                            Text(
                                text = table.headers.getOrElse(col) { "" },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(columnWidth).padding(end = 8.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(table.rows.size, key = { it }) { rowIndex ->
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)
                                    .padding(horizontal = Hallmark.ContentEdge, vertical = 6.dp),
                            ) {
                                repeat(columnCount) { col ->
                                    Text(
                                        text = table.rows[rowIndex].getOrElse(col) { "" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(columnWidth).padding(end = 8.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// JSON
// ---------------------------------------------------------------------------

@Composable
private fun JsonViewerBody(context: Context, uri: Uri) {
    when (val loadable = rememberLoadable(uri) { TextCharsetReader.read(context, uri, 8 * 1024 * 1024) }) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val raw = loadable.value.text
            val pretty = remember(raw) { JsonPretty.format(raw) }
            var showSource by remember(uri) { mutableStateOf(pretty == null) }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Hallmark.ContentEdge, vertical = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pretty == null) {
                        Text(
                            text = stringResource(R.string.doc_json_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        androidx.compose.foundation.layout.Spacer(Modifier)
                    }
                    if (pretty != null) {
                        TextButton(onClick = { showSource = !showSource }) {
                            Text(stringResource(if (showSource) R.string.doc_view_rendered else R.string.doc_view_source))
                        }
                    }
                }
                SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (showSource || pretty == null) raw else pretty,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(horizontal = Hallmark.ContentEdge, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Markdown
// ---------------------------------------------------------------------------

@Composable
private fun MarkdownViewerBody(context: Context, uri: Uri) {
    when (val loadable = rememberLoadable(uri) { TextCharsetReader.read(context, uri, 4 * 1024 * 1024) }) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val raw = loadable.value.text
            var showSource by remember(uri) { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Hallmark.ContentEdge, vertical = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    TextButton(onClick = { showSource = !showSource }) {
                        Text(stringResource(if (showSource) R.string.doc_view_rendered else R.string.doc_view_source))
                    }
                }
                if (showSource) {
                    SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = Hallmark.ContentEdge),
                        )
                    }
                } else {
                    val blocks = remember(raw) { MarkdownParser.parse(raw) }
                    if (blocks.isEmpty()) {
                        EmptyState(icon = Icons.Default.Description, title = stringResource(R.string.doc_empty), modifier = Modifier.fillMaxSize())
                    } else {
                        val linkColor = MaterialTheme.colorScheme.primary
                        val mathSizeCache = remember(uri) { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.unit.IntSize>() }
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Hallmark.ContentEdge)) {
                            items(blocks.size) { i -> MarkdownBlockView(blocks[i], linkColor, mathSizeCache) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MdBlock,
    linkColor: androidx.compose.ui.graphics.Color,
    mathSizeCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, androidx.compose.ui.unit.IntSize>,
) {
    when (block) {
        is MdBlock.Heading -> {
            val (text, inlineContent) = rememberMarkdownInlineContent(block.inline, linkColor, mathSizeCache)
            Text(
                text = text,
                inlineContent = inlineContent,
                style = when (block.level) {
                    1 -> MaterialTheme.typography.headlineMedium
                    2 -> MaterialTheme.typography.headlineSmall
                    3 -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.titleMedium
                },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        is MdBlock.Paragraph -> {
            val (text, inlineContent) = rememberMarkdownInlineContent(block.inline, linkColor, mathSizeCache)
            Text(
                text = text,
                inlineContent = inlineContent,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        is MdBlock.MathBlock -> Box(
            // NOTE: deliberately no horizontalScroll here — that gives its child an unbounded
            // width constraint, which defeats LatexView's internal fillMaxWidth() and makes
            // KaTeX wrap its own output character-by-character instead of laying out normally.
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            LatexView(latex = block.latex, displayMode = true, selfSizing = true)
        }
        is MdBlock.CodeBlock -> androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Hallmark.RadiusSmall),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(
                text = block.code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
            )
        }
        is MdBlock.BulletListBlock -> Column(Modifier.padding(start = 16.dp)) {
            block.items.forEach { item ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                    Column { item.forEach { MarkdownBlockView(it, linkColor, mathSizeCache) } }
                }
            }
        }
        is MdBlock.OrderedListBlock -> Column(Modifier.padding(start = 16.dp)) {
            block.items.forEachIndexed { i, item ->
                Row {
                    Text("${block.startNumber + i}.  ", style = MaterialTheme.typography.bodyLarge)
                    Column { item.forEach { MarkdownBlockView(it, linkColor, mathSizeCache) } }
                }
            }
        }
        is MdBlock.Quote -> androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Hallmark.RadiusSmall),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(Modifier.padding(12.dp)) { block.blocks.forEach { MarkdownBlockView(it, linkColor, mathSizeCache) } }
        }
        MdBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        is MdBlock.ImageBlock -> Text(
            text = "[${block.alt.ifBlank { block.url }}]",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

/**
 * Builds the paragraph/heading [AnnotatedString] plus its `inlineContent` map
 * (one entry per [MdInline.Math] span, each a tiny self-measuring [LatexView]
 * placed via Compose's `InlineTextContent`). Sizes come from [mathSizeCache]
 * once KaTeX has measured a given formula; before that, a rough
 * character-count heuristic avoids a zero-size placeholder popping in — the
 * cache means the correction only has to happen once per distinct formula.
 */
@Composable
private fun rememberMarkdownInlineContent(
    inline: List<MdInline>,
    linkColor: androidx.compose.ui.graphics.Color,
    mathSizeCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, androidx.compose.ui.unit.IntSize>,
): Pair<AnnotatedString, Map<String, androidx.compose.foundation.text.InlineTextContent>> {
    val density = LocalDensity.current
    val inlineContentMap = remember(inline) { mutableMapOf<String, androidx.compose.foundation.text.InlineTextContent>() }
    var mathCounter = 0
    val text = buildAnnotatedString {
        fun appendNodes(nodes: List<MdInline>) {
            nodes.forEach { node ->
                when (node) {
                    is MdInline.PlainText -> append(node.text)
                    is MdInline.Bold -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        appendNodes(node.children)
                        pop()
                    }
                    is MdInline.Italic -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        appendNodes(node.children)
                        pop()
                    }
                    is MdInline.InlineCode -> {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                        append(node.text)
                        pop()
                    }
                    is MdInline.LinkText -> {
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        appendNodes(node.children)
                        pop()
                    }
                    MdInline.LineBreak -> append("\n")
                    is MdInline.Math -> {
                        val id = "math_${mathCounter++}"
                        val cacheKey = "inline|${node.latex}"
                        val cachedSize = mathSizeCache[cacheKey]
                        val (widthSp, heightSp) = with(density) {
                            if (cachedSize != null) {
                                cachedSize.width.toDp().toSp() to cachedSize.height.toDp().toSp()
                            } else {
                                val guessWidthDp = (node.latex.length.coerceIn(1, 24) * 7).dp
                                guessWidthDp.toSp() to 20.dp.toSp()
                            }
                        }
                        appendInlineContent(id, node.latex)
                        inlineContentMap[id] = androidx.compose.foundation.text.InlineTextContent(
                            androidx.compose.ui.text.Placeholder(
                                width = widthSp,
                                height = heightSp,
                                placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
                            ),
                        ) {
                            LatexView(
                                latex = node.latex,
                                displayMode = false,
                                selfSizing = false,
                                modifier = Modifier.fillMaxSize(),
                                onMeasured = { w, h -> mathSizeCache[cacheKey] = androidx.compose.ui.unit.IntSize(w, h) },
                            )
                        }
                    }
                }
            }
        }
        appendNodes(inline)
    }
    return text to inlineContentMap
}

// ---------------------------------------------------------------------------
// LaTeX source (.tex) — not compiled (impractical on-device); math regions
// are typeset with KaTeX, everything else shown verbatim as source text.
// ---------------------------------------------------------------------------

@Composable
private fun LatexSourceBody(context: Context, uri: Uri) {
    when (val loadable = rememberLoadable(uri) { TextCharsetReader.read(context, uri, 4 * 1024 * 1024) }) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val raw = loadable.value.text
            val segments = remember(raw) { LatexSourceParser.parse(raw) }
            if (segments.isEmpty()) {
                EmptyState(icon = Icons.Default.Description, title = stringResource(R.string.doc_empty), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = Hallmark.ContentEdge)) {
                    items(segments.size) { i ->
                        when (val segment = segments[i]) {
                            is TexSegment.PlainText -> if (segment.text.isNotBlank()) {
                                SelectionContainer {
                                    Text(
                                        text = segment.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                            is TexSegment.Math -> Box(
                                // See the equivalent MdBlock.MathBlock note: no horizontalScroll —
                                // it gives the child unbounded width and breaks KaTeX's layout.
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                contentAlignment = if (segment.displayMode) Alignment.Center else Alignment.CenterStart,
                            ) {
                                LatexView(latex = segment.latex, displayMode = segment.displayMode, selfSizing = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PDF
// ---------------------------------------------------------------------------

@Composable
private fun PdfViewerBody(context: Context, uri: Uri) {
    when (val loadable = rememberLoadable(uri) { PdfPageRenderer.open(context, uri) }) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val renderer = loadable.value
            DisposableEffect(renderer) { onDispose { renderer.close() } }
            if (renderer.pageCount <= 0) {
                EmptyState(icon = Icons.Default.Description, title = stringResource(R.string.doc_empty), modifier = Modifier.fillMaxSize())
            } else {
                val pagerState = rememberPagerState(pageCount = { renderer.pageCount })
                val density = LocalDensity.current
                var isZoomedIn by remember { mutableStateOf(false) }
                LaunchedEffect(pagerState.currentPage) { isZoomedIn = false }
                Column(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.fillMaxSize().weight(1f)) {
                        val widthPx = with(density) { maxWidth.toPx().toInt() }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !isZoomedIn,
                        ) { page ->
                            var bitmap by remember(page, widthPx) { mutableStateOf<Bitmap?>(null) }
                            LaunchedEffect(page, widthPx) {
                                bitmap = withContext(Dispatchers.Default) { renderer.renderPage(page, widthPx) }
                            }
                            DisposableEffect(page, widthPx) {
                                onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
                            }
                            com.example.ui.components.ZoomableBox(
                                modifier = Modifier.fillMaxSize(),
                                onZoomChanged = { if (page == pagerState.currentPage) isZoomedIn = it },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val current = bitmap
                                    if (current != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = current.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${renderer.pageCount}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DOCX
// ---------------------------------------------------------------------------

@Composable
private fun DocxViewerBody(context: Context, uri: Uri) {
    when (val loadable = rememberLoadable(uri) { OoxmlDocumentReader.read(context, uri) }) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val doc = loadable.value
            if (doc.blocks.isEmpty()) {
                EmptyState(icon = Icons.Default.Description, title = stringResource(R.string.doc_empty), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(Hallmark.ContentEdge)) {
                    items(doc.blocks.size) { i ->
                        when (val block = doc.blocks[i]) {
                            is DocBlock.Paragraph -> Text(
                                text = docRunsToAnnotatedString(block.runs),
                                style = when (block.headingLevel) {
                                    1 -> MaterialTheme.typography.headlineMedium
                                    2 -> MaterialTheme.typography.headlineSmall
                                    null -> MaterialTheme.typography.bodyLarge
                                    else -> MaterialTheme.typography.titleMedium
                                },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            is DocBlock.ImageRef -> InlineDecodedImage(doc.imageBytesByPath[block.mediaPath])
                        }
                    }
                }
            }
        }
    }
}

private fun docRunsToAnnotatedString(runs: List<DocRun>): AnnotatedString = buildAnnotatedString {
    runs.forEach { run ->
        pushStyle(SpanStyle(fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal))
        append(run.text)
        pop()
    }
}

@Composable
private fun InlineDecodedImage(bytes: ByteArray?) {
    if (bytes == null) return
    val bitmap = remember(bytes) { runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
    bitmap?.let {
        com.example.ui.components.ZoomableBox(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PPTX
// ---------------------------------------------------------------------------

@Composable
private fun PptxViewerBody(context: Context, uri: Uri) {
    when (val loadable = rememberLoadable(uri) { OoxmlSlideReader.read(context, uri) }) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val deck = loadable.value
            if (deck.slides.isEmpty()) {
                EmptyState(icon = Icons.Default.Description, title = stringResource(R.string.doc_empty), modifier = Modifier.fillMaxSize())
            } else {
                val pagerState = rememberPagerState(pageCount = { deck.slides.size })
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        val slide = deck.slides[page]
                        LazyColumn(Modifier.fillMaxSize().padding(Hallmark.ContentEdge)) {
                            items(slide.images.size) { i -> InlineDecodedImage(deck.imageBytesByPath[slide.images[i]]) }
                            items(slide.paragraphs.size) { i ->
                                Text(
                                    text = slideRunsToAnnotatedString(slide.paragraphs[i]),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${deck.slides.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun slideRunsToAnnotatedString(runs: List<SlideRun>): AnnotatedString = buildAnnotatedString {
    runs.forEach { run ->
        pushStyle(SpanStyle(fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal))
        append(run.text)
        pop()
    }
}

// ---------------------------------------------------------------------------
// Legacy .doc / .ppt (no safe in-app renderer — see ViewerKind.EXTERNAL_ONLY)
// ---------------------------------------------------------------------------

@Composable
private fun ExternalOnlyBody(onOpenExternally: () -> Unit) {
    EmptyState(
        icon = Icons.Default.Description,
        title = stringResource(R.string.doc_unsupported_title),
        description = stringResource(R.string.doc_unsupported_desc),
        actionLabel = stringResource(R.string.doc_open_externally),
        onAction = onOpenExternally,
        modifier = Modifier.fillMaxSize(),
    )
}

// ---------------------------------------------------------------------------
// Binary hex/ASCII fallback
// ---------------------------------------------------------------------------

@Composable
private fun HexViewerBody(context: Context, uri: Uri) {
    when (val loadable = rememberLoadable(uri) { HexPageReader.open(context, uri) }) {
        Loadable.Loading -> CenteredProgress()
        Loadable.Failed -> ErrorState(message = stringResource(R.string.doc_parse_error), modifier = Modifier.fillMaxSize())
        is Loadable.Ready -> {
            val reader = loadable.value
            DisposableEffect(reader) { onDispose { reader.close() } }
            val pageCount = ((reader.size + HexPageReader.PAGE_SIZE - 1) / HexPageReader.PAGE_SIZE)
                .toInt().coerceAtLeast(1)
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = Hallmark.ContentEdge)) {
                items(pageCount, key = { it }) { pageIndex ->
                    var lines by remember(pageIndex) { mutableStateOf<List<String>?>(null) }
                    LaunchedEffect(pageIndex) {
                        lines = withContext(Dispatchers.IO) {
                            val bytes = reader.readPage(pageIndex)
                            HexFormatter.formatLines(bytes, pageIndex.toLong() * HexPageReader.PAGE_SIZE)
                        }
                    }
                    Column {
                        val currentLines = lines
                        if (currentLines == null) {
                            Text("…", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        } else {
                            currentLines.forEach { line ->
                                Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
