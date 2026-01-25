@file:OptIn(ExperimentalXmlUtilApi::class)

package com.deezer.caupain.serialization.xml

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.XmlSerializer
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable(with = PropertiesSerializer::class)
private data class Properties(val properties: List<Property> = emptyList())

@Serializable
private data class Property(val key: String, @XmlValue val value: String)

private object PropertiesSerializer : XmlSerializer<Properties> {

    private val elementSerializer = serializer<Property>()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Properties") {
        element("properties", ListSerializer(elementSerializer).descriptor)
    }

    override fun deserialize(decoder: Decoder): Properties {
        val properties = decoder.decodeStructure(descriptor) {
            decodeSerializableElement(descriptor, 0, ListSerializer(elementSerializer))
        }
        return Properties(properties)
    }

    override fun serialize(
        encoder: Encoder,
        value: Properties
    ) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(
                descriptor = descriptor,
                index = 0,
                serializer = ListSerializer(elementSerializer),
                value = value.properties
            )
        }
    }

    override fun deserializeXML(
        decoder: Decoder,
        input: XmlReader,
        previousValue: Properties?,
        isValueChild: Boolean
    ): Properties {
        val xml = (decoder as XML.XmlInput).delegateFormat()

        val elementXmlDescriptor = xml
            .xmlDescriptor(elementSerializer)
            .getElementDescriptor(0)

        val propertyList = mutableListOf<Property>()
        decoder.decodeStructure(descriptor) {
            while (input.next() != EventType.END_ELEMENT) {
                when (input.eventType) {
                    EventType.COMMENT,
                    EventType.IGNORABLE_WHITESPACE -> {
                        // Comments and whitespace are just ignored
                    }

                    EventType.ENTITY_REF,
                    EventType.TEXT -> {
                        if (input.text.isNotBlank()) {
                            @OptIn(ExperimentalXmlUtilApi::class)
                            xml.config.policy.handleUnknownContentRecovering(
                                input = input,
                                inputKind = InputKind.Text,
                                descriptor = elementXmlDescriptor,
                                name = null,
                                candidates = emptyList()
                            )
                        }
                    }

                    EventType.START_ELEMENT -> {
                        val filter = DynamicTagReader(
                            idPropertyName = "key",
                            reader = input,
                            descriptor = elementXmlDescriptor
                        )

                        val property = xml.decodeFromReader(elementSerializer, filter)
                        propertyList.add(property)
                    }

                    else -> // other content that shouldn't happen
                        throw XmlException("Unexpected tag content")
                }
            }
        }
        return Properties(propertyList)
    }

    override fun serializeXML(
        encoder: Encoder,
        output: XmlWriter,
        value: Properties,
        isValueChild: Boolean
    ) {
        error("PropertiesSerializer serialization is not supported")
    }
}

internal object PropertiesMapSerializer : KSerializer<Map<String, String>> {

    private val propertiesSerializer = PropertiesSerializer

    override val descriptor: SerialDescriptor
        get() = propertiesSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: Map<String, String>
    ) {
        error("PropertiesMapSerializer serialization is not supported")
    }

    override fun deserialize(decoder: Decoder): Map<String, String> {
        return decoder
            .decodeSerializableValue(propertiesSerializer)
            .properties
            .associate { it.key to it.value }
    }
}
