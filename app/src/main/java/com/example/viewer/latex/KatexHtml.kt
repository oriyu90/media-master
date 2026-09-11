package com.example.viewer.latex

import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds the offline HTML page a [com.example.ui.viewer.LatexView] loads to
 * typeset one LaTeX expression via the bundled KaTeX assets
 * (`app/src/main/assets/katex/`). Pure string building, no Android
 * dependency, so it's unit-testable on the JVM.
 */
object KatexHtml {

    /** Virtual origin [androidx.webkit.WebViewAssetLoader] serves `assets/` under. */
    const val VIRTUAL_ORIGIN = "https://appassets.androidplatform.net/assets/katex/"

    fun build(latex: String, displayMode: Boolean, textColorHex: String): String {
        // JSON-encode to a safe JS string literal, then neutralise "</" so an
        // adversarial formula (e.g. containing literal "</script>") can't
        // close our <script> tag early — the HTML tokenizer looks for that
        // sequence regardless of JS string-quoting context. "<\/" is valid,
        // identical-at-runtime JS (an unnecessary but legal escape of '/').
        val encodedLatex = JsonPrimitive(latex).toString().replace("</", "<\\/")
        val displayModeJs = if (displayMode) "true" else "false"
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <link rel="stylesheet" href="katex.min.css">
            <script src="katex.min.js"></script>
            <script src="auto-render.min.js"></script>
            <style>
            html,body{margin:0;padding:0;background:transparent;color:$textColorHex;}
            body{font-size:16px;display:inline-block;}
            .katex-display{margin:0;}
            .katex-error{color:#cc4444;white-space:pre-wrap;font-family:monospace;font-size:13px;}
            </style>
            </head>
            <body>
            <div id="math"></div>
            <script>
            function reportSize(){
              if (window.AndroidSizeReporter) {
                AndroidSizeReporter.reportSize(document.body.scrollWidth, document.body.scrollHeight);
              }
            }
            try {
              katex.render($encodedLatex, document.getElementById('math'), {
                throwOnError: false,
                displayMode: $displayModeJs,
                trust: false,
                strict: false
              });
            } catch (e) {
              document.getElementById('math').innerText = $encodedLatex;
            }
            if (document.fonts && document.fonts.ready) {
              document.fonts.ready.then(reportSize);
            }
            window.onload = reportSize;
            setTimeout(reportSize, 60);
            setTimeout(reportSize, 250);
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}
