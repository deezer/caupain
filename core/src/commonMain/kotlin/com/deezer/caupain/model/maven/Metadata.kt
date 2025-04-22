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
@XmlSerialName("versioning")
internal data class Versioning(
    @XmlElement(true) val latest: GradleDependencyVersion? = null,
    @XmlElement(true) val release: GradleDependencyVersion? = null,
    @XmlChildrenName("version") val versions: List<Version> = emptyList()
)

@Serializable
@XmlSerialName("metadata")
internal data class Metadata(val versioning: Versioning)