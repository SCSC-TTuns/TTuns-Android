package com.ttuns.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WebTimetableScreen(
    url: String,
    forceDefaults: Boolean,
    defaultYear: Int,
    defaultSemesterValue: String
) {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.background.toArgb()
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .consumeWindowInsets(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            ),
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(bg)
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "document.documentElement.style.background='transparent';" +
                                    "document.body.style.background='transparent';",
                            null
                        )
                    }
                }

                val finalUrl =
                    if (forceDefaults)
                        "$url?year=$defaultYear&semester=$defaultSemesterValue"
                    else url

                loadUrl(finalUrl)
                webView = this
            }
        },
        onRelease = { webView = null }
    )
}
