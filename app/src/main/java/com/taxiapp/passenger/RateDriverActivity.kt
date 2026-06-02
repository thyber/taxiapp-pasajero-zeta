package com.taxiapp.passenger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class RateDriverActivity : AppCompatActivity() {
    
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var txtDriverName: TextView
    private lateinit var txtDriverVehicle: TextView
    private lateinit var star1: TextView
    private lateinit var star2: TextView
    private lateinit var star3: TextView
    private lateinit var star4: TextView
    private lateinit var star5: TextView
    private lateinit var txtComment: EditText
    private lateinit var btnSubmitRating: Button
    
    private var rideId: String = ""
    private var driverId: String = ""
    private var driverName: String = ""
    private var driverVehicle: String = ""
    private var selectedRating = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_driver)
        
        firebaseHelper = FirebaseHelper()
        initViews()
        setupClickListeners()
        
        rideId = intent.getStringExtra("rideId") ?: ""
        driverId = intent.getStringExtra("driverId") ?: ""
        driverName = intent.getStringExtra("driverName") ?: "Conductor"
        driverVehicle = intent.getStringExtra("driverVehicle") ?: "Vehículo"
        
        txtDriverName.text = driverName
        txtDriverVehicle.text = driverVehicle
    }
    
    private fun initViews() {
        txtDriverName = findViewById(R.id.txtDriverName)
        txtDriverVehicle = findViewById(R.id.txtDriverVehicle)
        star1 = findViewById(R.id.star1)
        star2 = findViewById(R.id.star2)
        star3 = findViewById(R.id.star3)
        star4 = findViewById(R.id.star4)
        star5 = findViewById(R.id.star5)
        txtComment = findViewById(R.id.txtComment)
        btnSubmitRating = findViewById(R.id.btnSubmitRating)
    }
    
    private fun setupClickListeners() {
        star1.setOnClickListener { setRating(1) }
        star2.setOnClickListener { setRating(2) }
        star3.setOnClickListener { setRating(3) }
        star4.setOnClickListener { setRating(4) }
        star5.setOnClickListener { setRating(5) }
        
        btnSubmitRating.setOnClickListener {
            if (selectedRating == 0) {
                Toast.makeText(this, "Por favor selecciona una calificación", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val comment = txtComment.text.toString()
            
            if (driverId.isNotEmpty()) {
                val database = FirebaseDatabase.getInstance()
                
                val ratingRef = database.getReference("drivers").child(driverId).child("ratings").push()
                val ratingData = mapOf(
                    "rating" to selectedRating,
                    "comment" to comment,
                    "rideId" to rideId,
                    "timestamp" to System.currentTimeMillis()
                )
                
                ratingRef.setValue(ratingData).addOnCompleteListener { ratingTask ->
                    if (ratingTask.isSuccessful) {
                        val driverRef = database.getReference("drivers").child(driverId)
                        driverRef.get().addOnCompleteListener { driverTask ->
                            if (driverTask.isSuccessful) {
                                val snapshot = driverTask.result
                                val currentRating = snapshot?.child("averageRating")?.getValue(Double::class.java) ?: 0.0
                                val ratingCount = snapshot?.child("ratingCount")?.getValue(Int::class.java) ?: 0
                                
                                val newCount = ratingCount + 1
                                val newAverage = ((currentRating * ratingCount) + selectedRating) / newCount
                                
                                driverRef.child("averageRating").setValue(newAverage)
                                driverRef.child("ratingCount").setValue(newCount).addOnCompleteListener {
                                    Toast.makeText(this, "¡Gracias por tu calificación! 😊", Toast.LENGTH_LONG).show()
                                    goToHome()
                                }
                            } else {
                                Toast.makeText(this, "¡Gracias por tu calificación! 😊", Toast.LENGTH_LONG).show()
                                goToHome()
                            }
                        }
                    } else {
                        Toast.makeText(this, "¡Gracias por tu calificación! 😊", Toast.LENGTH_LONG).show()
                        goToHome()
                    }
                }
            } else {
                Toast.makeText(this, "¡Gracias por tu calificación! 😊", Toast.LENGTH_LONG).show()
                goToHome()
            }
        }
    }
    
    private fun setRating(rating: Int) {
        selectedRating = rating
        
        val stars = listOf(star1, star2, star3, star4, star5)
        val yellowColor = 0xFFfbbf24.toInt()
        val grayColor = 0xFFd1d5db.toInt()
        
        for (i in 0 until stars.size) {
            if (i < rating) {
                stars[i].text = "★"
                stars[i].setTextColor(yellowColor)
            } else {
                stars[i].text = "☆"
                stars[i].setTextColor(grayColor)
            }
        }
    }
    
    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
