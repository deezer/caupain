# Caupain GitHub Action

Check for updates of the dependencies declared in your Gradle
[version catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html) from a GitHub
workflow, with a report in the summary of the job.

The action doesn't need Java or Gradle on the runner, so it starts fast and works on any
[GitHub-hosted runner](https://docs.github.com/en/actions/concepts/runners/github-hosted-runners)
(Linux, macOS and Windows).

## Usage

```yaml
name: Dependency updates

on:
  schedule:
    - cron: '0 0 * * 1' # Every Monday
  workflow_dispatch:

jobs:
  check-updates:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v7
      - uses: deezer/caupain/github-action@v1
```

By default, the action checks `gradle/libs.versions.toml`, prints the updates in the log, and adds
them to the summary of the job.

### Using the results

The action doesn't change your files. Use its [outputs](#outputs) to decide what to do with the
result, for example to upload a report, or to fail the job:

```yaml
      - uses: deezer/caupain/github-action@v1
        id: caupain
        with:
          output-types: |
            console
            html
      - uses: actions/upload-artifact@v7
        if: steps.caupain.outputs.has-updates == 'true'
        with:
          name: dependency-updates
          path: ${{ steps.caupain.outputs.report-paths }}
```

To fail the step when updates are available, for example in a pull request check, use
`fail-on-updates`:

```yaml
      - uses: deezer/caupain/github-action@v1
        with:
          fail-on-updates: true
```

### Multiple version catalogs

```yaml
      - uses: deezer/caupain/github-action@v1
        with:
          version-catalog: |
            gradle/libs.versions.toml
            build-logic/gradle/libs.versions.toml
```

### Release notes

Caupain can add links to the release notes of the updated dependencies when they are hosted on
GitHub. The token of the workflow is used by default, so you only need to enable the search:

```yaml
      - uses: deezer/caupain/github-action@v1
        with:
          search-release-notes: true
```

## Configuration

The action can be configured with its inputs, and with a `caupain.toml`
[configuration file](../cli/README.md#configuration-file) at the root of your repository, which is
used by the action if it exists. The file is the same as for the command line tool, which also
documents everything it can do: repositories, filters, policies, etc.

Inputs take precedence over the configuration file, which takes precedence over the default
values. Boolean inputs can only enable an option: setting one to `false` doesn't override a `true`
value from the configuration file.

## Inputs

All inputs are optional.

| Input                       | Description                                                                                                                    | Default                                      |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| `config-file`               | Path of the configuration file. If set, the file must exist.                                                                   | `caupain.toml`, if it exists                 |
| `version-catalog`           | Path of the version catalog, relative to the workspace. One path per line to check multiple version catalogs.                  | `gradle/libs.versions.toml`                  |
| `excluded`                  | Keys of the version catalog to exclude, one per line.                                                                          |                                              |
| `policy`                    | Update policy. Multiple policies can be set as a comma-separated list, and a version must satisfy all of them to be accepted.  | `stability-level`                            |
| `gradle-stability-level`    | Stability level of the Gradle version to update to: `stable`, `rc`, `milestone`, `release-nightly` or `nightly`.               | `stable`                                     |
| `gradle-wrapper-properties` | Path of the Gradle wrapper properties, used to check for a Gradle update.                                                      | `gradle/wrapper/gradle-wrapper.properties`   |
| `search-release-notes`      | Search for the release notes of the updated versions on GitHub.                                                                | `false`                                      |
| `github-token`              | GitHub token used to search release notes.                                                                                     | The token of the workflow                    |
| `verify-existence`          | Verify that the `.pom` file exists before accepting a version update. This can slow down the check.                            | `false`                                      |
| `cache-dir`                 | Directory of the HTTP cache.                                                                                                   | `caupain-cache` in the runner temp directory |
| `no-cache`                  | Disable the HTTP cache.                                                                                                        | `false`                                      |
| `clean-cache`               | Clean the HTTP cache before checking.                                                                                          | `false`                                      |
| `output-types`              | Reports to generate, one per line: `console`, `html`, `markdown` or `json`. Reports other than `console` are written to files. | `console`                                    |
| `output-dir`                | Directory the report files are written to.                                                                                     | `build/reports`                              |
| `output-base-name`          | Base name of the report files, without extension.                                                                              | `dependencies-update`                        |
| `show-version-references`   | Show a summary of the updates of version references in the reports.                                                            | `false`                                      |
| `job-summary`               | Write the updates to the summary of the job.                                                                                   | `true`                                       |
| `fail-on-updates`           | Fail the step if updates are available.                                                                                        | `false`                                      |
| `version`                   | Version of Caupain to use, for example `1.2.3` or `1`. See [Versions](#versions).                                              | The version the action is used with          |

Invalid values for boolean inputs are ignored, as if the input wasn't set. Invalid values for
other inputs, such as an unknown output type, fail the step with an explanation.

## Outputs

| Output          | Description                                                                         |
|-----------------|-------------------------------------------------------------------------------------|
| `has-updates`   | `true` if updates are available, `false` otherwise.                                 |
| `updates-count` | Number of available updates, including the one of Gradle.                           |
| `report-paths`  | Paths of the generated report files, one per line. Empty if only `console` is used. |

## Versions

The action downloads the matching version of Caupain from the
[releases](https://github.com/deezer/caupain/releases) of this repository when it runs, and
verifies its checksum. The version is the one you use the action with:

- `deezer/caupain/github-action@v1` uses the latest `v1.x.y` release, so you get compatible
updates automatically.
- `deezer/caupain/github-action@v1.2.3` uses exactly this release.

If you prefer to pin the action to a commit, the version can't be deduced from it, so you also
have to set the `version` input:

```yaml
      - uses: deezer/caupain/github-action@3c9ab81f2d7e5a6b4c1d0e9f8a7b6c5d4e3f2a1b
        with:
          version: 1.2.3
```

## Differences with the command line tool

- Policy plugins are not supported, and are ignored with a warning.
- There is no in-place replacement of the versions in the version catalog.
- The check for a Caupain update is disabled.

## Requirements

On GitHub-hosted runners, there is nothing to install. On self-hosted runners, the action needs
bash, `curl`, `tar`, `node` and a SHA-256 tool (`sha256sum` or `shasum`), and the access to
`github.com` and `api.github.com`.

## Troubleshooting

- **`'...' is not a Caupain release version`**: the action was used with a branch or a commit.
Use a version tag, or set the `version` input.
- **`Unable to download ...`**: the release doesn't contain the action bundle, which is the case
for versions released before the action existed.
- **`Configuration file ... does not exist`**: the file set in `config-file` was not found. Paths
are relative to the workspace, so don't forget the checkout step.

## Development

The action is a Kotlin/JS module, which is packaged and attached to each release.

```shell
# Build the archive and its checksum in github-action/build/distributions
./gradlew :github-action:assembleAction

# Run the tests of the module
./gradlew :github-action:jsNodeTest

# Test the installation script against a fake GitHub
github-action/scripts/test-install.sh github-action/build/distributions
```

To try a local build in a workflow, extract the archive and use the `bundle-path` input, which
skips the download. This is what the checks of this repository do:

```yaml
      - uses: ./github-action
        with:
          bundle-path: github-action/build/bundle
```
