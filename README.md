# `dmote-keycap`: Keycap models for mechanical keyboards

`dmote-keycap` produces a three-dimensional geometry for a keycap: Either a
featureless maquette for use in easily rendered previews, or a featureful
“minimal” keycap that you can print and use.

Horizontal keycap size is measured in a traditional unit: The u, where 1 u is
19.05 mm (0.75”) per key mount. A 1 u cap is smaller at about 18.25 mm to allow
for space between caps on adjacent mounts.

## Features

* Supports both ALPS- and MX-style switches.
* The “minimal” style hugs the body of the switch. It enables keyboard designs
  more dense than the traditional 1 u, which can have ergonomic advantages.
  It offers better dust protection than a “top only” 0.75 u cap.

`dmote-keycap` does not provide models of switches.

## Usage

This is both a library for `scad-clj` applications that draw keyboards, and a
CLI utility for drawing printable keycaps that can be used with common
mechanical keyboards.

### As a library

The `dmote-keycap.data` namespace exposes various raw data and simple
mathematical functions to describe keycaps.

The `dmote-keycap.models` namespace exposes one function: `keycap`. It takes
a number of parameters, in a flat map, and returns a `scad-clj` specification:

* `:style`: One of `:minimal` (described above) or `:maquette` (a crude
  preview).
* `:switch-type`: One of `:alps` (ALPS style, including Matias) or
  `:mx` (Cherry MX style).
* `:unit-size`: A 2-tuple of horizontal size measured in u (hence non-linear).
  On a traditional ISO keyboard, the first value in this tuple (the width)
  varies from 1 for most keys to about 6 for a space bar. The second value
  (the depth) is 2 for the Enter key and 1 for all other keys. Please note that
  a non-rectangular shape, as is typical for the Enter key, is not yet
  supported by this library, nor are stabilizer mounts.
* `:top-size`: A 3-tuple describing the finger contact surface, including its
  thickness above the stem of the keycap. The first two numbers in this
  3-tuple can be omitted. All measurements in mm.
* `:slope`: A ratio between the top and bottom of the keycap. This setting is
  only used if `top-size` is left incomplete.
* `:top-rotation`: A 3-tuple describing the angle of the finger contact
  surface, in radians. This would be `[0 0 0]` for a DSA profile and would have
  a different non-zero first value for each row of an OEM profile.
* `:bowl-radii`: A 3-tuple describing the radii of a spheroid used as
  negative space to carve a bowl out of the top of a non-maquette keycap.
  If this option is set to `nil`, no bowl is used.
* `:bowl-plate-offset`: An optional modification of the vertical distance in
  mm between the bowl and the top of the keycap.
* `:max-skirt-length`: The maximum length of material from the top of the stem
  down toward the switch mounting plate. By default, on a `minimal` cap, this
  is 1 mm less than the space available when the switch is pressed.
* `:error-stem-positive`, `:error-stem-negative`, `:error-body-positive`:
  Printer-dependent measurements of error for different parts of the cap.
* `:sectioned`: If true, the model is cut in half for a sectioned view.
  This is useful in previews and development.

Some of these parameters have global default values, while others have default
values associated with particular styles, for ease of use.

### As a command-line application

Use `lein run` with command-line arguments corresponding to the parameters
listed above. This will generate files of OpenSCAD code under `output/scad`
and, optionally, STL files for slicing and 3D printing.

## License

Copyright © 2019 Viktor Eikman

This software is distributed under the [Eclipse Public License](LICENSE-EPL)
(EPL) v2.0 or any later version thereof. This software may also be made
available under the [GNU General Public License](LICENSE-GPL) (GPL), v3.0 or
any later version thereof, as a secondary license hereby granted under the
terms of the EPL.
