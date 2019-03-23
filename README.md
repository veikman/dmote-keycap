# `dmote-keycap`: Keycap models for mechanical keyboards

`dmote-keycap` produces a three-dimensional geometry for a keycap: Either a
featureless maquette for use in easily rendered previews, or a keycap that you
can print and use.

[![Clojars Project](https://img.shields.io/clojars/v/dmote-keycap.svg)](https://clojars.org/dmote-keycap)

Horizontal keycap size is measured in a traditional unit: The u, where 1 u is
19.05 mm (0.75”) per key mount. A 1 u cap is smaller at about 18.25 mm to allow
for space between caps on adjacent mounts. For the same reason, a 2 u cap is
more than twice as a wide as a 1 u cap, and so on.

## Features

* Supports both ALPS- and MX-style switches.
* A “minimal” style that hugs the body of the switch. This style enables
  keyboard designs more dense than the traditional 1 u, which can have
  ergonomic advantages. Dust protection is better than a 0.75 u cap that does
  not extend down the sides of the switch.
* A “maquette” style for previewing keyboard designs.

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
  thickness in the middle, directly above the stem of the keycap. The first two
  numbers in this 3-tuple can be omitted by replacing them with `nil`, in which
  case `:slope` (see below) will take precedence. All measurements in mm.
* `:slope`: A ratio between the top and bottom of the keycap. This setting is
  only used if `:top-size` is left incomplete.
* `:top-rotation`: A 3-tuple describing the angle of the finger contact
  surface, in radians. This would be `[0 0 0]` for a DSA profile and would have
  a different non-zero first value for each row of an OEM profile.
* `:bowl-radii`: A 3-tuple describing the radii of a spheroid used as
  negative space to carve a bowl out of the top of a non-maquette keycap.
  If this option is set to `nil`, no bowl is used.
* `:bowl-plate-offset`: An optional modification of the vertical distance in
  mm between the bowl and the top of the keycap.
* `:skirt-thickness`: The horizontal thickness of material in the outer walls
  of a `minimal` cap.
* `:skirt-length`: The length of material from the top of the stem
  down toward the switch mounting plate. By default, on a `minimal` cap, this
  is 1 mm less than the space available when the switch is pressed.
* `:error-stem-positive`, `:error-stem-negative`, `:error-body-positive`:
  Printer-dependent measurements of error for different parts of the cap.
* `:sectioned`: If true, the model is cut in half for a sectioned view.
  This is useful in previews and development.

Some of these parameters have global default values, while others have default
values associated with particular styles, for ease of use. The long-term plan
is to model a variety of traditional “families” with just a few parameters.

### As a command-line application

Use `lein run` with command-line arguments corresponding to the parameters
listed above. Not all parameters are supported; try `lein run --help`.
The CLI takes an additional parameter for resolution

The application will generate files of OpenSCAD code under `output/scad`
and, optionally, STL files for slicing and 3D printing.

## License

Copyright © 2019 Viktor Eikman

This software is distributed under the [Eclipse Public License](LICENSE-EPL)
(EPL) v2.0 or any later version thereof. This software may also be made
available under the [GNU General Public License](LICENSE-GPL) (GPL), v3.0 or
any later version thereof, as a secondary license hereby granted under the
terms of the EPL.
