package com.example.ui.viewer

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.example.viewer.latex.KatexHtml

/**
 * Renders one LaTeX expression via the offline KaTeX assets bundled at
 * `app/src/main/assets/katex/` (no network access — [WebViewAssetLoader]
 * serves everything from `file:///android_asset/katex/`).
 *
 * @param selfSizing When true (display/block math), this view grows its own
 *   height to fit the rendered content — safe inside a `Column`/`LazyColumn`.
 *   When false (inline math embedded via `InlineTextContent`), the caller
 *   controls the box size and this view only fills it; [onMeasured] still
 *   reports the natural content size so the caller can correct that box on
 *   the next layout pass.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LatexView(
    latex: String,
    displayMode: Boolean,
    modifier: Modifier = Modifier,
    selfSizing: Boolean = true,
    onMeasured: (widthPx: Int, heightPx: Int) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurface
    var measuredHeightPx by remember(latex, displayMode) { mutableIntStateOf(0) }

    // IMPORTANT: never constrain *width* to the measured value here. The WebView must be
    // given its full available width (fillMaxWidth) up front — if it starts at a near-zero
    // width (e.g. before the first measurement callback), KaTeX's inline (non-display) output
    // wraps character-by-character inside that tiny viewport, and the resulting scrollWidth
    // report then "confirms" that wrapped-narrow layout instead of correcting it. Found via
    // on-device testing of inline LaTeX in the .tex viewer (block/display math happened not to
    // exhibit this, but relying on that would be fragile). Height still grows from 0 safely,
    // since a too-small height only clips content the reportSize() callback then corrects.
    val resolvedModifier = if (selfSizing) {
        val heightDp: Dp = with(density) { measuredHeightPx.coerceAtLeast(1).toDp() }
        modifier.fillMaxWidth().height(heightDp)
    } else {
        modifier
    }

    AndroidView(
        modifier = resolvedModifier,
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.domStorageEnabled = false
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(request.url)
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun reportSize(widthCss: Float, heightCss: Float) {
                            val widthPxValue = (widthCss * resources.displayMetrics.density).toInt()
                            val heightPxValue = (heightCss * resources.displayMetrics.density).toInt()
                            Handler(Looper.getMainLooper()).post {
                                measuredHeightPx = heightPxValue
                                onMeasured(widthPxValue, heightPxValue)
                            }
                        }
                    },
                    "AndroidSizeReporter",
                )
            }
        },
        update = { webView ->
            val hex = "#%06X".format(0xFFFFFF and textColor.toArgb())
            webView.loadDataWithBaseURL(
                KatexHtml.VIRTUAL_ORIGIN,
                KatexHtml.build(latex, displayMode, hex),
                "text/html",
                "utf-8",
                null,
            )
        },
    )
}
