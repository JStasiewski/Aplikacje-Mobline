package com.example.a259366project

import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var geocoder: Geocoder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        temperatureTextView = findViewById(R.id.temperatureTextView)
        hourlyDataTextView = findViewById(R.id.hourlyDataTextView)

        cityEditText = findViewById(R.id.cityEditText)
        getLocationButton = findViewById(R.id.getLocationButton)
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        geocoder = Geocoder(this)

        getLocationButton.setOnClickListener {
            Log.d("debug", "DUPA")
            val cityName = cityEditText.text.toString()
            Log.d("debug", cityName)

            getLocationCoordinates(cityName)
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

                    val hourlyDataTextView = findViewById<TextView>(R.id.hourlyDataTextView)
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

    private fun parseDoubleArray(jsonArray: JSONArray): List<Double> {
        val doubleList = mutableListOf<Double>()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.optDouble(i)
            if (!value.isNaN()) {
                doubleList.add(value)
            }
        }
        return doubleList
    }

    private fun buildHourlyDataString(hourlyTemperature: List<Double>, hourlyWindSpeed: List<Double>): String {
        val stringBuilder = StringBuilder()
        for (i in 0 until hourlyTemperature.size) {
            stringBuilder.append("Time: ${i + 1}\n")
            stringBuilder.append("Temperature: ${hourlyTemperature[i]}°C\n")
            stringBuilder.append("Wind Speed: ${hourlyWindSpeed[i]}\n\n")
        }
        return stringBuilder.toString()
    }

    private fun getLocationCoordinates(location: String) {
        val addresses = geocoder.getFromLocationName(location, 1)

        if (addresses != null) {

            val latitude = addresses[0].latitude
            val longitude = addresses[0].longitude
            latitudeTextView.text = "Latitude: $latitude"
            longitudeTextView.text = "Longitude: $longitude"

            Log.d("debug", latitude.toString())
            fetchWeatherData(latitude.toString(), longitude.toString())
            Log.d("debug", longitude.toString())

        } else {
            Log.d("debug", "DUPA if niedziala")
            Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
        }

    }
}
