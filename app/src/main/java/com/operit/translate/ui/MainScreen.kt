package com.operit.translate.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.operit.translate.Config
import com.operit.translate.doc.DocWriter
import com.operit.translate.Translator
import com.operit.translate.doc.DocReader
import com.operit.translate.doc.DocTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BG = Color(0xFF101418)
private val CARD = Color(0xFF1B2129)
private val ACCENT = Color(0xFF4FC3F7)
private val TXT = Color(0xFFE8EAED)

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(Config.load(ctx)) }
    var tab by remember { mutableIntStateOf(0) }

    // نعيد تحميل الإعدادات لما نرجع للإعدادات
    Scaffold(
        containerColor = BG,
        bottomBar = {
            NavigationBar(containerColor = CARD) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("🌐", fontSize = 20.sp) },
                    label = { Text("متصفح") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("📝", fontSize = 20.sp) },
                    label = { Text("نص") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("📚", fontSize = 20.sp) },
                    label = { Text("مستندات") }
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Text("⚙️", fontSize = 20.sp) },
                    label = { Text("إعدادات") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> BrowserTab(cfg)
                1 -> TextTab(cfg) { cfg = it }
                2 -> DocsTab(cfg)
                else -> SettingsTab(cfg) { cfg = it }
            }
        }
    }
}

/* ============ تبويب النص السريع ============ */
@Composable
private fun TextTab(cfg: Config, onCfg: (Config) -> Unit) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text(
            "ترجمة نص — المحرك: ${cfg.engine.displayName}",
            color = ACCENT, fontWeight = FontWeight.Bold, fontSize = 15.sp
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("النص الأصلي") },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TXT, unfocusedTextColor = TXT
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (input.isBlank()) return@Button
                    busy = true; err = ""; output = ""
                    scope.launch {
                        try {
                            val r = withContext(Dispatchers.IO) {
                                Translator.translate(cfg, input)
                            }
                            output = r
                        } catch (e: Exception) {
                            err = e.message ?: "خطأ"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy
            ) { Text(if (busy) "عم يترجم..." else "ترجم") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { input = ""; output = ""; err = "" }) {
                Text("مسح")
            }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (err.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("⚠ $err", color = Color(0xFFFF8A80))
        }
        Spacer(Modifier.height(12.dp))
        if (output.isNotBlank()) {
            Text("الترجمة", color = ACCENT, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Card(colors = CardDefaults.cardColors(containerColor = CARD)) {
                Text(output, color = TXT, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

/* ============ تبويب المستندات (كتب / PDF) ============ */
@Composable
private fun DocsTab(cfg: Config) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by DocTranslator.state.collectAsState()
    var importErr by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val name = uri.lastPathSegment ?: "document"
                val ext = name.substringAfterLast('.', "txt").lowercase()
                val chunks = withContext(Dispatchers.IO) {
                    DocReader.read(ctx, uri, ext)
                }
                DocTranslator.loadDoc(name, chunks)
                importErr = ""
            } catch (e: Exception) {
                importErr = "فشل القراءة: ${e.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("ترجمة مستندات (TXT / MD / HTML / EPUB / PDF)",
            color = ACCENT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text("المحرك: ${cfg.engine.displayName}", color = TXT, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        Button(onClick = {
            picker.launch(arrayOf(
                "text/plain", "text/html", "text/markdown",
                "application/epub+zip", "application/pdf"
            ))
        }) { Text("📂 اختر ملف") }

        if (importErr.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("⚠ $importErr", color = Color(0xFFFF8A80))
        }

        if (st.chunks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(st.title, color = TXT, fontWeight = FontWeight.Bold)
            Text("${st.total} فقرة — ${st.status}", color = Color(0xFF9AA6B2), fontSize = 12.sp)
            if (st.running) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { st.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))

            // خيار نمط العرض
            Row {
                Text("نمط العرض: ", color = TXT, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                FilterChip(
                    selected = st.mode == "bilingual",
                    onClick = { DocTranslator.setMode("bilingual") },
                    label = { Text("أصل + ترجمة") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = st.mode == "translation_only",
                    onClick = { DocTranslator.setMode("translation_only") },
                    label = { Text("ترجمة فقط") }
                )
            }

            Spacer(Modifier.height(10.dp))
            Row {
                Button(
                    onClick = { DocTranslator.start(cfg, scope) },
                    enabled = !st.running
                ) { Text(if (st.running) "عم يترجم..." else "▶ ترجم الكتاب") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { DocTranslator.reset() },
                    enabled = !st.running
                ) { Text("تفريغ") }
            }

            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = { shareExport(ctx, false) },
                    enabled = !st.running && st.chunks.isNotEmpty()
                ) { Text("⬇ تصدير TXT") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { shareExport(ctx, true) },
                    enabled = !st.running && st.chunks.isNotEmpty()
                ) { Text("⬇ تصدير HTML") }
            }

            Spacer(Modifier.height(12.dp))
            Text("المعاينة:", color = ACCENT, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(st.chunks.take(60)) { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(c.text.take(400), color = Color(0xFFB0B8C0), fontSize = 13.sp)
                        if (st.mode == "bilingual" && c.translated != null) {
                            Text(c.translated!!.take(400), color = Color(0xFF66D9A0), fontSize = 13.sp)
                        } else if (st.mode != "bilingual" && c.translated != null) {
                            Text(c.translated!!.take(400), color = Color(0xFF66D9A0), fontSize = 13.sp)
                        }
                        if (c.error != null) {
                            Text("⚠ ${c.error}", color = Color(0xFFFF8A80), fontSize = 12.sp)
                        }
                        HorizontalDivider(color = Color(0xFF2A323C))
                    }
                }
            }
        }
    }
}

/* تصدير + مشاركة الملف المترجم */
private fun shareExport(ctx: Context, asHtml: Boolean) {
    try {
        val (name, content) = DocTranslator.export(asHtml)
        val dir = java.io.File(ctx.cacheDir, "export")
        dir.mkdirs()
        val f = java.io.File(dir, name)
        f.writeText(content, Charsets.UTF_8)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, ctx.packageName + ".fileprovider", f
        )
        val it = Intent(Intent.ACTION_SEND).apply {
            type = if (asHtml) "text/html" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(it, "مشاركة الملف المترجم"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(ctx, "فشل التصدير: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

/* ============ تبويب الإعدادات ============ */
@Composable
private fun SettingsTab(cfg: Config, onCfg: (Config) -> Unit) {
    val ctx = LocalContext.current
    var engineOpen by remember { mutableStateOf(false) }
    var srcOpen by remember { mutableStateOf(false) }
    var tgtOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)
    ) {
        Text("الإعدادات", color = ACCENT, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))

        /* ---- المحرك ---- */
        Text("محرك الترجمة", color = TXT, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedButton(
                onClick = { engineOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(cfg.engine.displayName) }
            DropdownMenu(expanded = engineOpen, onDismissRequest = { engineOpen = false }) {
                com.operit.translate.engine.EngineRegistry.all.forEach { e ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(e.displayName)
                                Text(e.description, color = Color(0xFF9AA6B2), fontSize = 11.sp)
                            }
                        },
                        onClick = {
                            engineOpen = false
                            val nc = cfg.copy(engineId = e.id)
                            Config.save(ctx, nc); onCfg(nc)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val eng = cfg.engine
        Text(
            when {
                eng.needsKey && eng.needsEndpoint -> "🔑 يحتاج مفتاح API + رابط endpoint"
                eng.needsKey -> "🔑 يحتاج مفتاح API"
                eng.needsEndpoint -> "🔗 يحتاج رابط endpoint"
                else -> "✅ ما بيحتاج مفتاح (مجاني)"
            },
            color = Color(0xFF9AA6B2), fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        /* ---- المفتاح ---- */
        if (eng.needsKey || cfg.apiKey.isNotEmpty()) {
            Text("مفتاح API", color = TXT, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = cfg.apiKey,
                onValueChange = { val nc = cfg.copy(apiKey = it); Config.save(ctx, nc); onCfg(nc) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("sk-...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TXT, unfocusedTextColor = TXT
                )
            )
            Spacer(Modifier.height(10.dp))
        }

        /* ---- الرابط ---- */
        if (eng.needsEndpoint || cfg.endpoint.isNotEmpty()) {
            Text("رابط الـ Endpoint", color = TXT, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = cfg.endpoint,
                onValueChange = { val nc = cfg.copy(endpoint = it); Config.save(ctx, nc); onCfg(nc) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TXT, unfocusedTextColor = TXT
                )
            )
            Spacer(Modifier.height(10.dp))
        }

        /* ---- الموديل ---- */
        if (com.operit.translate.engine.EngineRegistry.isAi(eng.id)) {
            Text("اسم الموديل", color = TXT, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = cfg.model,
                onValueChange = { val nc = cfg.copy(model = it); Config.save(ctx, nc); onCfg(nc) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("gpt-4o-mini") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TXT, unfocusedTextColor = TXT
                )
            )
            Spacer(Modifier.height(10.dp))
        }

        /* ---- اللغات ---- */
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("من", color = TXT, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(onClick = { srcOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(langName(cfg.sourceLang))
                    }
                    DropdownMenu(expanded = srcOpen, onDismissRequest = { srcOpen = false }) {
                        com.operit.translate.engine.EngineUtil.LANG_NAMES.forEach { (code, nm) ->
                            DropdownMenuItem(
                                text = { Text(nm) },
                                onClick = {
                                    srcOpen = false
                                    val nc = cfg.copy(sourceLang = code)
                                    Config.save(ctx, nc); onCfg(nc)
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("إلى", color = TXT, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(onClick = { tgtOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(langName(cfg.targetLang))
                    }
                    DropdownMenu(expanded = tgtOpen, onDismissRequest = { tgtOpen = false }) {
                        com.operit.translate.engine.EngineUtil.LANG_NAMES.forEach { (code, nm) ->
                            DropdownMenuItem(
                                text = { Text(nm) },
                                onClick = {
                                    tgtOpen = false
                                    val nc = cfg.copy(targetLang = code)
                                    Config.save(ctx, nc); onCfg(nc)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("نمط العرض الافتراضي للمستندات", color = TXT, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row {
            FilterChip(
                selected = cfg.displayMode == "bilingual",
                onClick = { val nc = cfg.copy(displayMode = "bilingual"); Config.save(ctx, nc); onCfg(nc) },
                label = { Text("أصل + ترجمة") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = cfg.displayMode == "translation_only",
                onClick = { val nc = cfg.copy(displayMode = "translation_only"); Config.save(ctx, nc); onCfg(nc) },
                label = { Text("ترجمة فقط") }
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("الترجمة التلقائية", color = TXT, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("لو مفعّلة: كل صفحة تفتحها بالمتصفح الداخلي تترجم لحالها. الافتراضي: معطّلة.",
            color = Color(0xFF9AA6B2), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = cfg.autoTranslate,
                onCheckedChange = { v ->
                    val nc = cfg.copy(autoTranslate = v); Config.save(ctx, nc); onCfg(nc)
                }
            )
            Spacer(Modifier.width(10.dp))
            Text(if (cfg.autoTranslate) "مفعّلة" else "معطّلة (موصى به)", color = TXT, fontSize = 13.sp)
        }

        Spacer(Modifier.height(14.dp))
        Text("برومبت الترجمة (للنماذج الذكية)", color = TXT, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("متغيّرات: {from} = اللغة المصدر، {to} = اللغة الهدف. اتركه فارغاً للافتراضي.",
            color = Color(0xFF9AA6B2), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = cfg.prompt,
            onValueChange = { val nc = cfg.copy(prompt = it); Config.save(ctx, nc); onCfg(nc) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            placeholder = { Text(com.operit.translate.engine.EngineUtil.DEFAULT_PROMPT, fontSize = 12.sp, color = Color(0xFF6A7480)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TXT, unfocusedTextColor = TXT
            )
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            val nc = cfg.copy(prompt = com.operit.translate.engine.EngineUtil.DEFAULT_PROMPT)
            Config.save(ctx, nc); onCfg(nc)
        }) { Text("استخدم البرومبت الافتراضي") }

        Spacer(Modifier.height(18.dp))
        OutlinedButton(
            onClick = {
                val nc = cfg.copy(cacheEnabled = !cfg.cacheEnabled)
                Config.save(ctx, nc); onCfg(nc)
                if (!nc.cacheEnabled) Translator.clearCache()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (cfg.cacheEnabled) "حفظ مؤقت: مفعّل (اضغط لتعطيله)" else "حفظ مؤقت: معطّل") }

        Spacer(Modifier.height(20.dp))
    }
}

private fun langName(code: String): String =
    com.operit.translate.engine.EngineUtil.LANG_NAMES[code] ?: code
