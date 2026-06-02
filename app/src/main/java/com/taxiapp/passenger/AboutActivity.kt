package com.taxiapp.passenger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        
        initViews()
        setupClickListeners()
    }
    
    private fun initViews() {
        tvEmail = findViewById(R.id.tvEmail)
        tvPhone = findViewById(R.id.tvPhone)
    }
    
    private fun setupClickListeners() {
        tvEmail.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:radio.movil.zeta@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Consulta - Radio Móvil Zeta")
            }
            startActivity(Intent.createChooser(emailIntent, "Enviar correo"))
        }
        
        tvPhone.setOnClickListener {
            openWhatsAppMessage("59177696600")
        }
    }
    
    private fun openWhatsAppMessage(phone: String) {
        val url = "https://api.whatsapp.com/send?phone=$phone"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
