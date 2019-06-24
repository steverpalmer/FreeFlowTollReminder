package uk.me.srpalmer.freeflowtollreminder
// Copyright 2019 Steve Palmer

import android.content.Context
import mu.KotlinLogging
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.SAXException
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.properties.Delegates

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
        logger.info { "XML constructor started" }
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
        logger.info { "XML constructor stopped: ${toString()}" }
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

const val FREE_ROAD = ""

class Model (context: Context) {

    private val logger = KotlinLogging.logger {}

    val tollRoads: Map<String, CircularRegion> by lazy {
        logger.info { "Initializing tollRoads" }
        val result = HashMap<String, CircularRegion>()
        try {
            val xmlStream = context.resources.openRawResource(R.raw.configuration)
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
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
                    result[circularRegion.name] = circularRegion
                }
            }
        } catch (e: IOException) {
            logger.error { e.message }
        } catch (e: ParserConfigurationException) {
            logger.error { e.message }
        } catch (e: SAXException) {
            logger.error { e.message }
        }
        result
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