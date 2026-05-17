package com.taxiapp.passenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.bottomsheet.BottomSheetBehavior

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var fabMenu: FloatingActionButton
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var searchBar: LinearLayout
    private lateinit var btnOpenMap: TextView
    private lateinit var btnAuto: LinearLayout
    private lateinit var btnVagoneta: LinearLayout
    private lateinit var btnMoto: LinearLayout
    private lateinit var destAirport: LinearLayout
    private lateinit var destPlaza: LinearLayout
    private lateinit var destMall: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var bottomSheetHandle: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var sessionManager: SessionManager
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var currentLocation: Location? = null
    private val driverMarkers = mutableMapOf<String, Marker>()
    
    private var selectedVehicleType = "auto"
    
    companion object {
        val AIRPORT_LAT = -17.6307
        val AIRPORT_LNG = -63.2336
        val PLAZA_LAT = -17.7833
        val PLAZA_LNG = -63.1821
        val MALL_LAT = -17.7639
        val MALL_LNG = -63.1714
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        sessionManager = SessionManager(this)
        firebaseHelper = FirebaseHelper()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        if (!sessionManager.isLoggedIn()) {
            goToLoginActivity()
            return
        }
        
        initViews()
        setupClickListeners()
        
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        checkLocationPermission()
        checkForAppUpdate()
    }
    
    private fun checkForAppUpdate() {
        firebaseHelper.getAppVersion { versionData ->
            versionData?.let { data ->
                val latestVersion = data["version"] as? String ?: ""
                val downloadUrl = data["downloadUrl"] as? String ?: ""
                val releaseNotes = data["releaseNotes"] as? String ?: ""
                val forceUpdate = data["forceUpdate"] as? Boolean ?: false
                
                if (latestVersion.isNotEmpty() && downloadUrl.isNotEmpty()) {
                    try {
                        val packageInfo = packageManager.getPackageInfo(packageName, 0)
                        val currentVersion = packageInfo.versionName
                        
                        if (currentVersion != latestVersion) {
                            runOnUiThread {
                                showUpdateDialog(latestVersion, downloadUrl, releaseNotes, forceUpdate)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    private fun showUpdateDialog(version: String, downloadUrl: String, releaseNotes: String, forceUpdate: Boolean) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Actualización Disponible! 📱")
            .setMessage("Versión: $version\n\nNotas:\n$releaseNotes")
            .setPositiveButton("Descargar") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
                }
            }
        
        if (!forceUpdate) {
            dialog.setNegativeButton("Después", null)
        }
        
        dialog.setCancelable(!forceUpdate)
        dialog.show()
    }
    
    private fun getDriverIcon(): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_car) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 60, 60, false)
        return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
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

    private fun listenToNearbyDrivers() {
        val database = com.google.firebase.database.FirebaseDatabase.getInstance()
        val activeDriversRef = database.getReference("activeDrivers")
        
        activeDriversRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                driverMarkers.values.forEach { it.remove() }
                driverMarkers.clear()
                
                val passengerLoc = currentLocation ?: return
                
                for (driverSnapshot in snapshot.children) {
                    val driverId = driverSnapshot.key ?: continue
                    val driverData = driverSnapshot.value as? Map<String, Any> ?: continue
                    val locationMap = driverData["location"] as? Map<String, Double> ?: continue
                    
                    val lat = locationMap["lat"] ?: continue
                    val lng = locationMap["lng"] ?: continue
                    val driverName = driverData["name"] as? String ?: "Conductor"
                    val status = driverData["status"] as? String ?: "available"
                    
                    val distance = calculateDistance(passengerLoc.latitude, passengerLoc.longitude, lat, lng)
                    
                    if (distance <= 6.0) {
                        val driverLatLng = LatLng(lat, lng)
                        
                        val driverIcon = getDriverIcon()
                        val marker = googleMap?.addMarker(
                            MarkerOptions()
                                .position(driverLatLng)
                                .title(driverName)
                                .snippet("${if (status == "available") "Disponible" else "Ocupado"} - ${String.format("%.1f", distance)} km")
                                .icon(driverIcon)
                        )
                        
                        marker?.let {
                            driverMarkers[driverId] = it
                        }
                    }
                }
            }
            
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }
    
    private fun initViews() {
        fabMenu = findViewById<FloatingActionButton>(R.id.fabMenu)
        fabMyLocation = findViewById<FloatingActionButton>(R.id.fabMyLocation)
        searchBar = findViewById<LinearLayout>(R.id.searchBar)
        btnOpenMap = findViewById<TextView>(R.id.btnOpenMap)
        btnAuto = findViewById<LinearLayout>(R.id.btnAuto)
        btnVagoneta = findViewById<LinearLayout>(R.id.btnVagoneta)
        btnMoto = findViewById<LinearLayout>(R.id.btnMoto)
        destAirport = findViewById<LinearLayout>(R.id.destAirport)
        destPlaza = findViewById<LinearLayout>(R.id.destPlaza)
        destMall = findViewById<LinearLayout>(R.id.destMall)
        bottomPanel = findViewById<LinearLayout>(R.id.bottomPanel)
        bottomSheetHandle = findViewById(R.id.bottomSheetHandle)
        
        bottomSheetBehavior = BottomSheetBehavior.from(bottomPanel)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.peekHeight = 260
        bottomSheetBehavior.isHideable = false
        
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
            }
            
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        })
        
        bottomSheetHandle.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }
    
    private fun setupClickListeners() {
        fabMenu.setOnClickListener {
            showMenuDialog()
        }
        
        fabMyLocation.setOnClickListener {
            centerOnMyLocation()
        }
        
        searchBar.setOnClickListener {
            openSearchActivity()
        }
        
        btnOpenMap.setOnClickListener {
            openMapSelectActivity()
        }
        
        btnAuto.setOnClickListener {
            selectVehicle("auto", btnAuto, btnVagoneta, btnMoto)
        }
        
        btnVagoneta.setOnClickListener {
            selectVehicle("vagoneta", btnVagoneta, btnAuto, btnMoto)
        }
        
        btnMoto.setOnClickListener {
            selectVehicle("moto", btnMoto, btnAuto, btnVagoneta)
        }
        
        destAirport.setOnClickListener {
            selectDestination("Aeropuerto Viru Viru", AIRPORT_LAT, AIRPORT_LNG)
        }
        
        destPlaza.setOnClickListener {
            selectDestination("Plaza 24 de Septiembre", PLAZA_LAT, PLAZA_LNG)
        }
        
        destMall.setOnClickListener {
            selectDestination("Mall Ventura", MALL_LAT, MALL_LNG)
        }
    }
    
    private fun openMapSelectActivity() {
        currentLocation?.let { location ->
            val intent = Intent(this, MapSelectActivity::class.java)
            intent.putExtra("vehicleType", selectedVehicleType)
            intent.putExtra("destLat", location.latitude)
            intent.putExtra("destLng", location.longitude)
            intent.putExtra("centerOnUser", true)
            startActivity(intent)
        } ?: run {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
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
                val latLng = LatLng(finalLocation.latitude, finalLocation.longitude)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }
    }
    
    private fun centerOnMyLocation() {
        currentLocation?.let {
            val latLng = LatLng(it.latitude, it.longitude)
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        } ?: run {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
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
        
        listenToNearbyDrivers()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        }
    }
    
    private fun selectVehicle(type: String, selected: LinearLayout, other1: LinearLayout, other2: LinearLayout) {
        selectedVehicleType = type
        selected.setBackgroundResource(R.drawable.bg_vehicle_type_selected)
        
        val selectedText = selected.getChildAt(1) as android.widget.TextView
        selectedText.setTextColor(0xFF2563eb.toInt())
        
        other1.setBackgroundResource(R.drawable.bg_vehicle_type)
        other2.setBackgroundResource(R.drawable.bg_vehicle_type)
        
        val otherText1 = other1.getChildAt(1) as android.widget.TextView
        val otherText2 = other2.getChildAt(1) as android.widget.TextView
        otherText1.setTextColor(0xFF6b7280.toInt())
        otherText2.setTextColor(0xFF6b7280.toInt())
        
        Toast.makeText(this, "Seleccionado: ${if (type == "auto") "Viaje" else if (type == "vagoneta") "Vagoneta" else "Moto"}", Toast.LENGTH_SHORT).show()
    }
    
    private fun openSearchActivity() {
        currentLocation?.let { location ->
            val intent = Intent(this, SearchActivity::class.java)
            intent.putExtra("vehicleType", selectedVehicleType)
            intent.putExtra("currentLat", location.latitude)
            intent.putExtra("currentLng", location.longitude)
            startActivity(intent)
        } ?: run {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun selectDestination(name: String, lat: Double, lng: Double) {
        val intent = Intent(this, MapSelectActivity::class.java)
        intent.putExtra("vehicleType", selectedVehicleType)
        intent.putExtra("destName", name)
        intent.putExtra("destLat", lat)
        intent.putExtra("destLng", lng)
        startActivity(intent)
    }
    
    private fun showMenuDialog() {
        val userName = sessionManager.getUserName() ?: "Usuario"
        val menuItems = arrayOf("Ciudad", "Historial de solicitudes", "Entregas", "Ciudad a Ciudad", "Flete", "Notificaciones", "Seguridad", "Configuración", "Ayuda", "Soporte", "Cerrar sesión")
        AlertDialog.Builder(this)
            .setTitle("Hola, $userName 👋")
            .setItems(menuItems) { _, which ->
                if (which == menuItems.size - 1) {
                    logout()
                } else {
                    Toast.makeText(this, "Seleccionaste: ${menuItems[which]}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
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
}
