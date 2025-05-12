**Caupain** is a tool to help you keep your Gradle versions catalogs up to date.  It is fast, simple
to use, and provides a simple report to help you know what dependencies you need to update.

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
  --cache-dir=<path>            Cache directory. This is not used if --no-cache
                                is set (default: user cache dir)
  --no--cache                   Disable HTTP cache
  -q, --quiet                   Suppress all output
  -v, --verbose                 Verbose output
  -d, --debug                   Debug output
  --debug-http-calls            Enable debugging for HTTP calls
  --version                     Print version and exit
  -h, --help                    Show this message and exit
```

For a base usage, just launch the `caupain` command in the root of your project, and it will find your
version catalog and generate a report in console.