package com.example.a259366project

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var hourlyDataTextView: TextView
    private lateinit var cityEditText: EditText
    private lateinit var getLocationButton: Button
    private lateinit var getGPSLocationButton: Button
    private lateinit var addToFavoritesButton: Button
    private lateinit var geocoder: Geocoder
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var favoritesLayout: LinearLayout
    private val favoriteCities: HashSet<String> = HashSet()
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        temperatureTextView = findViewById(R.id.temperatureTextView)
        hourlyDataTextView = findViewById(R.id.hourlyDataTextView)
        getGPSLocationButton = findViewById(R.id.getGPSLocationButton)
        cityEditText = findViewById(R.id.cityEditText)
        getLocationButton = findViewById(R.id.getLocationButton)
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        addToFavoritesButton = findViewById(R.id.addToFavoritesButton)
        favoritesLayout = findViewById(R.id.favoritesLayout)
        geocoder = Geocoder(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getLocationButton.setOnClickListener {
            val cityName = cityEditText.text.toString()
            getLocationCoordinates(cityName)
        }

        getGPSLocationButton.setOnClickListener {
            if (checkLocationPermission()) {
                getGPSLocation()
                cityEditText.setText("Your location")
            } else {
                requestLocationPermission()
            }
        }

        sharedPreferences = getSharedPreferences("Favorites", Context.MODE_PRIVATE)
        loadFavoriteCities()

        addToFavoritesButton.setOnClickListener {
            val cityName = cityEditText.text.toString()
            if (cityName.isNotEmpty()) {
                addFavoriteCity(cityName)
                createFavoriteButton(cityName)
                saveFavoriteCities()
                cityEditText.text.clear()
            }
        }
    }

    private fun fetchWeatherData(latitude: String, longitude: String) {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true&hourly=temperature_2m,relativehumidity_2m,windspeed_10m"

        val request = Request.Builder()
            .url(url)
            .build()

        WeatherTask().execute(request)
    }

    private inner class WeatherTask : AsyncTask<Request, Void, String>() {

        override fun doInBackground(vararg params: Request): String? {
            val client = OkHttpClient()
            val request = params[0]

            return try {
                val response = client.newCall(request).execute()
                response.body?.string()
            } catch (e: IOException) {
                null
            }
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)

            if (result != null) {
                try {
                    val weatherData = parseWeatherData(result)
                    temperatureTextView.text = "Temperature now: ${weatherData.temperature}°C"

                    val hourlyDataText = StringBuilder()
                    for (hourlyData in weatherData.hourlyData) {
                        hourlyDataText.append("Time: ${hourlyData.time}\n")
                        hourlyDataText.append("Temperature: ${hourlyData.temperature2m}°C\n")
                        hourlyDataText.append("Wind Speed: ${hourlyData.windSpeed10m}\n\n")
                    }
                    hourlyDataTextView.text = hourlyDataText.toString()
                } catch (e: JSONException) {
                    temperatureTextView.text = "Failed to parse weather data."
                }
            } else {
                temperatureTextView.text = "Failed to fetch weather data."
            }
        }
    }

    private data class WeatherData(val temperature: Double, val hourlyData: List<HourlyData>)

    private data class HourlyData(val time: String, val temperature2m: Double, val windSpeed10m: Double)

    private fun parseWeatherData(json: String): WeatherData {
        val jsonObject = JSONObject(json)

        // Parsing temperature for current weather
        if (!jsonObject.has("current_weather")) {
            throw JSONException("No 'current_weather' object found")
        }
        val currentWeatherObject = jsonObject.getJSONObject("current_weather")
        val temperature = currentWeatherObject.optDouble("temperature")
        if (temperature.isNaN()) {
            throw JSONException("No 'temperature' value found in 'current_weather'")
        }

        // Parsing hourly data
        if (!jsonObject.has("hourly")) {
            throw JSONException("No 'hourly' object found")
        }
        val hourlyObject = jsonObject.getJSONObject("hourly")

        val timeArray = hourlyObject.getJSONArray("time")
        val temperatureArray = hourlyObject.getJSONArray("temperature_2m")
        val windSpeedArray = hourlyObject.getJSONArray("windspeed_10m")

        val hourlyData = mutableListOf<HourlyData>()
        for (i in 0 until timeArray.length()) {
            val time = timeArray.getString(i)
            val temperature2m = temperatureArray.getDouble(i)
            val windSpeed10m = windSpeedArray.getDouble(i)
            hourlyData.add(HourlyData(time, temperature2m, windSpeed10m))
        }

        return WeatherData(temperature, hourlyData)
    }

    private fun getLocationCoordinates(cityName: String) {
        try {
            val addresses = geocoder.getFromLocationName(cityName, 1)
            if (addresses.isNotEmpty()) {
                val latitude = addresses[0].latitude.toString()
                val longitude = addresses[0].longitude.toString()
                latitudeTextView.text = "Latitude: $latitude"
                longitudeTextView.text = "Longitude: $longitude"
                fetchWeatherData(latitude, longitude)
            } else {
                temperatureTextView.text = "Invalid city name."
                hourlyDataTextView.text = ""
            }
        } catch (e: IOException) {
            Log.e("LOCATION", "Error getting location coordinates: ${e.message}")
            temperatureTextView.text = "Error getting location coordinates."
            hourlyDataTextView.text = ""
        }
    }

    private fun getGPSLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude.toString()
                    val longitude = location.longitude.toString()
                    latitudeTextView.text = "Latitude: $latitude"
                    longitudeTextView.text = "Longitude: $longitude"
                    fetchWeatherData(latitude, longitude)
                } else {
                    temperatureTextView.text = "Failed to get GPS location."
                    hourlyDataTextView.text = ""
                }
            }
            .addOnFailureListener { e ->
                Log.e("GPS", "Error getting GPS location: ${e.message}")
                temperatureTextView.text = "Error getting GPS location."
                hourlyDataTextView.text = ""
            }
    }

    private fun checkLocationPermission(): Boolean {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        val result = ContextCompat.checkSelfPermission(this, permission)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("favoriteCities", ArrayList(favoriteCities))
    }

    private fun addFavoriteCity(cityName: String) {
        favoriteCities.add(cityName)
    }

    private fun createFavoriteButton(cityName: String) {
        val favoriteButton = Button(this)
        favoriteButton.text = cityName

        favoriteButton.setOnClickListener {
            // Handle click event for favorite city button
            Toast.makeText(this, "Clicked on $cityName", Toast.LENGTH_SHORT).show()
            getLocationCoordinates(cityName)
        }

        favoriteButton.setOnLongClickListener {
            // Handle long press event for favorite city button
            removeFavoriteCity(cityName)
            favoritesLayout.removeView(favoriteButton)
            saveFavoriteCities()
            Toast.makeText(this, "$cityName removed from favorites", Toast.LENGTH_SHORT).show()
            true
        }

        favoritesLayout.addView(favoriteButton)
    }

    private fun removeFavoriteCity(cityName: String) {
        favoriteCities.remove(cityName)
    }

    private fun saveFavoriteCities() {
        val editor = sharedPreferences.edit()
        editor.putStringSet("FavoriteCities", favoriteCities)
        editor.apply()
    }

    private fun loadFavoriteCities() {
        favoriteCities.clear()
        val savedFavoriteCities = sharedPreferences.getStringSet("FavoriteCities", emptySet())
        if (savedFavoriteCities != null) {
            favoriteCities.addAll(savedFavoriteCities)
            for (city in favoriteCities) {
                createFavoriteButton(city)
            }
        }
    }
}