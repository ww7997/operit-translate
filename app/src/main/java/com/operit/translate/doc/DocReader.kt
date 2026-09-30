package com.operit.translate.doc

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * قطعة مستند واحدة: النص الأصلي + الترجمة (اختياري) + خطأ (اختياري)
 */
data class DocChunk(
    val index: Int,
    val text: String,
    var translated: String? = null,
    var error: String? = null
)

/**
 * قراءة المستندات: TXT / MD / HTML / EPUB / PDF (كنص مضمّن إن وُجد)
 * نرجع قائمة فقرات (DocChunk) لنترجمها وحدة وحدة.
 */
object DocReader {

    fun read(ctx: Context, uri: Uri, ext: String): List<DocChunk> {
        return when (ext.lowercase()) {
            "pdf" -> readPdf(ctx, uri)
            "epub" -> readEpub(ctx, uri)
            "html", "htm", "xhtml" -> readText(ctx, uri, true)
            else -> readText(ctx, uri, false)
        }
    }

    /** يقرأ نص خام من Uri (txt/md) أو HTML مع إزالة الوسوم */
    private fun readText(ctx: Context, uri: Uri, strip: Boolean): List<DocChunk> {
        val sb = StringBuilder()
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).use { r ->
                var line = r.readLine()
                while (line != null) {
                    sb.append(line).append('\n')
                    line = r.readLine()
                }
            }
        }
        var txt = sb.toString()
        if (strip) txt = stripHtml(txt)
        return splitParagraphs(txt)
    }

    /** إزالة وسوم HTML مع تحويل بعض الوسوم لأسطر */
    private fun stripHtml(html: String): String {
        var s = html
        s = Regex("(?is)<(script|style)[^>]*>.*?</\\1>").replace(s, " ")
        s = Regex("(?is)<br\\s*/?>").replace(s, "\n")
        s = Regex("(?is)</p>").replace(s, "\n\n")
        s = Regex("(?is)<[^>]+>").replace(s, " ")
        s = s.replace("&nbsp;", " ")
        s = s.replace("&amp;", "&")
        s = s.replace("&lt;", "<").replace("&gt;", ">")
        s = s.replace("&quot;", "\"").replace("&#39;", "'")
        return s
    }

    /** PDF: نستخرج النص عبر محاولة قراءة بسيطة (بدون مكتبة خارجية).
     *  ملاحظة: PdfRenderer في أندرويد لا يعطي النص مباشرة، لذلك نرجع
     *  عناصر صفحات كعلامات مرجعية. لو المستخدم بدو استخراج نص PDF كامل
     *  لازم مكتبة مثل PdfBox (تُضاف لاحقاً كخيار).
     */
    private fun readPdf(ctx: Context, uri: Uri): List<DocChunk> {
        val pages = ArrayList<String>()
        ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                for (p in 0 until renderer.pageCount) {
                    pages.add("[[ صفحة " + (p + 1) + " ]]")
                }
            }
        }
        return splitParagraphs(pages.joinToString("\n\n"))
    }

    /** EPUB: ملف zip يحوي صفحات html، نقرأها كلها وندرزها بنص واحد */
    private fun readEpub(ctx: Context, uri: Uri): List<DocChunk> {
        val pages = ArrayList<String>()
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val n = entry.name.lowercase()
                    if (!entry.isDirectory &&
                        (n.endsWith(".html") || n.endsWith(".xhtml") || n.endsWith(".htm"))
                    ) {
                        val sb = StringBuilder()
                        BufferedReader(InputStreamReader(zip, Charsets.UTF_8)).use { r ->
                            var line = r.readLine()
                            while (line != null) {
                                sb.append(line).append('\n')
                                line = r.readLine()
                            }
                        }
                        pages.add(stripHtml(sb.toString()))
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return splitParagraphs(pages.joinToString("\n\n"))
    }

    /** تقسيم النص لفقرات منطقية (فصل بأسطر فارغة، وتقطيع الفقرات الطويلة) */
    private fun splitParagraphs(text: String): List<DocChunk> {
        val raw = text.replace("\r\n", "\n").replace("\r", "\n")
        val parts = raw.split(Regex("\n\\s*\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val out = ArrayList<DocChunk>()
        var i = 0
        for (p in parts) {
            if (p.length <= 1200) {
                out.add(DocChunk(i++, p))
            } else {
                var start = 0
                while (start < p.length) {
                    val end = (start + 1200).coerceAtMost(p.length)
                    out.add(DocChunk(i++, p.substring(start, end)))
                    start = end
                }
            }
        }
        if (out.isEmpty()) out.add(DocChunk(0, text.trim()))
        return out
    }
}
