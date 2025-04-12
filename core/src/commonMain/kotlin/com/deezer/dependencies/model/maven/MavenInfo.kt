package com.deezer.dependencies.model.maven

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("project")
data class MavenInfo(
    @XmlElement(true) val name: String? = null,
    @XmlElement(true) val url: String? = null,
    @XmlChildrenName("dependency") val dependencies: List<Dependency> = emptyList(),
)

@Serializable
@XmlSerialName("dependency")
data class Dependency(
    @XmlElement(true) val groupId: String,
    @XmlElement(true) val artifactId: String,
    @XmlElement(true) val version: String,
)
