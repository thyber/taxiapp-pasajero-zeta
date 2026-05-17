package com.taxiapp.passenger

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class SearchActivity : AppCompatActivity() {
    
    private lateinit var btnBack: ImageButton
    private lateinit var txtOrigin: TextView
    private lateinit var etDestination: EditText
    private lateinit var btnOpenMap: Button
    private lateinit var btnAuto: LinearLayout
    private lateinit var btnVagoneta: LinearLayout
    private lateinit var btnMoto: LinearLayout
    private lateinit var txtSuggestionsTitle: TextView
    private lateinit var suggestionsRecyclerView: RecyclerView
    private lateinit var suggestionAdapter: SuggestionAdapter
    
    private var vehicleType: String = "auto"
    private var currentLat: Double = -17.7833
    private var currentLng: Double = -63.1821
    private val handler = Handler(Looper.getMainLooper())
    private var autocompleteRunnable: Runnable? = null
    
    private val specialDestinations = listOf(
        Suggestion("airport", "Aeropuerto Viru Viru", "Santa Cruz, Porongo", -17.647282047729476, -63.140426667507256),
        Suggestion("plaza", "Plaza 24 de Septiembre", "Centro, Santa Cruz", -17.782562741597296, -63.18210817571676),
        Suggestion("mall", "Mall Ventura", "Equipetrol, Santa Cruz", -17.754328074785317, -63.199201875825565),
        Suggestion("cine", "Cine Center", "Equipetrol, Santa Cruz", -17.79845525527782, -63.17899290484053)
    )
    
    private val cityCenterLat = -17.7833
    private val cityCenterLng = -63.1821
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        
        vehicleType = intent.getStringExtra("vehicleType") ?: "auto"
        currentLat = intent.getDoubleExtra("currentLat", -17.7833)
        currentLng = intent.getDoubleExtra("currentLng", -63.1821)
        
        initViews()
        setupClickListeners()
        setupTextWatcher()
        updateVehicleSelection()
        updateSuggestions(specialDestinations)
    }
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        txtOrigin = findViewById(R.id.txtOrigin)
        etDestination = findViewById(R.id.etDestination)
        btnOpenMap = findViewById(R.id.btnOpenMap)
        btnAuto = findViewById(R.id.btnAuto)
        btnVagoneta = findViewById(R.id.btnVagoneta)
        btnMoto = findViewById(R.id.btnMoto)
        txtSuggestionsTitle = findViewById(R.id.txtSuggestionsTitle)
        suggestionsRecyclerView = findViewById(R.id.suggestionsRecyclerView)
        
        suggestionsRecyclerView.layoutManager = LinearLayoutManager(this)
        suggestionAdapter = SuggestionAdapter(emptyList()) { suggestion ->
            selectDestination(suggestion.name, suggestion.lat, suggestion.lng)
        }
        suggestionsRecyclerView.adapter = suggestionAdapter
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        btnOpenMap.setOnClickListener {
            openMapSelectActivity()
        }
        
        btnAuto.setOnClickListener {
            vehicleType = "auto"
            updateVehicleSelection()
        }
        
        btnVagoneta.setOnClickListener {
            vehicleType = "vagoneta"
            updateVehicleSelection()
        }
        
        btnMoto.setOnClickListener {
            vehicleType = "moto"
            updateVehicleSelection()
        }
    }
    
    private fun updateVehicleSelection() {
        val selectedTextColor = 0xFF2563eb.toInt()
        val unselectedTextColor = 0xFF6b7280.toInt()
        
        btnAuto.setBackgroundResource(if (vehicleType == "auto") R.drawable.bg_vehicle_type_selected else R.drawable.bg_vehicle_type)
        btnVagoneta.setBackgroundResource(if (vehicleType == "vagoneta") R.drawable.bg_vehicle_type_selected else R.drawable.bg_vehicle_type)
        btnMoto.setBackgroundResource(if (vehicleType == "moto") R.drawable.bg_vehicle_type_selected else R.drawable.bg_vehicle_type)
        
        val autoText = btnAuto.getChildAt(1) as TextView
        val vagonetaText = btnVagoneta.getChildAt(1) as TextView
        val motoText = btnMoto.getChildAt(1) as TextView
        
        autoText.setTextColor(if (vehicleType == "auto") selectedTextColor else unselectedTextColor)
        vagonetaText.setTextColor(if (vehicleType == "vagoneta") selectedTextColor else unselectedTextColor)
        motoText.setTextColor(if (vehicleType == "moto") selectedTextColor else unselectedTextColor)
    }
    
    private fun setupTextWatcher() {
        etDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                autocompleteRunnable?.let { handler.removeCallbacks(it) }
                
                val query = s.toString().trim()
                if (query.length >= 2) {
                    txtSuggestionsTitle.text = "Sugerencias"
                    autocompleteRunnable = Runnable {
                        fetchAutocompleteSuggestions(query)
                    }
                    handler.postDelayed(autocompleteRunnable!!, 300)
                } else {
                    txtSuggestionsTitle.text = "Destinos especiales"
                    updateSuggestions(specialDestinations)
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun fetchAutocompleteSuggestions(query: String) {
        val apiKey = "AIzaSyAO8drN6VO4LS1DtjA11hbiGVp-Sg5-PZI"
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?" +
                "input=$query" +
                "&location=$currentLat,$currentLng" +
                "&radius=50000" +
                "&components=country:BO" +
                "&types=geocode" +
                "&key=$apiKey"
        
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    updateSuggestions(emptyList())
                }
            }
            
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
                            updateSuggestions(suggestions)
                        }
                        
                        for (suggestion in suggestions) {
                            fetchPlaceDetails(suggestion)
                        }
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            updateSuggestions(emptyList())
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
        
        val client = OkHttpClient()
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
        suggestionAdapter = SuggestionAdapter(suggestions) { suggestion ->
            selectDestination(suggestion.name, suggestion.lat, suggestion.lng)
        }
        suggestionsRecyclerView.adapter = suggestionAdapter
    }
    
    private fun openMapSelectActivity() {
        val intent = Intent(this, MapSelectActivity::class.java)
        intent.putExtra("vehicleType", vehicleType)
        intent.putExtra("destLat", cityCenterLat)
        intent.putExtra("destLng", cityCenterLng)
        intent.putExtra("centerOnUser", false)
        startActivity(intent)
    }
    
    private fun selectDestination(name: String, lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) {
            Toast.makeText(this, "Cargando coordenadas...", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(this, MapSelectActivity::class.java)
        intent.putExtra("vehicleType", vehicleType)
        intent.putExtra("destName", name)
        intent.putExtra("destLat", lat)
        intent.putExtra("destLng", lng)
        startActivity(intent)
    }
}
