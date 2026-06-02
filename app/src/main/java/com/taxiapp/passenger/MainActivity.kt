package com.taxiapp.passenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.socket.client.IO
import io.socket.client.Socket
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var socket: Socket
    private lateinit var fabMenu: FloatingActionButton
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var txtOrigen: TextView
    private lateinit var txtDestino: TextView
    private lateinit var txtDistancia: TextView
    private lateinit var txtTiempo: TextView
    private lateinit var txtTarifa: TextView
    private lateinit var btnSolicitar: Button
    private lateinit var pinCentro: ImageView
    private lateinit var sessionManager: SessionManager
    private lateinit var database: FirebaseDatabase
    private lateinit var btnPaymentCash: LinearLayout
    private lateinit var btnPaymentWallet: LinearLayout
    private lateinit var btnPaymentQR: LinearLayout
    private var selectedPaymentMethod = "Efectivo"
    
    private var googleMap: GoogleMap? = null
    private val driverMarkers = mutableMapOf<String, Marker>()
    private var routePolyline: Polyline? = null
    private var pickupMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var currentRide: JSONObject? = null
    private var destinationLocation: LatLng? = null
    private var currentFare = 0.0
    private var currentDistance = 0.0
    private var currentTime = 0
    private var selectedVehicleType = "Auto"
    private var specialRoutes = emptyList<Map<String, Any>>()
    
    private val SERVER_URL = "http://192.168.43.73:3000"
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    private var minFare = 10.0
    private var dayRatePerKm = 3.0
    private var nightRatePerKm = 4.0
    private var dayRatePerMin = 0.5
    private var nightRatePerMin = 0.7
    private var nightStartTime = "21:00"
    private var nightEndTime = "06:00"
    private var specialMultiplier = 1.5
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        sessionManager = SessionManager(this)
        database = FirebaseDatabase.getInstance()
        
        if (!sessionManager.isLoggedIn()) {
            goToLoginActivity()
            return
        }
        
        initViews()
        setupClickListeners()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        try {
            socket = IO.socket(SERVER_URL)
            socket.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        setupSocketListeners()
        
        checkLocationPermission()
        
        loadSystemSettings()
    }
    
    private fun loadSystemSettings() {
        val database = FirebaseDatabase.getInstance()
        val settingsRef = database.getReference("systemSettings")
        
        settingsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                minFare = snapshot.child("minFare").getValue(Double::class.java) ?: 10.0
                dayRatePerKm = snapshot.child("dayRatePerKm").getValue(Double::class.java) ?: 3.0
                nightRatePerKm = snapshot.child("nightRatePerKm").getValue(Double::class.java) ?: 4.0
                dayRatePerMin = snapshot.child("dayRatePerMin").getValue(Double::class.java) ?: 0.5
                nightRatePerMin = snapshot.child("nightRatePerMin").getValue(Double::class.java) ?: 0.7
                nightStartTime = snapshot.child("nightStartTime").getValue(String::class.java) ?: "21:00"
                nightEndTime = snapshot.child("nightEndTime").getValue(String::class.java) ?: "06:00"
                specialMultiplier = snapshot.child("specialMultiplier").getValue(Double::class.java) ?: 1.5
                
                val routesList = mutableListOf<Map<String, Any>>()
                snapshot.child("specialRoutes").children.forEach { routeSnap ->
                    val route = routeSnap.value as? Map<String, Any>
                    route?.let { routesList.add(it) }
                }
                specialRoutes = routesList
            }
            
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }
    
    private fun isNightTime(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val currentTime = hour * 60 + minute
        
        val startParts = nightStartTime.split(":")
        val startHour = startParts[0].toIntOrNull() ?: 21
        val startMinute = startParts.getOrNull(1)?.toIntOrNull() ?: 0
        val startTime = startHour * 60 + startMinute
        
        val endParts = nightEndTime.split(":")
        val endHour = endParts[0].toIntOrNull() ?: 6
        val endMinute = endParts.getOrNull(1)?.toIntOrNull() ?: 0
        val endTime = endHour * 60 + endMinute
        
        return if (startTime > endTime) {
            currentTime >= startTime || currentTime < endTime
        } else {
            currentTime >= startTime && currentTime < endTime
        }
    }
    
    private fun initViews() {
        fabMenu = findViewById(R.id.fabMenu)
        fabMyLocation = findViewById(R.id.fabMyLocation)
        txtOrigen = findViewById(R.id.txtOrigen)
        txtDestino = findViewById(R.id.txtDestino)
        txtDistancia = findViewById(R.id.txtDistancia)
        txtTiempo = findViewById(R.id.txtTiempo)
        txtTarifa = findViewById(R.id.txtTarifa)
        btnSolicitar = findViewById(R.id.btnSolicitar)
        pinCentro = findViewById(R.id.pinCentro)
        btnPaymentCash = findViewById(R.id.btnPaymentCash)
        btnPaymentWallet = findViewById(R.id.btnPaymentWallet)
        btnPaymentQR = findViewById(R.id.btnPaymentQR)
    }
    
    private fun setupClickListeners() {
        fabMenu.setOnClickListener {
            showMenuDialog()
        }
        
        fabMyLocation.setOnClickListener {
            centerOnMyLocation()
        }
        
        btnSolicitar.setOnClickListener {
            requestRide()
        }
        
        btnPaymentCash.setOnClickListener {
            selectPaymentMethod("Efectivo")
        }
        
        btnPaymentWallet.setOnClickListener {
            selectPaymentMethod("Billetera")
        }
        
        btnPaymentQR.setOnClickListener {
            selectPaymentMethod("QR")
        }
    }
    
    private fun selectPaymentMethod(method: String) {
        selectedPaymentMethod = method
        
        btnPaymentCash.setBackgroundResource(if (method == "Efectivo") R.drawable.bg_vehicle_type_selected else R.drawable.bg_vehicle_type)
        btnPaymentWallet.setBackgroundResource(if (method == "Billetera") R.drawable.bg_vehicle_type_selected else R.drawable.bg_vehicle_type)
        btnPaymentQR.setBackgroundResource(if (method == "QR") R.drawable.bg_vehicle_type_selected else R.drawable.bg_vehicle_type)
        
        val textColorCash = if (method == "Efectivo") "#16a34a" else "#64748b"
        val textColorWallet = if (method == "Billetera") "#16a34a" else "#64748b"
        val textColorQR = if (method == "QR") "#16a34a" else "#64748b"
    }
    
    private fun showMenuDialog() {
        val userName = sessionManager.getUserName() ?: "Usuario"
        val menuItems = arrayOf(
            "🎁 Promociones y Beneficios",
            "📱 Bot de WhatsApp (QR)",
            "📊 Historial de Carreras",
            "👤 Mis Datos",
            "📞 Contactar Central",
            "ℹ️ Acerca de Nosotros",
            "🚪 Cerrar sesión"
        )
        AlertDialog.Builder(this)
            .setTitle("Hola, $userName 👋")
            .setItems(menuItems) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, PromotionsActivity::class.java)
                        startActivity(intent)
                    }
                    1 -> {
                        val intent = Intent(this, QRBotActivity::class.java)
                        startActivity(intent)
                    }
                    2 -> {
                        val intent = Intent(this, PassengerHistoryActivity::class.java)
                        startActivity(intent)
                    }
                    3 -> {
                        Toast.makeText(this, "👤 Mis Datos - Próximamente!", Toast.LENGTH_SHORT).show()
                    }
                    4 -> {
                        openWhatsAppMessage("59177696600")
                    }
                    5 -> {
                        val intent = Intent(this, AboutActivity::class.java)
                        startActivity(intent)
                    }
                    6 -> {
                        logout()
                    }
                }
            }
            .show()
    }
    
    private fun openWhatsAppMessage(phone: String) {
        val url = "https://api.whatsapp.com/send?phone=$phone"
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        startActivity(intent)
    }
    
    private fun logout() {
        sessionManager.clearSession()
        goToLoginActivity()
    }
    
    private fun goToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun centerOnMyLocation() {
        currentLocation?.let {
            val latLng = LatLng(it.latitude, it.longitude)
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        } ?: run {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun estimateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
    
    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        } else {
            getCurrentLocation()
        }
    }
    
    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                val finalLocation = location ?: run {
                    val defaultLocation = Location("default")
                    defaultLocation.latitude = -17.7833
                    defaultLocation.longitude = -63.1821
                    Toast.makeText(this, "Usando ubicación predeterminada (Santa Cruz)", Toast.LENGTH_SHORT).show()
                    defaultLocation
                }
                
                currentLocation = finalLocation
                val latLng = LatLng(finalLocation.latitude, finalLocation.longitude)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                txtOrigen.text = "Tu ubicación"
            }
        }
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val santaCruz = LatLng(-17.7833, -63.1821)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(santaCruz, 13f))
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = false
            getCurrentLocation()
        }
        
        googleMap?.setOnCameraIdleListener {
            val destino = googleMap?.cameraPosition?.target
            destino?.let {
                calculateRouteAndFare(it)
            }
        }
    }
    
    private fun calculateRouteAndFare(destLatLng: LatLng) {
        destinationLocation = destLatLng
        val location = currentLocation ?: return
        
        val pickupLatLng = LatLng(location.latitude, location.longitude)
        
        pickupMarker?.remove()
        routePolyline?.remove()
        
        pickupMarker = googleMap?.addMarker(
            MarkerOptions().position(pickupLatLng).title("Tu ubicación")
        )
        
        fetchDirectionsFromAPI(pickupLatLng, destLatLng)
    }
    
    private fun fetchDirectionsFromAPI(origin: LatLng, destination: LatLng) {
        val apiKey = "TU_API_KEY_AQUI" 
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&mode=driving" +
                        "&key=$apiKey"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val json = JSONObject(responseBody ?: "")
                    val routes = json.optJSONArray("routes")
                    
                    if (routes != null && routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val legs = route.getJSONArray("legs")
                        val leg = legs.getJSONObject(0)
                        
                        val distanceObj = leg.getJSONObject("distance")
                        val durationObj = leg.getJSONObject("duration")
                        
                        val distanceMeters = distanceObj.getDouble("value")
                        currentDistance = distanceMeters / 1000
                        val durationSeconds = durationObj.getDouble("value")
                        currentTime = (durationSeconds / 60).toInt()
                        
                        val overviewPolyline = route.getJSONObject("overview_polyline")
                        val points = overviewPolyline.getString("points")
                        val decodedPath = PolylineDecoder.decode(points)
                        
                        withContext(Dispatchers.Main) {
                            routePolyline?.remove()
                            
                            val polylineOptions = PolylineOptions()
                                .addAll(decodedPath)
                                .width(12f)
                                .color(0xFF2563eb.toInt())
                            
                            routePolyline = googleMap?.addPolyline(polylineOptions)
                            
                            val ratePerKm = if (isNightTime()) nightRatePerKm else dayRatePerKm
                            val ratePerMin = if (isNightTime()) nightRatePerMin else dayRatePerMin
                            currentFare = maxOf(minFare, currentDistance * ratePerKm + currentTime * ratePerMin)
                            updateFareDisplay()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            val distance = estimateDistance(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
                            currentDistance = distance
                            currentTime = (distance * 5).toInt()
                            
                            val ratePerKm = if (isNightTime()) nightRatePerKm else dayRatePerKm
                            val ratePerMin = if (isNightTime()) nightRatePerMin else dayRatePerMin
                            currentFare = maxOf(minFare, currentDistance * ratePerKm + currentTime * ratePerMin)
                            
                            val polylineOptions = PolylineOptions()
                                .add(origin)
                                .add(destination)
                                .width(12f)
                                .color(0xFF2563eb.toInt())
                                .geodesic(true)
                            
                            routePolyline = googleMap?.addPolyline(polylineOptions)
                            updateFareDisplay()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val distance = estimateDistance(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
                    currentDistance = distance
                    currentTime = (distance * 5).toInt()
                    
                    val ratePerKm = if (isNightTime()) nightRatePerKm else dayRatePerKm
                    val ratePerMin = if (isNightTime()) nightRatePerMin else dayRatePerMin
                    currentFare = maxOf(minFare, currentDistance * ratePerKm + currentTime * ratePerMin)
                    
                    val polylineOptions = PolylineOptions()
                        .add(origin)
                        .add(destination)
                        .width(12f)
                        .color(0xFF2563eb.toInt())
                        .geodesic(true)
                    
                    routePolyline = googleMap?.addPolyline(polylineOptions)
                    updateFareDisplay()
                }
            }
        }
    }
    
    private fun calculateFirstRideDiscount(fare: Double): Double {
        val userId = sessionManager.getUserId() ?: return 0.0
        val hasDiscount = sessionManager.hasFirstRideDiscount()
        
        if (!hasDiscount) return 0.0
        
        return when {
            fare >= 10.0 && fare < 20.0 -> 5.0
            fare >= 20.0 && fare < 40.0 -> 10.0
            else -> 0.0
        }
    }

    private fun updateFareDisplay() {
        val firstRideDiscount = calculateFirstRideDiscount(currentFare)
        val walletBalance = sessionManager.getWalletBalance()
        
        var finalFare = currentFare
        
        if (firstRideDiscount > 0) {
            finalFare -= firstRideDiscount
        }
        
        if (selectedPaymentMethod == "Billetera" && walletBalance > 0) {
            val discountFromWallet = minOf(walletBalance, finalFare)
            finalFare -= discountFromWallet
        }
        
        finalFare = maxOf(0.0, finalFare)
        
        txtDistancia.text = "${String.format("%.1f", currentDistance)} km"
        txtTiempo.text = "$currentTime min"
        
        if (firstRideDiscount > 0 || walletBalance > 0) {
            val discountText = buildString {
                append("Bs ${String.format("%.0f", finalFare)}")
                if (firstRideDiscount > 0) {
                    append(" (-${String.format("%.0f", firstRideDiscount)} Bs desc.)")
                }
            }
            txtTarifa.text = discountText
        } else {
            txtTarifa.text = "Bs ${String.format("%.0f", finalFare)}"
        }
        
        txtDestino.text = "${String.format("%.6f", destinationLocation?.latitude ?: 0.0)}, ${String.format("%.6f", destinationLocation?.longitude ?: 0.0)}"
    }
    
    private fun requestRide() {
        val passengerSocketId = socket.id()
        val location = currentLocation ?: run {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
            return
        }
        val dest = destinationLocation ?: run {
            Toast.makeText(this, "Selecciona un destino moviendo el mapa", Toast.LENGTH_SHORT).show()
            return
        }

        if (passengerSocketId.isNullOrEmpty()) {
            socket.connect()
            Toast.makeText(this, "Esperando conexión con el servidor, intenta otra vez", Toast.LENGTH_SHORT).show()
            return
        }

        val firstRideDiscount = calculateFirstRideDiscount(currentFare)
        val walletBalance = sessionManager.getWalletBalance()

        var finalFare = currentFare
        var discountApplied = 0.0
        var walletUsed = 0.0

        if (firstRideDiscount > 0) {
            discountApplied = firstRideDiscount
            finalFare -= firstRideDiscount
        }

        if (selectedPaymentMethod == "Billetera" && walletBalance > 0) {
            walletUsed = minOf(walletBalance, finalFare)
            finalFare -= walletUsed
        }

        finalFare = maxOf(0.0, finalFare)
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("passengerId", passengerSocketId)
                    put("pickup", "Tu ubicación")
                    put("destination", txtDestino.text.toString())
                    put("pickupLat", location.latitude)
                    put("pickupLng", location.longitude)
                    put("destLat", dest.latitude)
                    put("destLng", dest.longitude)
                    put("fare", finalFare)
                    put("originalFare", currentFare)
                    put("discountApplied", discountApplied)
                    put("walletUsed", walletUsed)
                    put("distanceKm", currentDistance)
                    put("paymentMethod", selectedPaymentMethod)
                }
                
                val body = json.toString().toRequestBody(JSON)
                val request = Request.Builder()
                    .url("$SERVER_URL/api/ride/request")
                    .post(body)
                    .build()
                
                val response = client.newCall(request).execute()
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val rideResponse = JSONObject(responseBody ?: "")
                        currentRide = rideResponse.getJSONObject("ride")

                        if (discountApplied > 0) {
                            sessionManager.setFirstRideDiscount(true)
                            updateUserDiscountInFirebase()
                        }

                        if (walletUsed > 0) {
                            sessionManager.subtractFromWalletBalance(walletUsed)
                            updateUserWalletInFirebase(walletUsed)
                        }

                        sessionManager.incrementTotalRides()
                        updateUserTotalRidesInFirebase()
                        
                        Toast.makeText(this@MainActivity, "Buscando conductor...", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Error al solicitar taxi", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUserDiscountInFirebase() {
        val userId = sessionManager.getUserId() ?: return
        val userRef = database.getReference("passengers").child(userId)
        userRef.child("firstRideDiscountUsed").setValue(true)
    }

    private fun updateUserWalletInFirebase(amountUsed: Double) {
        val userId = sessionManager.getUserId() ?: return
        val userRef = database.getReference("passengers").child(userId)
        userRef.child("walletBalance").setValue(sessionManager.getWalletBalance())
    }

    private fun updateUserTotalRidesInFirebase() {
        val userId = sessionManager.getUserId() ?: return
        val userRef = database.getReference("passengers").child(userId)
        userRef.child("totalRides").setValue(sessionManager.getTotalRides())
    }
    
    private fun setupSocketListeners() {
        socket.on(Socket.EVENT_CONNECT) {
            runOnUiThread {
                Toast.makeText(this, "Conectado al servidor", Toast.LENGTH_SHORT).show()
            }
        }

        socket.on("ride-accepted") { args ->
            runOnUiThread {
                Toast.makeText(this, "¡Conductor encontrado! Viniendo hacia ti...", Toast.LENGTH_LONG).show()
            }
        }
        
        socket.on("ride-started") { args ->
            runOnUiThread {
                Toast.makeText(this, "¡Viaje iniciado!", Toast.LENGTH_SHORT).show()
            }
        }
        
        socket.on("ride-completed") { args ->
            runOnUiThread {
                Toast.makeText(this, "¡Viaje completado! Gracias por viajar con nosotros.", Toast.LENGTH_LONG).show()
            }
        }
        
        socket.on("ride-cancelled") { args ->
            runOnUiThread {
                Toast.makeText(this, "El viaje ha sido cancelado.", Toast.LENGTH_SHORT).show()
            }
        }
        
        socket.on("driver-list-update") { args ->
            runOnUiThread {
                val drivers = args[0] as? org.json.JSONArray
                drivers?.let {
                    driverMarkers.values.forEach { it.remove() }
                    driverMarkers.clear()
                    
                    for (i in 0 until it.length()) {
                        val driver = it.getJSONObject(i)
                        val locationObj = driver.optJSONObject("location")
                        locationObj?.let { loc ->
                            val latLng = LatLng(loc.getDouble("lat"), loc.getDouble("lng"))
                            val marker = googleMap?.addMarker(
                                MarkerOptions().position(latLng).title(driver.getString("name"))
                            )
                            marker?.let { m ->
                                driverMarkers[driver.getString("socketId")] = m
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                getCurrentLocation()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        socket.disconnect()
    }
}
