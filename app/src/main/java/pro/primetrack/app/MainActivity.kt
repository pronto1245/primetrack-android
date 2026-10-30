package pro.primetrack.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    companion object {
        private const val PREFS = "primetrack"
        private const val KEY_DOMAIN = "domain"
        private const val DEFAULT_DOMAIN = "primetrack.pro"
        private const val FILE_CHOOSER_REQUEST = 1001
        private const val BG = "#0D1117"
        private const val CARD = "#161B22"
        private const val BORDER = "#30363D"
        private const val ACCENT = "#10B981"
        private const val TEXT = "#F1F5F9"
        private const val MUTED = "#94A3B8"
    }

    private lateinit var root: FrameLayout
    private var web: WebView? = null
    private var progress: ProgressBar? = null
    private var errorView: View? = null
    private var domain: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor(BG)) }
        setContentView(root)

        domain = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DOMAIN, null)
        if (domain == null) showDomainScreen() else openCabinet(savedInstanceState)
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    // ---------- Cabinet address screen ----------

    private fun showDomainScreen() {
        destroyWeb()
        root.removeAllViews()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
        }

        box.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo_mark)
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(77)).apply { bottomMargin = dp(20) }
        })
        box.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.parseColor(TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = getString(R.string.domain_title)
            setTextColor(Color.parseColor(TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) }
        })

        val input = EditText(this).apply {
            setText(domain ?: DEFAULT_DOMAIN)
            setSelection(text.length)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(Color.parseColor(TEXT))
            setHintTextColor(Color.parseColor(MUTED))
            hint = DEFAULT_DOMAIN
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(CARD))
                setStroke(dp(1), Color.parseColor(BORDER))
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        box.addView(input)

        val help = TextView(this).apply {
            text = getString(R.string.domain_help)
            setTextColor(Color.parseColor(MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        box.addView(help)

        val submit = {
            val d = normalizeDomain(input.text.toString())
            if (d == null) {
                help.text = getString(R.string.domain_invalid)
                help.setTextColor(Color.parseColor("#F87171"))
            } else {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DOMAIN, d).apply()
                domain = d
                openCabinet(null)
            }
        }
        input.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) { submit(); true } else false
        }

        box.addView(accentButton(getString(R.string.continue_btn)) { submit() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(22) }
        })

        root.addView(box, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    }

    private fun accentButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        background = GradientDrawable().apply {
            setColor(Color.parseColor(ACCENT))
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun normalizeDomain(raw: String): String? {
        var s = raw.trim().lowercase().replace(" ", "")
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore("/").substringBefore("?").substringBefore("#")
        if (!s.contains('.') || s.startsWith('.') || s.endsWith('.')) return null
        if (!Regex("^[a-z0-9.-]+(:[0-9]+)?$").matches(s)) return null
        return s
    }

    // ---------- WebView ----------

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun openCabinet(state: Bundle?) {
        root.removeAllViews()
        val d = domain ?: return

        val w = WebView(this)
        web = w
        w.setBackgroundColor(Color.parseColor(BG))
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString PrimeTrackApp/${BuildConfig.VERSION_NAME} (Android)"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(w, true)
        }
        w.addJavascriptInterface(Bridge(), "PrimeTrackApp")

        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleUrl(request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                progress?.visibility = View.GONE
                view.evaluateJavascript(DOWNLOAD_HOOK, null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError()
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress?.progress = newProgress
                progress?.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        w.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) return@setDownloadListener
            try {
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val req = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    addRequestHeader("User-Agent", userAgent)
                    setTitle(name)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, R.string.downloading, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                openExternal(Uri.parse(url))
            }
        }

        root.addView(w, FrameLayout.LayoutParams(-1, -1))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT))
        }
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP))

        if (state == null || w.restoreState(state) == null) {
            w.loadUrl("https://$d/dashboard")
        }
    }

    private fun isOwnHost(host: String?): Boolean {
        val d = domain?.substringBefore(":") ?: return false
        if (host == null) return false
        val h = host.lowercase()
        return h == d || h.endsWith(".$d") || d.endsWith(".$h")
    }

    private fun handleUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if ((scheme == "https" || scheme == "http") && isOwnHost(uri.host)) return false
        if (scheme == "blob" || scheme == "data" || scheme == "about" || scheme == "javascript") return false
        if (scheme == "intent") {
            try {
                val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null
                intent.selector = null
                startActivity(intent)
            } catch (e: Exception) {
                val fallback = try {
                    Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                } catch (e2: Exception) { null }
                if (fallback != null) openExternal(Uri.parse(fallback))
            }
            return true
        }
        openExternal(uri)
        return true
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
        }
    }

    private fun showError() {
        if (errorView != null) return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor(BG))
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.error_title)
            setTextColor(Color.parseColor(TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = getString(R.string.error_text)
            setTextColor(Color.parseColor(MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        })
        box.addView(accentButton(getString(R.string.retry)) {
            root.removeView(errorView)
            errorView = null
            web?.reload()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(48)).apply { topMargin = dp(24) }
        })
        box.addView(TextView(this).apply {
            text = getString(R.string.change_domain)
            setTextColor(Color.parseColor(ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(16), dp(8), dp(8))
            setOnClickListener { errorView = null; showDomainScreen() }
        })
        errorView = box
        root.addView(box, FrameLayout.LayoutParams(-1, -1))
    }

    // ---------- Files from the page (CSV/XLSX exports made in JS) ----------

    inner class Bridge {
        @JavascriptInterface
        fun saveFile(dataUrl: String, fileName: String?, mime: String?) {
            val ok = try {
                val comma = dataUrl.indexOf(',')
                val header = if (comma > 0) dataUrl.substring(0, comma) else ""
                val body = if (comma > 0) dataUrl.substring(comma + 1) else dataUrl
                val bytes = if (header.contains(";base64")) Base64.decode(body, Base64.DEFAULT)
                else Uri.decode(body).toByteArray()
                val type = mime?.takeIf { it.isNotBlank() }
                    ?: header.removePrefix("data:").substringBefore(";").ifBlank { "application/octet-stream" }
                val name = (fileName?.takeIf { it.isNotBlank() } ?: "primetrack-${System.currentTimeMillis()}")
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                writeToDownloads(name, type, bytes)
                name
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (ok != null) Toast.makeText(this@MainActivity, getString(R.string.file_saved, ok), Toast.LENGTH_LONG).show()
                else Toast.makeText(this@MainActivity, R.string.file_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun writeToDownloads(name: String, mime: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("insert failed")
            contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            FileOutputStream(File(dir, name)).use { it.write(bytes) }
        }
    }

    private val DOWNLOAD_HOOK = """
        (function(){
          if (window.__ptAppHook) return; window.__ptAppHook = true;
          var blobs = {};
          var orig = URL.createObjectURL;
          URL.createObjectURL = function(o){ var u = orig.call(URL, o); try { if (o instanceof Blob) blobs[u] = o; } catch(e){} return u; };
          function send(a){
            var href = a.href || ''; var name = a.getAttribute('download') || '';
            if (href.indexOf('data:') === 0) { PrimeTrackApp.saveFile(href, name, ''); return true; }
            if (href.indexOf('blob:') === 0) {
              var b = blobs[href];
              var go = function(blob){ var r = new FileReader(); r.onload = function(){ PrimeTrackApp.saveFile(r.result, name, blob.type || ''); }; r.readAsDataURL(blob); };
              if (b) go(b); else fetch(href).then(function(x){ return x.blob(); }).then(go);
              return true;
            }
            return false;
          }
          var click = HTMLAnchorElement.prototype.click;
          HTMLAnchorElement.prototype.click = function(){ if (this.hasAttribute('download') && send(this)) return; return click.call(this); };
          document.addEventListener('click', function(e){
            var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
            if (a && send(a)) { e.preventDefault(); e.stopPropagation(); }
          }, true);
        })();
    """.trimIndent()

    // ---------- Lifecycle ----------

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val w = web
        if (w != null && errorView == null && w.canGoBack()) {
            w.goBack()
            return
        }
        if (w == null && domain != null) {
            openCabinet(null)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_title)
            .setItems(arrayOf(getString(R.string.change_domain), getString(R.string.exit))) { _, which ->
                if (which == 0) showDomainScreen() else finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web?.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        web?.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    private fun destroyWeb() {
        web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        web = null
        progress = null
        errorView = null
    }

    override fun onDestroy() {
        destroyWeb()
        super.onDestroy()
    }
}
