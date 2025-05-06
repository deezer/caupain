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

package com.deezer.caupain.model.maven

import com.deezer.caupain.model.GradleDependencyVersion
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
internal data class Version(@XmlValue val version: GradleDependencyVersion)

@Serializable
internal data class SnapshotVersion(
    @XmlElement(true) val extension: String,
    @XmlElement(true) val value: GradleDependencyVersion
)

@Serializable
@XmlSerialName("versioning")
internal data class Versioning(
    @XmlElement(true) val latest: GradleDependencyVersion? = null,
    @XmlElement(true) val release: GradleDependencyVersion? = null,
    @XmlChildrenName("version") val versions: List<Version> = emptyList(),
    @XmlChildrenName("snapshotVersion") val snapshotVersions: List<SnapshotVersion> = emptyList()
)

@Serializable
@XmlSerialName("metadata")
internal data class Metadata(val versioning: Versioning)