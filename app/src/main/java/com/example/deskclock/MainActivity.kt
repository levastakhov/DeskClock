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

    private val apiKey = "4adf465a46e69ad085bb117b45fc0a67"
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
        val urlString = "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$apiKey&units=metric&lang=ru"
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

                    val main = json.getJSONObject("main")
                    val temp = main.getDouble("temp").roundToInt()
                    val tempString = if (temp > 0) "+$temp°C" else "$temp°C"

                    val weatherArray = json.getJSONArray("weather")
                    var icon = "🌡️"
                    var description = ""

                    if (weatherArray.length() > 0) {
                        val weatherObj = weatherArray.getJSONObject(0)
                        description = weatherObj.getString("description").uppercase(Locale("ru"))
                        icon = decodeOpenWeatherIcon(weatherObj.getString("icon"))
                    }

                    val formattedWeather = "$icon $tempString $description".trim()

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

    private fun decodeOpenWeatherIcon(iconCode: String): String {
        return when (iconCode.take(2)) {
            "01" -> "☀️" // clear sky
            "02" -> "⛅" // few clouds
            "03" -> "☁️" // scattered clouds
            "04" -> "☁️" // broken/overcast clouds
            "09" -> "🌧️" // shower rain
            "10" -> "🌧️" // rain
            "11" -> "🌩️" // thunderstorm
            "13" -> "❄️" // snow
            "50" -> "🌫️" // mist/fog
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