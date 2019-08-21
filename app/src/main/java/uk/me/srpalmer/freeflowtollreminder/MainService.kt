package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import uk.me.srpalmer.freeflowtollreminder.model.TollRoad
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class MainService : Service(){

    private val sharedPreferences by lazy {
        getSharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
    }

    private val tollRoads: List<TollRoad> by lazy {
        val result = mutableListOf<TollRoad>()
        try {
            val xmlStream = resources.openRawResource(R.raw.configuration)
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
            // TODO: handle XML namespace properly
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()
            val doc = documentBuilder.parse(xmlStream)
            xmlStream.close()
            val xPathFactory = XPathFactory.newInstance()
            val xPath = xPathFactory.newXPath()
            val tollRoadsElements = xPath.evaluate("/configuration/toll_roads/toll_road",
                doc, XPathConstants.NODESET) as NodeList
            for (i in 0 until tollRoadsElements.length)
                if (tollRoadsElements.item(i).nodeType == Node.ELEMENT_NODE)
                    result.add(TollRoad(tollRoadsElements.item(i) as Element))
        } catch (e: Throwable) {
            // Do Nothing
        }
        result
    }

    private val calendarUpdater by lazy {
        val result = CalendarUpdater(this)
        result
    }

    private val fusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val settingsClient: SettingsClient by lazy { LocationServices.getSettingsClient(this) }

    private val locationRequest by lazy { LocationRequest.create() }

    private val locationCallback = object : LocationCallback() {
        var updatesRequested = false
            @SuppressLint("MissingPermission")  // Missing Permission caught in task failure
            set(value) {
                if (field != value)
                {
                    if (!value)
                        fusedLocationProviderClient.removeLocationUpdates(this)
                    else
                        fusedLocationProviderClient.requestLocationUpdates(locationRequest, this, null).apply {
                            addOnCompleteListener { task ->
                                if (!task.isSuccessful) {
                                    onFinishRequest()
                                }
                            }
                        }
                    field = value
                }
            }

        override fun onLocationResult(locationResult: LocationResult?) {
            if (locationResult != null) {
                for (location in locationResult.locations)
                    (tollRoads.map { it.isTollDue(location) }).filterNotNull().forEach {
                        calendarUpdater.addReminder(it)
                    }
                proximity = (tollRoads.map {it.lastLocationProximity()}).min()
            }
        }
    }

    private var proximity: TollRoad.Proximity? = null
        set(value) {
            if (field != value ) {
                locationCallback.updatesRequested = false
                if (value != null) {
                    locationRequest.apply {
                        interval = value.intervalMilliseconds
                        maxWaitTime = value.intervalMilliseconds
                        fastestInterval = TollRoad.Proximity.closeBy.intervalMilliseconds
                        priority = value.priority  // FIXME: for simulator use TollRoad.Proximity.closeBy.priority
                        smallestDisplacement = 20.0f
                    }
                    val locationSettingsRequest = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
                    settingsClient.checkLocationSettings(locationSettingsRequest.build())
                    locationCallback.updatesRequested = true
                }
                field = value
            }
        }

    override fun onCreate() {
        val notificationChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(true)
            setShowBadge(true)
            enableLights(true)
            description = getString(R.string.channel_description)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
        val notificationIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }, 0)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            setSmallIcon(R.mipmap.toll_launcher)
            setContentTitle("Free Flow Toll Reminder")
            setContentText("Monitoring location for toll road use.")
            priority = NotificationCompat.PRIORITY_DEFAULT
            setContentIntent(notificationIntent)
        }

        startForeground(666, notificationBuilder.build())

        calendarUpdater.onCreate(sharedPreferences)

        proximity = TollRoad.Proximity.closeBy  // get accurate first reading
    }

    inner class MainServiceBinder: Binder() {
        fun tollRoadList() = tollRoads.map { it.name }
        val calendarList = calendarUpdater.calendarInfoList.map { it.name }
        var calendarId
            get() = calendarUpdater.calendarId
            set(value) {calendarUpdater.calendarId = value}
        var calendarPosition
            get() = calendarUpdater.calendarPosition
            set(value) {calendarUpdater.calendarPosition = value}
        fun onFinishRequest() = this@MainService.onFinishRequest()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return MainServiceBinder()
    }

    fun onFinishRequest() {
        stopForeground(true)
        stopSelf()
    }

    @SuppressLint("ApplySharedPref")
    override fun onDestroy() {
        proximity = null
        sharedPreferences.edit().apply {
            calendarUpdater.onDestroy(this)
            commit()
        }
    }

    companion object {
        const val CHANNEL_ID = "uk.me.srpalmer.freeflowtollreminder.CHANNEL_ID"
        const val SHARED_PREFERENCES_FILE_NAME = "uk.me.srpalmer.freeflowtollreminder.MODEL_PREFERENCES"
    }

}
