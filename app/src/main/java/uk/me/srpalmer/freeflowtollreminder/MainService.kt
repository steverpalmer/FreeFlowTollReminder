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

/*
 * As well as being a service to receive Geofence Transition events,
 * potentially out-living the activity that created it,
 * this is also a facade on the objects it owns.
 */
class MainService : Service() {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Constructor" }
    }

    private val model = Model()
    fun attach(modelObserver: ModelObserver) = model.attach(modelObserver)
    fun detach(modelObserver: ModelObserver) = model.detach(modelObserver)

    private val calendarUpdater by lazy { CalendarUpdater(contentResolver) }
    var calendarId
        get () = calendarUpdater.calendarId
        set (value) {
            calendarUpdater.calendarId = value
        }
    fun unsetCalendarId() = calendarUpdater.unsetCalendarId()
    fun addReminder(name: String) = calendarUpdater.addReminder(name)

    private val notificationId = 666  // TODO: check what numbers should be used

    override fun onCreate() {
        logger.info { "onCreate() started" }

        logger.info { "Notification Stuff" }
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

        logger.info { "Promote to Foreground" }
        startForeground(notificationId, notificationBuilder.build())

        logger.info { "Calender Stuff" }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            logger.error { "Don't have permission to update calendar" }
        else
            model.attach(calendarUpdater)


        logger.info { "Geofence Stuff" }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            logger.error { "Don't have permission to access fine location" }
        else {
            geofencingClient = LocationServices.getGeofencingClient(this)
            //  Prepare GeoFences
            for ((name, region) in model.tollRoads) {
                val geofence = Geofence.Builder()
                    .setRequestId(name)
                    .setCircularRegion(region.latitude, region.longitude, region.radius)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
                val geofenceRequest = GeofencingRequest.Builder().apply {
                    setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    addGeofence(geofence)
                }.build()
                geofencingClient?.addGeofences(geofenceRequest, geofencePendingIntent)?.run {
                    addOnSuccessListener {
                        logger.info { "$name geofence ready" }
                    }
                    addOnFailureListener {
                        logger.error { "Failed to add $name geofence: ${it.message}" }
                    }

                }
            }
        }

        logger.info { "onCreate() stopped" }
    }

    private var geofencingClient: GeofencingClient? = null

    private val geofencePendingIntent : PendingIntent by lazy {
        val intent = Intent(this, MainService::class.java)
        PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.info { "onStartCommand($intent, $flags, $startId) started" }
        if (intent != null) {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)
            if (geofencingEvent.hasError()) {
                logger.error { "Geofencing Error: ${geofencingEvent.errorCode}" }
            } else when (geofencingEvent.geofenceTransition) {
                -1 -> {
                    logger.info { "Service Start Intent" }
                }
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    logger.info { "Geofence Enter Transition" }
                    // Only expecting one, but loop anyway ...
                    for (geofence in geofencingEvent.triggeringGeofences) {
                        model.location = geofence.requestId
                    }
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    logger.info { "Geofence Exit Transition" }
                    model.location = FREE_ROAD
                }
                else -> {
                    logger.error { "Unexpected geofence transition: ${geofencingEvent.geofenceTransition}" }
                }
            }
        }
        logger.info { "onStartCommand(...) stopped" }
        return START_NOT_STICKY
    }

    inner class MainServiceBinder: Binder() {
        fun getService() = this@MainService
    }

    override fun onBind(intent: Intent?): IBinder? {
        logger.info { "onBind(...) called" }
        return MainServiceBinder()
    }

    override fun onDestroy() {
        logger.error { "onDestroy() started" }
        geofencingClient?.removeGeofences(geofencePendingIntent)
        model.detach(calendarUpdater)
        logger.error { "onDestroy() started" }
    }

    companion object {
        const val CHANNEL_ID = "freeflowtollreminder.srpalmer.me.uk.CHANNEL_ID"
    }
}
