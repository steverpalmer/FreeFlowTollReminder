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
import android.widget.ArrayAdapter
import mu.KotlinLogging

class MainActivity : ServiceConnection, AppCompatActivity() {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Constructor" }
    }

    var service: MainService? = null

    val calendarList: List<Pair<CalendarId, String>> by lazy {
        logger.info { "calendarList Construction started" }
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            null,
            null,
            null
        ) ?: throw Exception("failed to access calendar")
        val list: MutableList<Pair<CalendarId, String>> = mutableListOf()
        if (cursor.moveToFirst())
        {
            do {
                list.add(Pair(cursor.getLong(0), cursor.getString(1)))
            } while (cursor.moveToNext())
            cursor.close()
        }
        logger.info { "calendarList Construction stopped" }
        list
    }

    inner class CalendarSelector: AdapterView.OnItemSelectedListener {

        init {
            logger.info { "CalendarSelector Construction started" }
            calendarSelection.visibility = View.INVISIBLE
            val calendarNameArray = Array(calendarList.size) { i -> calendarList[i].second }
            calendarSelection.adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                calendarNameArray)
            logger.info { "CalendarSelector Construction stopped" }
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            logger.info { "CalendarSelector.onItemSelected(_, $position, $id) started" }
            service?.calendarId = id
            addReminderButton.visibility = View.VISIBLE
            logger.info { "CalendarSelector.onItemSelected(_, $position, $id) stopped" }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            logger.info { "CalendarSelector.onItemSelected(...) started" }
            service?.calendarId = CALENDAR_ID_UNDEFINED
            addReminderButton.visibility = View.INVISIBLE
            logger.info { "CalendarSelector.onItemSelected(...) stopped" }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.info { "onCreate(...) started" }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startForegroundService(Intent(this, MainService::class.java))
        addReminderButton.setOnClickListener {
            logger.info { "addReminderButton.onClick(...) started" }
            service?.addReminder("Test")  // TODO: get a name from the model
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
        logger.info { "searching for calendar" }
        var pos = 0
        for ((id, _) in calendarList) {
            pos++
            if (id == service?.calendarId) {
                calendarSelection.setSelection(pos)
                break
            }
        }
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
