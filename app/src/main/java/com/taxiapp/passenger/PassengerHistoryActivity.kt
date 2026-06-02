package com.taxiapp.passenger

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PassengerHistoryActivity : AppCompatActivity() {
    
    private lateinit var sessionManager: SessionManager
    private lateinit var listViewRides: ListView
    
    private val database = FirebaseDatabase.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passenger_history)
        
        sessionManager = SessionManager(this)
        
        initViews()
        loadHistory()
    }
    
    private fun initViews() {
        listViewRides = findViewById(R.id.listViewRides)
    }
    
    private fun loadHistory() {
        val passengerId = sessionManager.getUserId() ?: return
        
        val ridesRef = database.getReference("passengers").child(passengerId).child("rides")
        ridesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rides = mutableListOf<String>()
                    
                    for (rideSnapshot in snapshot.children) {
                        val rideData = rideSnapshot.value as? Map<String, Any> ?: continue
                        val status = rideData["status"] as? String ?: ""
                        
                        if (status == "completed" || status == "accepted" || status == "in_progress") {
                            val pickup = rideData["pickup"] as? String ?: "Sin dirección"
                            val destination = rideData["destination"] as? String ?: "Sin destino"
                            var fareValue = 0.0
                            when (val fareObj = rideData["fare"]) {
                                is String -> fareValue = fareObj.toDoubleOrNull() ?: 0.0
                                is Double -> fareValue = fareObj
                                is Int -> fareValue = fareObj.toDouble()
                            }
                            
                            var driverName = "Conductor"
                            val driverData = rideData["driverData"] as? Map<String, Any>
                            if (driverData != null) {
                                driverName = driverData["name"] as? String ?: "Conductor"
                            }
                            
                            val distance = rideData["distanceKm"] as? Double ?: 0.0
                            
                            val statusText = when (status) {
                                "completed" -> "✅ Completado"
                                "accepted" -> "⏳ Aceptado"
                                "in_progress" -> "🚗 En curso"
                                else -> "📋 Pendiente"
                            }
                            
                            val distanceText = if (distance > 0) "📏 ${String.format("%.1f", distance)} km" else ""
                            val rideText = "$statusText\n📍 $pickup\n🏁 $destination\n👤 $driverName\n💰 Bs ${fareValue.toInt()} $distanceText"
                            rides.add(rideText)
                        }
                    }
                    
                    val adapter = ArrayAdapter(this@PassengerHistoryActivity, android.R.layout.simple_list_item_1, rides.reversed())
                    listViewRides.adapter = adapter
                    
                    if (rides.isEmpty()) {
                        Toast.makeText(this@PassengerHistoryActivity, "No tienes carreras aún", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@PassengerHistoryActivity, "Error al cargar historial", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
