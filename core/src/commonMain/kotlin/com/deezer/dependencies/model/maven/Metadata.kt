@file:UseSerializers(GradleDependencyVersionSerializer::class)

package com.deezer.dependencies.model.maven

import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.GradleDependencyVersionSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
data class Version(@XmlValue val version: GradleDependencyVersion)

@Serializable
@XmlSerialName("versioning")
data class Versioning(
    @XmlElement(true) val latest: GradleDependencyVersion? = null,
    @XmlElement(true) val release: GradleDependencyVersion? = null,
    @XmlChildrenName("version") val versions: List<Version> = emptyList()
)

@Serializable
@XmlSerialName("metadata")
data class Metadata(val versioning: Versioning)