package com.deezer.dependencies.model.maven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
@SerialName("project")
internal data class MavenInfo(
    @XmlElement(true) val name: String? = null,
    @XmlElement(true) val url: String? = null,
    @XmlChildrenName("dependency") val dependencies: List<Dependency> = emptyList(),
)

@Serializable
@SerialName("dependency")
internal data class Dependency(
    @XmlElement(true) val groupId: String,
    @XmlElement(true) val artifactId: String,
    @XmlElement(true) val version: String? = null,
)
