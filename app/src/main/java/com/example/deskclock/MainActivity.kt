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

class MainActivity : AppCompatActivity() {

    private lateinit var topInfoSwitcher: TextSwitcher
    private val handler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()

    private var isShowingWeather = true
    private var currentWeatherText = "⌛ Загрузка..."

    // API-ключ от Яндекс Погоды
    private val yandexApiKey = "2cca5a2a-c25a-4282-8712-2446b2f93dbb"

    
    // Координаты (Томск)
    private val latitude = 56.4977
    private val longitude = 84.9744

    private val toggleRunnable = object : Runnable {
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

    private val weatherRunnable = object : Runnable {
        override fun run() {
            fetchWeatherData()
            // Обновляем погоду каждые 30 минут
            handler.postDelayed(this, 30 * 60 * 1000L)
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

        handler.post(toggleRunnable)
        handler.post(weatherRunnable)
    }

    private fun fetchWeatherData() {
        val urlString = "https://api.weather.yandex.ru/v2/forecast?lat=$latitude&lon=$longitude"
        val httpUrl = urlString.toHttpUrlOrNull() ?: return

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("X-Yandex-Weather-Key", yandexApiKey)
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
                    val fact = json.getJSONObject("fact")

                    val temp = fact.getInt("temp")
                    val condition = fact.getString("condition")

                    val tempString = if (temp > 0) "+$temp°C" else "$temp°C"
                    val (icon, description) = decodeYandexCondition(condition)

                    val formattedWeather = "$icon $tempString $description"

                    handler.post {
                        currentWeatherText = formattedWeather
                        // Обновляем текст сразу же, если в данный момент показывается погода
                        if (!isShowingWeather) {
                            topInfoSwitcher.setText(currentWeatherText)
                        }
                    }
                }
            }
        })
    }

    private fun decodeYandexCondition(condition: String): Pair<String, String> {
        return when (condition) {
            "clear" -> Pair("☀️", "ЯСНО")
            "partly-cloudy" -> Pair("⛅", "МАЛООБЛАЧНО")
            "cloudy" -> Pair("⛅", "ОБЛАЧНО")
            "overcast" -> Pair("☁️", "пасмурно")
            "drizzle" -> Pair("🌧️", "морось")
            "light-rain" -> Pair("🌧️", "НЕБОЛЬШОЙ ДОЖДЬ")
            "rain" -> Pair("🌧️", "ДОЖДЬ")
            "heavy-rain" -> Pair("🌧️", "СИЛЬНЫЙ ДОЖДЬ")
            "showers" -> Pair("🌧️", "ЛИВЕНЬ")
            "wet-snow" -> Pair("🌧️❄️", "ДОЖДЬ СО СНЕГОМ")
            "light-snow" -> Pair("❄️", "НЕБОЛЬШОЙ СНЕГ")
            "snow" -> Pair("❄️", "СНЕГ")
            "snow-showers" -> Pair("❄️", "СНЕГОПАД")
            "hail" -> Pair("🌨️", "ГРАД")
            "thunderstorm" -> Pair("🌩️", "ГРОЗА")
            "thunderstorm-with-rain" -> Pair("🌩️", "ГРОЗА С ДОЖДЕМ")
            "thunderstorm-with-hail" -> Pair("🌩️", "ГРОЗА С ГРАДОМ")
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
        // Очищаем таймеры при закрытии или пересоздании Activity
        handler.removeCallbacks(toggleRunnable)
        handler.removeCallbacks(weatherRunnable)
    }
}