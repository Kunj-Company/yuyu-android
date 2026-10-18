package com.twilighty.yuyu.feature.broadcasts.preview

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

private val VIEWPORT_META = Regex("<meta[^>]+name\\s*=\\s*[\"']?viewport[\"']?[^>]*>", RegexOption.IGNORE_CASE)
private val HEAD_OPEN = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
private val IMAGE_PATH = Regex(".*\\.(png|jpe?g|gif|webp|avif|svg|bmp|ico)$", RegexOption.IGNORE_CASE)

/** Desktop clients render emails in a wide pane; the WebView is zoomed out to fit it. */
internal const val DESKTOP_VIEWPORT_WIDTH = 800

/** The server HTML with a viewport for the chosen width (any existing viewport meta is replaced). */
internal fun withViewport(html: String, width: PreviewWidth): String {
    val content = when (width) {
        PreviewWidth.Mobile -> "width=device-width, initial-scale=1"
        PreviewWidth.Desktop -> "width=$DESKTOP_VIEWPORT_WIDTH"
    }
    val meta = "<meta name=\"viewport\" content=\"$content\">"
    val cleaned = VIEWPORT_META.replace(html, "")
    val head = HEAD_OPEN.find(cleaned)
    return if (head != null) cleaned.replaceRange(head.range.last + 1, head.range.last + 1, meta) else meta + cleaned
}

/** Only https images may load; everything else (scripts, styles, frames, fonts, http) is refused. */
internal fun isAllowedSubresource(url: Uri, acceptHeader: String?): Boolean {
    if (url.scheme != "https") return false
    return acceptHeader?.contains("image/") == true || IMAGE_PATH.matches(url.path.orEmpty())
}

private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

@SuppressLint("ViewConstructor")
private class LockedDownWebView(context: Context) : WebView(context) {
    init {
        settings.apply {
            javaScriptEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            safeBrowsingEnabled = true
        }
    }
}

/**
 * Shows server-rendered email HTML with JavaScript, file and content access
 * off, and no network loads except https images. Links never navigate inside
 * the view: they are handed to [onLinkTapped] (the caller confirms, then opens
 * the external browser).
 */
@Composable
fun SafeEmailWebView(
    html: String,
    width: PreviewWidth,
    onLinkTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkHandler by rememberUpdatedState(onLinkTapped)
    val document = remember(html, width) { withViewport(html, width) }
    AndroidView(
        modifier = modifier.semantics { contentDescription = "Email preview" },
        factory = { context ->
            LockedDownWebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (url.scheme == "http" || url.scheme == "https") linkHandler(url.toString())
                        return true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url
                        if (url.scheme == "data" || url.scheme == "about") return null
                        return if (!request.isForMainFrame && isAllowedSubresource(url, request.requestHeaders["Accept"])) null else blocked()
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}
