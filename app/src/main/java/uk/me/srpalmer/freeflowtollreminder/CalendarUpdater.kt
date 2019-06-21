package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.CalendarContract
import mu.KotlinLogging
import java.util.*

typealias CalendarId = Long

private const val CALENDAR_ID_UNDEFINED: CalendarId = 0  // calendars are numbers from 1 ..

class CalendarUpdater (private val contentResolver: ContentResolver) : ModelObserver {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Constructor" }
    }

    var calendarId: CalendarId = CALENDAR_ID_UNDEFINED

    fun unsetCalendarId() {
        calendarId = CALENDAR_ID_UNDEFINED
    }

    private val durationMillis = 10 * 60 * 1_000

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
                // put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_TENTATIVE)
            }
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment
            logger.info { "addReminder(...) added $eventId" }
        }
        logger.info { "addReminder(...) stopped" }
    }

    override fun onTollRoadDeparture(name: String) = addReminder(name)
}