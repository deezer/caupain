package com.deezer.dependencies.model.maven

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("project")
data class MavenInfo(
    @XmlElement(true) val name: String? = null,
    @XmlElement(true) val url: String? = null
)
