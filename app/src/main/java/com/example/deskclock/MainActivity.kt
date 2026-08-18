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

    private val latitude = 56.4977
    private val longitude = 84.9744

    private val weatherDisplayDuration = 20_000L // 20 секунд
    private val dateDisplayDuration = 10_000L    // 10 секунд

    private val toggleRunnable = object : Runnable {
        override fun run() {
            val nextDelay = when (currentMode) {
                DisplayMode.WEATHER -> {
                    topInfoSwitcher.setText(currentWeatherText)
                    currentMode = DisplayMode.DATE
                    weatherDisplayDuration
                }
                DisplayMode.DATE -> {
                    topInfoSwitcher.setText(getCurrentDateString())
                    currentMode = DisplayMode.WEATHER
                    dateDisplayDuration
                }
            }
            handler.postDelayed(this, nextDelay)
        }
    }

    private val weatherRunnable = object : Runnable {
        override fun run() {
            fetchWeatherData()
            handler.postDelayed(this, 15 * 60 * 1000L) // Обновление каждые 15 минут
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
        handler.removeCallbacks(toggleRunnable)
        handler.post(toggleRunnable)
        
        handler.removeCallbacks(weatherRunnable)
        handler.post(weatherRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(toggleRunnable)
        handler.removeCallbacks(weatherRunnable)
    }

    private fun fetchWeatherData() {
        // Запрос к Open-Meteo с немецкой моделью DWD ICON
        val urlString = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude" +
                "&longitude=$longitude" +
                "&current=temperature_2m,weather_code" +
                "&models=icon_seamless" +
                "&wind_speed_unit=ms" +
                "&timezone=Asia/Novosibirsk"

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

                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").roundToInt()
                    val weatherCode = current.getInt("weather_code")

                    val tempString = if (temp > 0) "+$temp°C" else "$temp°C"
                    val icon = decodeWmoWeatherCode(weatherCode)

                    val formattedWeather = "$icon $tempString".trim()

                    handler.post {
                        currentWeatherText = formattedWeather
                        if (currentMode == DisplayMode.DATE) {
                            topInfoSwitcher.setText(currentWeatherText)
                        }
                    }
                }
            }
        })
    }

    private fun decodeWmoWeatherCode(code: Int): String {
        return when (code) {
            0 -> "☀️"            // Clear sky
            1 -> "🌤️"           // Mainly clear
            2 -> "⛅"            // Partly cloudy
            3 -> "☁️"            // Overcast
            45, 48 -> "🌫️"       // Fog
            51, 53, 55 -> "🌦️"   // Drizzle
            56, 57 -> "🌧️"       // Freezing Drizzle
            61, 63 -> "🌧️"       // Rain
            65 -> "🌧️💥"         // Heavy Rain
            66, 67 -> "🧊🌧️"     // Freezing Rain
            71, 73, 75 -> "🌨️"   // Snow fall
            77 -> "❄️"           // Snow grains
            80, 81, 82 -> "🌧️⚡" // Rain showers
            85, 86 -> "❄️🌨️"     // Snow showers
            95 -> "🌩️"          // Thunderstorm
            96, 99 -> "⛈️"       // Thunderstorm with hail
            else -> "🌡️"
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