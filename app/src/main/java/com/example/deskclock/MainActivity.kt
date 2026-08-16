package com.example.deskclock

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var topInfoSwitcher: TextSwitcher
    private val handler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()

    private enum class DisplayMode { WEATHER, DATE }
    private var currentMode = DisplayMode.WEATHER
    private var currentWeatherText = "⌛ Загрузка..."

    // Координаты (Томск)
    private val latitude = 56.4977
    private val longitude = 84.9744

    private val toggleRunnable = object : Runnable {
        override fun run() {
            when (currentMode) {
                DisplayMode.WEATHER -> {
                    topInfoSwitcher.setText(currentWeatherText)
                    currentMode = DisplayMode.DATE
                }
                DisplayMode.DATE -> {
                    topInfoSwitcher.setText(getCurrentDateString())
                    currentMode = DisplayMode.WEATHER
                }
            }
            handler.postDelayed(this, 20_000L)
        }
    }

    private val weatherRunnable = object : Runnable {
        override fun run() {
            fetchWeatherData()
            // Обновляем погоду каждые 15 минут
            handler.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        setContentView(R.layout.activity_main)

        topInfoSwitcher = findViewById(R.id.topInfoSwitcher)
        topInfoSwitcher.setFactory {
            TextView(this@MainActivity).apply {
                textSize = 100f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        handler.post(toggleRunnable)
        handler.post(weatherRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(toggleRunnable)
        handler.removeCallbacks(weatherRunnable)
    }

    private fun fetchWeatherData() {
        // Запрос к бесплатному Open-Meteo без API-ключей
        val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true"
        val httpUrl = urlString.toHttpUrlOrNull() ?: return

        val request = Request.Builder()
            .url(httpUrl)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    if (currentWeatherText == "⌛ Загрузка...") {
                        currentWeatherText = "⚠️ Нет сети"
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        handler.post {
                            currentWeatherText = "⚠️ Ошибка ${response.code}"
                        }
                        return
                    }

                    val responseBody = response.body?.string() ?: return
                    val json = JSONObject(responseBody)
                    val currentWeather = json.getJSONObject("current_weather")

                    val tempDouble = currentWeather.getDouble("temperature")
                    val temp = tempDouble.roundToInt()
                    val weatherCode = currentWeather.getInt("weathercode")

                    val tempString = if (temp > 0) "+$temp°C" else "$temp°C"
                    val (icon, description) = decodeWmoCode(weatherCode)

                    val formattedWeather = "$icon $tempString $description".trim()

                    handler.post {
                        currentWeatherText = formattedWeather
                        // Обновляем текст сразу же, если в данный момент отображается погода
                        if (currentMode == DisplayMode.DATE) {
                            topInfoSwitcher.setText(currentWeatherText)
                        }
                    }
                }
            }
        })
    }

    // Расшифровка WMO-кодов погоды (World Meteorological Organization)
    private fun decodeWmoCode(code: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("☀️", "ЯСНО")
            1, 2 -> Pair("⛅", "МАЛООБЛАЧНО")
            3 -> Pair("☁️", "ПАСМУРНО")
            45, 48 -> Pair("🌫️", "ТУМАН")
            51, 53, 55 -> Pair("🌧️", "МОРОСЬ")
            56, 57 -> Pair("🌧️❄️", "ЛЕ ДЯНАЯ МОРОСЬ")
            61, 63 -> Pair("🌧️", "ДОЖДЬ")
            65 -> Pair("🌧️", "СИЛЬНЫЙ ДОЖДЬ")
            66, 67 -> Pair("🌧️❄️", "ЗАМЕРЗАЮЩИЙ ДОЖДЬ")
            71, 73 -> Pair("❄️", "СНЕГ")
            75 -> Pair("❄️", "СИЛЬНЫЙ СНЕГ")
            77 -> Pair("❄️", "СНЕЖНАЯ КРУПА")
            80, 81, 82 -> Pair("🌧️", "ЛИВЕНЬ")
            85, 86 -> Pair("❄️", "СНЕГОПАД")
            95 -> Pair("🌩️", "ГРОЗА")
            96, 99 -> Pair("🌩️", "ГРОЗА С ГРАДОМ")
            else -> Pair("🌡️", "")
        }
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("E dd.MM", Locale("ru"))
        val formattedDate = sdf.format(Date())
        return formattedDate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.dispatcher.cancelAll()
    }
}