package uk.me.srpalmer.freeflowtollreminder.model
// Copyright 2019 Steve Palmer


import android.location.Location
import com.google.android.gms.location.LocationRequest
import mu.KotlinLogging
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.*

class TollRoad(node: Node) {

    private val logger = KotlinLogging.logger {}

    private lateinit var _name: String
    val name get() = _name
    private lateinit var position: Location
    private var radius: Float = 0.0f

    init {
        logger.trace { "XML constructor started" }
        val childNodes = node.childNodes
        val childNodesLength = childNodes.length
        for (i in 0 until childNodesLength) {
            if (childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                val childNode = childNodes.item(i) as Element
                // TODO XML Error detection and handling
                when (childNode.tagName) {
                    "name" -> {
                        _name = childNode.textContent
                    }
                    "gpl:GPL_CoordinateTuple" -> {
                        val coordinates = childNode.textContent.trim().split(whiteSpaceRegex)
                        position = Location("FreeFlowTollReminder").apply {
                            latitude = coordinates[0].toDouble()
                            longitude = coordinates[1].toDouble()
                        }
                    }
                    "proximity_meters" -> {
                        radius = childNode.textContent.toFloat()
                    }
                }
            }
        }
        logger.info { "Configured $name" }
        logger.trace { "XML constructor stopped: ${toString()}" }

    }

    data class Due(val reminder: String, val whenMilliseconds: Long)

    private val timeZone = TimeZone.getTimeZone("Europe/London")

    private var distance: Float = -1.0f
    private var inUse: Boolean = false

    fun isTollDue(location: Location): Due? {
        logger.trace { "isTollDue($location) started" }
        var result: Due? = null
        distance = location.distanceTo(position)
        // Uses 2σ for hysteresis to give 97.7% confidence
        if (inUse) {
            if (distance - 2 * location.accuracy > radius) {
                logger.info { "Departing $name" }
                val whenMilliseconds = Calendar.getInstance(timeZone).run {
                    timeInMillis = location.time
                    add(Calendar.DAY_OF_MONTH, 2)  // TODO: maybe better to use (HOUR_OF_DAY, 47)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    timeInMillis
                }
                result = Due("Pay $name Toll", whenMilliseconds)
                inUse = false
            }
        } else {
            if (distance + 2 * location.accuracy < radius) {
                logger.info { "Arriving $name" }
                inUse = true
            }
        }
        logger.trace { "isTollDue(...) returns $result" }
        return result
    }

    data class Proximity(val intervalMilliseconds: Long, val priority: Int): Comparable<Proximity> {

        override fun compareTo(other: Proximity): Int {
            return intervalMilliseconds.compareTo(other.intervalMilliseconds)
        }

        companion object {
            val farFarAway = Proximity(10 * 60 * 1_000, LocationRequest.PRIORITY_LOW_POWER)
            val inCity     = Proximity(     30 * 1_000, LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
            val closeBy    = Proximity(      2 * 1_000, LocationRequest.PRIORITY_HIGH_ACCURACY)
        }
    }

    fun lastLocationProximity(): Proximity {
        logger.trace { "lastLocationProximity() started" }
        val d = distance - radius
        val result = when {
            d <  1_000.0f -> Proximity.closeBy
            d < 20_000.0f -> Proximity.inCity
            else          -> Proximity.farFarAway
        }
        logger.trace { "lastLocationProximity() returns $result" }
        return result
    }

    override fun toString(): String {
        return "TollRoad(\"$_name\")"
    }

    companion object {
        private val whiteSpaceRegex = Regex("\\s")
    }
}