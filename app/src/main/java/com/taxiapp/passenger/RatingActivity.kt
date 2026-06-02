package com.taxiapp.passenger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class RatingActivity : AppCompatActivity() {
    
    private lateinit var star1: TextView
    private lateinit var star2: TextView
    private lateinit var star3: TextView
    private lateinit var star4: TextView
    private lateinit var star5: TextView
    private lateinit var btnSubmitRating: Button
    
    private var rideId: String? = null
    private var driverId: String? = null
    private var selectedRating = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rating)
        
        rideId = intent.getStringExtra("rideId")
        driverId = intent.getStringExtra("driverId")
        
        initViews()
        setupClickListeners()
    }
    
    private fun initViews() {
        star1 = findViewById(R.id.star1)
        star2 = findViewById(R.id.star2)
        star3 = findViewById(R.id.star3)
        star4 = findViewById(R.id.star4)
        star5 = findViewById(R.id.star5)
        btnSubmitRating = findViewById(R.id.btnSubmitRating)
    }
    
    private fun setupClickListeners() {
        star1.setOnClickListener { setRating(1) }
        star2.setOnClickListener { setRating(2) }
        star3.setOnClickListener { setRating(3) }
        star4.setOnClickListener { setRating(4) }
        star5.setOnClickListener { setRating(5) }
        
        btnSubmitRating.setOnClickListener {
            submitRating()
        }
    }
    
    private fun setRating(rating: Int) {
        selectedRating = rating
        
        val stars = listOf(star1, star2, star3, star4, star5)
        for (i in stars.indices) {
            stars[i].alpha = if (i < rating) 1.0f else 0.3f
        }
        
        btnSubmitRating.isEnabled = true
        btnSubmitRating.alpha = 1.0f
    }
    
    private fun submitRating() {
        if (selectedRating == 0) {
            Toast.makeText(this, "Selecciona una calificación", Toast.LENGTH_SHORT).show()
            return
        }
        
        driverId?.let { driverId ->
            val database = FirebaseDatabase.getInstance()
            
            val ratingRef = database.getReference("drivers").child(driverId).child("ratings").push()
            ratingRef.setValue(selectedRating).addOnCompleteListener { ratingTask ->
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
                                Toast.makeText(this, "Calificación enviada!", Toast.LENGTH_SHORT).show()
                                goToHome()
                            }
                        } else {
                            Toast.makeText(this, "Calificación enviada!", Toast.LENGTH_SHORT).show()
                            goToHome()
                        }
                    }
                } else {
                    Toast.makeText(this, "Calificación enviada!", Toast.LENGTH_SHORT).show()
                    goToHome()
                }
            }
        } ?: run {
            Toast.makeText(this, "Calificación enviada!", Toast.LENGTH_SHORT).show()
            goToHome()
        }
    }
    
    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }
}
