package kr.b1ink.scriptwidget.data.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * Parses XML with SAX and returns the events as a JSON array for `XMLParser` to replay:
 * `["startDoc"]`, `["start", name, {attrs}]`, `["text", chars]`, `["end", name]`, `["endDoc"]`, `["error", message]`.
 * Adjacent character chunks are merged. DTDs/external entities are disabled.
 */
internal object XmlEvents {
    fun parse(xml: String): String {
        val events = mutableListOf<JsonArray>()
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                events += buildJsonArray { add(JsonPrimitive("text")); add(JsonPrimitive(text.toString())) }
                text.clear()
            }
        }
        val handler = object : DefaultHandler() {
            override fun startDocument() {
                events += buildJsonArray { add(JsonPrimitive("startDoc")) }
            }

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                flushText()
                val attrs = JsonObject((0 until attributes.length).associate { attributes.getQName(it) to JsonPrimitive(attributes.getValue(it)) })
                events += buildJsonArray { add(JsonPrimitive("start")); add(JsonPrimitive(qName)); add(attrs) }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                text.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                flushText()
                events += buildJsonArray { add(JsonPrimitive("end")); add(JsonPrimitive(qName)) }
            }

            override fun endDocument() {
                flushText()
                events += buildJsonArray { add(JsonPrimitive("endDoc")) }
            }
        }
        try {
            val factory = SAXParserFactory.newInstance()
            listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
            ).forEach { (feature, value) -> runCatching { factory.setFeature(feature, value) } }
            factory.newSAXParser().parse(InputSource(StringReader(xml)), handler)
        } catch (e: SAXParseException) {
            events += buildJsonArray { add(JsonPrimitive("error")); add(JsonPrimitive("Line ${e.lineNumber}: ${e.message}")) }
        } catch (e: Exception) {
            events += buildJsonArray { add(JsonPrimitive("error")); add(JsonPrimitive(e.message ?: e.toString())) }
        }
        return JsonArray(events).toString()
    }
}
