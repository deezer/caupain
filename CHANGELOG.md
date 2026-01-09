# Changelog

## Unreleased

### Added
- Possibility to output formatted results directly to standard output in CLI (#72)

### Changed
- Removed base formatter class from core library, to allow formatters to write to any sink.

### Deprecated

### Removed

### Fixed

### Security

## 1.7.1 - 2025-12-02

### Fixed

- Fix handling of `outputType` in CLI configuration

## 1.7.0 - 2025-11-27

### Added

- Add option to verify .pom availability (#67, thanks to [@bacecek](https://github.com/bacecek))
- Add possibility to specify multiple output types in CLI

### Changed

- Use `stability-level` as default policy for update. The previous "accept-all" policy can be used by
using the `always` policy name. (#61)
- CLI options are now used in priority over configuration file. **This is a breaking change**.

### Fixed

- Ignored dependences are now sorted correctly (#66)

## 1.6.1 - 2025-09-22

### Fixed

- Handle cache issues correctly (#57).

## 1.6.0 - 2025-09-12

### Added

- Add release note URL resolver if dependency has a GitHub project (#53)
- JS version for core library (#54)

### Changed

- Updated to Kotlin 2.2.20

## 1.5.1 - 2025-08-11

### Fixed

- Fix version replacer issue when temporary file system is on another volumes (#51)

## 1.5.0 - 2025-08-10

### Added

- Possibility to replace versions directly in the catalog file (#49)

### Changed

- Updated Gradle to 9.0.0. **Important:** the Caupain Gradle plugin now requires Gradle 9.0.0 as its minimum version.

## 1.4.1 - 2025-07-30

### Fixed

- Compatibility issue with Gradle < 9.0 (#46)
- Ignored dependencies switch not taken into account in Gradle plugin (#47)

## 1.4.0 - 2025-07-18

### Added

- Add possibility to display updates for ignored dependencies (#42)

### Changed

- Use https://github.com/bishiboosh/properties-serializer for Gradle wrapper properties parsing
- Error on unknown policy in configuration (#41)

## 1.3.0 - 2025-06-17

### Changed

- Enhance repository configuration (#37)
- Enhance Gradle updates configuration (#38)
- Removed gradle versions url configuration, which was only used by tests

## 1.2.1 - 2025-06-06

### Added

- Explanation on the update policies, (#31, thanks to [@sschuberth](https://github.com/sschuberth))

### Fixed

- Format output when no updates are available
- CA certificate path for Linux ARM64 (#31)

## 1.2.0 - 2025-06-04

### Added

- Option to see the list of available policies in CLI (#23)
- Versions references block in output (#25)
- Self-update check (#28)

### Changed

- Gradle plugin now pulls default repositories from project build files in addition to setting files (#22)
- `Repository` is now an interface, and some creation methods have been moved to a specific Java namespace
- Formatter input has changed. This is a breaking change, but should only affect you if you used a custom formatter.

## 1.1.1 - 2025-05-25

### Added

- ARM64 support for Linux in CLI

### Fixed

- Version parsing for versions with long numbers (#17, thanks to [@chenxiaolong](https://github.com/chenxiaolong))

## 1.1.0 - 2025-05-21

### Added

- Dark mode handling for HTML formatter
- Component filtering for repositories (#12)
- Mutiple version catalog files handling (#13)
- Allow `?` character for matches in glob patterns

### Changed

- Updated style rules
- Removed usage of data classes in the core lib

## 1.0.1 - 2025-05-15

### Fixed

- Fix an issues with credentials in the Gradle Plugin (#9)

## 1.0.0 - 2025-05-12

### Added

- First official release !

### Changed

- Library exclusions can now use globs

### Fixed

- Handle correctly IO errors and retry version update checks

## 0.2.0 - 2025-05-07

### Added

- Possibility to ignore directly in the TOML file via inline comment
- Shell completion scripts

### Changed

- Use default cache dir from system in CLI 
- Enhance configuration for formatters in Gradle plugin and allow for custom formatter
- Add dependency information in policy

### Fixed

- Handle info for snapshot versions correctly
- Markdown table formatting

## 0.1.0 - 2025-04-25

### Added

- First version of the tool !
