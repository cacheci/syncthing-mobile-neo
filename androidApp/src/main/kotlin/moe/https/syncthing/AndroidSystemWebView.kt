package moe.https.syncthing

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.net.toUri

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AndroidSystemWebView(
    url: String,
    username: String,
    password: String,
    reloadToken: Int,
    onScroll: (deltaY: Float, isAtTop: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    key(url) {
        var webView by remember { mutableStateOf<WebView?>(null) }
        var canGoBack by remember { mutableStateOf(false) }
        val appliedReloadToken = remember { intArrayOf(reloadToken) }
        val currentOnScroll by rememberUpdatedState(onScroll)
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        BackHandler(enabled = canGoBack) {
            webView?.goBack()
        }

        DisposableEffect(lifecycleOwner, webView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> webView?.onResume()
                    Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                val container = FrameLayout(context)
                val progress = ProgressBar(context).apply {
                    isIndeterminate = true
                }
                val errorMessage = TextView(context).apply {
                    text = "WebUI 加载失败"
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                }
                val retryButton = Button(context).apply {
                    text = "重试"
                }
                val errorPanel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    addView(
                        errorMessage,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                    addView(
                        retryButton,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = 12.dp(context)
                        },
                    )
                }
                val systemWebView = WebView(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                        currentOnScroll(
                            (scrollY - oldScrollY).toFloat(),
                            scrollY == 0,
                        )
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            errorPanel.visibility = View.GONE
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            progress.visibility = View.GONE
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            if (!request.isForMainFrame || request.url.hasSameOrigin(url.toUri())) {
                                return false
                            }
                            return openExternalUrl(view, request.url)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                showError(errorPanel, errorMessage, error.description.toString())
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                showError(
                                    errorPanel,
                                    errorMessage,
                                    "HTTP ${errorResponse.statusCode}",
                                )
                            }
                        }

                        override fun onReceivedHttpAuthRequest(
                            view: WebView,
                            handler: HttpAuthHandler,
                            host: String,
                            realm: String,
                        ) {
                            if (
                                host.equals(url.toUri().host, ignoreCase = true) &&
                                username.isNotBlank() &&
                                password.isNotBlank()
                            ) {
                                handler.proceed(username, password)
                            } else {
                                handler.cancel()
                            }
                        }
                    }
                    loadUrl(url)
                }
                retryButton.setOnClickListener {
                    errorPanel.visibility = View.GONE
                    systemWebView.reload()
                }
                container.addView(
                    systemWebView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                container.addView(
                    progress,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
                container.addView(
                    errorPanel,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                webView = systemWebView
                container
            },
            update = {
                when {
                    webView?.url != url -> webView?.loadUrl(url)
                    appliedReloadToken[0] != reloadToken -> webView?.reload()
                }
                appliedReloadToken[0] = reloadToken
            },
            onRelease = {
                webView?.apply {
                    stopLoading()
                    setOnScrollChangeListener(null)
                    webChromeClient = null
                    webViewClient = WebViewClient()
                    removeAllViews()
                    destroy()
                }
            },
        )
    }
}

private fun showError(
    errorPanel: View,
    errorMessage: TextView,
    detail: String,
) {
    errorMessage.text = "WebUI 加载失败\n$detail"
    errorPanel.visibility = View.VISIBLE
}

private fun openExternalUrl(view: WebView, uri: Uri): Boolean {
    if (
        !uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)
    ) {
        return true
    }
    return runCatching {
        view.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    }.getOrDefault(true)
}

private fun Uri.hasSameOrigin(other: Uri): Boolean =
    scheme.equals(other.scheme, ignoreCase = true) &&
        host.equals(other.host, ignoreCase = true) &&
        effectivePort() == other.effectivePort()

private fun Uri.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> 443
    else -> 80
}

private fun Int.dp(context: android.content.Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
