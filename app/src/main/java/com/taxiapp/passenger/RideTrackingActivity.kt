package com.taxiapp.passenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.widget.Button
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Date

class RideTrackingActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var btnBack: FloatingActionButton
    private lateinit var btnCancelRide: Button
    private lateinit var btnOnTheWay: Button
    private lateinit var txtRideStatus: TextView
    private lateinit var txtDriverName: TextView
    private lateinit var txtDriverVehicle: TextView
    private lateinit var imgDriverPhoto: android.widget.ImageView
    private lateinit var imgVehiclePhoto: android.widget.ImageView
    private lateinit var btnCallDriver: android.widget.ImageButton
    private lateinit var btnMessageDriver: android.widget.ImageButton
    private lateinit var txtPickupAddress: TextView
    private lateinit var txtDestination: TextView
    private lateinit var txtDistance: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtFare: TextView
    private lateinit var driverDetails: LinearLayout
    private var currentDriverPhone: String? = null
    
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var routePolyline: Polyline? = null
    private var pickupMarker: Marker? = null
    private var destMarker: Marker? = null
    private var driverMarker: Marker? = null
    
    private var pickupLat: Double = 0.0
    private var pickupLng: Double = 0.0
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var destName: String = ""
    private var fare: Double = 0.0
    private var distance: Double = 0.0
    private var time: Int = 0
    private var vehicleType: String? = null
    
    private var currentRideId: String? = null
    private var currentDriver: JSONObject? = null
    private var rideStatus = "pending"
    private var lastDriverLocation: LatLng? = null
    
    private lateinit var sessionManager: SessionManager
    private val client = OkHttpClient()
    
    private fun getCarBitmapDescriptor(): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_car) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 80, 80, false)
        return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ride_tracking)
        
        sessionManager = SessionManager(this)
        firebaseHelper = FirebaseHelper()
        
        pickupLat = intent.getDoubleExtra("pickupLat", -17.7833)
        pickupLng = intent.getDoubleExtra("pickupLng", -63.1821)
        destLat = intent.getDoubleExtra("destLat", -17.7833)
        destLng = intent.getDoubleExtra("destLng", -63.1821)
        destName = intent.getStringExtra("destName") ?: "Destino"
        fare = intent.getDoubleExtra("fare", 0.0)
        distance = intent.getDoubleExtra("distance", 0.0)
        time = intent.getIntExtra("time", 0)
        vehicleType = intent.getStringExtra("vehicleType")
        
        initViews()
        setupClickListeners()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        checkLocationPermission()
        requestRide()
    }
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnCancelRide = findViewById(R.id.btnCancelRide)
        btnOnTheWay = findViewById(R.id.btnOnTheWay)
        txtRideStatus = findViewById(R.id.txtRideStatus)
        txtDriverName = findViewById(R.id.txtDriverName)
        txtDriverVehicle = findViewById(R.id.txtDriverVehicle)
        imgDriverPhoto = findViewById(R.id.imgDriverPhoto)
        imgVehiclePhoto = findViewById(R.id.imgVehiclePhoto)
        btnCallDriver = findViewById(R.id.btnCallDriver)
        btnMessageDriver = findViewById(R.id.btnMessageDriver)
        txtPickupAddress = findViewById(R.id.txtPickupAddress)
        txtDestination = findViewById(R.id.txtDestination)
        txtDistance = findViewById(R.id.txtDistance)
        txtTime = findViewById(R.id.txtTime)
        txtFare = findViewById(R.id.txtFare)
        driverDetails = findViewById(R.id.driverDetails)
        
        txtDestination.text = destName
        txtDistance.text = "${String.format("%.1f", distance)} km"
        txtTime.text = "$time min"
        txtFare.text = "Bs ${fare.toInt()}"
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        btnCancelRide.setOnClickListener {
            cancelRide()
        }
        
        btnOnTheWay.setOnClickListener {
            currentRideId?.let { rideId ->
                firebaseHelper.passengerOnTheWay(rideId)
                btnOnTheWay.visibility = Button.GONE
                Toast.makeText(this, "¡Notificado al conductor que estás en camino! 😊", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnCallDriver.setOnClickListener {
            currentDriverPhone?.let { phone ->
                openWhatsAppCall(phone)
            } ?: run {
                Toast.makeText(this, "Número de teléfono no disponible", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnMessageDriver.setOnClickListener {
            currentDriverPhone?.let { phone ->
                openWhatsAppMessage(phone)
            } ?: run {
                Toast.makeText(this, "Número de teléfono no disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openWhatsAppCall(phone: String) {
        try {
            val passengerName = sessionManager.getUserName() ?: "Pasajero"
            val message = "Hola, soy $passengerName, estoy esperando, esta de venida verdad?"
            val encodedMessage = android.net.Uri.encode(message)
            val uri = android.net.Uri.parse("https://wa.me/591$phone?text=$encodedMessage")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openWhatsAppMessage(phone: String) {
        try {
            val passengerName = sessionManager.getUserName() ?: "Pasajero"
            val message = "Hola, soy $passengerName, estoy esperando, esta de venida verdad?"
            val encodedMessage = android.net.Uri.encode(message)
            val uri = android.net.Uri.parse("https://wa.me/591$phone?text=$encodedMessage")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
    
    private fun requestRide() {
        val rideId = Date().time.toString()
        currentRideId = rideId
        
        var passengerId = sessionManager.getUserId()
        if (passengerId.isNullOrEmpty()) {
            passengerId = "passenger_" + Date().time
        }
        
        Toast.makeText(this, "Enviando solicitud...", Toast.LENGTH_SHORT).show()
        
        firebaseHelper.getNearbyDrivers { drivers ->
            val nearbyDrivers = drivers.mapNotNull { driver ->
                val locationMap = driver["location"] as? Map<*, *> ?: return@mapNotNull null
                val lat = locationMap["lat"] as? Double ?: return@mapNotNull null
                val lng = locationMap["lng"] as? Double ?: return@mapNotNull null
                val distance = calculateDistance(pickupLat, pickupLng, lat, lng)
                if (distance <= 6.0) {
                    driver.toMutableMap().apply { put("distance", distance) }
                } else {
                    null
                }
            }.sortedBy { it["distance"] as Double }
            
            val rideData = mutableMapOf<String, Any?>(
                "id" to rideId,
                "passengerId" to passengerId,
                "passengerName" to (sessionManager.getUserName() ?: "Pasajero"),
                "passengerPhone" to (sessionManager.getUserPhone() ?: ""),
                "pickup" to "Tu ubicación",
                "destination" to destName,
                "pickupLocation" to mapOf(
                    "lat" to pickupLat,
                    "lng" to pickupLng
                ),
                "destLocation" to mapOf(
                    "lat" to destLat,
                    "lng" to destLng
                ),
                "status" to "pending",
                "fare" to fare,
                "distanceKm" to distance,
                "createdAt" to Date().time,
                "nearbyDrivers" to nearbyDrivers,
                "currentDriverIndex" to 0
            )
            
            firebaseHelper.cancelPendingRidesForPassenger(passengerId) {
                firebaseHelper.requestRide(rideId, rideData) { success, errorMessage ->
                    if (success) {
                        txtRideStatus.text = "Buscando conductor... ⏳"
                        Toast.makeText(this, "Solicitud enviada! Buscando conductor...", Toast.LENGTH_LONG).show()
                        setupFirebaseListeners()
                    } else {
                        val message = errorMessage ?: "Error al solicitar taxi. Inténtalo nuevamente."
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun setupFirebaseListeners() {
        currentRideId?.let { rideId ->
            firebaseHelper.listenToRideStatus(rideId, object : FirebaseHelper.RideStatusListener {
                override fun onRideStatusChanged(ride: JSONObject) {
                    runOnUiThread {
                        handleRideStatusUpdate(ride)
                    }
                }
            })
        }
    }
    
    private fun handleRideStatusUpdate(ride: JSONObject) {
        val status = ride.optString("status", "pending")
        rideStatus = status
        
        when (status) {
            "accepted" -> {
                val driverData = ride.optJSONObject("driverData")
                currentDriver = driverData
                
                txtRideStatus.text = "Conductor aceptó la carrera! 🎉"
                driverDetails.visibility = LinearLayout.VISIBLE
                
                driverData?.let {
                    txtDriverName.text = it.optString("name", "Conductor")
                    val vehicle = it.optString("vehicle", "Vehículo")
                    val plate = it.optString("licensePlate", "")
                    txtDriverVehicle.text = if (plate.isNotEmpty()) "$vehicle - $plate" else vehicle
                    
                    currentDriverPhone = it.optString("phone", "")
                    
                    val driverPhotoUrl = it.optString("driverPhotoUrl", "")
                    val vehiclePhotoUrl = it.optString("vehiclePhotoUrl", "")
                    
                    if (driverPhotoUrl.isNotEmpty()) {
                        com.squareup.picasso.Picasso.get()
                            .load(driverPhotoUrl)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .into(imgDriverPhoto)
                    }
                    
                    if (vehiclePhotoUrl.isNotEmpty()) {
                        com.squareup.picasso.Picasso.get()
                            .load(vehiclePhotoUrl)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .into(imgVehiclePhoto)
                    }
                }
                
                val driverId = ride.optString("driverId")
                driverId?.let { listenToDriverLocation(it) }
                
                Toast.makeText(this, "¡Conductor aceptó la carrera! 😊", Toast.LENGTH_LONG).show()
            }
            
            "arrived" -> {
                txtRideStatus.text = "Conductor está aquí! 🎉"
                btnOnTheWay.visibility = Button.VISIBLE
                Toast.makeText(this, "El conductor ha llegado a tu ubicación!", Toast.LENGTH_LONG).show()
            }
            
            "in_progress" -> {
                txtRideStatus.text = "¡Póngase los cinturones y a viajar! 🚗"
                btnOnTheWay.visibility = Button.GONE
                Toast.makeText(this, "¡Viaje iniciado! 😊", Toast.LENGTH_SHORT).show()
            }
            
            "cancelled" -> {
                val cancelledBy = ride.optString("cancelledBy", "passenger")
                val message = if (cancelledBy == "driver") {
                    "¡El conductor canceló el viaje! 😔"
                } else {
                    "¡Viaje cancelado! 😔"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            
            "completed" -> {
                txtRideStatus.text = "¡Ya llegaste a tu destino! ✅"
                btnCancelRide.visibility = Button.GONE
                btnOnTheWay.visibility = Button.GONE
                
                val driverId = ride.optString("driverId", "")
                
                val intent = Intent(this, RatingActivity::class.java)
                intent.putExtra("rideId", currentRideId ?: "")
                intent.putExtra("driverId", driverId)
                startActivity(intent)
                finish()
            }
        }
    }
    
    private fun listenToDriverLocation(driverId: String) {
        firebaseHelper.listenToDriverLocation(driverId, object : FirebaseHelper.DriverLocationListener {
            override fun onDriverLocationUpdate(driverId: String, lat: Double, lng: Double) {
                runOnUiThread {
                    updateDriverMarker(LatLng(lat, lng))
                }
            }
        })
    }
    
    private fun updateDriverMarker(latLng: LatLng) {
        val carIcon = getCarBitmapDescriptor()
        
        if (driverMarker == null) {
            driverMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Conductor")
                    .snippet("En camino")
                    .icon(carIcon)
                    .anchor(0.5f, 0.5f)
            )
        } else {
            val lastLoc = lastDriverLocation
            if (lastLoc != null) {
                val rotation = calculateRotation(lastLoc, latLng)
                driverMarker?.rotation = rotation
            }
            driverMarker?.position = latLng
        }
        
        lastDriverLocation = latLng
        googleMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng))
    }
    
    private fun calculateRotation(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lng1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lng2 = Math.toRadians(to.longitude)
        
        val dLng = lng2 - lng1
        
        val y = Math.sin(dLng) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
        
        var bearing = Math.toDegrees(Math.atan2(y, x))
        bearing = (bearing + 360) % 360
        
        return bearing.toFloat()
    }
    
    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) {
                (result shr 1).inv()
            } else {
                result shr 1
            }
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) {
                (result shr 1).inv()
            } else {
                result shr 1
            }
            lng += dlng
            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }
        return poly
    }
    
    private fun cancelRide() {
        currentRideId?.let { rideId ->
            firebaseHelper.cancelRide(rideId)
        }
        
        Toast.makeText(this, "Viaje cancelado", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
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
                    defaultLocation
                }
                
                val pickupLatLng = LatLng(pickupLat, pickupLng)
                val destLatLng = LatLng(destLat, destLng)
                
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 14f))
                
                pickupMarker?.remove()
                pickupMarker = googleMap?.addMarker(
                    MarkerOptions().position(pickupLatLng).title("Tu ubicación")
                )
                
                destMarker?.remove()
                destMarker = googleMap?.addMarker(
                    MarkerOptions().position(destLatLng).title(destName)
                )
                
                getAddressFromLocation(pickupLat, pickupLng, txtPickupAddress)
                drawRoute(pickupLatLng, destLatLng)
            }
        }
    }
    
    private fun getAddressFromLocation(lat: Double, lng: Double, textView: TextView) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/geocode/json?" +
                "latlng=$lat,$lng" +
                "&key=$apiKey"
        
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    textView.text = "Ubicación desconocida"
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val results = json.getJSONArray("results")
                        if (results.length() > 0) {
                            val address = results.getJSONObject(0).optString("formatted_address", "Ubicación desconocida")
                            runOnUiThread {
                                textView.text = address
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            textView.text = "Ubicación desconocida"
                        }
                    }
                }
            }
        })
    }
    
    private fun drawRoute(from: LatLng, to: LatLng) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${from.latitude},${from.longitude}" +
                "&destination=${to.latitude},${to.longitude}" +
                "&key=$apiKey"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    routePolyline?.remove()
                    val polylineOptions = PolylineOptions()
                        .add(from)
                        .add(to)
                        .width(8f)
                        .color(0xFF10b981.toInt())
                        .geodesic(true)
                    routePolyline = googleMap?.addPolyline(polylineOptions)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val routes = json.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val overviewPolyline = route.getJSONObject("overview_polyline")
                            val points = overviewPolyline.getString("points")
                            val decodedPath = decodePoly(points)
                            
                            val legs = route.getJSONArray("legs")
                            if (legs.length() > 0) {
                                val leg = legs.getJSONObject(0)
                                val distanceObj = leg.getJSONObject("distance")
                                val durationObj = leg.getJSONObject("duration")
                                
                                val distanceMeters = distanceObj.optInt("value", 0)
                                val distanceKm = distanceMeters / 1000.0
                                val timeSeconds = durationObj.optInt("value", 0)
                                val timeMinutes = timeSeconds / 60
                                
                                val farePerKm = when (vehicleType) {
                                    "vagoneta" -> MapSelectActivity.FARE_VAGONETA_PER_KM
                                    "moto" -> MapSelectActivity.FARE_MOTO_PER_KM
                                    else -> MapSelectActivity.FARE_AUTO_PER_KM
                                }
                                val calculatedFare = Math.max(distanceKm * farePerKm, MapSelectActivity.MINIMUM_FARE)
                                
                                runOnUiThread {
                                    distance = distanceKm
                                    time = timeMinutes
                                    fare = calculatedFare
                                    txtDistance.text = "${String.format("%.1f", distance)} km"
                                    txtTime.text = "$time min"
                                    txtFare.text = "Bs ${fare.toInt()}"
                                }
                            }

                            runOnUiThread {
                                routePolyline?.remove()
                                val polylineOptions = PolylineOptions()
                                    .addAll(decodedPath)
                                    .width(8f)
                                    .color(0xFF10b981.toInt())
                                    .geodesic(true)
                                routePolyline = googleMap?.addPolyline(polylineOptions)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            routePolyline?.remove()
                            val polylineOptions = PolylineOptions()
                                .add(from)
                                .add(to)
                                .width(8f)
                                .color(0xFF10b981.toInt())
                                .geodesic(true)
                            routePolyline = googleMap?.addPolyline(polylineOptions)
                        }
                    }
                }
            }
        })
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = false
        }
    }
}
