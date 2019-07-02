package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.Context
import mu.KotlinLogging
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

data class CircularRegion(
    private var _name: String,
    private var _latitude: Double,
    private var _longitude: Double,
    private var _radius: Float) {

    val name get() = _name
    val latitude get() = _latitude
    val longitude get() = _longitude
    val radius get() = _radius

    private val logger = KotlinLogging.logger {}

    constructor(node: Node) : this("", 0.0, 0.0, 0.0f) {
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
                        _latitude = coordinates[0].toDouble()
                        _longitude = coordinates[1].toDouble()
                    }
                    "proximity_meters" -> {
                        _radius = childNode.textContent.toFloat()
                    }
                }
            }
        }
        logger.trace { "XML constructor stopped: ${toString()}" }
    }

    companion object {
        private val whiteSpaceRegex = Regex("\\s")
    }
}

interface ModelObserver {
    fun onTollRoadArrival(name: String) {
        // Do Nothing
    }
    fun onTollRoadDeparture(name: String) {
        // Do Nothing
    }
}

const val FREE_ROAD = -1

class Model (context: Context) {

    private val logger = KotlinLogging.logger {}

    val tollRoads: List<CircularRegion> by lazy {
        logger.trace { "Initializing tollRoads" }
        val result = mutableListOf<CircularRegion>()
        try {
            val xmlStream = context.resources.openRawResource(R.raw.configuration)
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
            // TODO: handle XML namespace properly
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()
            val doc = documentBuilder.parse(xmlStream)
            xmlStream.close()
            val xPathFactory = XPathFactory.newInstance()
            val xPath = xPathFactory.newXPath()
            val tollRoadsElements = xPath.evaluate("/configuration/toll_roads/toll_road", doc, XPathConstants.NODESET) as NodeList
            for (i in 0 until tollRoadsElements.length) {
                if (tollRoadsElements.item(i).nodeType == Node.ELEMENT_NODE) {
                    val tollRoadElement = tollRoadsElements.item(i) as Element
                    val circularRegion = CircularRegion(tollRoadElement)
                    result.add(circularRegion)
                }
            }
        } catch (e: Throwable) {
            logger.error { e.message }
        }
        logger.trace { "tollRoads Initialized" }
        result
    }

    private var observers = mutableSetOf<ModelObserver>()

    fun attach(modelObserver: ModelObserver) {
        logger.trace { "attach() started" }
        observers.add(modelObserver)
        logger.trace { "attach() stopped" }
    }

    fun detach(modelObserver: ModelObserver) {
        logger.trace { "detach() started" }
        observers.remove(modelObserver)
        logger.trace { "detach() stopped" }
    }

    private val _tollRoadId = AtomicInteger(FREE_ROAD)
    var tollRoadId
        get() = _tollRoadId.get()
        set(new) {
            if (new != FREE_ROAD && !(new >= 0 && new < tollRoads.size))
                logger.error { "Unexpected toll tollRoadId: $new" }
            else {
                val old = _tollRoadId.get()
                _tollRoadId.set(new)
                if (new != old) {
                    logger.trace { "tollRoadId notification started" }
                    if (old != FREE_ROAD) {
                        logger.info { "Departing ${tollRoads[old].name}" }
                        observers.forEach { it.onTollRoadDeparture(tollRoads[old].name) }
                    }
                    if (new != FREE_ROAD) {
                        logger.info { "Arriving ${tollRoads[new].name}" }
                        observers.forEach { it.onTollRoadArrival(tollRoads[new].name) }
                    }
                    logger.trace { "tollRoadId notification stopped" }
                }
            }
        }

}