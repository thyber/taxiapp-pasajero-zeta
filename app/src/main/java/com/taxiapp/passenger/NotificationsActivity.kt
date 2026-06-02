package com.taxiapp.passenger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class NotificationsActivity : AppCompatActivity() {
    
    private lateinit var sessionManager: SessionManager
    private lateinit var listViewNotifications: ListView
    
    private val database = FirebaseDatabase.getInstance()
    private val notifications = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    
    companion object {
        private const val CHANNEL_ID = "passenger_notifications"
        private const val NOTIFICATION_ID = 2001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        
        sessionManager = SessionManager(this)
        
        initViews()
        createNotificationChannel()
        loadNotifications()
        listenForNewNotifications()
    }
    
    private fun initViews() {
        listViewNotifications = findViewById(R.id.listViewNotifications)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, notifications)
        listViewNotifications.adapter = adapter
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones de Pasajeros"
            val descriptionText = "Notificaciones importantes para pasajeros"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showSystemNotification(title: String, message: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun listenForNewNotifications() {
        val passengerId = sessionManager.getUserId() ?: return
        val notificationsRef = database.getReference("passengers").child(passengerId).child("notifications")
        
        notificationsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val notificationData = snapshot.value as? Map<String, Any> ?: return
                
                val title = notificationData["title"] as? String ?: "Notificación"
                val message = notificationData["message"] as? String ?: ""
                val timestamp = notificationData["createdAt"] as? Long ?: 0
                
                val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(timestamp))
                
                val notificationText = "📢 $title\n$message\n🕐 $date"
                notifications.add(0, notificationText)
                adapter.notifyDataSetChanged()
                
                // Mostrar notificación del sistema
                showSystemNotification(title, message)
                
                // Marcar como leída (opcional)
                snapshot.ref.child("read").setValue(true)
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    private fun loadNotifications() {
        val passengerId = sessionManager.getUserId() ?: return
        val notificationsRef = database.getReference("passengers").child(passengerId).child("notifications")
        
        notificationsRef.orderByChild("createdAt").limitToLast(50)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    notifications.clear()
                    
                    for (notificationSnapshot in snapshot.children) {
                        val notificationData = notificationSnapshot.value as? Map<String, Any> ?: continue
                        val title = notificationData["title"] as? String ?: "Notificación"
                        val message = notificationData["message"] as? String ?: ""
                        val timestamp = notificationData["createdAt"] as? Long ?: 0
                        
                        val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(timestamp))
                        
                        val notificationText = "📢 $title\n$message\n🕐 $date"
                        notifications.add(0, notificationText)
                    }
                    
                    adapter.notifyDataSetChanged()
                    
                    if (notifications.isEmpty()) {
                        Toast.makeText(this@NotificationsActivity, "No hay notificaciones", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@NotificationsActivity, "Error al cargar notificaciones", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
