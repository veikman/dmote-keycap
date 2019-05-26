# Change log
This log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Changed
- Removed CLI-only defaults for DFM error measurements. This reduces
  potential differences between library and CLI results.

### Added
- Added print support for stems shorter than skirts.
- Added horizontal print supports between skirt and stem.
  Alignment with the skirt at print-bed level quick and dirty.
- Added vaulted ceilings to the interior of minimal caps.
- Exposed the `top-size` and `skirt-thickness` parameters through the CLI.

## [Version 0.1.1] - 2019-03-24
### Fixed
- Converted a mandatory `union` to a maybe for marginally simpler maquettes.

### Developer
- Delegated face size to `scad-app`.

## Version 0.1.0 - 2019-03-23
### Added
- Minimal and maquette styles.

[Unreleased]: https://github.com/veikman/dmote-keycap/compare/v0.1.1...HEAD
[Version 0.1.1]: https://github.com/veikman/dmote-keycap/compare/v0.1.0...v0.1.1
