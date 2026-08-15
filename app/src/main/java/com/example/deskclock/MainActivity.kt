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
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var topInfoSwitcher: TextSwitcher
    private val handler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()

    private var isShowingWeather = true
    private var currentWeatherText = "⌛ Загрузка..."

    // Координаты (например, Москва: 55.7558, 37.6173)
    private val latitude = 55.7558
    private val longitude = 37.6173

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

        startToggleLoop()
        startWeatherFetchLoop()
    }

    private fun startToggleLoop() {
        val runnable = object : Runnable {
            override fun run() {
                if (isShowingWeather) {
                    topInfoSwitcher.setText(currentWeatherText)
                } else {
                    topInfoSwitcher.setText(getCurrentDateString())
                }
                isShowingWeather = !isShowingWeather
                handler.postDelayed(this, 20000)
            }
        }
        handler.post(runnable)
    }

    private fun startWeatherFetchLoop() {
        val weatherRunnable = object : Runnable {
            override fun run() {
                fetchWeatherData()
                // Обновляем погоду каждые 30 минут (1 800 000 мс)
                handler.postDelayed(this, 30 * 60 * 1000L)
            }
        }
        handler.post(weatherRunnable)
    }

    private fun fetchWeatherData() {
        val url = "[https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true](https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true)"

        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // В случае ошибки сети оставляем предыдущее значение или выводим индикатор
                handler.post {
                    if (currentWeatherText == "⌛ Загрузка...") {
                        currentWeatherText = "⚠️ Нет сети"
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) return

                    val responseBody = response.body?.string() ?: return
                    val json = JSONObject(responseBody)
                    val currentWeather = json.getJSONObject("current_weather")

                    val temp = currentWeather.getDouble("temperature").toInt()
                    val weatherCode = currentWeather.getInt("weathercode")

                    val tempString = if (temp > 0) "+$temp°C" else "$temp°C"
                    val (icon, description) = decodeWeatherCode(weatherCode)

                    val formattedWeather = "$icon $tempString $description"

                    handler.post {
                        currentWeatherText = formattedWeather
                    }
                }
            }
        })
    }

    private fun decodeWeatherCode(code: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("☀️", "ЯСНО")
            1, 2, 3 -> Pair("⛅", "OБЛАЧНО")
            45, 48 -> Pair("🌫️", "ТУМАН")
            51, 53, 55, 56, 57 -> Pair("🌧️", "MOROSI")
            61, 63, 65, 66, 67 -> Pair("🌧️", "ДОЖДЬ")
            71, 73, 75, 77 -> Pair("❄️", "СНЕГ")
            80, 81, 82 -> Pair("🌧️", "ЛИВЕНЬ")
            85, 86 -> Pair("❄️", "СНЕГОПАД")
            95, 96, 99 -> Pair("🌩️", "ГРОЗА")
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
}