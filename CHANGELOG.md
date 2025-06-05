# Changelog

## Unreleased

### Added

### Changed

### Deprecated

### Removed

### Fixed
- Format output when no updates are available

### Security

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
