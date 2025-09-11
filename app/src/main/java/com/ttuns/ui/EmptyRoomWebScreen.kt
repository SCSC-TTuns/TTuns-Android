package com.ttuns.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun EmptyRoomWebScreen(
    url: String, // 예: "https://ttuns.vercel.app/snutt/empty" (실제 경로로 전달)
) {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.background.toArgb()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding() // 하단 네비게이션 바 영역 확보
            .imePadding()
            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(bg) // WebView 자체 배경을 테마 배경과 동일하게
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // 문서/바디 배경을 투명으로 강제 (페이지가 검은 배경일 때 덮어쓰기)
                        view?.evaluateJavascript(
                            "document.documentElement.style.background='transparent';" +
                                    "document.body.style.background='transparent';", null
                        )
                    }
                }
                loadUrl(url)
                webViewRef = this
            }
        },
        onRelease = { webViewRef = null }
    )
}
