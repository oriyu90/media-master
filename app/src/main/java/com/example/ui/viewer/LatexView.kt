package com.example.ui.viewer

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
    var measuredWidthPx by remember(latex, displayMode) { mutableIntStateOf(0) }
    var measuredHeightPx by remember(latex, displayMode) { mutableIntStateOf(0) }

    val resolvedModifier = if (selfSizing) {
        val widthDp: Dp = with(density) { measuredWidthPx.coerceAtLeast(1).toDp() }
        val heightDp: Dp = with(density) { measuredHeightPx.coerceAtLeast(1).toDp() }
        modifier.width(widthDp).height(heightDp)
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
                                measuredWidthPx = widthPxValue
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
