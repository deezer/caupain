# Caupain Command Line Interface

[![Homebrew](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2Fdeezer%2Fhomebrew-repo%2Frefs%2Fheads%2Fmain%2FInfo%2Fcaupain.json&query=%24.versions.stable&label=homebrew)](https://github.com/deezer/homebrew-repo)
[![Debian](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fresearch.deezer.com%2Fdebian-repo%2Fdists%2Fstable%2Fmain%2Fbinary-amd64%2FPackages&search=%5EVersion%3A%20(.*)%24&replace=%241&flags=m&label=debian)](https://github.com/deezer/debian-repo)

This is the command-line version of the tool. It is available as a single executable file for Linux,
MacOS (Intel and Silicon), Windows, and as a JAR file alongside its launch script.

## Installation

### macOS

You can install Caupain via Homebrew by running the following command:
```bash
brew install deezer/repo/caupain
```

### Linux

For Debian-based distributions, you'll first need to add the repository to your system:
```bash
sudo mkdir -p /usr/local/share/keyrings
sudo curl -sfLo /usr/local/share/keyrings/deezer.gpg https://research.deezer.com/debian-repo/gpg.key
echo "deb [signed-by=/usr/share/local/keyrings/deezer.gpg] https://research.deezer.com/debian-repo/ stable main" | sudo tee -a /etc/apt/sources.list.d/deezer.list
sudo apt update
```

You can then install Caupain with the following command:
```bash
sudo apt install caupain
```

### Other

You can also download the latest release from the [releases page](https://github.com/deezer/caupain/releases)
or build it yourself by cloning the repository and running `./gradlew :cli:buildCurrentArchBinary` in the root directory.

## Usage

```
Usage: caupain [<options>]

Options:
  -i, --version-catalog=<path>  Version catalog path. Use multiple times to use
                                multiple version catalogs
  --gradle-wrapper-properties=<path>
                                Gradle wrapper properties path (default:
                                gradle/wrapper/gradle-wrapper.properties)
  -e, --excluded=<text>         Excluded keys
  -c, --config=<path>           Configuration file (default: caupain.toml)
  --policy-plugin-dir=<path>    Custom policies plugin dir
  -p, --policy=<text>           Update policy
  --list-policies               List available policies
  -t, --output-type=(console|html|markdown|json)
                                Output type (default: console)
  -o, --output=<path>           Report output path
  --show-version-references     Show versions references update summary in the
                                report
  --cache-dir=<path>            Cache directory. This is not used if --no-cache
                                is set (default: user cache dir)
  --no--cache                   Disable HTTP cache
  -q, --quiet                   Suppress all output
  -v, --verbose                 Verbose output
  -d, --debug                   Debug output
  --debug-http-calls            Enable debugging for HTTP calls
  --version                     Show the version and exit
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
    { url = "http://www.your.secure.repo/maven", user = "my-user", password = "my-password" }, # You can also specify password credentials
    { url = "http://www.your.secure.repo/maven", authHeaderName = "my-header-name", authHeaderValue = "my-header-value" } # Or header credentials
]
# Configures the Maven repositories where the plugins are hosted.
# If none are specified, the default repositories "gradlePluginPortal", "google" and "mavenCentral" are used.
# Repositories are queried in the order they are specified.
# Syntax is the same as for the "repositories" key.
pluginRepositories = [ "google" ]
# Version catalog path. Default is "gradle/libs.versions.toml".
versionCatalogPath = "another/path/to/libs.versions.toml"
# You can also define multiple version catalogs. Warning: defining this will override the previous single path
versionCatalogPaths = [ "libs.versions.toml", "another/path/to/libs.versions.toml" ]
# Excluded dependencies. These will not be checked for updates.
# You can exclude by key. The key matches the name used in the version catalog.
excludedKeys = [ "excluded1", "excluded2" ]
# You can also exclude by group and optionally by name. If name is not specified, all dependencies in the group
# are excluded. Furthermore, if name is not specified, then group is interpreted as a glob.
excludedLibraries = [
    { group = "com.google.guava" }, # Exclude all dependencies in the group
    { group = "com.google.guava", name = "guava" }, # Exclude only the guava dependency
    { group = "com.google.**" }, # Exclude all dependencies with group starting with com.google
    { group = "com.google.*.sub" }, # Exclude all dependencies like com.google.something.sub
]
# You can exclude plugins by their id
excludedPlugins = [
    { id = "excluded.plugin.id" }
]
# Policy name to use. See the documentation section about policies for more information
policy = "stability-level"
# Policy plugin directory. This is the directory where the custom policies are located. 
# Only applies to JVM, see the documentation section about policies for more information
policyPluginDir = "path/to/policy/plugin/dir"
# Cache directory. This is the directory where the HTTP cache is stored. Defaults to the user cache
# directory.
cacheDir = "path/to/cache/dir"
# Output type. Can be "console", "html", "markdown" or "json". Default is "console".
outputType = "html"
# Output path, for HTML, Markdown and JSON output types. Default is build/reports/dependencies-update.(html|md|json)
outputPath = "path/to/output/file.html"
# Whether or not to show a block in the report that summarizes the updates from the version block in
# the version catalog. This allows to quickly see what to update if you use the versions block heavily.
# Default is false.
showVersionsReferences = true
# Path to the Gradle wrapper properties file. This is used to check for an update in the Gradle wrapper. 
# Default is "gradle/wrapper/gradle-wrapper.properties".
gradleWrapperPropertiesPath = "/path/to/properties/file"
# By default, Caupain only checks updates for static versions in the version catalog (versions like 1.0.0 or 
# 1.0.0-SNAPSHOT).
# The rationale behind this is that dynamic versions or rich versions indicate that the version range is
# already specified, and that an update isn't needed. If this value is passed to false, Caupain will try
# to find the latest version for all dependencies, even if they are dynamic or rich versions.
# This is where the result may not be exactly what you want, because Caupain will not use Gradle to 
# try to resolve the exact dependency that is used.
# Default is true.
onlyCheckStaticVersions = false
```

#### Repository component filtering

By default, Caupain will search for dependencies in all repositories, going through them in order until
the dependency is found. If you want to specify what kind of dependencies are in each repository, you
can set up component filtering by defining repositories using a [TOML array of tables](https://toml.io/en/v1.0.0#array-of-tables)
like so:
```toml
#... The rest of your configuration
# Repository block in array of tables needs to be at the very end of the configuration file to be
# parsed correctly
[[ repositories ]]
# If the repository is a predefined one, you can use the shortcut name with the key "default"
default = "mavenCentral"
# If only exclusions are defined, then all components except those excluded will be searched
# for in the repository. If only inclusions are defined, then only those components will be searched
# for in the repository. If both are defined, then only the components that are included and not excluded
# will be searched for in the repository.
includes = [
    { group = "com.example", name = "example-lib" }, # You can specify a group and name
    { group = "com.example" }, # You can also only specify a group...
    { group = "com.example2.**" } # ...or use globs
]
excludes = [
    { group = "com.other" }
]
[[ repositories ]]
# This can also be done for custom repositories
url = "http://www.example.com/repo"
# The credentials can be specified in the same way as for the repositories key
user = "my-user"
password = "my-password"
authHeaderName = "my-header-name"
authHeaderValue = "my-header-value"
includes = [
    { group = "com.example", name = "example-lib" },
    { group = "com.example2.**" }
]
excludes = [
    { group = "com.other" }
]
```

### Exclusions

Alongside the exclusion configuration, you can also exclude dependencies directly in the TOML file by
adding an inline comment `#ignoreUpdates` on the same line as a version reference or dependency/plugin 
definition, like so:
```toml
[versions]
my-excluded-version-ref = "1.0.0" #ignoreUpdates
my-regular-ref = "1.0.0"
[libraries]
my-excluded-lib = { module = "com.example:example", version.ref = "my-excluded-version-ref" } #ignoreUpdates
my-regular-lib = { module = "com.example:example", version.ref = "my-regular-ref" }
[plugins]
my-excluded-plugin = { id = "com.example.plugin", version.ref = "my-excluded-version-ref" } #ignoreUpdates
my-regular-plugin = { id = "com.example.plugin", version.ref = "my-regular-ref" }
```

### Policies

Policies are used to filter available updates. You can either use a predefined policy or create your own.

#### Predefined policies

There's only one predefined policy at the moment, which is `stability-level`. This policy will
only select an update if its stability is greater than or equal to the stability of the current version.

This means that if you're using a beta version, alpha versions won't be suggested for you, and so on.

#### Custom policies

Custom policies are only usable if you launch the tool via the JVM launch script.

You'll also need to use the `core` dependency in order to create this policy. See the [core module](../core/README.md) 
for more information.

You can write your custom policy by extending the [Policy](../core/src/commonMain/kotlin/com/deezer/caupain/model/Policy.kt) 
interface and implementing the `select` method. You can take an example from `StabilityLevelPolicy` 
in the same file if needed. You'll then need to make a JAR file of your custom policy, with a `META-INF/services/com.deezer.caupain.model.Policy` 
file containing the fully qualified name of your class, and place the JAR file in your `policyPluginDir` directory.

As sample can be found in the [sample plugin module](../sample-plugin-policy-jar) of the project.

If this is a bit too difficult to do, custom policy use is way easier when using the [Gradle plugin version](../gradle-plugin/README.md)
of the tool.

## Completions

Shell completions can be found in the [completions](completions) directory. You can source them in
your shell to enable completions for the `caupain` command.

## Build locally

You can build binaries locally by using `./gradlew :cli:assembleAll`

## Troubleshooting

If you need to see what's going on and the detailed queries sent and received from the Maven repositories,
you can enable HTTP call debugging by using the `--debug-http-calls` option along the `--debug` one.
This will print all HTTP calls to the output stream and allow you to see what's going on.
