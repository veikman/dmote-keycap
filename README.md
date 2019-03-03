# `dmote-keycap`: Minimal keycaps

An application that produces a three-dimensional geometry for a keycap that
you can put on a mechanical keyboard.

The purpose of this application is to enable keyboard designs more dense than
the traditional 1u, with better dust protection than a “top only” 0.75u cap.

## Features

* Supports both ALPS- and MX-style switches.
* Wraps the body of the switch for minimal space requirements.

## Usage

Use `lein run` with command-line arguments to generate files of OpenSCAD code
under `output/scad` and, optionally, STL files for slicing and 3D printing.

## License

Copyright © 2019 Viktor Eikman

This software is distributed under the [Eclipse Public License](LICENSE-EPL),
(EPL) v2.0 or any later version thereof. This software may also be made
available under the [GNU General Public License](LICENSE-GPL) (GPL), v3.0 or
any later version thereof, as a secondary license hereby granted under the
terms of the EPL.
