package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

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

class MainService : Service() {

    private val logger = KotlinLogging.logger {}

    private val sharedPreferences by lazy {
        getSharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
    }

    private val model = Model(this)

    private lateinit var calendarUpdater: CalendarUpdater

    private val notificationId = 666  // TODO: check what numbers should be used

    private var geofencingClient: GeofencingClient? = null

    private val geofencePendingIntent : PendingIntent by lazy {
        val intent = Intent(this, MainService::class.java)
        PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
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
        calendarUpdater = CalendarUpdater(contentResolver)
        calendarUpdater.onCreate(sharedPreferences)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            logger.error { "Don't have permission to update calendar" }
        else
            model.attach(calendarUpdater)

        logger.trace { "Geofence Stuff" }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            logger.error { "Don't have permission to access fine location" }
        else {
            geofencingClient = LocationServices.getGeofencingClient(this)
            //  Prepare GeoFences
            model.tollRoads.forEachIndexed { index, circularRegion ->
                val geofence = Geofence.Builder()
                    .setRequestId(index.toString())
                    .setCircularRegion(circularRegion.latitude, circularRegion.longitude, circularRegion.radius)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
                val geofenceRequest = GeofencingRequest.Builder().apply {
                    setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    addGeofence(geofence)
                }.build()
                geofencingClient?.addGeofences(geofenceRequest, geofencePendingIntent)?.run {
                    addOnSuccessListener {
                        logger.info { "${circularRegion.name} geofence ready" }
                    }
                    addOnFailureListener {
                        logger.error { "Failed to add ${circularRegion.name} geofence: ${it.message}" }
                    }
                }
            }
        }

        logger.trace { "onCreate() stopped" }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.trace { "onStartCommand($intent, $flags, $startId) started" }
        if (intent != null) {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)
            if (geofencingEvent.hasError()) {
                logger.error { "Geofencing Error: ${geofencingEvent.errorCode}" }
            } else when (geofencingEvent.geofenceTransition) {
                -1 -> {
                    logger.trace { "Service Start Intent" }
                }
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    logger.trace { "Geofence Enter Transition" }
                    // Only expecting one, but loop anyway ...
                    for (geofence in geofencingEvent.triggeringGeofences) {
                        model.tollRoadId = geofence.requestId.toInt()
                    }
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    logger.trace { "Geofence Exit Transition" }
                    model.tollRoadId = FREE_ROAD
                }
                else -> {
                    logger.error { "Unexpected geofence transition: ${geofencingEvent.geofenceTransition}" }
                }
            }
        }
        logger.trace { "onStartCommand(...) stopped" }
        return START_NOT_STICKY
    }

    inner class MainServiceBinder: Binder() {
        var calendarId
            get () = calendarUpdater.calendarId
            set (value) {calendarUpdater.calendarId = value}
        // fun modelAttach(modelObserver: ModelObserver) = model.attach(modelObserver)
        // fun modelDetach(modelObserver: ModelObserver) = model.detach(modelObserver)
        fun onFinishRequest() = this@MainService.onFinishRequest()
    }

    override fun onBind(intent: Intent?): IBinder? {
        logger.trace { "onBind(...) called" }
        return MainServiceBinder()
    }

    override fun onDestroy() {
        logger.trace { "onDestroy() started" }
        logger.info { "All geofences cleared" }
        geofencingClient?.removeGeofences(geofencePendingIntent)
        model.detach(calendarUpdater)
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
