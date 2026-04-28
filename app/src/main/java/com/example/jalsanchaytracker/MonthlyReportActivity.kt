package com.example.jalsanchaytracker

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

// ─── Custom Canvas bar chart view ────────────────────────────────────────────
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Bar(val label: String, val value: Float)

    private var bars: List<Bar> = emptyList()
    private var animFraction = 0f

    private val paintBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A7BD5")
        style = Paint.Style.FILL
    }
    private val paintBarFade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A3A5C")
        style = Paint.Style.FILL
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7EAECF")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val paintValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A3A5C")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun setBars(data: List<Bar>) {
        bars = data
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 800
        anim.addUpdateListener {
            animFraction = it.animatedValue as Float
            invalidate()
        }
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val bottomPad = 60f
        val topPad = 50f
        val chartH = h - bottomPad - topPad
        val maxVal = bars.maxOf { it.value }.coerceAtLeast(1f)
        val barCount = bars.size
        val totalBarW = w * 0.7f
        val barW = (totalBarW / barCount).coerceAtMost(80f)
        val gap = (w - barW * barCount) / (barCount + 1)

        // baseline
        canvas.drawLine(0f, h - bottomPad, w, h - bottomPad, paintLine)

        bars.forEachIndexed { i, bar ->
            val x = gap + i * (barW + gap)
            val barHeight = (bar.value / maxVal) * chartH * animFraction
            val top = h - bottomPad - barHeight
            val rect = RectF(x, top, x + barW, h - bottomPad)
            // background track
            val trackRect = RectF(x, topPad, x + barW, h - bottomPad)
            canvas.drawRoundRect(trackRect, 8f, 8f, paintBarFade)
            // filled bar
            canvas.drawRoundRect(rect, 8f, 8f, paintBar)
            // month label
            canvas.drawText(bar.label, x + barW / 2, h - 20f, paintText)
            // value label above bar
            if (animFraction > 0.7f) {
                val valStr = if (bar.value >= 1000) "%.1fk".format(bar.value / 1000) else "%.0f".format(bar.value)
                canvas.drawText(valStr, x + barW / 2, top - 8f, paintValue)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.55f).toInt())
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────
class MonthlyReportActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var tabGraph: TextView
    private lateinit var tabMonthly: TextView
    private lateinit var tabEntries: TextView
    private lateinit var panelGraph: LinearLayout
    private lateinit var panelMonthly: ScrollView
    private lateinit var panelEntries: ScrollView
    private lateinit var llMonthContainer: LinearLayout
    private lateinit var llEntriesContainer: LinearLayout
    private lateinit var tvTotalAllTime: TextView
    private lateinit var tvTotalDays: TextView
    private lateinit var barChart: BarChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monthly_report)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }

        db = AppDatabase.getInstance(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tabGraph = findViewById(R.id.tabGraph)
        tabMonthly = findViewById(R.id.tabMonthly)
        tabEntries = findViewById(R.id.tabEntries)
        panelGraph = findViewById(R.id.panelGraph)
        panelMonthly = findViewById(R.id.panelMonthly)
        panelEntries = findViewById(R.id.panelEntries)
        llMonthContainer = findViewById(R.id.llMonthContainer)
        llEntriesContainer = findViewById(R.id.llEntriesContainer)
        tvTotalAllTime = findViewById(R.id.tvTotalAllTime)
        tvTotalDays = findViewById(R.id.tvTotalDays)
        barChart = findViewById(R.id.barChart)

        tabGraph.setOnClickListener { switchTab(0) }
        tabMonthly.setOnClickListener { switchTab(1) }
        tabEntries.setOnClickListener { switchTab(2) }

        switchTab(0)
        loadData()
    }

    private fun switchTab(index: Int) {
        val tabs = listOf(tabGraph, tabMonthly, tabEntries)
        val panels = listOf(panelGraph, panelMonthly, panelEntries)
        tabs.forEachIndexed { i, tab ->
            tab.setBackgroundResource(if (i == index) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
            tab.setTextColor(if (i == index) 0xFFFFFFFF.toInt() else 0xFF7EAECF.toInt())
        }
        panels.forEachIndexed { i, panel ->
            panel.visibility = if (i == index) View.VISIBLE else View.GONE
        }
    }

    private fun loadData() {
        scope.launch {
            val monthlySummaries = withContext(Dispatchers.IO) { db.rainfallDao().getMonthlySummaries() }
            val dailySummaries = withContext(Dispatchers.IO) { db.rainfallDao().getDailySummaries() }
            val allEntries = withContext(Dispatchers.IO) { db.rainfallDao().getAll() }
            val totalAll = withContext(Dispatchers.IO) {
                db.rainfallDao().getTotalWater()
            }

            tvTotalAllTime.text = "%.0f L".format(totalAll)
            tvTotalDays.text = "%.1f days".format(totalAll / 540.0)

            buildBarChart(dailySummaries)
            buildMonthlyList(monthlySummaries, totalAll)
            buildEntriesList(allEntries)
        }
    }

    private fun buildBarChart(summaries: List<RainfallEntry>) {
        if (summaries.isEmpty()) return
        val bars = summaries.map { entry ->
            val label = "%02d/%02d".format(entry.day, entry.month)  // e.g. "28/04"
            BarChartView.Bar(label, entry.waterCollectedL.toFloat())
        }
        barChart.setBars(bars)
    }

    private fun buildMonthlyList(summaries: List<RainfallEntry>, totalAll: Double) {
        llMonthContainer.removeAllViews()
        if (summaries.isEmpty()) {
            addEmptyState(llMonthContainer, "No entries yet")
            return
        }
        val maxWater = summaries.maxOf { it.waterCollectedL }.coerceAtLeast(1.0)
        summaries.reversed().forEach { entry ->
            val cal = Calendar.getInstance().apply { set(entry.year, entry.month - 1, 1) }
            val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
            val pct = if (totalAll > 0) (entry.waterCollectedL / totalAll * 100).toInt() else 0

            val card = layoutInflater.inflate(R.layout.item_monthly_entry, llMonthContainer, false)
            card.findViewById<TextView>(R.id.tvMonthLabel).text = monthLabel
            card.findViewById<TextView>(R.id.tvMonthWater).text = "%.0f L".format(entry.waterCollectedL)
            card.findViewById<TextView>(R.id.tvMonthDays).text = "%.1f days".format(entry.waterCollectedL / 540.0)

            // pct of total badge
            val tvPct = card.findViewById<TextView>(R.id.tvMonthPct)
            tvPct?.text = "$pct% of total"

            val barFill = card.findViewById<View>(R.id.viewBarFill)
            val barContainer = card.findViewById<View>(R.id.viewBarContainer)
            barContainer.post {
                val targetWidth = ((entry.waterCollectedL / maxWater) * barContainer.width).toInt()
                ValueAnimator.ofInt(0, targetWidth).apply {
                    duration = 600
                    addUpdateListener {
                        barFill.layoutParams.width = it.animatedValue as Int
                        barFill.requestLayout()
                    }
                    start()
                }
            }
            llMonthContainer.addView(card)
        }
    }

    private fun buildEntriesList(entries: List<RainfallEntry>) {
        llEntriesContainer.removeAllViews()
        if (entries.isEmpty()) {
            addEmptyState(llEntriesContainer, "No entries yet. Start tracking!")
            return
        }
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        entries.forEach { entry ->
            val row = layoutInflater.inflate(R.layout.item_entry_row, llEntriesContainer, false)
            row.findViewById<TextView>(R.id.tvEntryDate).text = sdf.format(Date(entry.dateMillis))
            row.findViewById<TextView>(R.id.tvEntryWater).text = "%.1f L".format(entry.waterCollectedL)
            row.findViewById<TextView>(R.id.tvEntryDetails).text =
                "Roof: %.0f sq ft  ·  Rain: %.1f mm".format(entry.roofAreaSqFt, entry.rainfallMm)

            row.findViewById<ImageButton>(R.id.btnDeleteEntry).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete entry?")
                    .setMessage("This will remove %.1f L from your records.".format(entry.waterCollectedL))
                    .setPositiveButton("Delete") { _, _ ->
                        scope.launch {
                            withContext(Dispatchers.IO) { db.rainfallDao().deleteById(entry.id) }
                            loadData()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            llEntriesContainer.addView(row)
        }
    }

    private fun addEmptyState(container: LinearLayout, msg: String) {
        TextView(this).apply {
            text = msg
            setTextColor(0xFF7EAECF.toInt())
            textSize = 14f
            setPadding(0, 48, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            container.addView(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
