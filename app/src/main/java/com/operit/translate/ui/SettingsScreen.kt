package com.operit.translate.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.operit.translate.AppConfig

/**
 * كل النماذج والروابط والمفاتيح قابلة للضبط من هنا — لا شيء مثبّت في الكود.
 */
@Composable
fun SettingsTab(ctx: Context) {
    var cfg by remember { mutableStateOf(AppConfig.load(ctx)) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section("خدمة تحويل الكلام (STT)")
        Field("Endpoint", cfg.sttEndpoint) { cfg = cfg.copy(sttEndpoint = it); saved = false }
        Field("API Key", cfg.sttApiKey, secret = true) { cfg = cfg.copy(sttApiKey = it); saved = false }
        Field("Model", cfg.sttModel) { cfg = cfg.copy(sttModel = it); saved = false }

        Divider()
        Section("خدمة الترجمة (MT)")
        Field("Endpoint", cfg.mtEndpoint) { cfg = cfg.copy(mtEndpoint = it); saved = false }
        Field("API Key", cfg.mtApiKey, secret = true) { cfg = cfg.copy(mtApiKey = it); saved = false }
        Field("Model", cfg.mtModel) { cfg = cfg.copy(mtModel = it); saved = false }

        Divider()
        Section("اللغات")
        Field("من (auto = تلقائي)", cfg.sourceLang) { cfg = cfg.copy(sourceLang = it); saved = false }
        Field("إلى (مثال: ar, en, fr)", cfg.targetLang) { cfg = cfg.copy(targetLang = it); saved = false }

        Divider()
        Section("مصدر الصوت")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip(cfg.audioSource == "mic", { cfg = cfg.copy(audioSource = "mic"); saved = false }, "ميكروفون")
            OptionChip(cfg.audioSource == "system", { cfg = cfg.copy(audioSource = "system"); saved = false }, "صوت النظام")
            OptionChip(cfg.audioSource == "both", { cfg = cfg.copy(audioSource = "both"); saved = false }, "الاثنان")
        }
        Text(
            "ملاحظة: صوت النظام يعمل فقط مع التطبيقات التي تسمح بذلك (يوتيوب/نتفليكس محجوبان غالباً).",
            style = MaterialTheme.typography.bodySmall
        )

        Divider()
        Section("العرض")
        SwitchRow("إظهار النص الأصلي", cfg.showOriginal) { cfg = cfg.copy(showOriginal = it); saved = false }

        Divider()
        Section("التقسيم (ثوانٍ لكل مقطع)")
        Field("chunkSeconds", cfg.chunkSeconds.toString()) {
            it.toIntOrNull()?.let { v -> cfg = cfg.copy(chunkSeconds = v.coerceIn(1, 15)); saved = false }
        }
        Field("عتبة السكوت (RMS 0-5000)", cfg.silenceThreshold.toString()) {
            it.toIntOrNull()?.let { v -> cfg = cfg.copy(silenceThreshold = v.coerceIn(100, 5000)); saved = false }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { AppConfig.save(ctx, cfg); saved = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (saved) "تم الحفظ ✓" else "حفظ الإعدادات")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color(0xFF4FC3F7))
}

@Composable
private fun Divider() {
    HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFF2A3A50))
}

@Composable
private fun Field(
    label: String,
    value: String,
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OptionChip(selected: Boolean, onClick: () -> Unit, text: String) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}