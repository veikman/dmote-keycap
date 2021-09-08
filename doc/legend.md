## Legends

`dmote-keycap` uses OpenSCAD’s `import` function, not the `text` function, to
put markings on caps. This enables a wide variety of markings: Anything that
can be expressed in SVG, DXF and other 2D image formats OpenSCAD can import.

To select a legend, when calling into the library, supply a map like this as
part of the options to `keycap`:

```clojure
{:legend {:faces {:top {:char "F1"}}}}
```

The corresponding CLI parameter is `--legend-top-char F1`.

In this example, `:char` supplies a text sequence for the F1 key, not a file
path. You can use any text you like but formatting for such sequences is
rudimentary in the current version of `dmote-keycap` and is likely to change
in future versions.

You will get more power by replacing the `:char` keyword with
one of `:importable`, for a file OpenSCAD can read directly, or
`:unimportable`, for an SVG file that must be simplified before OpenSCAD can
read it.

`dmote-keycap` uses [Inkscape](https://inkscape.org/) programmatically to
simplify SVG, so you will need that installed for both `:char` and
`:unimportable`. If you are calling `keycap` from an application that does
not use `scad-app`’s default output structure, you will also need to supply
a function as a the top-level `:importable-filepath-fn` parameter. This
function must take a string filename and return a path for placing the file
where OpenSCAD will be able to import it.

### Placement

The `:top` keyword in the example above targets the top face, where the user’s
finger touches the key. Other faces can be addressed with `:north`, `:east`
and so on.

When importing from SVG for the top face, `dmote-keycap` will match coordinates
[0, 0] in the image to [0, 0] in OpenSCAD’s coordinate system, which is the
middle of the face.

Targeting is a bit more complicated for the sides:
After being extruded to three dimensions, each image used to mark a side of
the key is tilted using the `:slope` parameter and moved so that SVG’s [0, 0]
ends up at the height of the top of the stem.
