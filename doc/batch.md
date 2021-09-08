## Batch mode

The `--batch` mode [flag to the CLI utility](param.md) takes an EDN file path.

Batch mode allows for rendering arbitrarily large sets of keys with arbitrary
shared and unique properties. Such properties are `keycap` function
[parameters](param.md).

The EDN data serialization format was chosen for these properties because,
unlike e.g. YAML, it allows maps as map keys. Overrides to settings in an EDN
file can be passed on the command line and apply to the entire batch.

The EDN file for a batch must contain a vector of maps, keyed by maps of
properties, with vectors of individual switches as their values. Each
individual switch must be represented either by a map of properties or by a
shorthand format interpreted according to the parent map.

Valid examples of the expected format are available under `config`. Here is a
usage example calling one of those examples without any further customization
through the CLI:

`lein run -- --batch config/concertina/64key/alps/colemak.edn --render`

OpenSCAD and Inkscape are required to run this example.

For large batches with complex legends, add `--montage` for an easier means of
inspecting the typesetting, using ImageMagick.
