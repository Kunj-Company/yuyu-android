package com.twilighty.yuyu.core.designsystem.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens a website page (sign-up, password reset, security settings, `/join/…`)
 * in a Custom Tab of a real browser. The browser package is set explicitly so
 * a URL on the App Links host never bounces back into this app.
 */
fun openInBrowser(context: Context, url: String, toolbarColor: Int? = null): Boolean {
    val uri: Uri = url.toUri()
    if (uri.scheme != "https" && uri.scheme != "http") return false
    val browser = CustomTabsClient.getPackageName(context, emptyList<String>())
        ?.takeIf { it != context.packageName }
    return try {
        if (browser != null) {
            val tab = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .apply {
                    if (toolbarColor != null) {
                        setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
                    }
                }
                .build()
            tab.intent.setPackage(browser)
            tab.launchUrl(context, uri)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            val target = context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .firstOrNull { it != context.packageName }
            if (target != null) intent.setPackage(target)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
