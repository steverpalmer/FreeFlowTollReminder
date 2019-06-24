package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.ContentResolver
import android.content.ContentValues
import android.content.SharedPreferences
import android.provider.CalendarContract
import mu.KotlinLogging
import java.util.*

typealias CalendarId = Long

const val CALENDAR_ID_UNDEFINED: CalendarId = 0  // calendars are numbers from 1 ..

class CalendarUpdater (private val contentResolver: ContentResolver) : ModelObserver {

    private val logger = KotlinLogging.logger {}

    var calendarId: CalendarId = CALENDAR_ID_UNDEFINED

    fun onCreate(sharedPreferences: SharedPreferences) {
        calendarId = sharedPreferences.getLong(calendarIdKey, CALENDAR_ID_UNDEFINED)
    }

    private val durationMillis = 10 * 60 * 1_000  // TODO: Configuration option

    private val timeZone = TimeZone.getTimeZone("Europe/London")

    fun addReminder(name: String) {
        logger.info { "addReminder($name) started" }
        if (calendarId == CALENDAR_ID_UNDEFINED)
            logger.error { "Calendar not identified" }
        else {
            val startMillis = Calendar.getInstance(timeZone).run {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                timeInMillis
            }
            val endMillis = startMillis + durationMillis
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "Pay $name toll")
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, timeZone.id)
                // TODO: private or not?  put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_TENTATIVE)
            }
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment
            logger.info { "addReminder(...) added $eventId" }
        }
        logger.info { "addReminder(...) stopped" }
    }

    override fun onTollRoadDeparture(name: String) = addReminder(name)

    fun onDestroy(sharedPreferencesEditor: SharedPreferences.Editor) {
        sharedPreferencesEditor.putLong(calendarIdKey, calendarId)
    }

    companion object {
        private const val calendarIdKey = "CalendarUpdater.calendarId"
    }
}