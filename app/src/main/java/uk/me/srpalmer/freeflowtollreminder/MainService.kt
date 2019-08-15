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
        logger.trace { "Initializing MainService.tollRoads" }
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
            for (i in 0 until tollRoadsElements.length)
                if (tollRoadsElements.item(i).nodeType == Node.ELEMENT_NODE)
                    result.add(TollRoad(tollRoadsElements.item(i) as Element))
        } catch (e: Throwable) {
            logger.error { e.message }
        }
        logger.trace { "MainService.tollRoads Initialized: $result" }
        result
    }

    private val calendarUpdater by lazy {
        logger.trace { "Initializing calendarUpdater" }
        val result = CalendarUpdater(this)
        logger.trace { "calendarUpdater Initialized" }
        result
    }

    private val notificationId = 666  // TODO: check what numbers should be used

    private val fusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val settingsClient: SettingsClient by lazy { LocationServices.getSettingsClient(this) }

    private val locationRequest by lazy { LocationRequest.create() }

    private val locationCallback = object : LocationCallback() {
        var updatesRequested = false
            set(value) {
                logger.trace { "MainService.locationCallback.setUpdatesRequested($value) started" }
                if (field != value)
                {
                    logger.info { "MainService.locationCallback.UpdatesRequested updated to: $value" }
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
                logger.trace { "MainService.locationCallback.setUpdatesRequested($value) stopped" }
            }

        override fun onLocationResult(locationResult: LocationResult?) {
            logger.trace { "MainService.onLocationResult($locationResult) started" }
            if (locationResult != null) {
                for (location in locationResult.locations)
                    (tollRoads.map { it.isTollDue(location) }).filterNotNull().forEach {
                        logger.info { "Toll due: ${it.reminder}" }
                        calendarUpdater.addReminder(it)
                    }
                proximity = (tollRoads.map {it.lastLocationProximity()}).min()
            }
            logger.trace { "MainService.onLocationResult(...) stopped" }
        }
    }

    private var proximity: TollRoad.Proximity? = null
        set(value) {
            logger.trace { "MainService.setProximity($value) started" }
            if (field != value ) {
                logger.info { "Proximity updated to: $value" }
                locationCallback.updatesRequested = false
                if (value != null) {
                    locationRequest.apply {
                        interval = value.intervalMilliseconds
                        maxWaitTime = value.intervalMilliseconds
                        fastestInterval = TollRoad.Proximity.closeBy.intervalMilliseconds
                        priority = if (false) value.priority else TollRoad.Proximity.closeBy.priority // FIXME: fudge for simulator
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
                    locationCallback.updatesRequested = true
                }
                field = value
            }
            logger.trace { "MainService.setProximity($value) stopped" }
        }

    override fun onCreate() {
        logger.trace { "MainService.onCreate() started" }
        logger.info { "Service Started" }

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
            setContentText("Monitoring location for toll road use.")
            priority = NotificationCompat.PRIORITY_DEFAULT
            setContentIntent(notificationIntent)
        }

        logger.trace { "Promote to Foreground" }
        startForeground(notificationId, notificationBuilder.build())

        logger.trace { "Calender Stuff" }
        calendarUpdater.onCreate(sharedPreferences)

        logger.trace { "Location Stuff" }
        proximity = TollRoad.Proximity.closeBy  // get accurate first reading

        logger.trace { "MainService.onCreate() stopped" }
    }

    inner class MainServiceBinder: Binder() {
        fun tollRoadList() = tollRoads.map { it.name }
        val calendarList = calendarUpdater.calendarInfoList.map { it.name }
        var calendarPosition
            get () = calendarUpdater.calendarPosition
            set (value) {calendarUpdater.calendarPosition = value}
        fun onFinishRequest() = this@MainService.onFinishRequest()
    }

    override fun onBind(intent: Intent?): IBinder? {
        logger.trace { "MainService.onBind(...) called" }
        return MainServiceBinder()
    }

    fun onFinishRequest() {
        // TODO: Worry about thread safety
        logger.trace { "MainService.onFinishRequest() started" }
        stopForeground(true)
        stopSelf()
        logger.trace { "MainService.onFinishRequest() stopped" }
    }

    @SuppressLint("ApplySharedPref")
    override fun onDestroy() {
        logger.trace { "MainService.onDestroy() started" }
        proximity = null
        sharedPreferences.edit().apply {
            calendarUpdater.onDestroy(this)
            commit()
        }
        logger.info { "Service Stopped" }
        logger.trace { "MainService.onDestroy() stopped" }
    }

    companion object {
        const val CHANNEL_ID = "uk.me.srpalmer.freeflowtollreminder.CHANNEL_ID"
        const val SHARED_PREFERENCES_FILE_NAME = "uk.me.srpalmer.freeflowtollreminder.MODEL_PREFERENCES"
    }

}
