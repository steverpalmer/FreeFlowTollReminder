package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.SharedPreferences
import android.provider.CalendarContract
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.atomic.AtomicLong

const val CALENDAR_ID_UNDEFINED: Long = -1  // calendars are numbers from 1 ..
const val EVENT_ID_UNDEFINED: Long = -1

class CalendarUpdater (private val contentResolver: ContentResolver) : ModelObserver {

    private val logger = KotlinLogging.logger {}

    private val _calendarId = AtomicLong(CALENDAR_ID_UNDEFINED)
    var calendarId
        get() = _calendarId.get()
        set(value) = _calendarId.set(value)

    fun onCreate(sharedPreferences: SharedPreferences) {
        calendarId = sharedPreferences.getLong(calendarIdKey, CALENDAR_ID_UNDEFINED)
    }

    private val durationMillis = 10 * 60 * 1_000  // TODO: Configuration option

    private val timeZone = TimeZone.getTimeZone("Europe/London")

    private fun findEvent(title: String, startMillis: Long): Long {
        logger.trace { "findEvent($title, $startMillis) started" }
        var result = EVENT_ID_UNDEFINED
        if (calendarId == CALENDAR_ID_UNDEFINED)
            logger.error { "Calendar not identified" }
        else {
            val searchStartMillis = startMillis - durationMillis / 2
            val searchEndMillis = searchStartMillis + 2 * durationMillis
            val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(uriBuilder, searchStartMillis)
            ContentUris.appendId(uriBuilder, searchEndMillis)
            val cur = contentResolver.query(
                uriBuilder.build(),
                arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE),
                null,
                null)
            if (cur == null)
                logger.error { "query returned null cursor" }
            else {
                while (cur.moveToNext()) {
                    if (cur.getString(1) == title) {
                        result = cur.getLong(0)
                        break
                    }
                }
                cur.close()
            }
        }
        logger.trace { "findEvent(...) returns $result" }
        return result
    }

    private fun putEvent(title: String, startMillis: Long) {
        logger.trace { "putEvent($title, $startMillis) started" }
        val endMillis = startMillis + durationMillis
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone.id)
            // TODO: private or not?  put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_TENTATIVE)
        }
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        logger.trace { "putEvent(...) added event: ${uri?.lastPathSegment}" }
    }

    override fun onTollRoadDeparture(name: String) {
        logger.trace { "onTollRoadDeparture($name) started" }
        if (calendarId == CALENDAR_ID_UNDEFINED)
            logger.error { "Calendar not identified" }
        else {
            val title = "Pay $name toll"
            val startMillis = Calendar.getInstance(timeZone).run {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                timeInMillis
            }
            val oldEventId = findEvent(title, startMillis)
            if (oldEventId != EVENT_ID_UNDEFINED)
                logger.debug { "Event already present: $oldEventId" }
            else
                putEvent(title, startMillis)
        }
        logger.trace { "onTollRoadDeparture(...) stopped" }
    }

    fun onDestroy(sharedPreferencesEditor: SharedPreferences.Editor) {
        logger.trace { "onDestroy(...) started" }
        sharedPreferencesEditor.putLong(calendarIdKey, calendarId)
        logger.trace { "onDestroy(...) stopped" }
    }

    companion object {
        private const val calendarIdKey = "CalendarUpdater.calendarId"
    }
}