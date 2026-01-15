package com.deezer.caupain.serialization.xml

import com.deezer.caupain.model.maven.Dependency
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.maven.SCMInfos
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@SerialName("project")
private data class RawMavenInfo(
    @XmlElement val name: String? = null,
    @XmlElement val url: String? = null,
    @XmlChildrenName("dependency") val dependencies: List<Dependency> = emptyList(),
    @XmlElement @XmlSerialName("scm") val scm: SCMInfos? = null,
    @XmlElement
    @XmlSerialName("properties")
    @Serializable(PropertiesMapSerializer::class)
    val properties: Map<String, String> = emptyMap(),
) {
    constructor(mavenInfo: MavenInfo) : this(
        name = mavenInfo.name,
        url = mavenInfo.url,
        dependencies = mavenInfo.dependencies,
        scm = mavenInfo.scm,
    )

    fun resolved(): MavenInfo {
        return if (properties.isEmpty()) {
            return MavenInfo(
                name = name,
                url = url,
                dependencies = dependencies,
                scm = scm,
            )
        } else {
            MavenInfo(
                name = name?.resolve(properties),
                url = url?.resolve(properties),
                dependencies = dependencies.map { dependency ->
                    Dependency(
                        groupId = dependency.groupId.resolve(properties),
                        artifactId = dependency.artifactId.resolve(properties),
                        version = dependency.version?.resolve(properties),
                    )
                },
                scm = scm?.let { scmInfos ->
                    SCMInfos(
                        url = scmInfos.url?.resolve(properties)
                    )
                }
            )
        }
    }

    companion object {
        private fun String.resolve(properties: Map<String, String>): String {
            return PLACEHOLDER_REGEX.replace(this) { matchResult ->
                properties[matchResult.groupValues[1]] ?: matchResult.value
            }
        }

        private val PLACEHOLDER_REGEX = "\\$\\{(.+?)}".toRegex()
    }
}

internal object MavenInfoSerializer : KSerializer<MavenInfo> {

    private val rawSerializer = serializer<RawMavenInfo>()

    override val descriptor: SerialDescriptor
        get() = rawSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: MavenInfo
    ) {
        encoder.encodeSerializableValue(rawSerializer, RawMavenInfo(value))
    }

    override fun deserialize(decoder: Decoder): MavenInfo {
        return decoder.decodeSerializableValue(rawSerializer).resolved()
    }
}
