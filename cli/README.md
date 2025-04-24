# Caupain Command Line Interface

This is the command-line version of the tool. It is available as a single executable file for Linux,
MacOS (Intel and Silicon), Windows, and as a JAR file alongside its launch script. You can download 
the latest version from the [releases page](https://github.com/bishiboosh/caupain/releases).

## Usage

```
Usage: caupain [<options>]

Options:
  -i, --version-catalog=<path>  Version catalog path (default:
                                gradle/libs.versions.toml)
  --gradle-wrapper-properties=<path>
                                Gradle wrapper properties path (default:
                                gradle/wrapper/gradle-wrapper.properties)
  -e, --excluded=<text>         Excluded keys
  -c, --config=<path>           Configuration file (default: caupain.toml)
  --policy-plugin-dir=<path>    Custom policies plugin dir
  -p, --policy=<text>           Update policy
  -t, --output-type=(console|html|markdown)
                                Output type (default: console)
  -o, --output=<path>           Report output path
  --cache-dir=<path>            Cache directory
  -q, --quiet                   Suppress all output
  -v, --verbose                 Verbose output
  -d, --debug                   Debug output
  --debug-http-calls            Enable debugging for HTTP calls
  -h, --help                    Show this message and exit
```

For a base usage, just launch the `caupain` command in the root of your project, and it will find your
version catalog and generate a report in console.

Most of the options correspond to options in the configuration file, see the
[configuration file section](#configuration-file) for more information.

## Configuration

You can configure the tool either via a configuration file in TOML format or via command line options.
If present, configuration in the file will override command line options.

### Configuration file

```toml
# Configures the Maven repositories where the dependencies are hosted.
# If none are specified, the default repositories "google" and "mavenCentral" are used.
# Repositories are queried in the order they are specified.
repositories = [ 
    "mavenCentral", # Default repositories "google", "mavenCentral" and "gradlePluginPortal" are provided as shortcuts
    { url = "http://www.your.repo/maven" }, # You can also specify a custom repository
    { url = "http://www.your.secure.repo/maven", user = "my-user", password = "my-password" } # You can also specify credentials
]
# Configures the Maven repositories where the plugins are hosted.
# If none are specified, the default repositories "gradlePluginPortal", "google" and "mavenCentral" are used.
# Repositories are queried in the order they are specified.
# Syntax is the same as for the "repositories" key.
pluginRepositories = [ "google" ]
# Version catalog path. Default is "gradle/libs.versions.toml".
versionCatalogPath = "another/path/to/libs.versions.toml"
# Excluded dependencies. These will not be checked for updates.
# You can exclude by key. The key matches the name used in the version catalog.
excludedKeys = [ "excluded1", "excluded2" ]
# You can also exclude by group and optionally by name. If name is not specified, all dependencies in the group are excluded.
excludedLibraries = [
    { group = "com.google.guava" }, # Exclude all dependencies in the group
    { group = "com.google.guava", name = "guava" } # Exclude only the guava dependency
]
# You can exclude plugins by their id
excludedPlugins = [
    { id = "excluded.plugin.id" }
]
# Policy name to use. See the documentation section about policies for more information
policy = "androidx-version-level"
# Policy plugin directory. This is the directory where the custom policies are located. 
# Only applies to JVM, see the documentation section about policies for more information
policyPluginDir = "path/to/policy/plugin/dir"
# Cache directory. This is the directory where the HTTP cache is stored, if specified.
cacheDir = "path/to/cache/dir"
# Output type. Can be "console", "html" or "markdown". Default is "console".
outputType = "html"
# Output path, for HTML and Markdown output types. Default is build/reports/dependencies-update.(html|md)
outputPath = "path/to/output/file.html"
# Path to the Gradle wrapper properties file. This is used to check for an update in the Gradle wrapper. 
# Default is "gradle/wrapper/gradle-wrapper.properties".
gradleWrapperPropertiesPath = "/path/to/properties/file"
# By default, Caupain only checks updates for static versions in the version catalog (versions like 1.0.0 or 1.0.0-SNAPSHOT).
# The rationale behind this is that dynamic versions or rich versions indicate that the version range is
# already specified, and that an update isn't needed. If this value is passed to false, Caupain will try
# to find the latest version for all dependencies, even if they are dynamic or rich versions.
# Default is true.
onlyCheckStaticVersions = false
```

### Policies

Policies are used to filter available updates. You can either use a predefined policy or create your own.

#### Predefined policies

There's only one predefined policy at the moment, which is `androidx-version-level`. This policy will
only select an update if its stability is greater than or equal to the stability of the current version.

This means that if you're using a beta version, alpha versions won't be suggested for you, and so on.

#### Custom policies

Custom policies are only usable if you launch the tool via the JVM launch script.

You'll also need to use the `core` dependency in order to create this policy. See the [core module](../core/README.md) 
for more information.

You can write your custom policy by extending the [Policy](../core/src/commonMain/kotlin/com/deezer/caupain/model/Policy.kt) 
interface and implementing the `select` method. You can take an example from `AndroidXVersionLevelPolicy` 
in the same file if needed. You'll then need to make a JAR file of your custom policy, with a `META-INF/services/com.deezer.caupain.model.Policy` 
file containing the fully qualified name of your class, and place the JAR file in your `policyPluginDir` directory.

If this is a bit too difficult to do (and let's be honest, it is), custom policy use is way easier 
when using the [Gradle plugin version](../gradle-plugin/README.md) of the tool.

## Troubleshooting

If you need to see what's going on and the detailed queries sent and received from the Maven repositories,
you can enable HTTP call debugging by using the `--debug-http-calls` option along the `--debug` one.
This will print all HTTP calls to the output stream and allow you to see what's going on.