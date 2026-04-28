package com.example.jalsanchaytracker
import android.graphics.Color
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var etArea: EditText
    private lateinit var etRainfall: EditText
    private lateinit var etTank: EditText
    private lateinit var btnCalculate: TextView
    private lateinit var tvResult: TextView
    private lateinit var progressTank: ProgressBar

    private lateinit var cardTank: FrameLayout
    private lateinit var viewWaveFill: View
    private lateinit var tvTankPercent: TextView
    private lateinit var tvWaterCollected: TextView
    private lateinit var tvDaysSupported: TextView
    private lateinit var tvTodaySaved: TextView
    private lateinit var tvTotalSaved: TextView
    private lateinit var llDropGrid: LinearLayout
    private lateinit var llDrops: LinearLayout
    private lateinit var tvDropSubtitle: TextView
    private lateinit var btnMonthly: TextView
    private lateinit var btnTips: TextView

    private lateinit var db: AppDatabase
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        db = AppDatabase.getInstance(this)

        etArea = findViewById(R.id.etArea)
        etRainfall = findViewById(R.id.etRainfall)
        etTank = findViewById(R.id.etTank)
        btnCalculate = findViewById(R.id.btnCalculate)
        tvResult = findViewById(R.id.tvResult)
        progressTank = findViewById(R.id.progressTank)
        cardTank = findViewById(R.id.cardTank)
        viewWaveFill = findViewById(R.id.viewWaveFill)
        tvTankPercent = findViewById(R.id.tvTankPercent)
        tvWaterCollected = findViewById(R.id.tvWaterCollected)
        tvDaysSupported = findViewById(R.id.tvDaysSupported)
        tvTodaySaved = findViewById(R.id.tvTodaySaved)
        tvTotalSaved = findViewById(R.id.tvTotalSaved)
        llDropGrid = findViewById(R.id.llDropGrid)
        llDrops = findViewById(R.id.llDrops)
        tvDropSubtitle = findViewById(R.id.tvDropSubtitle)
        btnMonthly = findViewById(R.id.btnMonthly)
        btnTips = findViewById(R.id.btnTips)
        etArea.setHintTextColor(Color.parseColor("#3D5A78"))
        etRainfall.setHintTextColor(Color.parseColor("#3D5A78"))
        etTank.setHintTextColor(Color.parseColor("#3D5A78"))

        btnMonthly.setOnClickListener {
            startActivity(Intent(this, MonthlyReportActivity::class.java))
        }
        btnTips.setOnClickListener {
            startActivity(Intent(this, TipsActivity::class.java))
        }

        btnCalculate.setOnClickListener {
            hideKeyboard()
            handleCalculate()
        }

        loadDashboardTotals()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun handleCalculate() {
        // Validate — clear previous errors
        etArea.error = null
        etRainfall.error = null
        etTank.error = null

        val areaStr = etArea.text.toString().trim()
        val rainfallStr = etRainfall.text.toString().trim()
        val tankStr = etTank.text.toString().trim()

        var hasError = false

        val area = areaStr.toDoubleOrNull()
        if (areaStr.isEmpty() || area == null || area <= 0) {
            etArea.error = "Enter a valid roof area (sq ft)"
            hasError = true
        }

        val rainfall = rainfallStr.toDoubleOrNull()
        if (rainfallStr.isEmpty() || rainfall == null || rainfall <= 0) {
            etRainfall.error = "Enter a valid rainfall in mm"
            hasError = true
        }

        val tankCapacity = tankStr.toDoubleOrNull()
        if (tankStr.isEmpty() || tankCapacity == null || tankCapacity <= 0) {
            etTank.error = "Enter a valid tank capacity (L)"
            hasError = true
        }

        if (hasError) return

        val runoffCoefficient = 0.8
        val water = area!! * rainfall!! * 0.0929 * runoffCoefficient
        val dailyNeed = 540.0
        val days = water / dailyNeed
        val percentage = ((water / tankCapacity!!) * 100).toInt().coerceIn(0, 100)

        tvWaterCollected.text = "%.1f L".format(water)
        tvDaysSupported.text = "%.1f days".format(days)
        tvResult.text = "Water: %.2f L | Days: %.1f".format(water, days)
        progressTank.progress = percentage

        animateTankFill(percentage)
        updateDropGrid(water)
        llDropGrid.visibility = View.VISIBLE

        // Save to Room DB
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val entry = RainfallEntry(
            dateMillis = now,
            roofAreaSqFt = area,
            rainfallMm = rainfall,
            tankCapacityL = tankCapacity,
            waterCollectedL = water,
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )

        scope.launch {
            withContext(Dispatchers.IO) { db.rainfallDao().insert(entry) }
            loadDashboardTotals()
        }
    }

    private fun loadDashboardTotals() {
        scope.launch {
            val total = withContext(Dispatchers.IO) {
                db.rainfallDao().getTotalWater()
            }
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val today = withContext(Dispatchers.IO) {
                db.rainfallDao().getTodayTotal(startOfDay)
            }
            tvTodaySaved.text = "%.1f L".format(today)
            tvTotalSaved.text = "%.0f L".format(total)
        }
    }

    private fun animateTankFill(targetPercent: Int) {
        cardTank.post {
            val actualHeight = cardTank.height
            val targetHeight = (actualHeight * targetPercent / 100f).toInt()
            val animator = ValueAnimator.ofInt(0, targetHeight)
            animator.duration = 1000
            animator.addUpdateListener { anim ->
                val h = anim.animatedValue as Int
                val params = viewWaveFill.layoutParams
                params.height = h
                viewWaveFill.layoutParams = params
                val pct = ((h.toFloat() / actualHeight) * 100).toInt()
                tvTankPercent.text = "$pct%"
            }
            animator.start()
        }
    }

    private fun updateDropGrid(waterLiters: Double) {
        llDrops.removeAllViews()
        val dropUnit = 100.0
        val totalDrops = 20
        val filledDrops = (waterLiters / dropUnit).toInt().coerceIn(0, totalDrops)
        tvDropSubtitle.text = "Each 💧 = ${dropUnit.toInt()} L  ·  $filledDrops/$totalDrops filled"

        var currentRow: LinearLayout? = null
        for (i in 0 until totalDrops) {
            if (i % 5 == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = 8 }
                }
                llDrops.addView(currentRow)
            }
            val drop = TextView(this).apply {
                text = "💧"
                textSize = 22f
                alpha = if (i < filledDrops) 1.0f else 0.2f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            currentRow?.addView(drop)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}