package com.openclaw.agent.ui.mijia

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toolbar
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.openclaw.agent.R
import com.openclaw.agent.core.mijia.MijiaAuth
import com.openclaw.agent.core.mijia.MijiaAuthStore
import com.openclaw.agent.core.mijia.MijiaTokenRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MijiaLoginActivity"

/**
 * WebView-based OAuth login for Xiaomi Mi Home.
 *
 * Flow:
 * 1. Open Xiaomi serviceLogin page (sid=mijia)
 * 2. User logs in via web form or QR code scan
 * 3. After login, Xiaomi redirects to STS callback
 * 4. We extract serviceToken + passToken from cookies
 * 5. Call serviceLogin API with passToken to get ssecurity
 * 6. Save complete auth and finish
 */
@AndroidEntryPoint
class MijiaLoginActivity : ComponentActivity() {

    @Inject
    lateinit var authStore: MijiaAuthStore

    @Inject
    lateinit var tokenRefresher: MijiaTokenRefresher

    private val loginUrl = "https://account.xiaomi.com/pass/serviceLogin?sid=mijia&_locale=zh_CN&_qrsize=240"

    private var loginCompleted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mijia_login)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val webView = findViewById<WebView>(R.id.webview)
        val progress = findViewById<ProgressBar>(R.id.progress)

        setActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        // Clear cookies for fresh login
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }

        // Add JS interface to receive auth data from injected script
        webView.addJavascriptInterface(AuthBridge(), "MijiaAuthBridge")

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedError(
                view: WebView?, errorCode: Int, description: String?, failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "WebView error: code=$errorCode, desc=$description, url=$failingUrl")
            }

            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?
            ) {
                Log.e(TAG, "SSL error: $error")
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                Log.d(TAG, "Navigation: $url")
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")
                progress.visibility = View.GONE

                if (loginCompleted) return

                // After login, Xiaomi redirects through several URLs.
                // The STS callback (sts.api.mijia.tech) returns a page that may have JSON.
                if (url != null && (
                    url.contains("sts.api.mijia.tech") ||
                    url.contains("home.mi.com") ||
                    url.contains("i.mi.com"))) {

                    Log.d(TAG, "On callback page, injecting JS to extract body...")
                    view?.evaluateJavascript(
                        "(function() { " +
                        "  try { " +
                        "    var body = document.body.innerText || document.body.textContent; " +
                        "    MijiaAuthBridge.onBody(body || ''); " +
                        "  } catch(e) { " +
                        "    MijiaAuthBridge.onBody(''); " +
                        "  } " +
                        "})()", null
                    )
                }

                // Also try cookies on every page
                tryExtractFromCookies()
            }
        }

        Log.d(TAG, "Loading login URL: $loginUrl")
        webView.loadUrl(loginUrl)
    }

    /**
     * JS Interface to receive the page body from injected JavaScript.
     */
    inner class AuthBridge {
        @JavascriptInterface
        fun onBody(body: String) {
            Log.d(TAG, "AuthBridge.onBody: ${body.take(200)}")
            if (loginCompleted) return

            try {
                val json = org.json.JSONObject(body)
                val ssecurity = json.optString("ssecurity", "")
                val nonce = json.optString("nonce", "")
                val userId = json.optString("userId", "")
                val cUserId = json.optString("cUserId", "")
                val passToken = json.optString("passToken", "")

                if (ssecurity.isNotBlank()) {
                    Log.d(TAG, "Got ssecurity from page body! userId=$userId")
                    val cookies = getCookieMap()
                    val serviceToken = cookies["serviceToken"] ?: ""

                    val auth = MijiaAuth(
                        userId = userId.ifBlank { cookies["userId"] ?: "" },
                        cUserId = cUserId.ifBlank { cookies["cUserId"] ?: "" },
                        ssecurity = ssecurity,
                        serviceToken = serviceToken,
                        passToken = passToken.ifBlank { cookies["passToken"] ?: "" },
                        ua = buildUserAgent(),
                        deviceId = generateDeviceId(),
                        passO = generatePassO(),
                        expireTime = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                    )
                    saveAndFinish(auth)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Page body is not JSON: ${e.message}")
            }
        }
    }

    private fun tryExtractFromCookies() {
        if (loginCompleted) return
        val cookies = getCookieMap()
        val serviceToken = cookies["serviceToken"] ?: cookies["yetAnotherServiceToken"]
        val userId = cookies["userId"]
        val passToken = cookies["passToken"]

        if (!serviceToken.isNullOrBlank() && !userId.isNullOrBlank()) {
            val ssecurity = cookies["ssecurity"] ?: ""
            if (ssecurity.isNotBlank()) {
                // Got everything from cookies (unlikely but handle it)
                Log.d(TAG, "Got full auth from cookies including ssecurity")
                val auth = MijiaAuth(
                    userId = userId,
                    cUserId = cookies["cUserId"] ?: "",
                    ssecurity = ssecurity,
                    serviceToken = serviceToken,
                    passToken = passToken ?: "",
                    ua = buildUserAgent(),
                    deviceId = generateDeviceId(),
                    passO = generatePassO(),
                    expireTime = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                )
                saveAndFinish(auth)
            } else {
                // Have serviceToken but no ssecurity — save partial auth then fetch ssecurity
                Log.d(TAG, "Have serviceToken, fetching ssecurity via serviceLogin API...")

                val partialAuth = MijiaAuth(
                    userId = userId,
                    cUserId = cookies["cUserId"] ?: "",
                    ssecurity = "",
                    serviceToken = serviceToken,
                    passToken = passToken ?: "",
                    ua = buildUserAgent(),
                    deviceId = generateDeviceId(),
                    passO = generatePassO(),
                    expireTime = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                )
                authStore.save(partialAuth)

                // Immediately fetch ssecurity using passToken
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val refreshed = tokenRefresher.refreshSsecurity()
                        if (refreshed != null && refreshed.ssecurity.isNotBlank()) {
                            Log.d(TAG, "ssecurity fetched successfully!")
                            runOnUiThread {
                                Toast.makeText(this@MijiaLoginActivity, "登录成功 ✅", Toast.LENGTH_SHORT).show()
                            }
                            loginCompleted = true
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            Log.w(TAG, "Failed to fetch ssecurity, auth incomplete")
                            runOnUiThread {
                                Toast.makeText(this@MijiaLoginActivity, "获取密钥失败，请重试", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching ssecurity", e)
                        runOnUiThread {
                            Toast.makeText(this@MijiaLoginActivity, "登录异常: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun getCookieMap(): Map<String, String> {
        val cookieManager = CookieManager.getInstance()
        val domains = listOf(
            "https://account.xiaomi.com",
            "https://sts.api.mijia.tech",
            "https://api.mijia.tech",
            "https://home.mi.com",
            "https://i.mi.com"
        )
        val allCookies = mutableMapOf<String, String>()
        domains.forEach { domain ->
            cookieManager.getCookie(domain)?.split(";")?.forEach { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    allCookies[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        Log.d(TAG, "Extracted cookies: ${allCookies.keys}")
        return allCookies
    }

    private fun saveAndFinish(auth: MijiaAuth) {
        if (loginCompleted) return
        loginCompleted = true
        Log.d(TAG, "Login complete! userId=${auth.userId}, hasSsecurity=${auth.ssecurity.isNotBlank()}, hasServiceToken=${auth.serviceToken.isNotBlank()}")
        authStore.save(auth)
        setResult(RESULT_OK)
        runOnUiThread {
            Toast.makeText(this, "登录成功 ✅", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val REQUEST_CODE = 10086

        fun buildUserAgent(): String {
            val hex40 = (1..40).map { "0123456789ABCDEF".random() }.joinToString("")
            val hex32a = (1..32).map { "0123456789ABCDEF".random() }.joinToString("")
            val hex32b = (1..32).map { "0123456789ABCDEF".random() }.joinToString("")
            val hex40b = (1..40).map { "0123456789ABCDEF".random() }.joinToString("")
            val passO = (1..16).map { "0123456789abcdef".random() }.joinToString("")
            return "Android-15-11.0.701-Xiaomi-23046RP50C-OS2.0.212.0.VMYCNXM-$hex40-CN-$hex32b-$hex32a-SmartHome-MI_APP_STORE-$hex40b|$hex40b|$passO-64"
        }

        fun generateDeviceId(): String =
            (1..16).map { "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-".random() }.joinToString("")

        fun generatePassO(): String =
            (1..16).map { "0123456789abcdef".random() }.joinToString("")
    }
}
