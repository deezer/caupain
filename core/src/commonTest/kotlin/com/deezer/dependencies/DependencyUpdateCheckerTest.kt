package com.deezer.dependencies

import com.deezer.dependencies.model.Configuration
import com.deezer.dependencies.model.Dependency
import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.LibraryExclusion
import com.deezer.dependencies.model.Logger
import com.deezer.dependencies.model.Repository
import com.deezer.dependencies.model.UpdateInfo
import com.deezer.dependencies.model.maven.MavenInfo
import com.deezer.dependencies.model.maven.Metadata
import com.deezer.dependencies.model.maven.Versioning
import com.deezer.dependencies.model.versionCatalog.Version
import com.deezer.dependencies.model.versionCatalog.VersionCatalog
import com.deezer.dependencies.serialization.DefaultXml
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.xml.xml
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.deezer.dependencies.model.maven.Dependency as MavenDependency
import com.deezer.dependencies.model.maven.Version as MavenVersion

@OptIn(ExperimentalCoroutinesApi::class)
class DependencyUpdateCheckerTest {

    private lateinit var engine: MockEngine

    private val fileSystem = FakeFileSystem()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var checker: DependencyUpdateChecker

    @BeforeTest
    fun setup() {
        val configuration = Configuration(
            repositories = listOf(SIGNED_REPOSITORY, BASE_REPOSITORY),
            pluginRepositories = listOf(BASE_REPOSITORY, SIGNED_REPOSITORY),
            excludedKeys = setOf("groovy-json"),
            excludedLibraries = listOf(LibraryExclusion(group = "org.apache.commons"))
        )
        fileSystem.createDirectories(configuration.versionCatalogPath.parent!!)
        fileSystem.write(configuration.versionCatalogPath) {}
        engine = MockEngine { requestData ->
            handleRequest(this, requestData)
                ?: respond("Not found", HttpStatusCode.NotFound)
        }
        checker = DependencyUpdateChecker(
            configuration = configuration,
            fileSystem = fileSystem,
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    xml(DefaultXml)
                }
            },
            ioDispatcher = testDispatcher,
            versionCatalogParser = FixedVersionCatalogParser,
            policies = emptyMap(),
            logger = Logger.EMPTY,
            progressListener = DependencyUpdateChecker.ProgressListener.EMPTY
        )
    }

    private fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        val url = requestData.url
        // Check authentification
        if (url.host == SIGNED_URL.host) {
            val authHeader = requestData.headers[HttpHeaders.Authorization]
            if (authHeader != AUTHORIZATION) {
                return scope.respond("Unauthorized", HttpStatusCode.Unauthorized)
            }
        }
        return when (url) {
            GROOVY_CORE_METADATA_URL -> scope.respondElement(GROOVY_CORE_METADATA)
            GROOVY_CORE_INFO_URL -> scope.respondElement(GROOVY_CORE_INFO)
            GROOVY_NIO_METADATA_URL -> scope.respondElement(GROOVY_NIO_METADATA)
            VERSIONS_METADATA_URL -> scope.respondElement(VERSIONS_METADATA)
            VERSIONS_INFO_URL -> scope.respondElement(VERSIONS_INFO)
            RESOLVED_VERSIONS_INFO_URL -> scope.respondElement(RESOLVED_VERSIONS_INFO)
            else -> null
        }
    }

    @AfterTest
    fun teardown() {
        engine.close()
    }

    @Test
    fun testUpdate() = runTest(testDispatcher) {
        assertEquals(
            expected = listOf(
                UpdateInfo(
                    dependency = "groovy-core",
                    dependencyId = "org.codehaus.groovy:groovy",
                    type = UpdateInfo.Type.LIBRARY,
                    name = "Groovy core",
                    url = "https://groovy-lang.org/",
                    updatedVersion = "3.0.6"
                ),
                UpdateInfo(
                    dependency = "versions",
                    dependencyId = "com.github.ben-manes.versions",
                    type = UpdateInfo.Type.PLUGIN,
                    name = "Resolved plugin",
                    url = "http://www.example.com/resolved",
                    updatedVersion = "1.0.0"
                )
            ),
            actual = checker.checkForUpdates()
        )
    }
}

private inline fun <reified T> MockRequestHandleScope.respondElement(element: T): HttpResponseData {
    return respond(
        content = DefaultXml.encodeToString(element),
        headers = headersOf(HttpHeaders.ContentType, "application/xml")
    )
}

private val BASE_URL = Url("http://www.example.com")
private val BASE_REPOSITORY = Repository(BASE_URL.toString())

private const val USER = "user"
private const val PASSWORD = "password"
private const val AUTHORIZATION = "Basic dXNlcjpwYXNzd29yZA=="

private val SIGNED_URL = Url("http://www.example.fr")

private val SIGNED_REPOSITORY = Repository(
    url = SIGNED_URL.toString(),
    user = USER,
    password = PASSWORD
)

private object FixedVersionCatalogParser : VersionCatalogParser {
    override suspend fun parseDependencyInfo(): VersionCatalog = VERSION_CATALOG
}

private fun metadata(
    latest: String,
    versions: List<String>
) = Metadata(
    versioning = Versioning(
        latest = GradleDependencyVersion(latest),
        versions = versions.map { MavenVersion(GradleDependencyVersion(it)) }
    )
)

private fun info(
    name: String?,
    url: String?,
    dependency: Pair<String, String>? = null
) = MavenInfo(
    name = name,
    url = url,
    dependencies = listOfNotNull(
        dependency
            ?.let { (group, artifact) ->
                MavenDependency(
                    groupId = group,
                    artifactId = artifact,
                    version = "1.0"
                )
            }
    )
)

private val GROOVY_CORE_METADATA = metadata(
    latest = "3.0.6",
    versions = listOf("3.0.5", "3.0.5-alpha-1", "3.0.6")
)
private val GROOVY_CORE_METADATA_URL = URLBuilder()
    .takeFrom(BASE_URL)
    .appendPathSegments("org", "codehaus", "groovy", "groovy", "maven-metadata.xml")
    .build()

private val GROOVY_CORE_INFO = info(
    name = "Groovy core",
    url = "https://groovy-lang.org/"
)
private val GROOVY_CORE_INFO_URL = URLBuilder()
    .takeFrom(BASE_URL)
    .appendPathSegments("org", "codehaus", "groovy", "groovy", "3.0.6", "groovy-3.0.6.pom")
    .build()

private val GROOVY_NIO_METADATA = metadata(
    latest = "3.0.5-alpha-1",
    versions = listOf("3.0.5", "3.0.5-alpha-1")
)
private val GROOVY_NIO_METADATA_URL = URLBuilder()
    .takeFrom(BASE_URL)
    .appendPathSegments("org", "codehaus", "groovy", "groovy-nio", "maven-metadata.xml")
    .build()

private val VERSIONS_METADATA = metadata(
    latest = "1.0.0",
    versions = listOf("0.45.0-SNAPSHOT", "1.0.0")
)
private val VERSIONS_METADATA_URL = URLBuilder()
    .takeFrom(SIGNED_URL)
    .appendPathSegments(
        "com",
        "github",
        "ben-manes",
        "versions",
        "com.github.ben-manes.versions.gradle.plugin",
        "maven-metadata.xml"
    )
    .build()

private val VERSIONS_INFO = info(
    name = null,
    url = null,
    dependency = "resolved" to "plugin"
)
private val VERSIONS_INFO_URL = URLBuilder()
    .takeFrom(SIGNED_URL)
    .appendPathSegments(
        "com",
        "github",
        "ben-manes",
        "versions",
        "com.github.ben-manes.versions.gradle.plugin",
        "1.0.0",
        "com.github.ben-manes.versions.gradle.plugin-1.0.0.pom"
    )
    .build()
private val RESOLVED_VERSIONS_INFO = info(
    name = "Resolved plugin",
    url = "http://www.example.com/resolved",
)
private val RESOLVED_VERSIONS_INFO_URL = URLBuilder()
    .takeFrom(SIGNED_URL)
    .appendPathSegments(
        "resolved",
        "plugin",
        "1.0",
        "plugin-1.0.pom"
    )
    .build()

private val VERSION_CATALOG = VersionCatalog(
    versions = mapOf(
        "groovy" to Version.Simple(GradleDependencyVersion.Exact("3.0.5-alpha-1")),
        "checkstyle" to Version.Simple(GradleDependencyVersion.Exact("8.37"))
    ),
    libraries = mapOf(
        "groovy-core" to Dependency.Library(
            module = "org.codehaus.groovy:groovy",
            version = Version.Reference("groovy")
        ),
        "groovy-json" to Dependency.Library(
            module = "org.codehaus.groovy:groovy-json",
            version = Version.Reference("groovy")
        ),
        "groovy-nio" to Dependency.Library(
            module = "org.codehaus.groovy:groovy-nio",
            version = Version.Reference("groovy")
        ),
        "commons-lang3" to Dependency.Library(
            group = "org.apache.commons",
            name = "commons-lang3",
            version = Version.Rich(
                strictly = GradleDependencyVersion.Range("[3.8, 4.0["),
                prefer = GradleDependencyVersion.Exact("3.9")
            )
        )
    ),
    plugins = mapOf(
        "versions" to Dependency.Plugin(
            id = "com.github.ben-manes.versions",
            version = Version.Simple(GradleDependencyVersion.Snapshot("0.45.0-SNAPSHOT"))
        )
    )
)