package com.taxiapp.passenger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors

class QRBotActivity : AppCompatActivity() {

    private lateinit var imgQR: ImageView
    private lateinit var txtStatus: TextView
    private lateinit var btnRefresh: Button
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    
    // Cambia esta IP por la de tu computadora en la red local
    private val SERVER_URL = "http://192.168.43.73:3001"

    private val updateRunnable = object : Runnable {
        override fun run() {
            loadQRStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_bot)

        imgQR = findViewById(R.id.imgQR)
        txtStatus = findViewById(R.id.txtStatus)
        btnRefresh = findViewById(R.id.btnRefresh)

        btnRefresh.setOnClickListener {
            loadQRStatus()
        }

        loadQRStatus()
        handler.post(updateRunnable)
    }

    private fun loadQRStatus() {
        executor.execute {
            try {
                val requestStatus = Request.Builder()
                    .url("$SERVER_URL/qr-status")
                    .get()
                    .build()

                val responseStatus = client.newCall(requestStatus).execute()
                if (responseStatus.isSuccessful) {
                    val json = JSONObject(responseStatus.body?.string() ?: "")
                    val authenticated = json.getBoolean("authenticated")
                    val hasQR = json.has("qr") && !json.isNull("qr")

                    runOnUiThread {
                        if (authenticated) {
                            txtStatus.text = "✅ ¡Autenticado correctamente!"
                            txtStatus.setTextColor(0xFF155724.toInt())
                            imgQR.setImageResource(android.R.drawable.ic_menu_info_details)
                        } else if (hasQR) {
                            txtStatus.text = "Escanea el código QR con WhatsApp"
                            txtStatus.setTextColor(0xFF856404.toInt())
                            loadQRImage()
                        } else {
                            txtStatus.text = "Esperando QR..."
                            txtStatus.setTextColor(0xFF64748b.toInt())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadQRImage() {
        executor.execute {
            try {
                val request = Request.Builder()
                    .url("$SERVER_URL/qr-image")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val inputStream = response.body?.byteStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    runOnUiThread {
                        imgQR.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }
}
