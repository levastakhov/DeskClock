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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var topInfoSwitcher: TextSwitcher
    private val handler = Handler(Looper.getMainLooper())
    private var isShowingWeather = true
    private var currentWeatherText = "☀️ +16°C ЯСНО"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        setContentView(R.layout.activity_main)

        topInfoSwitcher = findViewById(R.id.topInfoSwitcher)
        topInfoSwitcher.setFactory {
            TextView(this@MainActivity).apply {
                textSize = 90f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
            }
        }

        startToggleLoop()
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
