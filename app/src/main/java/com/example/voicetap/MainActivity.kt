package com.example.voicetap

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var marker: View
    private lateinit var container: FrameLayout
    private lateinit var coordText: TextView
    private lateinit var triggerEdit: EditText
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("voicetap_prefs", MODE_PRIVATE)

        container = findViewById(R.id.container)
        marker = findViewById(R.id.marker)
        coordText = findViewById(R.id.coordText)
        triggerEdit = findViewById(R.id.triggerWordEdit)

        triggerEdit.setText(prefs.getString("trigger_word", "tap"))

        container.post {
            val savedX = prefs.getFloat("tap_x", -1f)
            val savedY = prefs.getFloat("tap_y", -1f)
            if (savedX >= 0f && savedY >= 0f) {
                marker.x = savedX - marker.width / 2f
                marker.y = savedY - marker.height / 2f
            } else {
                marker.x = container.width / 2f - marker.width / 2f
                marker.y = container.height / 2f - marker.height / 2f
            }
            updateCoordText()
        }

        marker.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_MOVE -> {
                    view.x = (event.rawX - view.width / 2f)
                        .coerceIn(0f, (container.width - view.width).toFloat())
                    view.y = (event.rawY - view.height / 2f)
                        .coerceIn(0f, (container.height - view.height).toFloat())
                    updateCoordText()
                    true
                }
                else -> false
            }
        }

        findViewById<Button>(R.id.saveBtn).setOnClickListener {
            val centerX = marker.x + marker.width / 2f
            val centerY = marker.y + marker.height / 2f
            prefs.edit()
                .putFloat("tap_x", centerX)
                .putFloat("tap_y", centerY)
                .putString("trigger_word", triggerEdit.text.toString().trim().ifBlank { "tap" })
                .apply()
            Toast.makeText(this, "Position saved: (${centerX.toInt()}, ${centerY.toInt()})", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.accessibilityBtn).setOnClickListener {
            Toast.makeText(this, "Find and enable 'VoiceTap' in the list", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            if (requestPermissionsIfNeeded()) {
                startListening()
            }
        }

        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, VoiceListenerService::class.java))
            Toast.makeText(this, "Listening stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListening() {
        val svcIntent = Intent(this, VoiceListenerService::class.java)
        ContextCompat.startForegroundService(this, svcIntent)
        Toast.makeText(this, "Listening for trigger word...", Toast.LENGTH_SHORT).show()
    }

    private fun updateCoordText() {
        val centerX = (marker.x + marker.width / 2f).toInt()
        val centerY = (marker.y + marker.height / 2f).toInt()
        coordText.text = "Tap position: $centerX, $centerY"
    }

    private fun requestPermissionsIfNeeded(): Boolean {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val need = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startListening()
        } else if (requestCode == 100) {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }
}
