package com.example.a259366project

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
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
    private lateinit var geocoder: Geocoder
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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

    private fun getLocationCoordinates(location: String) {
        val addresses = geocoder.getFromLocationName(location, 1)

        if (addresses != null && addresses.isNotEmpty()) {
            val latitude = addresses[0].latitude
            val longitude = addresses[0].longitude
            latitudeTextView.text = "Latitude: $latitude"
            longitudeTextView.text = "Longitude: $longitude"
            fetchWeatherData(latitude.toString(), longitude.toString())
        } else {
            Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_PERMISSIONS_CODE
        )
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
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    latitudeTextView.text = "Latitude: $latitude"
                    longitudeTextView.text = "Longitude: $longitude"
                    fetchWeatherData(latitude.toString(), longitude.toString())
                } else {
                    Toast.makeText(this, "GPS location not available", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to get GPS location: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getGPSLocation()
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 1
    }
}
