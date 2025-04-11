package com.deezer.dependencies

import com.deezer.dependencies.model.DefaultRepositories
import com.deezer.dependencies.model.Repository
import com.deezer.dependencies.model.UpdateInfo
import com.deezer.dependencies.serialization.DefaultToml
import com.deezer.dependencies.serialization.DefaultXml
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.xml.xml
import net.peanuuutz.tomlkt.Toml
import nl.adaptivity.xmlutil.serialization.XML

class DependencyUpdateChecker(
    private val xml: XML = DefaultXml,
    private val toml: Toml = DefaultToml,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            xml(xml)
        }
    },
    private val repositories: List<Repository> = listOf(
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    private val pluginRepositories: List<Repository> = listOf(
        DefaultRepositories.gradlePlugins,
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    )
) {
    fun checkForUpdates(): List<UpdateInfo> {

    }
}