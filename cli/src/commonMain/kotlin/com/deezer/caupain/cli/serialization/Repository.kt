/*
 * MIT License
 *
 * Copyright (c) 2025 Deezer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.deezer.caupain.cli.serialization

import com.deezer.caupain.cli.model.DefaultRepository
import com.deezer.caupain.cli.model.Repository
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlDecoder

@Suppress("UnnecessaryOptInAnnotation")
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
object RepositorySerializer : KSerializer<Repository> {

    private val richRepositorySerializer = Repository.Rich.serializer()

    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        serialName = "com.deezer.caupain.cli.model.Repository",
        kind = SerialKind.CONTEXTUAL,
    )

    override fun deserialize(decoder: Decoder): Repository {
        val tomlDecoder = decoder.asTomlDecoder()
        return when (val element = tomlDecoder.decodeTomlElement()) {
            is TomlLiteral -> Repository.Default(
                DefaultRepository
                    .entries
                    .firstOrNull { it.key == element.content }
                    ?: throw SerializationException("Unknown repository: ${element.content}")
            )

            is TomlTable -> (element["default"] as? TomlLiteral)
                ?.content
                ?.let { defaultRepoName ->
                    DefaultRepository
                        .entries
                        .firstOrNull { it.key == defaultRepoName }
                        ?.let { Repository.Default(it) }
                }
                ?: tomlDecoder
                    .toml
                    .decodeFromTomlElement(richRepositorySerializer, element)

            else ->
                throw SerializationException("Unsupported TOML element type: ${element::class.simpleName}")
        }
    }

    override fun serialize(encoder: Encoder, value: Repository) {
        when (value) {
            is Repository.Default -> encoder.encodeString(value.repository.key)
            is Repository.Rich -> encoder.encodeSerializableValue(richRepositorySerializer, value)
        }
    }
}