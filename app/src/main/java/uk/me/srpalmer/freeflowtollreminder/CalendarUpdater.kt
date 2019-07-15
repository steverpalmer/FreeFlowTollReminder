package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.SharedPreferences
import android.provider.CalendarContract
import mu.KotlinLogging
import uk.me.srpalmer.freeflowtollreminder.model.TollRoad
import java.util.*
import java.util.concurrent.atomic.AtomicLong

const val CALENDAR_ID_UNDEFINED: Long = -1  // calendars are numbers from 1 ..
const val EVENT_ID_UNDEFINED: Long = -1

class CalendarUpdater (private val contentResolver: ContentResolver) {

    private val logger = KotlinLogging.logger {}

    private val _calendarId = AtomicLong(CALENDAR_ID_UNDEFINED)
    var calendarId
        get() = _calendarId.get()
        set(value) = _calendarId.set(value)

    fun onCreate(sharedPreferences: SharedPreferences) {
        calendarId = sharedPreferences.getLong(calendarIdKey, CALENDAR_ID_UNDEFINED)
    }

    private val durationMillis = 10 * 60 * 1_000  // TODO: Configuration option

    private val timeZone = TimeZone.getTimeZone("Europe/London")  // TODO: Generalize

    fun addReminder(tollDue: TollRoad.Due) {
        logger.trace { "addReminder($tollDue) started" }
        // To avoid the user selecting a different calendar in the middle of adding a reminder, take a local copy...
        val reminderCalendarId = calendarId
        if (reminderCalendarId == CALENDAR_ID_UNDEFINED)
            logger.error { "Calendar not identified" }
        else {
            val startMillis = tollDue.whenMilliseconds - 12 * 60 * 60 * 1_000
            var oldEventId = EVENT_ID_UNDEFINED
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
                val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                logger.info { "Event \"${tollDue.reminder}\" added: ${uri?.lastPathSegment}" }
            }
        }
        logger.trace { "addReminder(...) stopped" }
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