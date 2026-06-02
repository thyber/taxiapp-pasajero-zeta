package com.taxiapp.passenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class MapSelectActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var btnBack: FloatingActionButton
    private lateinit var btnSolicitarTaxi: Button
    private lateinit var txtDestName: TextView
    private lateinit var txtDestAddress: TextView
    private lateinit var txtFare: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtDistance: TextView
    private lateinit var etRideDetails: EditText
    private lateinit var etSearchDestination: EditText
    private lateinit var rvSuggestions: RecyclerView
    private lateinit var suggestionsAdapter: SuggestionsAdapter
    private lateinit var fareInfo: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var btnServiceByDistance: LinearLayout
    private lateinit var btnServiceByHour: LinearLayout
    private lateinit var layoutDestino: LinearLayout
    
    private data class Suggestion(
        val placeId: String,
        val name: String,
        val address: String
    )
    
    private var selectedServiceType = "distance"
    private var selectedVehicleType = "auto"
    
    private val handler = Handler(Looper.getMainLooper())
    private var autocompleteRunnable: Runnable? = null

    private var googleMap: GoogleMap? = null
    private var routePolyline: Polyline? = null
    private var pickupMarker: Marker? = null
    private var destMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var destinationLocation: LatLng? = null
    private var currentFare = 0.0
    private var currentDistance = 0.0
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun getMinimumFare(vehicleType: String): Double {
        return when (vehicleType) {
            "moto" -> 5.0
            else -> 10.0
        }
    }

    private fun getRatePerKm(vehicleType: String): Double {
        return when (vehicleType) {
            "moto" -> 2.2
            else -> 3.0
        }
    }

    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var destName: String? = null
    private var destAddress: String? = null
    private var editMode: String = "destino"

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_select)

        sessionManager = SessionManager(this)

        destLat = intent.getDoubleExtra("destLat", 0.0)
        destLng = intent.getDoubleExtra("destLng", 0.0)
        destName = intent.getStringExtra("destName")
        destAddress = intent.getStringExtra("destAddress")
        editMode = intent.getStringExtra("editMode") ?: "destino"

        if (destLat != 0.0 && destLng != 0.0) {
            destinationLocation = LatLng(destLat, destLng)
        }

        initViews()
        setupClickListeners()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        checkLocationPermission()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnSolicitarTaxi = findViewById(R.id.btnSolicitarTaxi)
        txtDestName = findViewById(R.id.txtDestName)
        txtDestAddress = findViewById(R.id.txtDestAddress)
        txtFare = findViewById(R.id.txtFare)
        txtTime = findViewById(R.id.txtTime)
        txtDistance = findViewById(R.id.txtDistance)
        etRideDetails = findViewById<EditText>(R.id.etRideDetails)
        etSearchDestination = findViewById(R.id.etSearchDestination)
        rvSuggestions = findViewById(R.id.rvSuggestions)
        fareInfo = findViewById(R.id.fareInfo)
        bottomPanel = findViewById(R.id.bottomPanel)
        btnServiceByDistance = findViewById(R.id.btnServiceByDistance)
        btnServiceByHour = findViewById(R.id.btnServiceByHour)
        layoutDestino = findViewById(R.id.layoutDestino)
        
        suggestionsAdapter = SuggestionsAdapter(emptyList()) { suggestion ->
            selectSuggestion(suggestion)
        }
        rvSuggestions.layoutManager = LinearLayoutManager(this)
        rvSuggestions.adapter = suggestionsAdapter
        
        bottomSheetBehavior = BottomSheetBehavior.from(bottomPanel)
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        selectedVehicleType = intent.getStringExtra("vehicleType") ?: "auto"
        
        if (selectedVehicleType == "moto") {
            btnServiceByHour.visibility = View.GONE
        }

        destName?.let {
            txtDestName.text = it
            layoutDestino.visibility = View.VISIBLE
        }
        
        if (editMode == "pickup") {
            btnSolicitarTaxi.text = "Listo"
            btnServiceByDistance.visibility = View.GONE
            btnServiceByHour.visibility = View.GONE
            etRideDetails.visibility = View.GONE
        } else {
            btnSolicitarTaxi.text = "Listo"
        }
        
        setupSearchTextWatcher()
    }
    
    private fun setupSearchTextWatcher() {
        etSearchDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                autocompleteRunnable?.let { handler.removeCallbacks(it) }
                
                val query = s.toString().trim()
                if (query.length >= 2) {
                    autocompleteRunnable = Runnable {
                        fetchAutocompleteSuggestions(query)
                    }
                    handler.postDelayed(autocompleteRunnable!!, 300)
                } else if (query.isEmpty()) {
                    layoutDestino.visibility = View.GONE
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun fetchAutocompleteSuggestions(query: String) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val location = currentLocation ?: return
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?" +
                "input=$query" +
                "&location=${location.latitude},${location.longitude}" +
                "&radius=50000" +
                "&components=country:BO" +
                "&key=$apiKey"
        
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val predictions = json.getJSONArray("predictions")
                        val suggestions = mutableListOf<Suggestion>()
                        
                        for (i in 0 until predictions.length()) {
                            val prediction = predictions.getJSONObject(i)
                            val placeId = prediction.getString("place_id")
                            val description = prediction.getString("description")
                            val mainText = prediction.optJSONObject("structured_formatting")?.optString("main_text") ?: description
                            suggestions.add(Suggestion(placeId, mainText, description))
                        }
                        
                        runOnUiThread {
                            suggestionsAdapter.updateData(suggestions)
                        }
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }
    
    private fun selectSuggestion(suggestion: Suggestion) {
        rvSuggestions.visibility = View.GONE
        etSearchDestination.setText(suggestion.name)
        fetchPlaceDetails(suggestion.placeId)
    }
    
    private fun fetchPlaceDetails(placeId: String) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/place/details/json?" +
                "place_id=$placeId" +
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
                        
                        val name = result.optString("name", "Destino")
                        val address = result.optString("formatted_address", name)
                        
                        destLat = lat
                        destLng = lng
                        destName = name
                        destAddress = address
                        destinationLocation = LatLng(lat, lng)
                        
                        runOnUiThread {
                            txtDestName.text = name
                            txtDestAddress.text = address
                            layoutDestino.visibility = View.VISIBLE
                            
                            googleMap?.let { map ->
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f))
                                destMarker?.remove()
                                destMarker = map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(lat, lng))
                                        .title(name)
                                )
                            }
                            
                            calculateRouteAndFare(LatLng(lat, lng))
                        }
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }
    
    private class SuggestionsAdapter(
        private var suggestions: List<Suggestion>,
        private val onSuggestionClick: (Suggestion) -> Unit
    ) : RecyclerView.Adapter<SuggestionsAdapter.ViewHolder>() {
        
        fun updateData(newSuggestions: List<Suggestion>) {
            this.suggestions = newSuggestions
            notifyDataSetChanged()
        }
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(android.R.id.text1)
            val txtAddress: TextView = view.findViewById(android.R.id.text2)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val suggestion = suggestions[position]
            holder.txtName.text = suggestion.name
            holder.txtAddress.text = suggestion.address
            holder.itemView.setOnClickListener {
                onSuggestionClick(suggestion)
            }
        }
        
        override fun getItemCount(): Int = suggestions.size
    }
    
    private fun fetchPlaceDetailsFromDescription(description: String) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/geocode/json?" +
                "address=$description" +
                "&key=$apiKey"
        
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val results = json.getJSONArray("results")
                        if (results.length() > 0) {
                            val result = results.getJSONObject(0)
                            val geometry = result.getJSONObject("geometry")
                            val location = geometry.getJSONObject("location")
                            val lat = location.getDouble("lat")
                            val lng = location.getDouble("lng")
                            
                            val address = result.optString("formatted_address", description)
                            val components = result.optJSONArray("address_components")
                            var shortName = "Destino"
                            if (components != null) {
                                for (i in 0 until components.length()) {
                                    val component = components.getJSONObject(i)
                                    val types = component.optJSONArray("types")
                                    if (types != null) {
                                        for (j in 0 until types.length()) {
                                            val type = types.optString(j)
                                            if (type == "route" || type == "street_address") {
                                                shortName = component.optString("long_name", "Destino")
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                            
                            runOnUiThread {
                                txtDestName.text = shortName
                                txtDestAddress.text = address
                                layoutDestino.visibility = View.VISIBLE
                                
                                destinationLocation = LatLng(lat, lng)
                                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f))
                                calculateRouteAndFare(LatLng(lat, lng))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSolicitarTaxi.setOnClickListener {
            confirmAndRequestRide()
        }
        
        btnServiceByDistance.setOnClickListener {
            selectServiceType("distance", btnServiceByDistance, btnServiceByHour)
        }
        
        btnServiceByHour.setOnClickListener {
            selectServiceType("hour", btnServiceByHour, btnServiceByDistance)
        }
    }
    
    private fun selectServiceType(type: String, selected: LinearLayout, other: LinearLayout) {
        selectedServiceType = type
        selected.setBackgroundResource(R.drawable.bg_vehicle_type_selected)
        
        val selectedText = selected.getChildAt(1) as TextView
        selectedText.setTextColor(0xFF2563eb.toInt())
        
        other.setBackgroundResource(R.drawable.bg_vehicle_type)
        
        val otherText = other.getChildAt(1) as TextView
        otherText.setTextColor(0xFF6b7280.toInt())
        
        if (type == "hour") {
            currentFare = 50.0
            txtFare.text = "Bs ${String.format("%.0f", currentFare)}"
        } else {
            destinationLocation?.let {
                calculateRouteAndFare(it)
            }
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
                1000
            )
            return
        }
        getCurrentLocation()
    }

    private fun getCurrentLocation() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        currentLocation = it
                        googleMap?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(it.latitude, it.longitude),
                                15f
                            )
                        )

                        destinationLocation?.let { dest ->
                            calculateRouteAndFare(dest)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        googleMap?.isTrafficEnabled = true

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap?.isMyLocationEnabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        googleMap?.setOnCameraIdleListener {
            val center = googleMap?.cameraPosition?.target
            center?.let {
                destinationLocation = it
                calculateRouteAndFare(it)
            }
        }

        destinationLocation?.let {
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
        }
    }

    private fun getAddressFromLocation(lat: Double, lng: Double) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/geocode/json?" +
                "latlng=$lat,$lng" +
                "&key=$apiKey"
        
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    txtDestName.text = "Dirección no encontrada"
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    try {
                        val json = JSONObject(it)
                        val results = json.getJSONArray("results")
                        if (results.length() > 0) {
                            val address = results.getJSONObject(0).optString("formatted_address", "Dirección desconocida")
                            val components = results.getJSONObject(0).optJSONArray("address_components")
                            var shortName = "Destino"
                            if (components != null) {
                                for (i in 0 until components.length()) {
                                    val component = components.getJSONObject(i)
                                    val types = component.optJSONArray("types")
                                    if (types != null) {
                                        for (j in 0 until types.length()) {
                                            val type = types.optString(j)
                                            if (type == "route" || type == "street_address") {
                                                shortName = component.optString("long_name", "Destino")
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                            runOnUiThread {
                                txtDestName.text = shortName
                                txtDestAddress.text = address
                                layoutDestino.visibility = View.VISIBLE
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            txtDestName.text = "Dirección desconocida"
                        }
                    }
                }
            }
        })
    }
    
    private fun calculateRouteAndFare(destLatLng: LatLng) {
        val location = currentLocation ?: return

        val pickupLatLng = LatLng(location.latitude, location.longitude)

        pickupMarker?.remove()
        destMarker?.remove()
        routePolyline?.remove()

        pickupMarker = googleMap?.addMarker(
            MarkerOptions()
                .position(pickupLatLng)
                .title("Tu ubicación")
                .snippet("Punto de recogida")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )

        destMarker = googleMap?.addMarker(
            MarkerOptions()
                .position(destLatLng)
                .title("Destino")
                .snippet("Punto de llegada")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
        )

        getAddressFromLocation(destLatLng.latitude, destLatLng.longitude)

        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            destLatLng.latitude,
            destLatLng.longitude,
            results
        )

        currentDistance = results[0] / 1000.0

        // Tarifa dinámica según distancia
        if (selectedVehicleType == "moto") {
            // Moto: 2.2 bs/km siempre
            currentFare = currentDistance * 2.2
        } else {
            // Auto/Vagoneta: 3.48 hasta 6km, 3 después
            if (currentDistance <= 6.0) {
                currentFare = currentDistance * 3.48
            } else {
                currentFare = (6.0 * 3.48) + ((currentDistance - 6.0) * 3.0)
            }
        }
        
        // Extra para vagoneta
        if (selectedVehicleType == "vagoneta") {
            currentFare += 15.0
        }
        
        // Tarifa mínima
        val minimumFare = getMinimumFare(selectedVehicleType)
        currentFare = Math.max(currentFare, minimumFare)

        val estimatedTimeMinutes = (currentDistance * 3).toInt()

        txtFare.text = "Bs ${String.format("%.0f", currentFare)}"
        txtDistance.text = "${String.format("%.1f", currentDistance)} km"
        txtTime.text = "$estimatedTimeMinutes min"
    }

    private fun confirmAndRequestRide() {
        val location = currentLocation ?: run {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
            return
        }
        val dest = destinationLocation ?: run {
            Toast.makeText(this, "Selecciona un destino", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent()
        intent.putExtra("pickupLat", location.latitude)
        intent.putExtra("pickupLng", location.longitude)
        intent.putExtra("destLat", dest.latitude)
        intent.putExtra("destLng", dest.longitude)
        intent.putExtra("fare", currentFare)
        intent.putExtra("distanceKm", currentDistance)
        intent.putExtra("rideDetails", etRideDetails.text.toString())
        intent.putExtra("serviceType", selectedServiceType)
        intent.putExtra("destName", txtDestName.text.toString())
        intent.putExtra("destAddress", txtDestAddress.text.toString())
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            }
        }
    }
}
