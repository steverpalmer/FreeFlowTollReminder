package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import com.google.android.gms.location.*
import mu.KotlinLogging
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import uk.me.srpalmer.freeflowtollreminder.model.TollRoad
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class MainService : Service(){

    private val logger = KotlinLogging.logger {}

    private val sharedPreferences by lazy {
        getSharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
    }

    private val tollRoads: List<TollRoad> by lazy {
        logger.trace { "Initializing tollRoads" }
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
            val tollRoadsElements = xPath.evaluate("/configuration/toll_roads/toll_road", doc, XPathConstants.NODESET) as NodeList
            for (i in 0 until tollRoadsElements.length) {
                if (tollRoadsElements.item(i).nodeType == Node.ELEMENT_NODE) {
                    val tollRoadElement = tollRoadsElements.item(i) as Element
                    val tollRoad = TollRoad(tollRoadElement)
                    result.add(tollRoad)
                }
            }
        } catch (e: Throwable) {
            logger.error { e.message }
        }
        logger.trace { "tollRoads Initialized" }
        result
    }

    private val calendarUpdater by lazy { CalendarUpdater(contentResolver) }

    private val notificationId = 666  // TODO: check what numbers should be used

    private val fusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val settingsClient: SettingsClient by lazy { LocationServices.getSettingsClient(this) }

    private val locationRequest by lazy { LocationRequest.create() }

    private val locationCallback = object : LocationCallback() {
        var armed = false
            set(value) {
                logger.trace { "locationCallback.setArmed($value) started" }
                if (field != value)
                {
                    if (!value)
                        fusedLocationProviderClient.removeLocationUpdates(this).apply {
                            addOnFailureListener { exception ->
                                logger.error { "removeLocationUpdates failure: $exception" }
                            }
                            addOnSuccessListener {
                                logger.debug { "removeLocationUpdates success" }
                            }
                        }
                    else try {
                        fusedLocationProviderClient.requestLocationUpdates(locationRequest, this, null).apply {
                            addOnFailureListener { exception ->
                                logger.error { "requestLocationUpdates failure: $exception" }
                            }
                            addOnSuccessListener {
                                logger.debug { "requestLocationUpdates success" }
                            }
                        }
                    } catch (exception: SecurityException) {
                        logger.error { "requestLocationUpdates failure: $exception" }
                    }
                    field = value
                }
                logger.trace { "locationCallback.setArmed($value) stopped" }
            }

        override fun onLocationResult(locationResult: LocationResult?) {
            logger.trace { "onLocationResult($locationResult) started" }
            locationResult ?: return
            for (location in locationResult.locations)
                (tollRoads.map { it.isTollDue(location) }).filterNotNull().forEach {
                    logger.info { "Toll due: ${it.reminder}" }
                    calendarUpdater.addReminder(it)
                }
            proximity = (tollRoads.map {it.lastLocationProximity()}).min()
            logger.trace { "onLocationResult(...) stopped" }
        }
    }

    private var proximity: TollRoad.Proximity? = null
        set(value) {
            logger.trace { "setProximity($value) started" }
            if (field != value ) {
                logger.info { "Proximity updated to: $value" }
                locationCallback.armed = false
                if (value != null) {
                    locationRequest.apply {
                        interval = value.intervalMilliseconds
                        maxWaitTime = value.intervalMilliseconds
                        fastestInterval = TollRoad.Proximity.closeBy.intervalMilliseconds
                        priority = TollRoad.Proximity.closeBy.priority // FIXME: fudge for simulator value.priority
                        smallestDisplacement = 20.0f
                    }
                    val locationSettingsRequest = LocationSettingsRequest.Builder()
                        .addLocationRequest(locationRequest)
                    settingsClient.checkLocationSettings(locationSettingsRequest.build()).apply {
                        addOnFailureListener { exception ->
                            logger.error { "location settings error: $exception" }
                        }
                        addOnSuccessListener { locationSettingsResponse ->
                            logger.debug { "location settings response: $locationSettingsResponse" }
                        }
                    }
                    logger.debug { "locationRequest: $locationRequest" }
                    locationCallback.armed = true
                }
                field = value
            }
            logger.trace { "setProximity($value) stopped" }
        }

    override fun onCreate() {
        logger.trace { "onCreate() started" }

        logger.trace { "Notification Stuff" }
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
            setContentText("Monitor when you use Toll road and create a reminder to pay")
            priority = NotificationCompat.PRIORITY_DEFAULT
            setContentIntent(notificationIntent)
        }

        logger.trace { "Promote to Foreground" }
        startForeground(notificationId, notificationBuilder.build())

        logger.trace { "Calender Stuff" }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            logger.error { "Don't have permission to update calendar" }
        else
            calendarUpdater.onCreate(sharedPreferences)

        logger.trace { "Location Stuff" }
        when {
            tollRoads.isEmpty()
                -> logger.error { "No Toll Roads defined" }
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                -> logger.error { "Don't have permission to access fine location" }
            else
                -> proximity = TollRoad.Proximity.closeBy  // get accurate first reading
        }

        logger.trace { "onCreate() stopped" }
    }

    inner class MainServiceBinder: Binder() {
        var calendarId
            get () = calendarUpdater.calendarId
            set (value) {calendarUpdater.calendarId = value}
        fun onFinishRequest() = this@MainService.onFinishRequest()
    }

    override fun onBind(intent: Intent?): IBinder? {
        logger.trace { "onBind(...) called" }
        return MainServiceBinder()
    }

    @SuppressLint("ApplySharedPref")
    override fun onDestroy() {
        logger.trace { "onDestroy() started" }
        proximity = null
        sharedPreferences.edit().apply {
            calendarUpdater.onDestroy(this)
            commit()
        }
        logger.trace { "onDestroy() stopped" }
    }

    fun onFinishRequest() {
        // TODO: Worry about thread safety
        logger.trace { "onFinishRequest() started" }
        stopForeground(true)
        stopSelf()
        logger.trace { "onFinishRequest() stopped" }
    }

    companion object {
        const val CHANNEL_ID = "uk.me.srpalmer.freeflowtollreminder.CHANNEL_ID"
        const val SHARED_PREFERENCES_FILE_NAME = "uk.me.srpalmer.freeflowtollreminder.MODEL_PREFERENCES"
    }
}
