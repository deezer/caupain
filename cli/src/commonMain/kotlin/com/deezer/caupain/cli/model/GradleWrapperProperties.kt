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
@file:UseSerializers(UrlSerializer::class)

package com.deezer.caupain.cli.model

import io.ktor.http.Url
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class GradleWrapperProperties(
    val distributionUrl: Url? = null,
    val distributionSha256Sum: String? = null,
    val distributionBase: String? = null,
    val distributionPath: String? = null,
    val zipStoreBase: String? = null,
    val zipStorePath: String? = null,
    val networkTimeout: Int? = null,
    val validateDistributionUrl: Boolean? = null,
    val retries: Int? = null,
    val retryBackOffMs: Int? = null,
)

val GRADLE_DISTRIBUTION_REGEX = Regex("""gradle-(.+)-[^-]+\.zip""")

private object UrlSerializer : KSerializer<Url> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("url", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Url) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Url = Url(decoder.decodeString())
}
