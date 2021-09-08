# Keycaps for mechanical keyboards

`dmote-keycap` produces three-dimensional geometry for keycaps: Either
featureless maquettes for use in quick previews, or proper keycaps you can
print and use on any common mechanical keyboard.

[![Clojars Project](https://img.shields.io/clojars/v/dmote-keycap.svg)](https://clojars.org/dmote-keycap)

[![Image of a Concertina thumb-key cluster using designs bundled with dmote-keycap](https://viktor.eikman.se/image/concertina-2-left-thumb-cluster/display)](https://viktor.eikman.se/image/concertina-2-left-thumb-cluster/)

## Features

`dmote-keycap` is both a library for `scad-clj` applications that draw
keyboards, and a CLI (command-line interface) utility. Both offer:

* Support for various types of switches. Details [here](doc/param.md).
* A “minimal” keycap style that hugs the body of the switch. This style enables
  keyboard designs more dense than the traditional 1 u,<sup>[1](#u_unit)</sup>
  which can have ergonomic advantages. Dust protection is better than a 0.75 u
  cap that does not extend down the sides of the switch.
* A “maquette” keycap style for previewing keyboard designs.
* [Legends](doc/legend.md): Arbitrary 2D designs can be “engraved” into any of
  the faces of a non-maquette cap: The top and sides, in any combination.

`dmote-keycap` has no support for stabilizers, raised legends, or multiple
materials (printing to simulate “double shot” injection moulding).

## Usage

To run the utility and generate a printable model, enter `lein run` at your
command line, in this folder.

The CLI utility uses what the library exposes as `dmote-kgeycap.models/keycap`.
That function takes a number of parameters and returns a `scad-clj`
specification. Most of them are mirrored in the CLI. Parameters in both
contexts are documented [here](doc/param.md).

For the use of `dmote-keycap` as a function library in keyboard design, two
other namespaces are likely to be useful. `dmote-keycap.data` exposes various
raw data and `dmote-keycap.measure` exposes functions for calculating how much
space a keycap model would need.

Printing advice is available [here](doc/print.md).

## License

Copyright © 2019–2021 Viktor Eikman

This software is distributed under the [Eclipse Public License](LICENSE-EPL)
(EPL) v2.0 or any later version thereof. This software may also be made
available under the [GNU General Public License](LICENSE-GPL) (GPL), v3.0 or
any later version thereof, as a secondary license hereby granted under the
terms of the EPL.

---

<a name="u_unit">^1</a>: Key size is measured in a traditional unit. 1 u is
19.05 mm (0.75”) per key mount. A 1 u cap is smaller at about 18.25 mm to allow
for space between caps on adjacent mounts. For the same reason, a 2 u cap is
more than twice as wide as a 1 u cap, and so on.
