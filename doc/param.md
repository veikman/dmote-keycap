## Parameters to the `keycap` function

The following are the most interesting parameters to the main function in this
function library, `dmote-keycap.models.keycap`.

* `:style`: One of `:minimal` or `:maquette` (see [the readme](../README.md)).
* `:switch-type`: See below.
* `:unit-size`: A 2-tuple of horizontal size measured in u, hence non-linear.
  On a traditional ISO keyboard, the first value in this tuple (the width)
  varies from 1 for most keys to about 6 for a space bar. The second value
  (the depth) is 2 for the Enter key and 1 for all other keys. Please note that
  a non-rectangular shape, as is typical for the Enter key, is not yet
  supported by this library.
* `:top-size`: A 3-tuple describing the finger contact surface, including its
  thickness in the middle, directly above the stem of the keycap. The first two
  numbers in this 3-tuple can be omitted by replacing them with `nil`, in which
  case `:slope` (see below) will take precedence. All measurements in mm.
* `:slope`: A ratio between the top and bottom widths of the keycap. This
  setting is used to compute top size if `:top-size` is left incomplete, and
  to position legends on the sides of the cap.
* `:top-rotation`: A 3-tuple describing the angle of the finger contact
  surface, in radians. This would be `[0 0 0]` for a (row 3 or standard) DSA
  profile and would have a different non-zero first value for each row of an
  OEM profile.
* `:bowl-radii`: A 3-tuple describing the radii in mm of a spheroid used as
  negative space to carve a bowl out of the top of a non-maquette keycap.
  If this option is set to `nil` (CLI: `'nil nil nil'`), the top is left flat.
* `:bowl-plate-offset`: An optional modification of the vertical distance in
  mm between the bowl and the top of the keycap.
* `:skirt-thickness`: The horizontal thickness of material in the outer walls
  of a `minimal` cap.
* `:skirt-space`: A horizontal measure of space between the outer wall of a
  `minimal` cap and the switch inside, where they are closest.
* `:skirt-length`: The length of material from the top of the stem
  down toward the switch mounting plate. By default, on a `minimal` cap, this
  is 1 mm less than the space available when the switch is pressed.
* `:legend`: Sources of and related parameters for 2D designs, documented
  [here](legend.md).
* `:sectioned`: If true, the model is cut in half for a sectioned view.
  This is useful in previews and development.
* `:supported`: If true, support structures are added underneath the model.
* `:nozzle-width`: The width of the printer nozzle that will be used to print
  the cap. This parameter is only used to build supports, which will have the
  width of the nozzle because this improves print speed and quality.
* `:horizontal-support-height`: The height of support structures added by
  `:supported`, where this are not already determined by the parts they
  support.
* `:error-body-positive`, `:error-side-negative`, `:error-stem-negative`,
  `:error-stem-positive`, `:error-top-negative`:
  Printer-dependent measurements of error for different parts of the cap.
  A **breaking change** is planned for version 1.0.0 or some feature release
  before it: All of these error values will default to zero to broaden support
  for different printing technologies.

Some of these parameters have global default values, while others have default
values associated with particular styles, for ease of use. The long-term plan
is to model a variety of traditional “families” with just a few parameters.

## Parameters to the command-line application

Use `lein run` with command-line arguments corresponding to the parameters
listed above. Not all parameters are supported; try `lein run -- --help`.
The application will generate files of OpenSCAD code under `output/scad`
and, optionally, STL files for slicing and 3D printing.

The CLI supports some additional parameters that are not interpreted by the
`keycap` function inside the library. Here are some highlights:

* `--batch`: An alternative operating mode for multiple keys, documented
  [here](batch.md).
* `--jig-mode`: An alternative operating mode for a tool and no keys,
  documented [here](jig.md).
* `--face-size`: Rendering resolution.
* `--filename`: For your own CLI-based scripting needs.
* `--render`: Render results to STL under `output/stl`.
* `--montage`: With `--render`, render to PNG  under `output/png`.

Montages include a view of all sides of all keys in a `--batch`.

Rendering requires OpenSCAD. Montages also require ImageMagick.

## Switch types

`dmote-keycap` does not provide models of electromechanical switches, but it
does contain some data on common types of switches, for shaping keycaps to fit.
The following keywords are recognized for the switch type parameter mentioned
above:

* `:alps`: ALPS-like designs, including Matias.
* `:mx`: Cherry MX and designs with very similar upper bodies.

The `mx` category covers, for example, Gateron’s KS-3 series and some of
Kailh’s PG1511 series among other MX clones. However, the PG1511 series
includes some models with shorter travel, and some types of stems (e.g. BOX)
that will not fit with `dmote-keycap`.

Minor differences in the lower body of two types of switches, such as plate
mount versus PCB mount, and lateral recesses on some MX-like switches but not
on others, are not modelled by this library because they are irrelevant to
keycaps. Version 0.7.0 of the DMOTE application introduced some support for
additional switch types that are relevant to the mounting plate.
