package com.taxiapp.passenger

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PromotionsActivity : AppCompatActivity() {
    
    private lateinit var txtReferralCode: TextView
    private lateinit var txtWalletBalance: TextView
    private lateinit var txtTotalRides: TextView
    private lateinit var txtReferralCount: TextView
    private lateinit var btnCopyCode: Button
    private lateinit var sessionManager: SessionManager
    private lateinit var database: FirebaseDatabase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promotions)
        
        sessionManager = SessionManager(this)
        database = FirebaseDatabase.getInstance()
        
        initViews()
        setupClickListeners()
        loadUserData()
    }
    
    private fun initViews() {
        txtReferralCode = findViewById(R.id.txtReferralCode)
        txtWalletBalance = findViewById(R.id.txtWalletBalance)
        txtTotalRides = findViewById(R.id.txtTotalRides)
        txtReferralCount = findViewById(R.id.txtReferralCount)
        btnCopyCode = findViewById(R.id.btnCopyCode)
    }
    
    private fun setupClickListeners() {
        btnCopyCode.setOnClickListener {
            copyReferralCode()
        }
    }
    
    private fun loadUserData() {
        val userId = sessionManager.getUserId() ?: return
        
        val userRef = database.getReference("passengers").child(userId)
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val referralCode = snapshot.child("referralCode").getValue(String::class.java) ?: ""
                val walletBalance = snapshot.child("walletBalance").getValue(Double::class.java) ?: 0.0
                val totalRides = snapshot.child("totalRides").getValue(Int::class.java) ?: 0
                val referralCount = snapshot.child("referralCount").getValue(Int::class.java) ?: 0
                
                sessionManager.setReferralCode(referralCode)
                sessionManager.setWalletBalance(walletBalance)
                sessionManager.setTotalRides(totalRides)
                sessionManager.setReferralCount(referralCount)
                
                txtReferralCode.text = referralCode
                txtWalletBalance.text = "Bs ${String.format("%.0f", walletBalance)}"
                txtTotalRides.text = totalRides.toString()
                txtReferralCount.text = referralCount.toString()
            }
            
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    private fun copyReferralCode() {
        val code = txtReferralCode.text.toString()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Código de invitación", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "¡Código copiado!", Toast.LENGTH_SHORT).show()
    }
}
