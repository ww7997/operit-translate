package com.operit.translate.doc

import android.content.Context
import android.net.Uri

/**
 * تصدير المستند المترجم إلى ملف نصي/HTML.
 * - bilingual: الأصل ثم الترجمة تحت كل فقرة.
 * - translation_only: الترجمة فقط.
 * - replace: نفس الشي (للمستندات ما في فرق عن translation_only).
 */
object DocWriter {

    fun buildText(
        title: String,
        chunks: List<DocChunk>,
        mode: String,
        asHtml: Boolean
    ): String {
        val sb = StringBuilder()
        if (asHtml) {
            sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">\n")
            sb.append("<title>").append(esc(title)).append("</title>\n")
            sb.append("<style>body{font-family:sans-serif;line-height:1.7;max-width:820px;margin:auto;padding:18px}")
            sb.append(".orig{color:#222}.tr{color:#0a5;}.sep{color:#bbb;text-align:center;margin:6px 0}</style>\n")
            sb.append("</head><body>\n")
        } else {
            sb.append(title).append("\n")
            sb.append("====================\n\n")
        }

        for (c in chunks) {
            val orig = c.text
            val tr = c.translated ?: ""
            if (asHtml) {
                if (mode == "bilingual") {
                    sb.append("<div class=\"orig\">").append(esc(orig)).append("</div>\n")
                    if (tr.isNotBlank()) {
                        sb.append("<div class=\"tr\">").append(esc(tr)).append("</div>\n")
                    }
                } else {
                    sb.append("<div class=\"tr\">").append(esc(if (tr.isNotBlank()) tr else orig)).append("</div>\n")
                }
                sb.append("<div class=\"sep\">· · ·</div>\n")
            } else {
                if (mode == "bilingual") {
                    sb.append(orig).append("\n")
                    if (tr.isNotBlank()) sb.append(">> ").append(tr).append("\n")
                } else {
                    sb.append(if (tr.isNotBlank()) tr else orig).append("\n")
                }
                sb.append("\n")
            }
        }

        if (asHtml) sb.append("</body></html>\n")
        return sb.toString()
    }

    private fun esc(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}