package com.taxiapp.passenger

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {
    
    companion object {
        private const val CHANNEL_ID = "passenger_notifications"
        private const val NOTIFICATION_ID = 2001
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var fabMenu: FloatingActionButton
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var bottomPanel: LinearLayout
    private val database = FirebaseDatabase.getInstance()
    private var notificationChildListener: ChildEventListener? = null
    
    private lateinit var btnAuto: LinearLayout
    private lateinit var btnVagoneta: LinearLayout
    private lateinit var btnMoto: LinearLayout
    
    private lateinit var layoutPickup: LinearLayout
    private lateinit var txtPickupLocation: TextView
    private lateinit var btnEditPickup: TextView
    
    private lateinit var layoutDestino: LinearLayout
    private lateinit var txtDestLocation: EditText
    private lateinit var btnEditDestino: TextView
    private lateinit var rvSuggestions: RecyclerView
    private lateinit var rvPickupSuggestions: RecyclerView
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var pickupSuggestionAdapter: SuggestionAdapter
    private var activeSearch: String = "destino"

    private lateinit var layoutPriceTime: LinearLayout
    private lateinit var txtPrice: TextView
    private lateinit var txtTime: TextView
    
    private lateinit var btnPedirTaxi: Button
    
    private var selectedVehicleType = "auto"
    private var pickupLat: Double = -17.7833
    private var pickupLng: Double = -63.1821
    private var pickupName: String = "Tu ubicación"
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var destName: String = ""
    
    private var selectedFare: Double = 0.0
    private var selectedDistance: Double = 0.0
    private var selectedTimeMinutes: Int = 0
    private var selectedRideDetails: String = ""
    private var selectedServiceType: String = "distance"
    
    private lateinit var sessionManager: SessionManager
    private var ridesWithDiscount = 0
    private var applyDiscountToRide = false
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var autocompleteRunnable: Runnable? = null
    private val markers = mutableListOf<Marker>()
    private var routePolyline: Polyline? = null
    
    private val specialDestinations = listOf(
        Suggestion("airport", "✈️ Aeropuerto Viru Viru", "Santa Cruz, Porongo", -17.647282047729476, -63.140426667507256),
        Suggestion("plaza", "🏛️ Plaza 24 de Septiembre", "Centro, Santa Cruz", -17.782562741597296, -63.18210817571676),
        Suggestion("mall", "🛍️ Mall Ventura", "Equipetrol, Santa Cruz", -17.754328074785317, -63.199201875825565),
        Suggestion("cine", "🎬 Cine Center", "Equipetrol, Santa Cruz", -17.79845525527782, -63.17899290484053),
        Suggestion("hospital", "🏥 Hospital Japonés", "Santa Cruz", -17.7923, -63.1894),
        Suggestion("terminal", "🚍 Terminal de Buses", "Santa Cruz", -17.7789, -63.1923)
    )
    
    private val EDIT_PICKUP_REQUEST = 1001
    private val EDIT_DESTINO_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        initViews()
        setupClickListeners()
        setupTextWatcher()
        initMap()
        checkLocationPermission()
        createNotificationChannel()
        setupNotificationListener()
        
        // Cargar el número de carreras con descuento disponibles
        loadRidesWithDiscount()
    }
    
    private fun loadRidesWithDiscount() {
        val passengerId = sessionManager.getUserId() ?: return
        val passengerRef = database.getReference("passengers").child(passengerId).child("ridesWithDiscount")
        passengerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ridesWithDiscount = snapshot.getValue(Int::class.java) ?: 0
                
                if (ridesWithDiscount > 0) {
                    Toast.makeText(
                        this@HomeActivity,
                        "¡Tienes $ridesWithDiscount carrera(s) con descuento de 5 Bs!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        notificationChildListener?.let { listener ->
            val passengerId = sessionManager.getUserId()
            if (passengerId != null) {
                database.getReference("passengers").child(passengerId).child("notifications")
                    .removeEventListener(listener)
            }
        }
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
    
    private fun setupNotificationListener() {
        val passengerId = sessionManager.getUserId() ?: return
        val notificationsRef = database.getReference("passengers").child(passengerId).child("notifications")
        
        notificationChildListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val notificationData = snapshot.value as? Map<String, Any> ?: return
                
                val read = notificationData["read"] as? Boolean ?: false
                if (!read) {
                    val title = notificationData["title"] as? String ?: "Notificación"
                    val message = notificationData["message"] as? String ?: ""
                    
                    // Mostrar notificación del sistema
                    showSystemNotification(title, message)
                    
                    // Marcar como leída
                    snapshot.ref.child("read").setValue(true)
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        
        notificationsRef.addChildEventListener(notificationChildListener!!)
    }
    
    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        fabMenu = findViewById(R.id.fabMenu)
        fabMyLocation = findViewById(R.id.fabMyLocation)
        bottomPanel = findViewById(R.id.bottomPanel)
        
        btnAuto = findViewById(R.id.btnAuto)
        btnVagoneta = findViewById(R.id.btnVagoneta)
        btnMoto = findViewById(R.id.btnMoto)
        
        layoutPickup = findViewById(R.id.layoutPickup)
        txtPickupLocation = findViewById(R.id.txtPickupLocation)
        btnEditPickup = findViewById(R.id.btnEditPickup)
        rvPickupSuggestions = findViewById(R.id.rvPickupSuggestions)
        
        layoutDestino = findViewById(R.id.layoutDestino)
        txtDestLocation = findViewById(R.id.txtDestLocation)
        btnEditDestino = findViewById(R.id.btnEditDestino)
        rvSuggestions = findViewById(R.id.rvSuggestions)
        
        layoutPriceTime = findViewById(R.id.layoutPriceTime)
        txtPrice = findViewById(R.id.txtPrice)
        txtTime = findViewById(R.id.txtTime)
        
        btnPedirTaxi = findViewById(R.id.btnPedirTaxi)
        
        rvSuggestions.layoutManager = LinearLayoutManager(this)
        suggestionAdapter = SuggestionAdapter(emptyList()) { suggestion ->
            selectDestination(suggestion)
        }
        rvSuggestions.adapter = suggestionAdapter

        rvPickupSuggestions.layoutManager = LinearLayoutManager(this)
        pickupSuggestionAdapter = SuggestionAdapter(emptyList()) { suggestion ->
            selectPickupLocation(suggestion)
        }
        rvPickupSuggestions.adapter = pickupSuggestionAdapter
    }
    
    private fun setupClickListeners() {
        fabMenu.setOnClickListener {
            drawerLayout.openDrawer(navigationView)
        }
        
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> {
                    Toast.makeText(this, "Mi perfil", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_notifications -> {
                    val intent = Intent(this, NotificationsActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_ride_history -> {
                    val intent = Intent(this, PassengerHistoryActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_contact -> {
                    Toast.makeText(this, "Contactar central", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_about -> {
                    Toast.makeText(this, "Acerca de nosotros", Toast.LENGTH_SHORT).show()
                }
            }
            drawerLayout.closeDrawer(navigationView)
            true
        }
        
        fabMyLocation.setOnClickListener {
            centerOnMyLocation()
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
        
        btnEditPickup.setOnClickListener {
            openMapSelectActivity(EDIT_PICKUP_REQUEST)
        }
        
        btnEditDestino.setOnClickListener {
            openMapSelectActivity(EDIT_DESTINO_REQUEST)
        }
        
        btnPedirTaxi.setOnClickListener {
            openRideTracking()
        }
    }
    
    private fun setupTextWatcher() {
        txtPickupLocation.setOnClickListener {
            activeSearch = "pickup"
            rvPickupSuggestions.visibility = View.VISIBLE
            rvSuggestions.visibility = View.GONE
        }
        
        txtPickupLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activeSearch = "pickup"
                autocompleteRunnable?.let { handler.removeCallbacks(it) }
                
                val query = s.toString().trim()
                
                if (query.isNotEmpty()) {
                    rvPickupSuggestions.visibility = View.VISIBLE
                    rvSuggestions.visibility = View.GONE
                } else {
                    rvPickupSuggestions.visibility = View.GONE
                }
                
                if (query.length >= 2) {
                    autocompleteRunnable = Runnable {
                        fetchAutocompleteSuggestions(query)
                    }
                    handler.postDelayed(autocompleteRunnable!!, 300)
                } else {
                    if (activeSearch == "pickup") {
                        updatePickupSuggestions(emptyList())
                    } else {
                        updateSuggestions(emptyList())
                    }
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })

        txtDestLocation.setOnClickListener {
            activeSearch = "destino"
            rvSuggestions.visibility = View.VISIBLE
            rvPickupSuggestions.visibility = View.GONE
        }
        
        txtDestLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activeSearch = "destino"
                autocompleteRunnable?.let { handler.removeCallbacks(it) }
                
                val query = s.toString().trim()
                
                if (query.isNotEmpty()) {
                    rvSuggestions.visibility = View.VISIBLE
                    rvPickupSuggestions.visibility = View.GONE
                } else {
                    rvSuggestions.visibility = View.GONE
                }
                
                if (query.length >= 2) {
                    autocompleteRunnable = Runnable {
                        fetchAutocompleteSuggestions(query)
                    }
                    handler.postDelayed(autocompleteRunnable!!, 300)
                } else {
                    if (activeSearch == "pickup") {
                        updatePickupSuggestions(emptyList())
                    } else {
                        updateSuggestions(emptyList())
                    }
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun fetchAutocompleteSuggestions(query: String) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?" +
                "input=$query" +
                "&location=$pickupLat,$pickupLng" +
                "&radius=10000" +
                "&strictbounds=true" +
                "&components=country:BO" +
                "&key=$apiKey"
        
        runOnUiThread {
            Toast.makeText(this, "Buscando sugerencias...", Toast.LENGTH_SHORT).show()
        }
        
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@HomeActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                    if (activeSearch == "destino") {
                        updateSuggestions(emptyList())
                    } else {
                        updatePickupSuggestions(emptyList())
                    }
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val status = json.optString("status", "UNKNOWN_ERROR")
                        
                        if (status != "OK") {
                            runOnUiThread {
                                Toast.makeText(this@HomeActivity, "Error API: $status", Toast.LENGTH_LONG).show()
                                if (activeSearch == "destino") {
                                    updateSuggestions(emptyList())
                                } else {
                                    updatePickupSuggestions(emptyList())
                                }
                            }
                            return
                        }
                        
                        val predictions = json.getJSONArray("predictions")
                        val suggestions = mutableListOf<Suggestion>()
                        
                        for (i in 0 until predictions.length()) {
                            val prediction = predictions.getJSONObject(i)
                            val placeId = prediction.getString("place_id")
                            val description = prediction.getString("description")
                            val mainText = prediction.getJSONObject("structured_formatting")
                                .getString("main_text")
                            
                            suggestions.add(Suggestion(
                                id = placeId,
                                name = mainText,
                                address = description,
                                lat = 0.0,
                                lng = 0.0
                            ))
                        }
                        
                        runOnUiThread {
                            Toast.makeText(this@HomeActivity, "Se encontraron ${suggestions.size} sugerencias", Toast.LENGTH_SHORT).show()
                            if (activeSearch == "destino") {
                                updateSuggestions(suggestions)
                            } else {
                                updatePickupSuggestions(suggestions)
                            }
                        }
                        
                        for (suggestion in suggestions) {
                            fetchPlaceDetails(suggestion)
                        }
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(this@HomeActivity, "Error al procesar: ${e.message}", Toast.LENGTH_LONG).show()
                            if (activeSearch == "destino") {
                                updateSuggestions(emptyList())
                            } else {
                                updatePickupSuggestions(emptyList())
                            }
                        }
                    }
                }
            }
        })
    }
    
    private fun fetchPlaceDetails(suggestion: Suggestion) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/place/details/json?" +
                "place_id=${suggestion.id}" +
                "&key=$apiKey"
        
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val result = json.getJSONObject("result")
                        val geometry = result.getJSONObject("geometry")
                        val location = geometry.getJSONObject("location")
                        val lat = location.getDouble("lat")
                        val lng = location.getDouble("lng")
                        
                        suggestion.lat = lat
                        suggestion.lng = lng
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }
    
    private fun updateSuggestions(suggestions: List<Suggestion>) {
        suggestionAdapter.updateData(suggestions)
        rvSuggestions.visibility = View.VISIBLE
        rvPickupSuggestions.visibility = View.GONE
    }

    private fun updatePickupSuggestions(suggestions: List<Suggestion>) {
        pickupSuggestionAdapter.updateData(suggestions)
        rvPickupSuggestions.visibility = View.VISIBLE
        rvSuggestions.visibility = View.GONE
    }
    
    private fun clearMarkers() {
        for (marker in markers) {
            marker.remove()
        }
        markers.clear()
    }

    private fun clearRoute() {
        routePolyline?.remove()
        routePolyline = null
    }

    private fun drawRoute() {
        if (pickupLat == 0.0 || pickupLng == 0.0 || destLat == 0.0 || destLng == 0.0) {
            return
        }

        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val origin = "$pickupLat,$pickupLng"
        val destination = "$destLat,$destLng"
        
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=$origin" +
                "&destination=$destination" +
                "&mode=driving" +
                "&key=$apiKey"
        
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val status = json.optString("status", "ERROR")
                        
                        if (status == "OK") {
                            val routes = json.getJSONArray("routes")
                            if (routes.length() > 0) {
                                val route = routes.getJSONObject(0)
                                val overviewPolyline = route.getJSONObject("overview_polyline")
                                val points = overviewPolyline.getString("points")
                                
                                val decodedPath = decodePoly(points)
                                
                                runOnUiThread {
                                    clearRoute()
                                    val polylineOptions = PolylineOptions()
                                        .addAll(decodedPath)
                                        .width(12f)
                                        .color(0xFFFF9800.toInt()) // Color naranja (Material Design Orange)
                                        .geodesic(true)
                                    
                                    routePolyline = googleMap?.addPolyline(polylineOptions)
                                    
                                    // Mover la cámara para ver toda la ruta
                                    val boundsBuilder = LatLngBounds.Builder()
                                    boundsBuilder.include(LatLng(pickupLat, pickupLng))
                                    boundsBuilder.include(LatLng(destLat, destLng))
                                    val bounds = boundsBuilder.build()
                                    val padding = 100 // Espacio en pixeles alrededor de la ruta
                                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                                    googleMap?.animateCamera(cameraUpdate)
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
                b = encoded[index].code - 63
                index++
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat
            
            shift = 0
            result = 0
            do {
                b = encoded[index].code - 63
                index++
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dLng
            
            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }
        return poly
    }

    private fun selectDestination(suggestion: Suggestion) {
        if (suggestion.lat == 0.0 && suggestion.lng == 0.0) {
            Toast.makeText(this, "Cargando coordenadas...", Toast.LENGTH_SHORT).show()
            return
        }
        
        destLat = suggestion.lat
        destLng = suggestion.lng
        destName = suggestion.name
        
        txtDestLocation.setText(suggestion.address)
        rvSuggestions.visibility = View.GONE
        rvPickupSuggestions.visibility = View.GONE
        
        // Limpiar marcadores y rutas anteriores
        clearMarkers()
        clearRoute()
        
        // Mover el mapa al destino seleccionado
        val destLatLng = LatLng(destLat, destLng)
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(destLatLng, 15f))
        
        // Agregar marcador en el destino
        val marker = googleMap?.addMarker(
            MarkerOptions()
                .position(destLatLng)
                .title("Destino: $destName")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        marker?.let { markers.add(it) }
        
        // Si hay punto de partida, también mostramos su marcador y dibujamos la ruta
        if (pickupLat != 0.0 && pickupLng != 0.0) {
            val pickupMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(LatLng(pickupLat, pickupLng))
                    .title("Origen: $pickupName")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            pickupMarker?.let { markers.add(it) }
            
            // Dibujar la ruta
            drawRoute()
        }
        
        calculateFare()
        showPriceAndTime()
    }

    private fun selectPickupLocation(suggestion: Suggestion) {
        if (suggestion.lat == 0.0 && suggestion.lng == 0.0) {
            Toast.makeText(this, "Cargando coordenadas...", Toast.LENGTH_SHORT).show()
            return
        }
        
        pickupLat = suggestion.lat
        pickupLng = suggestion.lng
        pickupName = suggestion.name
        
        txtPickupLocation.setText(suggestion.address)
        rvPickupSuggestions.visibility = View.GONE
        rvSuggestions.visibility = View.GONE

        // Limpiar marcadores y rutas anteriores
        clearMarkers()
        clearRoute()

        // Mover el mapa al punto de partida
        val pickupLatLng = LatLng(pickupLat, pickupLng)
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f))
        
        // Agregar marcador en el punto de partida
        val marker = googleMap?.addMarker(
            MarkerOptions()
                .position(pickupLatLng)
                .title("Origen: $pickupName")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        marker?.let { markers.add(it) }

        // Si hay destino, también mostramos su marcador y calculamos la ruta
        if (destLat != 0.0 && destLng != 0.0) {
            val destMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(LatLng(destLat, destLng))
                    .title("Destino: $destName")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            destMarker?.let { markers.add(it) }
            
            // Dibujar la ruta
            drawRoute()
            
            calculateFare()
            showPriceAndTime()
        }
    }
    
    private fun calculateFare() {
        val distance = calculateDistance(pickupLat, pickupLng, destLat, destLng)
        selectedDistance = distance
        
        val minimumFare = when (selectedVehicleType) {
            "moto" -> 5.0
            else -> 10.0
        }
        
        // Tarifa dinámica según distancia
        var fare = if (selectedVehicleType == "moto") {
            // Moto: 2.2 bs/km siempre
            distance * 2.2
        } else {
            // Auto/Vagoneta: 3.48 hasta 6km, 3 después
            if (distance <= 6.0) {
                distance * 3.48
            } else {
                (6.0 * 3.48) + ((distance - 6.0) * 3.0)
            }
        }
        
        if (selectedVehicleType == "vagoneta") {
            fare += 15.0
        }
        
        if (fare < minimumFare) {
            fare = minimumFare
        }
        
        // Aplicar descuento si hay disponibles
        applyDiscountToRide = ridesWithDiscount > 0
        if (applyDiscountToRide) {
            selectedFare = fare - 5.0
            if (selectedFare < 0) {
                selectedFare = 0.0
            }
        } else {
            selectedFare = fare
        }
        
        selectedTimeMinutes = (distance * 3).toInt()
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
    
    private fun selectVehicle(type: String, selected: LinearLayout, other1: LinearLayout, other2: LinearLayout) {
        selectedVehicleType = type
        selected.setBackgroundResource(R.drawable.bg_vehicle_modern_selected)
        
        val selectedText = selected.getChildAt(1) as TextView
        selectedText.setTextColor(0xFF2563eb.toInt())
        
        other1.setBackgroundResource(R.drawable.bg_vehicle_modern)
        other2.setBackgroundResource(R.drawable.bg_vehicle_modern)
        
        val otherText1 = other1.getChildAt(1) as TextView
        val otherText2 = other2.getChildAt(1) as TextView
        otherText1.setTextColor(0xFF64748b.toInt())
        otherText2.setTextColor(0xFF64748b.toInt())
        
        if (destLat != 0.0 && destLng != 0.0) {
            calculateFare()
            showPriceAndTime()
        }
    }
    
    private fun openMapSelectActivity(requestCode: Int) {
        currentLocation?.let { location ->
            val intent = Intent(this, MapSelectActivity::class.java)
            intent.putExtra("vehicleType", selectedVehicleType)
            intent.putExtra("currentLat", location.latitude)
            intent.putExtra("currentLng", location.longitude)
            intent.putExtra("editMode", if (requestCode == EDIT_PICKUP_REQUEST) "pickup" else "destino")
            startActivityForResult(intent, requestCode)
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
                pickupLat = finalLocation.latitude
                pickupLng = finalLocation.longitude
                txtPickupLocation.text = "Tu ubicación"
                
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
    
    private fun initMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        googleMap?.isTrafficEnabled = true
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = false
        }
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
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val lat = data.getDoubleExtra("destLat", 0.0)
            val lng = data.getDoubleExtra("destLng", 0.0)
            val name = data.getStringExtra("destName") ?: "Ubicación"
            val fare = data.getDoubleExtra("fare", 0.0)
            val distanceKm = data.getDoubleExtra("distanceKm", 0.0)
            
            if (requestCode == EDIT_PICKUP_REQUEST) {
                pickupLat = lat
                pickupLng = lng
                pickupName = name
                txtPickupLocation.text = name
            } else if (requestCode == EDIT_DESTINO_REQUEST) {
                destLat = lat
                destLng = lng
                destName = name
                txtDestLocation.setText(name)
                selectedFare = fare
                selectedDistance = distanceKm
                selectedTimeMinutes = (distanceKm * 3).toInt()
                
                calculateFare()
                showPriceAndTime()
            }
        }
    }
    
    private fun showPriceAndTime() {
        if (applyDiscountToRide) {
            val originalFare = selectedFare + 5.0
            txtPrice.text = "Bs ${String.format("%.0f", selectedFare)} (Descuento: 5 Bs)"
            Toast.makeText(
                this,
                "¡Descuento de 5 Bs aplicado! Te quedan ${ridesWithDiscount - 1} carrera(s) con descuento",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            txtPrice.text = "Bs ${String.format("%.0f", selectedFare)}"
        }
        txtTime.text = "${selectedTimeMinutes} min"
        layoutPriceTime.visibility = View.VISIBLE
        btnPedirTaxi.isEnabled = true
    }
    
    private fun openRideTracking() {
        val intent = Intent(this, RideTrackingActivity::class.java)
        intent.putExtra("pickupLat", pickupLat)
        intent.putExtra("pickupLng", pickupLng)
        intent.putExtra("destLat", destLat)
        intent.putExtra("destLng", destLng)
        intent.putExtra("fare", selectedFare)
        intent.putExtra("distanceKm", selectedDistance)
        intent.putExtra("rideDetails", selectedRideDetails)
        intent.putExtra("vehicleType", selectedVehicleType)
        intent.putExtra("serviceType", selectedServiceType)
        intent.putExtra("destName", destName)
        intent.putExtra("applyDiscount", applyDiscountToRide)
        startActivity(intent)
    }
}
