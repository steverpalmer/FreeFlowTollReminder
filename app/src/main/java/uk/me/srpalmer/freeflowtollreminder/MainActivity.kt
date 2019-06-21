package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.android.synthetic.main.activity_main.*
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.provider.CalendarContract
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import mu.KotlinLogging

class MainActivity : ServiceConnection, AppCompatActivity() {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Constructor" }
    }

    var service: MainService? = null

    inner class CalendarSelector: AdapterView.OnItemSelectedListener {

        init {
            logger.info { "CalendarSelector Construction started" }
            calendarSelection.visibility = View.INVISIBLE
            val cursor = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                null,
                null,
                "${CalendarContract.Calendars._ID} DESC")
            when (cursor?.count) {
                null -> logger.error { "null calendarCursor" }
                0 -> logger.error { "no calendars found" }
                else -> {
                    calendarSelection.adapter = SimpleCursorAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_item,
                        cursor,
                        arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                        IntArray(1) { android.R.id.text1 },
                        0
                    )
                }
            }
            logger.info { "CalendarSelector Construction stopped" }
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            logger.info { "CalendarSelector.onItemSelected(_, $position, $id) called" }
            service?.calendarId = id
            addReminderButton.visibility = View.VISIBLE
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            logger.info { "CalendarSelector.onItemSelected(...) called" }
            service?.unsetCalendarId()
            addReminderButton.visibility = View.INVISIBLE
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.info { "onCreate(...) started" }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // startService(Intent(this, MainService::class.java))
        startForegroundService(Intent(this, MainService::class.java))
        addReminderButton.setOnClickListener {
            logger.info { "addReminderButton.onClick(...) started" }
            service?.addReminder("Test")
            logger.info { "addReminderButton.onClick(...) stopped" }
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED)
            logger.error { "Don't have permission to read calendar" }
        else
            calendarSelection.onItemSelectedListener = CalendarSelector()

        logger.info { "onCreate(...) stopped" }
    }

    private val modelObserver = object: ModelObserver {

        override fun onTollRoadArrival(name: String) {
            logger.info { "onTollRoadArrival($name) started" }
            statusDisplay.text = name
            logger.info { "onTollRoadArrival(...) stopped" }
        }

        override fun onTollRoadDeparture(name: String) {
            logger.info { "onTollRoadDeparture($name) started" }
            statusDisplay.text = ""
            logger.info { "onTollRoadDeparture(...) stopped" }
        }

    }

    override fun onStart() {
        logger.info { "onStart(...) started" }
        super.onStart()
        val success = bindService(
            Intent(this, MainService::class.java),
            this,
            Context.BIND_AUTO_CREATE)
        if (!success)
            logger.error { "Failed to bind to service" }
        logger.info { "onStart(...) stopped" }
    }

    override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
        logger.info { "onServiceConnected(...) started" }
        val binder = iBinder as MainService.MainServiceBinder
        service = binder.getService()
        service?.attach(modelObserver)
        calendarSelection.visibility = View.VISIBLE
        statusDisplay.text = ""
        logger.info { "onServiceConnected(...) stopped" }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        logger.info { "onServiceDisconnected(...) started" }
        calendarSelection.visibility = View.INVISIBLE
        service?.detach(modelObserver)
        service = null
        statusDisplay.text = "..."
        logger.info { "onServiceDisconnected(...) stopped" }
    }

    override fun onStop() {
        logger.info { "OnStop() started" }
        super.onStop()
        service?.detach(modelObserver)
        unbindService(this)
        logger.info { "OnStop() stopped" }
    }

    override fun onDestroy() {
        logger.info { "onDestroy() started" }
        super.onDestroy()
        logger.info { "onDestroy() stopped" }
    }
}
