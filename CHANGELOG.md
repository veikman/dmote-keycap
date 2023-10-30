# Change log
This log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Changed
- The default type of key switch has changed, from `alps` to `mx`.
- The design of the `mx` switch type has changed. This type now produces a
  cylindrical stem on keycaps. The new design is compatible with a broader
  range of MX clones.
  The original design is still available under the name `rect-mx`.

### Added
- Tweaks to stem support, specific to each switch type.

### Fixed
- Minor improvements to vertical centering and font choice for the Concertina.

## [Version 0.8.0] - 2021-02-10
### Changed
- The skirt on a `minimal` cap is of more even thickness, because it is now
  based on insetting the outer shell, whereas before, the interior and exterior
  of the skirt were offsets, with the same radius, from different-size
  rectangles.
- In tandem with the addition of `skirt-space`, the default value of the
  `error-body-positive` parameter has changed from -0.5 to 0. This makes
  all error parameters neutral by default.
- The precise shape of the top of a `minimal` cap with non-neutral
  `bowl-radii`. The user-defined top (specified with `top-size`) is no longer
  extended upward for the bowl to cut into. Instead, a separate positive shape
  is added above it, extending less and shrinking horizontally according to the
  `slope` argument.
- Default parameter values have changed to reflect both the redesign of the
  `minimal` skirt and the fact that 0.4 mm FDM printer nozzles
  are more common than the previous 0.5 mm.
- Minor redesign of Concertina keycaps using consistent `bowl-radii`, that is,
  a sphere.

### Added
- `skirt-space` parameter, controlling the nominal distance between switch and
  skirt on a `minimal`-style cap.
- Trusses inside the vaulted ceiling of a `minimal`-style cap with a high top,
  when `supported`.
    - A `truss-offset` parameter to control the height at which these trusses
      appear.
- The `slope` parameter, which already existed in the API, is now exposed in
  the CLI as well.
- Support for a wider range of bowl radii, including a more responsive sanding
  jig design.
- `--facet-angle` CLI argument for improved detail with larger bowl radii.
  Default `--facet-size` was raised to compensate.

### Fixed
- A 1-minute delay between the application’s useful work and its termination
  following any use of the application that involved Inkscape to process
  legends.
- `skirt-thickness`, `nozzle-width` and `horizontal-support-height` specified
  in an EDN batch file were being ignored, because default values
  for the application CLI took precedence over them. (The default `facet-size`
  still takes precedence over a batch file.)
- Skirt thickness values following the change in interpretation made in v0.7.0.
    - Incorrect `skirt-thickness` setting in built-in defaults.
    - Incorrect `skirt-thickness` settings in the Concertina configuration.
    - In addition, the parameter schema now requires `skirt-thickness` to be
      positive.

## [Version 0.7.0] - 2021-09-30
### Changed
- Engraved legends on the sides of keycaps cut more deeply:
  Closer to the depth of legends on the tops of keycaps.

### Added
- New parameters to tweak the size of legends for printer error.

### Fixed
- Corrected the interpretation of `skirt-thickness` by doubling its effect.
  Incrementing the setting by 1 now adds 1 mm to the thickness of the skirt
  itself, whereas before, it added 1 mm to the diameter of a cap but only 0.5
  mm to the skirt.
- Incorrect default `bowl-radii` for keycaps. Regressed in v0.6.0.

## [Version 0.6.0] - 2021-09-12
### Added
- A new operating mode, `--jig-mode`. This makes a model of a jig for sanding
  down the top bowls of printed keycaps to the desired finish.

### Fixed
- Intermediate directories are now automatically created as needed for a
  montage and for generated SVG files (`:char` legends).

## [Version 0.5.1] - 2020-07-31
### Fixed
- Flat tops.
    - Failure to parse CLI argument for all-nil bowl radii.
    - Regression in actually modeling flat-topped keys given the right
      parameters.

## [Version 0.5.0] - 2020-04-10
### Changed
- Changed the target version of Inkscape for SVG exports, from v0 to v1.
- Started joining the names of sides with underscores instead of hyphens,
  because it makes more sense to hyphenate within the names of sides.
- One minor change to the CLI matching `scad-app` v1:
  `--face-size` has been replaced with `--facet-size`.

### Added
- 2D montages for inspecting whole batches of caps.
- Exposed the `horizontal-support-height` parameter through the CLI.
- A brief troubleshooting guide.

### Fixed
- Corrected nominal travel distance on Cherry MX from 3.6 mm to 4.0 mm.
  4.0 mm is also more common on MX clones.
- Improved designs for Concertina v0.7.0.
- Improved feedback on incorrect CLI arguments.

## [Version 0.4.0] - 2020-11-29
### Changed
- The default typeface for legends is now DejaVu Sans Mono. This should
  present excellent backwards compatibility with the previous default,
  Bitstream Vera Sans Mono, and broader Unicode support.

### Added
- A batch mode, where the CLI takes an EDN file describing several caps.
- CSS and other legend styling properties are now exposed for overrides.
- Support for arbitrary SVG transforms of generated legends.
- Colourization of legends in OpenSCAD previews.
- A `whitelist` CLI parameter for the batch mode.
- Sample configuration files for batches, principally a complete set for the
  Concertina.
- Sample SVG files for advanced legends.
- A basic keycap parameter schema for applications that support a superset of
  this library’s switch types.

## [Version 0.3.0] - 2019-11-09
### Changed
- Broke the `data` module into three: Added a `measure` module for measurement
  functions and a `schema` module for validation, leaving the rest in place.

### Added
- Added perfunctory support for legends. API stability not guaranteed.
- Added an output `filename` parameter to the CLI, for scripting.
- Exposed the `bowl-radii` and `error-body-positive` parameters through the
  CLI.
- Added an option parser map, for deserialization in the DMOTE application.

### Developer
- Added parameter parsers for use with serialized inputs in other applications.
- Added trivial unit tests.

### Fixed
- More default values now appear in the CLI.

## [Version 0.2.0] - 2019-06-01
### Changed
- Removed CLI-only defaults for DFM error measurements. This reduces
  potential differences between library and CLI results.

### Added
- Added the option of print supports: For stems shorter than skirts, for skirts
  longer than stems, and horizontally, between skirt and stem.
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

[Unreleased]: https://github.com/veikman/dmote-keycap/compare/v0.8.0...HEAD
[Version 0.8.0]: https://github.com/veikman/dmote-keycap/compare/v0.7.0...v0.8.0
[Version 0.7.0]: https://github.com/veikman/dmote-keycap/compare/v0.6.0...v0.7.0
[Version 0.6.0]: https://github.com/veikman/dmote-keycap/compare/v0.5.1...v0.6.0
[Version 0.5.1]: https://github.com/veikman/dmote-keycap/compare/v0.5.0...v0.5.1
[Version 0.5.0]: https://github.com/veikman/dmote-keycap/compare/v0.4.0...v0.5.0
[Version 0.4.0]: https://github.com/veikman/dmote-keycap/compare/v0.3.0...v0.4.0
[Version 0.3.0]: https://github.com/veikman/dmote-keycap/compare/v0.2.0...v0.3.0
[Version 0.2.0]: https://github.com/veikman/dmote-keycap/compare/v0.1.1...v0.2.0
[Version 0.1.1]: https://github.com/veikman/dmote-keycap/compare/v0.1.0...v0.1.1
