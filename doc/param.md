## Parameters to the `keycap` function

The following are the main parameters to the main function in this
function library, `dmote-keycap.models.keycap`.

* `:style`: One of `:minimal` or `:maquette` (see [the readme](../README.md)).
* `:switch-type`: See below.
* `:unit-size`: A 2-tuple of horizontal size measured in u, hence non-linear.
  On a traditional ISO keyboard, the first value in this tuple (the width)
  varies from 1 for most keys to about 6 for a space bar. The second value
  (the depth) is 2 for the Enter key and 1 for all other keys. Please note that
  a non-rectangular shape, as is typical for the Enter key, is not yet
  supported by this library.
* `:legend`: Sources of and related parameters for 2D designs, documented
  [here](legend.md).

### Detailed control of keycap shape

Some of these parameters have global default values, while others have default
values associated with particular styles, for ease of use.

* `:top-size`: A 3-tuple describing the finger contact surface, including its
  thickness in the middle, directly above the stem of the keycap. The first two
  numbers in this 3-tuple can be omitted by replacing them with `nil`, in which
  case `:slope` (see below) will take precedence. All measurements in mm.
* `:slope`: A ratio between the top and bottom widths of the keycap or of one
  of its parts. This setting is used to compute top size if `:top-size` is left
  incomplete, and to position legends on the sides of the cap, and to shape
  the upper edge of a cap with `bowl-radii`.
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

### Design for manufacture (DFM)

* `:supported`: If true, support structures are added underneath the model.
  There are other parameters to control these supports in more detail.
    * `:horizontal-support-height`: The height (measured from the floor) of
      support structures on the floor that are added by `:supported`, where
      height is not automatically determined by the parts they support.
    * `:truss-offset`: The starting height (measured from the top of the
      switch) of support structures added by `:supported` inside vaults which
      in turn are created inside the top of a cap with tall `:top-size`.
      The function of these “trusses” is to prevent a moving print head from
      bending the stem of a cap before it is connected to the rest of the cap
      at the very top.
    * `:nozzle-width`: The width of the printer nozzle that will be used to
      print the cap. This parameter is only used to build supports, which will
      have the width of the nozzle because this improves print speed and
      quality.
* `:sectioned`: If true, the model is cut in half for a sectioned view.
  This is useful in previews and development.
* The “error parameters”: `:error-body-positive`, `:error-side-negative`,
  `:error-stem-negative`, `:error-stem-positive`, `:error-top-negative`.
  These describe your 3D printer’s accuracy for different parts of a keycap.
  Usage advice is available [here](print.md).

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
* `--facet-size`, `--facet-angle`: Rendering resolution.
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
* `:kailh-box-silent`: Kailh BOX Silent series designs.
  These have a body and cross-shaped stem compatible with `mx`, but there’s a
  tube around the stem, with a circular profile.

The `mx` category covers, for example, Gateron’s KS-3 series and some of
Kailh’s PG1511 series among other MX clones. However, the PG1511 series
includes some models with shorter travel, and some types of stems (e.g.
non-Silent BOX, CPG151101F) that will not fit with `dmote-keycap`.

Minor differences in the lower body of two types of switches, such as plate
mount versus PCB mount, and lateral recesses on some MX-like switches but not
on others, are not modelled by this library because they are irrelevant to
keycaps. Version 0.7.0 of the DMOTE application introduced some support for
additional switch types that are relevant to the mounting plate.
