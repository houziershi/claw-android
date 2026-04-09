package com.openclaw.agent.ui.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.ComponentActivity
import com.openclaw.agent.R
import com.openclaw.agent.core.web.cookie.CookieVault
import com.openclaw.agent.core.web.cookie.SiteConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "WebLoginActivity"

/**
 * Generic WebView login for any supported site.
 *
 * Launch with:
 *   intent.putExtra(EXTRA_SITE, "bilibili")
 *
 * Flow:
 * 1. Load the site's login URL in WebView
 * 2. User logs in normally
 * 3. On each page load, check cookies for required login tokens
 * 4. When login detected, save cookies to CookieVault and finish
 */
@AndroidEntryPoint
class WebLoginActivity : ComponentActivity() {

    @Inject
    lateinit var cookieVault: CookieVault

    private var loginCompleted = false
    private lateinit var siteConfig: SiteConfig

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_login)

        val siteName = intent.getStringExtra(EXTRA_SITE)
        siteConfig = siteName?.let { SiteConfig.findBySite(it) } ?: run {
            Log.e(TAG, "No site config for: $siteName")
            Toast.makeText(this, "未知站点: $siteName", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val webView = findViewById<WebView>(R.id.webview)
        val progress = findViewById<ProgressBar>(R.id.progress)

        toolbar.title = "${siteConfig.icon} 登录 ${siteConfig.displayName}"
        setActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        // Clear cookies for this domain to ensure fresh login
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                Log.d(TAG, "Navigation: ${request.url}")
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.visibility = View.GONE
                Log.d(TAG, "Page finished: $url")

                if (loginCompleted) return
                checkLoginCookies(url)
            }
        }

        Log.d(TAG, "Loading login URL: ${siteConfig.loginUrl}")
        progress.visibility = View.VISIBLE
        webView.loadUrl(siteConfig.loginUrl)
    }

    private fun checkLoginCookies(url: String?) {
        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie(siteConfig.domain) ?: return

        Log.d(TAG, "Cookies for ${siteConfig.domain}: ${cookieString.take(100)}...")

        // Check if required cookies are present
        val hasRequired = if (siteConfig.requiredCookies.isEmpty()) {
            // If no specific cookies required, check that we have any cookies
            // and the URL matches the success pattern
            url?.contains(siteConfig.successUrlPattern) == true && cookieString.isNotBlank()
        } else {
            siteConfig.requiredCookies.any { required ->
                cookieString.contains("$required=")
            }
        }

        if (hasRequired) {
            loginCompleted = true
            Log.d(TAG, "Login detected for ${siteConfig.site}!")

            // Save all cookies for this domain
            cookieVault.saveCookies(siteConfig.site, siteConfig.domain, cookieString)

            Toast.makeText(this, "${siteConfig.displayName} 登录成功！", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        const val EXTRA_SITE = "extra_site"
    }
}
