package com.example.ui.viewer

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

/**
 * v1.8.0 (#4): single full-document WebView for Show-as-is mode.
 * Mirrors LatexView hardening: no file/content access, no DOM storage,
 * no zoom confusion; serves bundled KaTeX via WebViewAssetLoader.
 */
@Composable
fun AsIsHtmlView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler(
                    "/assets/",
                    WebViewAssetLoader.AssetsPathHandler(context),
                )
                .build()
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                setBackgroundColor(0x00000000)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): android.webkit.WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                AsIsHtmlBuilder.VIRTUAL_ORIGIN + "/",
                html,
                "text/html",
                "utf-8",
                null,
            )
        },
        modifier = modifier.fillMaxSize(),
    )
}
