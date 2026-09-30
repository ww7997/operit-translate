package com.operit.translate.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.operit.translate.Config
import com.operit.translate.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * متصفح داخلي: تفتح أي موقع، وتضغط «ترجم» فيحقن الترجمة تحت كل فقرة.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserTab(cfg: Config) {
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var urlBar by remember { mutableStateOf("https://ar.wikipedia.org") }
    var loading by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }

    fun load(u: String) {
        val fixed = when {
            u.isBlank() -> return
            u.startsWith("http") -> u
            u.contains(" ") || !u.contains(".") ->
                "https://www.google.com/search?q=" + android.net.Uri.encode(u)
            else -> "https://$u"
        }
        urlBar = fixed
        webView?.loadUrl(fixed)
    }

    /**
     * يجمع فقرات الصفحة (P / LI / H1..H6 / TD / BLOCKQUOTE / ARTICLE ...) عبر JS،
     * ثم يترجمها ويحقن الترجمة تحت كل فقرة.
     */
    suspend fun translatePage() {
        val wv = webView ?: return
        translating = true
        status = "عم يقرأ الصفحة..."
        progress = 0f
        try {
            // 1) اجمع الفقرات
            val collectJs = """
                (function(){
                  var sel='p,li,h1,h2,h3,h4,h5,h6,blockquote,td,th,figcaption,dd,dt';
                  var nodes=document.querySelectorAll(sel);
                  var out=[];
                  for(var i=0;i<nodes.length;i++){
                    var el=nodes[i];
                    if(el.getAttribute('data-opt-idx')!==null) continue;
                    var t=(el.innerText||'').trim();
                    if(t.length<2) continue;
                    if(t.length>4000) continue;
                    if(el.querySelector(sel)) continue; // تجنّب الحاويات
                    el.setAttribute('data-opt-idx', out.length);
                    out.push(t);
                  }
                  return JSON.stringify(out);
                })();
            """.trimIndent()

            val json = withContext(Dispatchers.Main) {
                var result: String? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                wv.evaluateJavascript(collectJs) { v ->
                    result = v
                    latch.countDown()
                }
                latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
                result
            }

            // evaluateJavascript يعيد النتيجة كسلسلة JSON مُقتبسة داخل "" — نفكّها خطوتين
            val raw = json ?: "[]"
            val unquoted = if (raw.startsWith("\"")) {
                JSONObject("{\"v\":$raw}").optString("v")
            } else raw
            val items = JSONArray(unquoted)

            val total = items.length()
            if (total == 0) {
                status = "ما لقيت نص بالصفحة"
                translating = false
                return
            }
            status = "عم يترجم 0/$total"
            val mode = cfg.displayMode

            for (i in 0 until total) {
                val text = items.optString(i)
                if (text.isBlank()) continue
                try {
                    val tr = withContext(Dispatchers.IO) { Translator.translate(cfg, text) }
                    val safe = JSONObject.quote(tr)
                    val inject = """
                        (function(){
                          var el=document.querySelector('[data-opt-idx="$i"]');
                          if(!el) return;
                          var old=el.querySelector('.opt-tr');
                          if(old) old.remove();
                          var d=document.createElement('div');
                          d.className='opt-tr';
                          d.style.cssText='color:#1a7f37;border-right:3px solid #1a7f37;padding:2px 8px 6px 0;margin:4px 0;font-size:0.97em;direction:rtl;text-align:right;';
                          d.textContent=$safe;
                          if('${mode}'==='replace'){ el.innerText=$safe; }
                          else { el.appendChild(d); }
                        })();
                    """.trimIndent()
                    withContext(Dispatchers.Main) { wv.evaluateJavascript(inject, null) }
                } catch (e: Exception) {
                    // تجاهل الفقرة اللي فشلت
                }
                progress = (i + 1).toFloat() / total
                status = "عم يترجم ${i + 1}/$total"
            }
            status = "خلصت ✅ ($total فقرة)"
        } catch (e: Exception) {
            status = "خطأ: ${e.message}"
        } finally {
            translating = false
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Column(Modifier.fillMaxSize()) {
        // ==== شريط الأدوات ====
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { webView?.goBack() }) { Text("◀", fontSize = 18.sp) }
            IconButton(onClick = { webView?.goForward() }) { Text("▶", fontSize = 18.sp) }
            IconButton(onClick = { webView?.reload() }) { Text("⟳", fontSize = 18.sp) }
            OutlinedTextField(
                value = urlBar,
                onValueChange = { urlBar = it },
                modifier = Modifier.weight(1f).heightIn(max = 56.dp),
                singleLine = true,
                placeholder = { Text("اكتب عنوان الموقع", fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { load(urlBar) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFE8EAED),
                    unfocusedTextColor = Color(0xFFE8EAED)
                )
            )
        }

        // ==== شريط الترجمة ====
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { scope.launch { translatePage() } },
                enabled = !translating && webView != null,
                modifier = Modifier.weight(1f)
            ) { Text(if (translating) "عم يترجم..." else "🌐 ترجم هالصفحة") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    // إزالة الترجمة من الصفحة
                    webView?.evaluateJavascript(
                        "(function(){document.querySelectorAll('.opt-tr').forEach(e=>e.remove());})();",
                        null
                    )
                    status = ""
                }
            ) { Text("أصل") }
        }

        if (status.isNotBlank()) {
            Text(status, color = Color(0xFF9AA6B2), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (translating) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        // ==== الـ WebView ====
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.userAgentString = settings.userAgentString + " OperitTranslate"
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                            loading = true
                            if (url != null) urlBar = url
                        }
                        override fun onPageFinished(v: WebView?, url: String?) {
                            loading = false
                            if (url != null) urlBar = url
                            if (cfg.autoTranslate && !translating) {
                                scope.launch { translatePage() }
                            }
                        }
                    }
                    loadUrl("https://ar.wikipedia.org")
                    webView = this
                }
            },
            update = { wv -> webView = wv }
        )
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
