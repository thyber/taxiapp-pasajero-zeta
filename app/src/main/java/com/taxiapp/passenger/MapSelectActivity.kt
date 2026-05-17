package com.taxiapp.passenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class MapSelectActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var btnBack: FloatingActionButton
    private lateinit var btnSolicitarTaxi: Button
    private lateinit var pinCentro: ImageView
    private lateinit var txtDestName: TextView
    private lateinit var txtDestAddress: TextView
    private lateinit var txtFare: TextView
    private lateinit var txtFareInfo: TextView
    private lateinit var fareInfo: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    
    private var googleMap: GoogleMap? = null
    private var routePolyline: Polyline? = null
    private var pickupMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var destinationLocation: LatLng? = null
    private var currentFare = 0.0
    private var currentDistance = 0.0
    private var currentTime = 0
    
    private var vehicleType: String? = null
    private var destName: String? = null
    private var specialDestinations: List<Map<String, Any>> = emptyList()
    private var currentSpecialDestination: Map<String, Any>? = null
    
    companion object {
        const val FARE_AUTO_PER_KM = 3.0
        const val FARE_VAGONETA_PER_KM = 3.5
        const val FARE_MOTO_PER_KM = 2.5
        const val MINIMUM_FARE = 5.0
    }
    
    private lateinit var firebaseHelper: FirebaseHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_select)
        
        firebaseHelper = FirebaseHelper()
        vehicleType = intent.getStringExtra("vehicleType")
        destName = intent.getStringExtra("destName")
        val destLat = intent.getDoubleExtra("destLat", -17.7833)
        val destLng = intent.getDoubleExtra("destLng", -63.1821)
        val centerOnUser = intent.getBooleanExtra("centerOnUser", false)
        destinationLocation = LatLng(destLat, destLng)
        
        initViews()
        setupClickListeners()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        checkLocationPermission()
        
        loadSystemSettings()
    }
    
    private fun loadSystemSettings() {
        firebaseHelper.getSystemSettings { settings ->
            settings?.let {
                val dests = it["specialDestinations"] as? List<Map<String, Any>> ?: emptyList()
                specialDestinations = dests
            }
        }
    }
    
    private fun findSpecialDestination(lat: Double, lng: Double): Map<String, Any>? {
        for (dest in specialDestinations) {
            val destLat = dest["lat"] as? Double ?: continue
            val destLng = dest["lng"] as? Double ?: continue
            val distance = calculateDistance(lat, lng, destLat, destLng)
            if (distance <= 2.0) {
                return dest
            }
        }
        return null
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
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnSolicitarTaxi = findViewById(R.id.btnSolicitarTaxi)
        pinCentro = findViewById(R.id.pinCentro)
        txtDestName = findViewById(R.id.txtDestName)
        txtDestAddress = findViewById(R.id.txtDestAddress)
        txtFare = findViewById(R.id.txtFare)
        txtFareInfo = findViewById(R.id.txtFareInfo)
        fareInfo = findViewById(R.id.fareInfo)
        bottomPanel = findViewById(R.id.bottomPanel)
        
        destName?.let {
            txtDestName.text = it
        }
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        btnSolicitarTaxi.setOnClickListener {
            confirmDestination()
        }
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
                
                currentLocation = finalLocation
                
                destinationLocation?.let { dest ->
                    calculateRouteAndFare(dest)
                }
            }
        }
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
        
        destinationLocation?.let { dest ->
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(dest, 14f))
            calculateRouteAndFare(dest)
            getAddressFromLocation(dest.latitude, dest.longitude)
        }
        
        googleMap?.setOnCameraIdleListener {
            val destino = googleMap?.cameraPosition?.target
            destino?.let {
                destinationLocation = it
                calculateRouteAndFare(it)
                getAddressFromLocation(it.latitude, it.longitude)
            }
        }
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
    
    private fun calculateRouteAndFare(destLatLng: LatLng) {
        val location = currentLocation ?: return
        
        val pickupLatLng = LatLng(location.latitude, location.longitude)
        
        pickupMarker?.remove()
        routePolyline?.remove()
        
        pickupMarker = googleMap?.addMarker(
            MarkerOptions().position(pickupLatLng).title("Tu ubicación")
        )
        
        currentSpecialDestination = findSpecialDestination(destLatLng.latitude, destLatLng.longitude)
        
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${pickupLatLng.latitude},${pickupLatLng.longitude}" +
                "&destination=${destLatLng.latitude},${destLatLng.longitude}" +
                "&key=$apiKey"
        
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    val lat1 = pickupLatLng.latitude
                    val lng1 = pickupLatLng.longitude
                    val lat2 = destLatLng.latitude
                    val lng2 = destLatLng.longitude
                    
                    val distance = Math.sqrt(Math.pow(lat2 - lat1, 2.0) + Math.pow(lng2 - lng1, 2.0)) * 111.0
                    currentDistance = distance
                    currentTime = (distance * 5).toInt()
                    
                    val farePerKm = when (vehicleType) {
                        "vagoneta" -> FARE_VAGONETA_PER_KM
                        "moto" -> FARE_MOTO_PER_KM
                        else -> FARE_AUTO_PER_KM
                    }
                    currentFare = Math.max(distance * farePerKm, MINIMUM_FARE)
                    
                    currentSpecialDestination?.let { dest ->
                        val extraFare = dest["extraFare"] as? Double ?: 0.0
                        currentFare += extraFare
                    }
                    
                    val polylineOptions = PolylineOptions()
                        .add(pickupLatLng)
                        .add(destLatLng)
                        .width(10f)
                        .color(0xFF10b981.toInt())
                        .geodesic(true)
                    
                    routePolyline = googleMap?.addPolyline(polylineOptions)
                    
                    txtFare.text = "Bs ${currentFare.toInt()}"
                    txtFareInfo.text = currentSpecialDestination?.let { dest ->
                        val destName = dest["name"] as? String ?: "Destino especial"
                        val extraFare = dest["extraFare"] as? Double ?: 0.0
                        "Distancia: ${String.format("%.1f", currentDistance)} km | $currentTime min | +Bs $extraFare recargo $destName"
                    } ?: run {
                        "Distancia: ${String.format("%.1f", currentDistance)} km | $currentTime min"
                    }
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
                            val legs = route.getJSONArray("legs")
                            if (legs.length() > 0) {
                                val leg = legs.getJSONObject(0)
                                val distance = leg.getJSONObject("distance").getDouble("value") / 1000.0
                                val duration = leg.getJSONObject("duration").getInt("value") / 60
                                
                                currentDistance = distance
                                currentTime = duration
                                
                                val farePerKm = when (vehicleType) {
                                    "vagoneta" -> FARE_VAGONETA_PER_KM
                                    "moto" -> FARE_MOTO_PER_KM
                                    else -> FARE_AUTO_PER_KM
                                }
                                currentFare = Math.max(distance * farePerKm, MINIMUM_FARE)
                                
                                currentSpecialDestination?.let { dest ->
                                    val extraFare = dest["extraFare"] as? Double ?: 0.0
                                    currentFare += extraFare
                                }
                                
                                val overviewPolyline = route.getJSONObject("overview_polyline")
                                val points = overviewPolyline.getString("points")
                                val decodedPath = decodePoly(points)
                                
                                runOnUiThread {
                                    val polylineOptions = PolylineOptions()
                                        .addAll(decodedPath)
                                        .width(10f)
                                        .color(0xFF10b981.toInt())
                                        .geodesic(true)
                                    
                                    routePolyline = googleMap?.addPolyline(polylineOptions)
                                    
                                    txtFare.text = "Bs ${currentFare.toInt()}"
                                    txtFareInfo.text = currentSpecialDestination?.let { dest ->
                                        val destName = dest["name"] as? String ?: "Destino especial"
                                        val extraFare = dest["extraFare"] as? Double ?: 0.0
                                        "Distancia: ${String.format("%.1f", currentDistance)} km | $currentTime min | +Bs $extraFare recargo $destName"
                                    } ?: run {
                                        "Distancia: ${String.format("%.1f", currentDistance)} km | $currentTime min"
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }
    
    private fun confirmDestination() {
        val dest = destinationLocation ?: run {
            Toast.makeText(this, "Selecciona un destino", Toast.LENGTH_SHORT).show()
            return
        }
        val location = currentLocation ?: run {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
            return
        }
        
        var fareToSend = if (currentFare == 0.0) {
            val lat1 = location.latitude
            val lng1 = location.longitude
            val lat2 = dest.latitude
            val lng2 = dest.longitude
            val distance = Math.sqrt(Math.pow(lat2 - lat1, 2.0) + Math.pow(lng2 - lng1, 2.0)) * 111.0
            
            val farePerKm = when (vehicleType) {
                "vagoneta" -> FARE_VAGONETA_PER_KM
                "moto" -> FARE_MOTO_PER_KM
                else -> FARE_AUTO_PER_KM
            }
            Math.max(distance * farePerKm, MINIMUM_FARE)
        } else {
            currentFare
        }
        
        currentSpecialDestination?.let { dest ->
            val extraFare = dest["extraFare"] as? Double ?: 0.0
            fareToSend += extraFare
        }
        
        val distanceToSend = if (currentDistance == 0.0) {
            val lat1 = location.latitude
            val lng1 = location.longitude
            val lat2 = dest.latitude
            val lng2 = dest.longitude
            Math.sqrt(Math.pow(lat2 - lat1, 2.0) + Math.pow(lng2 - lng1, 2.0)) * 111.0
        } else {
            currentDistance
        }
        
        val timeToSend = if (currentTime == 0) {
            (distanceToSend * 5).toInt()
        } else {
            currentTime
        }
        
        val intent = Intent(this, RideTrackingActivity::class.java)
        intent.putExtra("vehicleType", vehicleType)
        intent.putExtra("pickupLat", location.latitude)
        intent.putExtra("pickupLng", location.longitude)
        intent.putExtra("destLat", dest.latitude)
        intent.putExtra("destLng", dest.longitude)
        intent.putExtra("destName", txtDestName.text.toString())
        intent.putExtra("fare", fareToSend)
        intent.putExtra("distance", distanceToSend)
        intent.putExtra("time", timeToSend)
        startActivity(intent)
        finish()
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
    
    private fun getAddressFromLocation(lat: Double, lng: Double) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/geocode/json?" +
                "latlng=$lat,$lng" +
                "&key=$apiKey"
        
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    txtDestAddress.text = "Santa Cruz de la Sierra"
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val results = json.getJSONArray("results")
                        if (results.length() > 0) {
                            val address = results.getJSONObject(0).optString("formatted_address", "Santa Cruz de la Sierra")
                            runOnUiThread {
                                txtDestAddress.text = address
                                txtDestName.text = address.split(",").firstOrNull() ?: address
                                val oldSpecialDest = currentSpecialDestination
                                currentSpecialDestination = findSpecialDestination(lat, lng)
                                
                                if (oldSpecialDest != currentSpecialDestination && destinationLocation != null) {
                                    calculateRouteAndFare(destinationLocation!!)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            txtDestAddress.text = "Santa Cruz de la Sierra"
                        }
                    }
                }
            }
        })
    }
}
