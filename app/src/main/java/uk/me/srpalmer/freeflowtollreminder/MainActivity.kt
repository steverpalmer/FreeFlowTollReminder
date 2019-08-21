package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.android.synthetic.main.activity_main.*
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import mu.KotlinLogging

class MainActivity : ServiceConnection, AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    private val logger = KotlinLogging.logger {}

    private var permissionsList: List<String>? = null
    private var awaitingPermissions: Boolean = false
    private var serviceBinder: MainService.MainServiceBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.trace { "MainActivity.onCreate(...) started" }
        logger.info { "Activity Started" }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logger.trace { "MainActivity.onCreate(...) stopped" }
    }

    private fun startMainService() {
        logger.trace { "MainActivity.startMainService() started" }
        startForegroundService(Intent(this, MainService::class.java))!!
        val rc = bindService(Intent(this, MainService::class.java), this,
            Context.BIND_ABOVE_CLIENT or Context.BIND_NOT_FOREGROUND)
        assert (rc) { "Failed to bind to service" }
        logger.trace { "MainActivity.startMainService() stopped" }
    }

    private val finishListener = View.OnClickListener {
        logger.trace { "finishButton.onClick(...) started" }
        serviceBinder?.onFinishRequest()
        // finish()
        logger.trace { "finishButton.onClick(...) stopped" }
    }

    private val calendarSelectorListener = object : AdapterView.OnItemSelectedListener {

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            logger.trace { "CalendarSelector.onItemSelected(..., $position, $id) started" }
            serviceBinder?.calendarPosition = position
            logger.trace { "CalendarSelector.onItemSelected(..., $position, $id) stopped" }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            logger.trace { "CalendarSelector.onItemSelected(...) started" }
            serviceBinder?.calendarId = CALENDAR_ID_UNDEFINED
            logger.trace { "CalendarSelector.onItemSelected(...) stopped" }
        }

    }

    override fun onResume() {
        logger.trace { "MainActivity.onResume(...) started" }
        super.onResume()
        if (permissionsList == null) {
            logger.debug { "Processing required permissions" }
            val newPermissionsList = (packageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED } ) ?: emptyList()
            permissionsList = newPermissionsList
            if (newPermissionsList.isNotEmpty()) {
                requestPermissions(newPermissionsList.toTypedArray(), 666)
                awaitingPermissions = true
            }
        }
        if (!awaitingPermissions && serviceBinder == null)
            startMainService()
        finishButton.setOnClickListener(finishListener)
        calendarSelection.onItemSelectedListener = calendarSelectorListener
        logger.trace { "MainActivity.onResume(...) stopped" }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        logger.trace { "MainActivity.onRequestPermissionsResult($requestCode, $permissions, $grantResults) started" }
        assert(requestCode == 666) { "Expected requestCode to be 666, but got $requestCode" }
        startMainService()
        logger.trace { "MainActivity.onRequestPermissionsResultCallback($requestCode, ...) stopped" }
    }

    override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
        logger.trace { "MainActivity.onServiceConnected(...) started" }
        when (iBinder) {
            is MainService.MainServiceBinder -> {
                serviceBinder = iBinder
                logger.debug { "Displaying Toll Road List" }
                tollRoadList.text = ((iBinder.tollRoadList().fold(StringBuilder("Known toll roads:")) {
                        sb, s -> sb.append("\n• ").append(s) }).toString())
                tollRoadList.visibility = View.VISIBLE
                logger.debug { "Displaying calendar selector" }
                val calendarList = iBinder.calendarList
                calendarSelection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, calendarList.toTypedArray())
                if (iBinder.calendarPosition != -1)
                    calendarSelection.setSelection(iBinder.calendarPosition)
                calendarSelection.visibility = View.VISIBLE
            }
            else ->
                logger.error { "Unexpected iBinder type: $iBinder" }
        }
        logger.trace { "MainActivity.onServiceConnected(...) stopped" }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        logger.trace { "MainActivity.onServiceDisconnected(...) started" }
        if (serviceBinder != null) {
            unbindService(this)
            serviceBinder = null
        }
        finish()
        logger.trace { "MainActivity.onServiceDisconnected(...) stopped" }
    }

    override fun onPause() {
        logger.trace { "MainActivity.onPause() started" }
        super.onPause()
        calendarSelection.onItemSelectedListener = null
        finishButton.setOnClickListener(null)
        if (serviceBinder != null) {
            unbindService(this)
            serviceBinder = null
        }
        logger.trace { "MainActivity.onPause() stopped" }
    }

    override fun onDestroy() {
        logger.trace { "MainActivity.onDestroy() started" }
        super.onDestroy()
        logger.info { "Activity Stopped" }
        logger.trace { "MainActivity.onDestroy() started" }
    }

}
