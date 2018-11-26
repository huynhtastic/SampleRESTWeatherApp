package edu.utexas.mpc.samplerestweatherapp

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
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

class MainActivity : AppCompatActivity() {


    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var retrieveButton: Button
    lateinit var textView2: TextView
    lateinit var textView3: TextView
    lateinit var textView4: TextView


    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var mqttAndroidClient: MqttAndroidClient

    val serverUri = "tcp://192.168.4.1:1883"
    val clientId = "EmergingTechMQTTClient"

    val subscribeTopic = "goalTopic"
    val yesterdayTopic = "yesterdayTopic"
    val publishTopic = "weatherTopic"

    // store information to send to pi
    var sb_android = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // reference the activity_main xml
        textView = this.findViewById(R.id.text)
        textView2 = this.findViewById(R.id.texttemp)
        textView3 = this.findViewById(R.id.textmintemp)
        textView4 = this.findViewById(R.id.textmaxtemp)
        retrieveButton = this.findViewById(R.id.retrieveButton)

        // when the user presses the syncbutton, this method will get called
        retrieveButton.setOnClickListener({ requestWeather() })

        queue = Volley.newRequestQueue(this)
        gson = Gson()

        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId)
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()
        sendWeather.setOnClickListener({ sendWeather(sb_android) })
        syncButton.setOnClickListener({ syncWithPi() })
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                mqttStatus.text = getString(R.string.mqttc)
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
                mqttAndroidClient.subscribe(yesterdayTopic, 0)
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

        val sb = StringBuilder("https://api.openweathermap.org/data/2.5/weather?zip=")
        sb.append(zip.getText().toString())
        sb.append("&appid=" )
        sb.append(getString(R.string.api))
        val url = sb.toString()
        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    //textView.text = response
                    val sbWeather = StringBuilder()

                    mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)

                    // Get the city name
                    sbWeather.append(mostRecentWeatherResult.name)
                    sb_android.append("City: ")
                    sb_android.append(mostRecentWeatherResult.name)
                    sb_android.append(System.getProperty("line.separator"))
                    sbWeather.append(System.getProperty("line.separator"))

                    // Get the weather type e.g. mist, sun, clear, rain, etc
                    sbWeather.append(mostRecentWeatherResult.weather.get(0).main)
                    sbWeather.append(System.getProperty("line.separator"))
                    sb_android.append("Weather: ")
                    sb_android.append(mostRecentWeatherResult.weather.get(0).main)
                    sb_android.append(System.getProperty("line.separator"))
                    // Display city name and weather type onto android app as a textView
                    textView.text = sbWeather.toString()

                    // lambda function to convert Kelvin to Fahrenheit
                    val convert_to_f = { x: Float -> (x - 273.15) * (9/5) + 32 }

                    // Get the weather temp
                    var sbTemp_str = StringBuilder()
                    sbTemp_str.append(mostRecentWeatherResult.main.temp)
                    // convert Kelvin to Fahrenheit
                    var sbTemp_num = sbTemp_str.toString().toFloat()
                    sbTemp_num = convert_to_f(sbTemp_num).toFloat()
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

                    // Get the icon for the icon
                    val picassoBuilder = Picasso.Builder(this)
                    val picasso = picassoBuilder.build()
                    picasso.load("http://openweathermap.org/img/w/" + mostRecentWeatherResult.weather.get(0).icon + ".png").into(imgView)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    fun syncWithPi() {
        mqttAndroidClient.connect()
    }

    fun sendWeather(sb: StringBuilder){

        val message = MqttMessage()
        message.payload = (sb.toString()).toByteArray()

        // this publishes a message to the publish topic
        mqttAndroidClient.publish(publishTopic, message)
    }
}

class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

