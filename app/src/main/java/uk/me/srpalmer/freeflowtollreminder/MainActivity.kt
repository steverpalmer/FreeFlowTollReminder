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

    var serviceBinder: MainService.MainServiceBinder? = null

    data class CalendarInfo (val id: Long, val name: String)
    val calendarInfoList: List<CalendarInfo> by lazy {
        logger.trace { "calendarInfoList Construction started" }
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            null,
            null,
            null
        ) ?: throw Exception("failed to access calendar")
        val list: MutableList<CalendarInfo> = mutableListOf()
        if (cursor.moveToFirst())
        {
            do {
                list.add(CalendarInfo(cursor.getLong(0), cursor.getString(1)))
            } while (cursor.moveToNext())
            cursor.close()
        }
        logger.trace { "calendarInfoList Construction stopped" }
        list
    }

    inner class CalendarSelector: AdapterView.OnItemSelectedListener {

        init {
            logger.trace { "CalendarSelector Construction started" }
            calendarSelection.visibility = View.INVISIBLE
            val calendarNameArray = Array(calendarInfoList.size) { i -> calendarInfoList[i].name }
            calendarSelection.adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                calendarNameArray)
            logger.trace { "CalendarSelector Construction stopped" }
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            logger.trace { "CalendarSelector.onItemSelected(_, $position, _) started" }
            serviceBinder?.calendarId = calendarInfoList[position].id
            logger.trace { "CalendarSelector.onItemSelected(_, $position, _) stopped" }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            logger.trace { "CalendarSelector.onItemSelected(...) started" }
            serviceBinder?.calendarId = CALENDAR_ID_UNDEFINED
            logger.trace { "CalendarSelector.onItemSelected(...) stopped" }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.trace { "onCreate(...) started" }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startForegroundService(Intent(this, MainService::class.java))
        finishButton.setOnClickListener {
            logger.trace { "finishButton.onClick(...) started" }
            onFinishRequest()
            logger.trace { "finishButton.onClick(...) stopped" }
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED)
            logger.error { "Don't have permission to read calendar" }
        else
            calendarSelection.onItemSelectedListener = CalendarSelector()

        logger.trace { "onCreate(...) stopped" }
    }

    /*
    private val modelObserver = object: ModelObserver {

        override fun onTollRoadArrival(name: String) {
            logger.trace { "onTollRoadArrival($name) started" }
            statusDisplay.text = name
            logger.trace { "onTollRoadArrival(...) stopped" }
        }

        override fun onTollRoadDeparture(name: String) {
            logger.trace { "onTollRoadDeparture($name) started" }
            statusDisplay.text = ""
            logger.trace { "onTollRoadDeparture(...) stopped" }
        }

    }
    */

    override fun onStart() {
        logger.trace { "onStart(...) started" }
        super.onStart()
        val success = bindService(
            Intent(this, MainService::class.java),
            this,
            Context.BIND_AUTO_CREATE)
        if (!success)
            logger.error { "Failed to bind to service" }
        logger.trace { "onStart(...) stopped" }
    }

    override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
        logger.trace { "onServiceConnected(...) started" }
        serviceBinder = iBinder as MainService.MainServiceBinder
        // serviceBinder?.modelAttach(modelObserver)
        logger.trace { "searching for calendar" }
        var position = -1
        for ((id, _) in calendarInfoList) {
            position++
            if (id == serviceBinder?.calendarId) {
                calendarSelection.setSelection(position)
                break
            }
        }
        calendarSelection.visibility = View.VISIBLE
        statusDisplay.text = "Connected"
        logger.trace { "onServiceConnected(...) stopped" }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        logger.trace { "onServiceDisconnected(...) started" }
        calendarSelection.visibility = View.INVISIBLE
        // serviceBinder?.modelDetach(modelObserver)
        serviceBinder = null
        statusDisplay.text = "..."
        logger.trace { "onServiceDisconnected(...) stopped" }
    }

    override fun onStop() {
        logger.trace { "OnStop() started" }
        super.onStop()
        // serviceBinder?.modelDetach(modelObserver)
        unbindService(this)
        logger.trace { "OnStop() stopped" }
    }

    override fun onDestroy() {
        logger.trace { "onDestroy() started" }
        super.onDestroy()
        logger.trace { "onDestroy() stopped" }
    }

    private fun onFinishRequest() {
        logger.trace { "onFinishRequest() started" }
        serviceBinder?.onFinishRequest()
        finish()
        logger.trace { "onFinishRequest() stopped" }
    }
}
