package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.provider.CalendarContract
import android.os.Handler
import mu.KotlinLogging
import uk.me.srpalmer.freeflowtollreminder.model.TollRoad
import java.util.*
import java.util.concurrent.atomic.AtomicLong

const val CALENDAR_ID_UNDEFINED: Long = -1  // calendars are numbers from 1 ..
const val EVENT_ID_UNDEFINED: Long = -1

class CalendarUpdater (private val context: Context) {

    private val logger = KotlinLogging.logger {}

    data class CalendarInfo (val id: Long, val name: String)
    private var _calendarInfoList: List<CalendarInfo>? = null
    private val _calendarId = AtomicLong(CALENDAR_ID_UNDEFINED)
    private var _calendarPosition: Int = -1  // value depends on _calendarInfoList and _calendarId

    private fun syncState() {
        logger.trace { "CalendarUpdater.syncState() started" }
        val localCalendarInfoList = _calendarInfoList
        val localCalendarId = _calendarId.get()
        if (localCalendarInfoList == null || localCalendarId == CALENDAR_ID_UNDEFINED)
            _calendarPosition = -1
        else {
            _calendarPosition = localCalendarInfoList.indexOfFirst { (id, _) -> id == localCalendarId }
            if (_calendarPosition == -1) {
                _calendarId.set(CALENDAR_ID_UNDEFINED)
            }
        }
        logger.trace { "CalendarUpdater.syncState() stopped: ($_calendarInfoList, $_calendarId, $_calendarPosition)" }
    }

    val calendarInfoList: List<CalendarInfo>
        get() {
            logger.trace { "CalendarUpdater.calendarInfoList.get() started" }
            val localCalendarInfoList = _calendarInfoList
            val result: List<CalendarInfo> = if (localCalendarInfoList != null) localCalendarInfoList
            else {
                val accumulator: MutableList<CalendarInfo> = mutableListOf()
                try {
                    val cursor = context.contentResolver.query(
                        CalendarContract.Calendars.CONTENT_URI,
                        arrayOf(
                            CalendarContract.Calendars._ID,
                            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                        null,
                        null,
                        null
                    )
                    if (cursor != null && cursor.moveToFirst())
                    {
                        do
                            accumulator.add(CalendarInfo(cursor.getLong(0), cursor.getString(1)))
                        while (cursor.moveToNext())
                        cursor.close()
                    }
                } catch (exception: SecurityException) {
                    logger.error { "Failed to access calendar: $exception" }
                }
                _calendarInfoList = accumulator
                accumulator
            }
            syncState()
            logger.trace { "CalendarUpdater.calendarInfoList.gat() stopped: $_calendarInfoList" }
            return result
        }

    var calendarId
        get() = _calendarId.get()
        set(value) {
            logger.trace { "CalendarUpdater.calendarId.set($value) started" }
            _calendarId.set(value)
            syncState()
            logger.trace { "CalendarUpdater.calendarId.set($value) stopped" }
        }

    var calendarPosition
        get () = _calendarPosition
        set(value) {
            logger.trace { "CalendarUpdater.calendarPosition.set($value) started" }
            if (value == -1) {
                logger.trace { "CalendarUpdater.calendarPosition: set to -1" }
                _calendarPosition = value
            }
            else {
                val localCalendarInfoList = _calendarInfoList
                if (localCalendarInfoList != null) {
                    logger.trace { "CalendarUpdater.calendarPosition: setting values" }
                    _calendarId.set(localCalendarInfoList[value].id)
                    _calendarPosition = value
                }
                else
                    logger.trace { "CalendarUpdater.calendarPosition: no calendar list" }
            }
            logger.trace { "CalendarUpdater.calendarPosition.set($value) stopped: ($_calendarInfoList, $_calendarId, $_calendarPosition)" }
        }

    private val calendarsObserver = object : ContentObserver(Handler()) {
        override fun onChange(seflChange: Boolean) {
            logger.trace { "onChange(Boolean) started" }
            _calendarInfoList = null
            syncState()
            logger.trace { "onChange(Boolean) stopped" }
        }

        override fun onChange(seflChange: Boolean, uri: Uri?) {
            logger.trace { "onChange(Boolean, Uri) started" }
            _calendarInfoList = null
            syncState()
            logger.trace { "onChange(Boolean, Uri) stopped" }
        }
    }

    fun onCreate(sharedPreferences: SharedPreferences) {
        logger.trace { "CalendarUpdater.onCreate started" }
        context.contentResolver.registerContentObserver(CalendarContract.Calendars.CONTENT_URI, false, calendarsObserver)
        calendarId = sharedPreferences.getLong(calendarIdKey, CALENDAR_ID_UNDEFINED)
        logger.trace { "CalendarUpdater.onCreate stopped" }
    }

    private val durationMillis = 10 * 60 * 1_000  // TODO: Configuration option

    private val timeZone = TimeZone.getTimeZone("Europe/London")  // TODO: Generalize

    fun addReminder(tollDue: TollRoad.Due) {
        logger.trace { "CalendarUpdater.addReminder($tollDue) started" }
        // To avoid the user selecting a different calendar in the middle of adding a reminder, take a local copy...
        val reminderCalendarId = calendarId
        if (reminderCalendarId == CALENDAR_ID_UNDEFINED)
            logger.error { "Calendar not identified" }
        else if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_DENIED ||
            context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_DENIED) {
            logger.error { "Can't update calendar" }
            _calendarInfoList = null
            syncState()
        }
        else {
            val startMillis = tollDue.whenMilliseconds - 12 * 60 * 60 * 1_000
            var oldEventId = EVENT_ID_UNDEFINED
            val searchStartMillis = startMillis - durationMillis / 2
            val searchEndMillis = searchStartMillis + 2 * durationMillis
            val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(uriBuilder, searchStartMillis)
            ContentUris.appendId(uriBuilder, searchEndMillis)
            val cur = context.contentResolver.query(
                uriBuilder.build(),
                arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE),
               null,
               null)
            if (cur == null)
                logger.error { "query returned null cursor" }
            else {
                while (cur.moveToNext()) {
                    if (cur.getString(1) == tollDue.reminder) {
                        oldEventId = cur.getLong(0)
                        break
                    }
                }
                cur.close()
            }
            if (oldEventId != EVENT_ID_UNDEFINED)
                logger.info { "Event \"${tollDue.reminder}\" found: $oldEventId" }
            else {
                val endMillis = startMillis + durationMillis
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.TITLE, tollDue.reminder)
                    put(CalendarContract.Events.CALENDAR_ID, reminderCalendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, timeZone.id)
                    put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
                    put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_TENTATIVE)
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                logger.info { "Event \"${tollDue.reminder}\" added: ${uri?.lastPathSegment}" }
            }
        }
        logger.trace { "CalendarUpdater.addReminder(...) stopped" }
    }

    fun onDestroy(sharedPreferencesEditor: SharedPreferences.Editor) {
        logger.trace { "CalendarUpdater.onDestroy(...) started" }
        sharedPreferencesEditor.putLong(calendarIdKey, calendarId)
        context.contentResolver.unregisterContentObserver(calendarsObserver)
        logger.trace { "CalendarUpdater.onDestroy(...) stopped" }
    }

    companion object {
        private const val calendarIdKey = "CalendarUpdater.calendarId"
    }
}