package com.taxiapp.passenger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Random

class RegisterActivity : AppCompatActivity() {
    
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etCountryCode: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var etReferralCode: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView
    private lateinit var sessionManager: SessionManager
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        sessionManager = SessionManager(this)
        initViews()
        setupClickListeners()
    }
    
    private fun initViews() {
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etCountryCode = findViewById(R.id.etCountryCode)
        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etPassword)
        etReferralCode = findViewById(R.id.etReferralCode)
        btnRegister = findViewById(R.id.btnRegister)
        tvLogin = findViewById(R.id.tvLogin)
    }
    
    private fun setupClickListeners() {
        btnRegister.setOnClickListener {
            registerUser()
        }
        
        tvLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
    
    private fun generateReferralCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = Random()
        val code = StringBuilder()
        for (i in 0..5) {
            code.append(chars[random.nextInt(chars.length)])
        }
        return code.toString()
    }
    
    private fun registerUser() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val referralCodeInput = etReferralCode.text.toString().trim().uppercase()
        
        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        
        val emailPattern = android.util.Patterns.EMAIL_ADDRESS
        if (!emailPattern.matcher(email).matches()) {
            Toast.makeText(this, "Por favor ingresa un correo electrónico válido", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (password.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val referralCode = generateReferralCode()
                    saveUserDataToDatabase(userId, name, email, phone, referralCode, referralCodeInput)
                } else {
                    Toast.makeText(this, "Error al registrarse: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun saveUserDataToDatabase(userId: String, name: String, email: String, phone: String, referralCode: String, referralCodeInput: String) {
        val userData = hashMapOf(
            "id" to userId,
            "name" to name,
            "email" to email,
            "phone" to phone,
            "referralCode" to referralCode,
            "ridesWithDiscount" to 2,
            "walletBalance" to 0.0,
            "totalRides" to 0,
            "referralCount" to 0,
            "createdAt" to System.currentTimeMillis()
        )
        
        if (referralCodeInput.isNotEmpty()) {
            userData["referredBy"] = referralCodeInput
            processReferralCode(referralCodeInput)
        }
        
        val userRef = database.getReference("passengers").child(userId)
        userRef.setValue(userData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    sessionManager.saveSession(userId, name, email, phone)
                    sessionManager.setReferralCode(referralCode)
                    Toast.makeText(this@RegisterActivity, "¡Registro exitoso! Bienvenido $name", Toast.LENGTH_SHORT).show()
                    goToMainActivity()
                } else {
                    Toast.makeText(this@RegisterActivity, "Error al guardar datos del usuario", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun processReferralCode(referralCode: String) {
        val passengersRef = database.getReference("passengers")
        passengersRef.orderByChild("referralCode").equalTo(referralCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            val referrerId = userSnapshot.key ?: continue
                            val currentBalance = userSnapshot.child("walletBalance").getValue(Double::class.java) ?: 0.0
                            val currentReferralCount = userSnapshot.child("referralCount").getValue(Int::class.java) ?: 0
                            
                            val referrerRef = database.getReference("passengers").child(referrerId)
                            referrerRef.updateChildren(mapOf(
                                "walletBalance" to (currentBalance + 5.0),
                                "referralCount" to (currentReferralCount + 1)
                            ))
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    
    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
