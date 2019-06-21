package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import mu.KotlinLogging
import kotlin.properties.Delegates

data class CircularRegion(
    val latitude: Double,
    val longitude: Double,
    val radius: Float)

interface ModelObserver {
    fun onTollRoadArrival(name: String) {
        // Do Nothing
    }
    fun onTollRoadDeparture(name: String) {
        // Do Nothing
    }
}

const val FREE_ROAD = ""

class Model {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Constructor" }
    }

    var tollRoads = hashMapOf<String, CircularRegion>()

    init {
        // TODO: read data from an external file.
        tollRoads["Test"] = CircularRegion(54.052430, -2.818521, 50.0f)
    }

    private var observers = mutableSetOf<ModelObserver>()

    fun attach(modelObserver: ModelObserver) {
        logger.info { "attach() started" }
        observers.add(modelObserver)
        logger.info { "attach() stopped" }
    }

    fun detach(modelObserver: ModelObserver) {
        logger.trace { "detach() started" }
        observers.remove(modelObserver)
        logger.trace { "detach() stopped" }
    }

    var location: String by Delegates.observable(FREE_ROAD) {
            _, old, new ->
        logger.info { "location notification started" }
        if (new != FREE_ROAD && new !in tollRoads)
            logger.error { "Unexpected toll location name: $new" }
        if (new == old)
            logger.error { "Delegate.observer,onChange called when value unchanged!" }
        else {
            if (old != FREE_ROAD)
                observers.forEach { it.onTollRoadDeparture(old) }
            if (new != FREE_ROAD)
                observers.forEach { it.onTollRoadArrival(new) }
        }
        logger.info { "location notification stopped" }
    }

}