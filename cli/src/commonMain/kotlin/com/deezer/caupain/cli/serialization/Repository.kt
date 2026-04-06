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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable

object RepositorySerializer : TomlContentPolymorphicSerializer<Repository>(Repository::class) {

    override fun selectDeserializer(root: TomlElement): DeserializationStrategy<Repository> {
        return when (root) {
            is TomlLiteral -> TextRepositoryDeserializer

            is TomlTable -> if ("default" in root.keys) {
                Repository.Default.serializer()
            } else {
                Repository.Rich.serializer()
            }

            else ->
                throw SerializationException("Unsupported TOML element type: ${root::class.simpleName}")
        }
    }
}

private object TextRepositoryDeserializer : DeserializationStrategy<Repository.Default> {

    private val defaultRepositorySerializer = DefaultRepository.serializer()

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Repository.Default", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Repository.Default {
        return Repository.Default(decoder.decodeSerializableValue(defaultRepositorySerializer))
    }
}
