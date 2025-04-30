package com.deezer.caupain.cli.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.findPolymorphicSerializer
import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlinx.serialization.internal.NamedValueDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import okio.FileSystem
import okio.Path

fun parsePropertiesFile(fileSystem: FileSystem, path: Path): Map<String, String> {
    val map = mutableMapOf<String, String>()
    fileSystem.read(path) {
        var line = readUtf8Line()
        while (line != null) {
            if (line.isNotBlank() && !line.startsWith("#")) {
                val index = line.indexOf('=')
                if (index != -1) {
                    val key = line.substring(0, index).trim()
                    val value = line.substring(index + 1).trim()
                    map[key.replace("\\", "")] = value.replace("\\", "")
                }
            }
            line = readUtf8Line()
        }
    }
    return map
}

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Suppress("TooManyFunctions", "UnnecessaryOptInAnnotation") // Needs all implementations
private class StringMapDecoder(
    private val map: Map<String, String>,
    descriptor: SerialDescriptor,
    override val serializersModule: SerializersModule,
) : NamedValueDecoder() {

    private var currentIndex = 0
    private val isCollection =
        descriptor.kind == StructureKind.LIST || descriptor.kind == StructureKind.MAP
    private val size = if (isCollection) Int.MAX_VALUE else descriptor.elementsCount

    private fun structure(descriptor: SerialDescriptor): StringMapDecoder = StringMapDecoder(
        map = map,
        descriptor = descriptor,
        serializersModule = serializersModule
    )

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return structure(descriptor).also { copyTagsTo(it) }
    }

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        if (deserializer is AbstractPolymorphicSerializer<*>) {
            val type = map[nested("type")]
            val actualSerializer: DeserializationStrategy<Any> =
                deserializer.findPolymorphicSerializer(this, type)

            @Suppress("UNCHECKED_CAST")
            return actualSerializer.deserialize(this) as T
        }

        return deserializer.deserialize(this)
    }

    override fun decodeTaggedValue(tag: String): String = map.getValue(tag)

    override fun decodeTaggedEnum(tag: String, enumDescriptor: SerialDescriptor): Int {
        val taggedValue = map.getValue(tag)
        return enumDescriptor.getElementIndex(taggedValue)
            .also { if (it == CompositeDecoder.UNKNOWN_NAME) throw SerializationException("Enum '${enumDescriptor.serialName}' does not contain element with name '$taggedValue'") }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (currentIndex < size) {
            val name = descriptor.getTag(currentIndex++)
            if (map.keys.any {
                    it.startsWith(name) && (it.length == name.length || it[name.length] == '.')
                }) return currentIndex - 1
            if (isCollection) {
                // if map does not contain key we look for, then indices in collection have ended
                break
            }
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeTaggedBoolean(tag: String): Boolean = decodeTaggedValue(tag).toBoolean()
    override fun decodeTaggedByte(tag: String): Byte = decodeTaggedValue(tag).toByte()
    override fun decodeTaggedShort(tag: String): Short = decodeTaggedValue(tag).toShort()
    override fun decodeTaggedInt(tag: String): Int = decodeTaggedValue(tag).toInt()
    override fun decodeTaggedLong(tag: String): Long = decodeTaggedValue(tag).toLong()
    override fun decodeTaggedFloat(tag: String): Float = decodeTaggedValue(tag).toFloat()
    override fun decodeTaggedDouble(tag: String): Double = decodeTaggedValue(tag).toDouble()
    override fun decodeTaggedChar(tag: String): Char = decodeTaggedValue(tag).single()
}

fun <T> decodeFromProperties(
    fileSystem: FileSystem,
    path: Path,
    deserializer: DeserializationStrategy<T>,
): T {
    val map = parsePropertiesFile(fileSystem, path)
    val decoder = StringMapDecoder(
        map = map,
        descriptor = deserializer.descriptor,
        serializersModule = EmptySerializersModule()
    )
    return deserializer.deserialize(decoder)
}

inline fun <reified T> decodeFromProperties(
    fileSystem: FileSystem,
    path: Path
): T = decodeFromProperties(fileSystem, path, EmptySerializersModule().serializer())
