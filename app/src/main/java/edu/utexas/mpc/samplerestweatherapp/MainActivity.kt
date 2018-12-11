package edu.utexas.mpc.samplerestweatherapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import java.security.AccessController.getContext


import com.google.android.gms.location.places.GeoDataClient
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.Places
import com.google.android.gms.location.places.PlaceDetectionClient

class MainActivity : AppCompatActivity() {


    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var retrieveButton: Button
    lateinit var textView2: TextView
    lateinit var textView3: TextView
    lateinit var textView4: TextView
    lateinit var textView_prompt_user: TextView


    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var mostRecentForecastResult: ForecastResult
    lateinit var mqttAndroidClient: MqttAndroidClient
    lateinit var RecentPlaceResult: PlaceResult

    val serverUri = "tcp://192.168.4.1:1883"
    val clientId = "EmergingTechMQTTClient"

    val subscribeTopic = "goalTopic"
    val yesterdayTopic = "yesterdayTopic"
    val publishTopic = "weatherTopic"


    lateinit var mGeoDataClient: GeoDataClient

    // store information to send to pi
    var sb_android = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Construct a GeoDataClient.
        mGeoDataClient = Places.getGeoDataClient(this)

        // TODO: Start using the Places API



        // remove title bar
        this.supportActionBar!!.hide()

        // edit the status bar
        this.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        this.window.statusBarColor = Color.WHITE

        // reference the activity_main xml
        textView = this.findViewById(R.id.text)
        textView2 = this.findViewById(R.id.texttemp)
        textView3 = this.findViewById(R.id.textmintemp)
        textView4 = this.findViewById(R.id.textmaxtemp)
        retrieveButton = this.findViewById(R.id.retrieveButton)
        textView_prompt_user = this.findViewById(R.id.prompt_user)




        // hide the buttons
        nobutton.visibility = View.GONE
        syncButton.visibility = View.GONE

        // when the user presses the YES button, this method will get called
        retrieveButton.setOnClickListener({ requestWeather() })

        // when the user presses the NO button this method will be called
        nobutton.setOnClickListener({ startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK)) })

        queue = Volley.newRequestQueue(this)
        gson = Gson()

        mqttAndroidClient = MqttAndroidClient(applicationContext, serverUri, clientId)
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()



        syncButton.setOnClickListener({ syncWithPi() })
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                mqttStatus.text = getString(R.string.mqttc)
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
                mqttAndroidClient.subscribe(yesterdayTopic, 0)

                publishWeather()
            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println("Received")
                Log.d("mqttmessage", topic)
                if (topic.equals(subscribeTopic)) {
                    if (message.toString().toBoolean()) {
                        stepsText.text = "You've reached your step goal for today!"
                    } else {
                        stepsText.text = "You still need some steps to reach today's step goal!"
                    }
                } else if (topic.equals(yesterdayTopic)) {
                    if (message.toString().toBoolean()) {
                        yesterdayText.text = "Great job! You reached your goal yesterday."
                    } else {
                        yesterdayText.text = "You didn't reach your goal yesterday."
                    }
                }
            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
                mqttStatus.text = getString(R.string.mqttd)
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })
    }

    fun requestWeather(){

        // today's weather
        val sb = StringBuilder("https://api.openweathermap.org/data/2.5/weather?zip=")
        sb.append(zip.text.toString())
        // unique app id key
        sb.append("&appid=" )
        sb.append(getString(R.string.api))
        val url = sb.toString()
        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    //textView.text = response
                    val sbWeather = StringBuilder()
                    val sbWeathertype = StringBuilder()

                    mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)

                    // Get the city name
                    sbWeather.append(mostRecentWeatherResult.name)
                    sb_android.append("City: ")
                    sb_android.append(mostRecentWeatherResult.name)
                    sb_android.append(System.getProperty("line.separator"))
                    sbWeather.append(System.getProperty("line.separator"))

                    // Get the weather type e.g. mist, sun, clear, rain, etc
                    sbWeathertype.append(mostRecentWeatherResult.weather.get(0).main)
                    sb_android.append("Weather: ")
                    sb_android.append(mostRecentWeatherResult.weather.get(0).main)
                    sb_android.append(System.getProperty("line.separator"))
                    // Display city name and weather type onto android app as a textView
                    weathertype.text =  sbWeathertype.toString()
                    textView.text = sbWeather.toString()

                    // lambda function to convert Kelvin to Fahrenheit
                    val convert_to_f = { x: Float -> ((x - 273.15)  * 9.0 * 0.2) + 32 }

                    // Get the weather temp
                    var sbTemp_str = StringBuilder()
                    sbTemp_str.append(mostRecentWeatherResult.main.temp)

                    // convert Kelvin to Fahrenheit
                    var sbTemp_num = sbTemp_str.toString().toFloat()
                    println("This is the temp")
                    println(sbTemp_num.toString())

                    sbTemp_num = convert_to_f(sbTemp_num).toFloat()
                    println("\nThis is the converted temp")
                    println(sbTemp_num.toString())


                    // Throw converted temperature into a string builder
                    sbTemp_str = StringBuilder()
                    sbTemp_str.append("Current")
                    sbTemp_str.append(System.getProperty("line.separator"))
                    sbTemp_str.append(String.format("%.2f", sbTemp_num))
                    sbTemp_str.append("°F")
                    sb_android.append("Current Temp: ")
                    sb_android.append(String.format("%.2f", sbTemp_num))
                    sb_android.append("°F")
                    sb_android.append(System.getProperty("line.separator"))
                    // Display weather temp onto android app as a textView
                    textView2.text = sbTemp_str.toString()

                    // Get the min weather temp
                    sbTemp_str = StringBuilder()
                    sbTemp_str.append(mostRecentWeatherResult.main.temp_min)
                    // convert Kelvin to Fahrenheit
                    sbTemp_num = sbTemp_str.toString().toFloat()
                    sbTemp_num = convert_to_f(sbTemp_num).toFloat()
                    // Throw converted temperature into a string builder
                    sbTemp_str = StringBuilder()
                    sbTemp_str.append("Min")
                    sbTemp_str.append(System.getProperty("line.separator"))
                    sbTemp_str.append(String.format("%.2f", sbTemp_num))
                    sbTemp_str.append("°F")
                    sb_android.append("Minimum Temp: ")
                    sb_android.append(String.format("%.2f", sbTemp_num))
                    sb_android.append("°F")
                    sb_android.append(System.getProperty("line.separator"))
                    // Display weather temp onto android app as a textView
                    textView3.text = sbTemp_str.toString()

                    // Get the max weather temp
                    sbTemp_str = StringBuilder()
                    sbTemp_str.append(mostRecentWeatherResult.main.temp_max)
                    // convert Kelvin to Fahrenheit
                    sbTemp_num = sbTemp_str.toString().toFloat()
                    sbTemp_num = convert_to_f(sbTemp_num).toFloat()
                    // Throw converted temperature into a string builder
                    sbTemp_str = StringBuilder()
                    sbTemp_str.append("Max")
                    sbTemp_str.append(System.getProperty("line.separator"))
                    sbTemp_str.append(String.format("%.2f", sbTemp_num))
                    sbTemp_str.append("°F")
                    sb_android.append("Maximum Temp: ")
                    sb_android.append(String.format("%.2f", sbTemp_num))
                    sb_android.append("°F")
                    sb_android.append(System.getProperty("line.separator"))
                    // Display weather temp onto android app as a textView
                    textView4.text = sbTemp_str.toString()


                    // Send humidity
                    sb_android.append("humidity: ")
                    sb_android.append(mostRecentWeatherResult.main.humidity)
                    sb_android.append(System.getProperty("line.separator"))
                    sb_android.append(System.getProperty("line.separator"))

                    // Get the icon for the icon
                    val picassoBuilder = Picasso.Builder(this)
                    val picasso = picassoBuilder.build()
                    picasso.load("http://openweathermap.org/img/w/" + mostRecentWeatherResult.weather.get(0).icon + ".png").into(imgView)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)

        // Forecast
        val sb2 = StringBuilder("https://api.openweathermap.org/data/2.5/forecast?zip=")
        sb2.append(zip.text.toString())
        // unique app id key
        sb2.append("&appid=" )
        sb2.append(getString(R.string.api))
        val url2 = sb2.toString()

        val stringRequest2 = object : StringRequest(com.android.volley.Request.Method.GET, url2,
                com.android.volley.Response.Listener<String> { response ->

                    // Used to make the API call for json
                    mostRecentForecastResult = gson.fromJson(response, ForecastResult::class.java)

                    // Because the api results in forecasts every 3 hours for 5 days, grab the 8th one because its exactly 24 hours from now
                    var theForecast = mostRecentForecastResult.list.get(7)

                    // lambda function to convert Kelvin to Fahrenheit
                    val convert_to_f = { x: Float -> (x - 273.15) * (9/5) + 32 }

                    // Get the weather temp
                    var sbTemp_str = StringBuilder()
                    sbTemp_str.append(theForecast.main.temp)
                    // convert Kelvin to Fahrenheit
                    var sbTemp_num = sbTemp_str.toString().toFloat()
                    sbTemp_num = convert_to_f(sbTemp_num).toFloat()
                    // Throw converted temperature into a string builder
                    sb_android.append("Tomorrow's Forecast")
                    sb_android.append(System.getProperty("line.separator"))
                    sb_android.append("Temp: ")
                    sb_android.append(String.format("%.2f", sbTemp_num))
                    sb_android.append("°F")
                    sb_android.append(System.getProperty("line.separator"))

                    // Get the min weather temp
                    sbTemp_str = StringBuilder()
                    sbTemp_str.append(theForecast.main.temp_min)
                    // convert Kelvin to Fahrenheit
                    sbTemp_num = sbTemp_str.toString().toFloat()
                    sbTemp_num = convert_to_f(sbTemp_num).toFloat()
                    // Throw converted temperature into a string builder
                    sb_android.append("Minimum Temp: ")
                    sb_android.append(String.format("%.2f", sbTemp_num))
                    sb_android.append("°F")
                    sb_android.append(System.getProperty("line.separator"))

                    // Get the max weather temp
                    sbTemp_str = StringBuilder()
                    sbTemp_str.append(theForecast.main.temp_max)
                    // convert Kelvin to Fahrenheit
                    sbTemp_num = sbTemp_str.toString().toFloat()
                    sbTemp_num = convert_to_f(sbTemp_num).toFloat()
                    // Throw converted temperature into a string builder
                    sb_android.append("Maximum Temp: ")
                    sb_android.append(String.format("%.2f", sbTemp_num))
                    sb_android.append("°F")
                    sb_android.append(System.getProperty("line.separator"))

                    // Send humidity
                    sb_android.append("Humidity: ")
                    sb_android.append(theForecast.main.humidity)
                    sb_android.append(System.getProperty("line.separator"))


                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest2)

        // places api
        // api key
        val kunci = getString(R.string.places_api_2)
        var places_sb = StringBuilder("https://maps.googleapis.com/maps/api/place/textsearch/json?query=")
        places_sb.append(zip.text.toString())
        places_sb.append("&key=")
        places_sb.append(kunci)

        val stringRequest3 = object : StringRequest(com.android.volley.Request.Method.GET, places_sb.toString(),
                com.android.volley.Response.Listener<String> { response ->
                    // Used to make the API call for json
                    RecentPlaceResult = gson.fromJson(response, PlaceResult::class.java)

                    // Because the api
                    var result_pl = RecentPlaceResult.results.get(0).formatted_address.split(" ")

                    // String builder
                    var result_place_sb = StringBuilder(result_pl.get(0))
                    result_place_sb.append(result_pl.get(1))

                    // recall the api
                    var places_sb = StringBuilder("https://maps.googleapis.com/maps/api/place/textsearch/json?query=")
                    places_sb.append(result_place_sb.toString())
                    places_sb.append("&key=")
                    places_sb.append(kunci)

                    val stringRequest4 = object : StringRequest(com.android.volley.Request.Method.GET, places_sb.toString(),
                            com.android.volley.Response.Listener<String> { response ->

                                // display metrics
                                val displayMetrics = DisplayMetrics()
                                windowManager.defaultDisplay.getMetrics(displayMetrics)
                                var height = displayMetrics.heightPixels


                                // Used to make the API call for json
                                RecentPlaceResult = gson.fromJson(response, PlaceResult::class.java)
                                var result_pl = RecentPlaceResult.results.get(0)
                                var result_pl_2 = result_pl.photos.get(0)
                                var result_pl_3 = result_pl_2.photo_reference


                                var html_photo = StringBuilder("https://maps.googleapis.com/maps/api/place/photo?maxheight=")
                                html_photo.append(height)
                                html_photo.append("&photoreference=")
                                html_photo.append(result_pl_3)
                                html_photo.append("&key=")
                                html_photo.append(kunci)

                                // Get the icon for the icon
                                val picassoBuilder = Picasso.Builder(this)
                                val picasso = picassoBuilder.build()
                                picasso.load(html_photo.toString()).into(placeimg)

                            },
                            com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
                    // Add the request to the RequestQueue.
                    queue.add(stringRequest4)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        queue.add(stringRequest3)

        startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))

        // wait a sec before showing the new view
        Thread.sleep(1_000)

        // play with the font
        val typeface = resources.getFont(R.font.pacifico)
        textView.typeface = typeface

        textView_prompt_user.text = "Have you changed to the appropriate Network?"
        nobutton.visibility = View.VISIBLE
        syncButton.visibility = View.VISIBLE
    }

    fun syncWithPi() {
        println(mqttAndroidClient.isConnected)
        if (!mqttAndroidClient.isConnected) {
            mqttAndroidClient.connect()
        } else {
            mqttStatus.text = getString(R.string.mqttc)
            publishWeather()
        }
    }

    fun publishWeather() {
        val message = MqttMessage()
        message.payload = (sb_android.toString()).toByteArray()

        // this publishes a message to the publish topic
        mqttAndroidClient.publish(publishTopic, message)
    }
}


// Format of JSON objects as a result of the API calls
class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

class ForecastResult(val list: Array<Forecast>)
class Forecast(val dt: Int, val main: ForecastMain, val weather: Array<Weather>)
class ForecastMain(val temp: Double, val temp_min: Double, val temp_max: Double, val pressure: Double, val humidity: Int )

class GooglePlace(val formatted_address: String, val photos: Array<Photo>)
class PlaceResult(val results: Array<GooglePlace>)
class Photo(val photo_reference: String)

