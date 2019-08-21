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

class MainActivity : ServiceConnection, AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    private var permissionsList: List<String>? = null
    private var awaitingPermissions: Boolean = false
    private var serviceBinder: MainService.MainServiceBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    private fun startMainService() {
        startForegroundService(Intent(this, MainService::class.java))!!
        val rc = bindService(Intent(this, MainService::class.java), this,
            Context.BIND_ABOVE_CLIENT or Context.BIND_NOT_FOREGROUND)
        assert (rc) { "Failed to bind to service" }
    }

    private val finishListener = View.OnClickListener {
        serviceBinder?.onFinishRequest()
    }

    private val calendarSelectorListener = object : AdapterView.OnItemSelectedListener {

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            serviceBinder?.calendarPosition = position
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            serviceBinder?.calendarId = CALENDAR_ID_UNDEFINED
        }

    }

    override fun onResume() {
        super.onResume()
        if (permissionsList == null) {
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
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        assert(requestCode == 666) { "Expected requestCode to be 666, but got $requestCode" }
        startMainService()
    }

    override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
        when (iBinder) {
            is MainService.MainServiceBinder -> {
                serviceBinder = iBinder
                tollRoadList.text = ((iBinder.tollRoadList().fold(StringBuilder("Known toll roads:")) {
                        sb, s -> sb.append("\n• ").append(s) }).toString())
                tollRoadList.visibility = View.VISIBLE
                val calendarList = iBinder.calendarList
                calendarSelection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, calendarList.toTypedArray())
                if (iBinder.calendarPosition != -1)
                    calendarSelection.setSelection(iBinder.calendarPosition)
                calendarSelection.visibility = View.VISIBLE
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        if (serviceBinder != null) {
            unbindService(this)
            serviceBinder = null
        }
        finish()
    }

    override fun onPause() {
        super.onPause()
        calendarSelection.onItemSelectedListener = null
        finishButton.setOnClickListener(null)
        if (serviceBinder != null) {
            unbindService(this)
            serviceBinder = null
        }
    }

}
