package com.example.a259366project

import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.AsyncTask
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var cityEditText: EditText
    private lateinit var getLocationButton: Button
    private lateinit var geocoder: Geocoder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        temperatureTextView = findViewById(R.id.temperatureTextView)

        cityEditText = findViewById(R.id.cityEditText)
        getLocationButton = findViewById(R.id.getLocationButton)
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        geocoder = Geocoder(this)

        getLocationButton.setOnClickListener {
            val cityName = cityEditText.text.toString()
            getLocationCoordinates(cityName)
        }
    }

    private fun fetchWeatherData(latitude: String, longitude: String) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true&hourly=temperature_2m,relativehumidity_2m,windspeed_10m"

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
                } catch (e: JSONException) {
                    temperatureTextView.text = "Failed to parse weather data."
                }
            } else {
                temperatureTextView.text = "Failed to fetch weather data."
            }
        }
    }

    private data class WeatherData(val temperature: Double)

    private fun parseWeatherData(json: String): WeatherData {
        val jsonObject = JSONObject(json)

        if (!jsonObject.has("current_weather")) {
            throw JSONException("No 'current_weather' object found")
        }

        val currentWeatherObject = jsonObject.getJSONObject("current_weather")

        val temperature = currentWeatherObject.optDouble("temperature")
        if (temperature.isNaN()) {
            throw JSONException("No 'temperature' value found in 'current_weather'")
        }

        return WeatherData(temperature)
    }

    private fun getLocationCoordinates(location: String) {
        val addresses = geocoder.getFromLocationName(location, 1)
        if (addresses != null) {
            if (addresses.isNotEmpty()) {
                val latitude = addresses[0].latitude
                val longitude = addresses[0].longitude
                val roundedLatitude = "%.2f".format(latitude).toDouble()
                val roundedLongitude = "%.2f".format(longitude).toDouble()
                latitudeTextView.text = "Latitude: $roundedLatitude"
                longitudeTextView.text = "Longitude: $roundedLongitude"

                fetchWeatherData(roundedLatitude.toString(), roundedLongitude.toString());
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
