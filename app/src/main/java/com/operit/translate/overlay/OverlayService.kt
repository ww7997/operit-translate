package com.operit.translate.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.operit.translate.AppConfig
import com.operit.translate.EngineState
import com.operit.translate.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * طبقة عائمة فوق كل التطبيقات تعرض آخر جملة (الأصل + الترجمة).
 * قابلة للسحب، وتُخفى/تظهر حسب الإعداد.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var tvOriginal: TextView? = null
    private var tvTranslated: TextView? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cfg: AppConfig = AppConfig()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cfg = AppConfig.load(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun addOverlay() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.parseColor("#CC0B1220"))
        }

        tvOriginal = TextView(this).apply {
            setTextColor(Color.parseColor("#9FB3C8"))
            textSize = 12f
            maxLines = 2
            setTypeface(typeface, Typeface.NORMAL)
        }
        tvTranslated = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            maxLines = 4
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(tvOriginal)
        container.addView(tvTranslated)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(96)
        }

        // سحب عمودي بسيط
        var startY = 0; var touchY = 0
        container.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startY = lp.y; touchY = e.rawY.toInt(); false }
                MotionEvent.ACTION_MOVE -> {
                    lp.y = startY - (e.rawY.toInt() - touchY)
                    wm.updateViewLayout(container, lp); true
                }
                else -> false
            }
        }

        wm.addView(container, lp)
        root = container

        // ربط المحتوى بالحالة
        scope.launch {
            EngineState.lines.collectLatest { lines ->
                val last = lines.lastOrNull()
                tvOriginal?.text = if (cfg.showOriginal) (last?.original ?: "") else ""
                tvOriginal?.visibility = if (cfg.showOriginal && !last?.original.isNullOrBlank())
                    View.VISIBLE else View.GONE
                tvTranslated?.text = last?.translated ?: ""
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }
}