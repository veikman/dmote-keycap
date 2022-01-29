## Printing advice

Some [parameters](param.md) are intended to aid manufacturability. This
includes the `supported` parameter, which is intended to reduce the need for
building supports in slicing software.

### Orientation

With single-head FDM printing in a material like PLA, a relatively simple way
to get a good result is to print each key in an upright position, with
`supported`. In general, a `minimal`-style cap with a tall top plate (hence
a vaulted ceiling) should need no further support and no brim.

Consider the main alternative for FDM: Printing each key upside down. This will
often give you a cleaner stem and skirt, but if the face of the key is not even
(i.e. `bowl-radii` is not `nil`), cleaning up the print will be more difficult.
In particular, even with fairly dense supports added by a slicer, you will
probably find cavities behind the face to such a depth that a really good
surface finish is hard to achieve. Still, if you intend to paint your prints
anyway, or if you have a dual-head printer with a soluble support material,
printing upside down may ultimately be a better option.

For SLA printing, try rotating the cap and adding supports in your slicer to
drain excess resin away from the tip of the stem on the cap.

### General printer settings

For FDM printing, try reducing extrusion width in your slicer. In
PrusaSlicer v2.3.0, for example, go into “Print settings” → “Advanced” →
“Extrusion width” and reduce e.g. “Perimeters” to a value very close to, but
not smaller than, the actual width of your nozzle. By default, slicers for FDM
tend to overextrude filament to reduce gaps and compensate for thermal
contraction, which doesn’t work for small objects like keycaps.

### Cleanup

If you’re using `supported` and printing in an upright position, you will need
to remove the supports. The recommended way to do this is with a pair of flush
cutters, working gradually through a series of small snips.

Minor details of a bad FDM print can be improved with a soldering iron set to
low heat. For PLA, try 150 °C (300 °F). Work in a well-ventilated area and do
not waste good soldering tips on plastic; prefer tips that are already old and
oxidized. Apply to narrow stems to strengthen layer adhesion or fine-tune
dimensions. Apply to legends to clean up smudged contours.

For help sanding keycaps to the desired finish, use the [jig mode](jig.md).

### Troubleshooting

Recommended solutions to common problems.

#### The stem is too thick

Check general printer settings and try running `dmote-keycap` with error
compensation.

* For an `:alps` stem that is too thick, run with `--error-stem-positive 0.1`
  or more.
* For an `:mx` stem that does not fit over the cross of the stem, run with
  `--error-stem-negative -0.1` or less.

#### The cap is too narrow when printed

Check general printer settings and make sure your prints are properly cleaned.
If the first layer of the skirt has an inward lip to it, that means your nozzle
was too close to the print bed; trim that lip with a hobby knife.

If the problem persists, there are two parameters you can tweak:

* `--skirt-space` puts the skirt further away from the switch and leaves it
  intact.
* `--error-body-positive`, when set to a negative value, creates a block of
  negative space around the body of the switch, following the shape of the
  switch itself more closely than `--skirt-space`.

The former parameter describes the ideal shape. The latter describes a common
printer behaviour, not the ideal shape. The negative space created by the error
parameter eats the skirt from the inside, specifically to compensate for
printer inaccuracy.

The two parameters do not interact with one another, nor with
`--skirt-thickness`. If `--skirt-space` is much larger than the error parameter,
the error parameter will have no effect. If, on the other hand, the error
parameter is set to such a negative value that it does have an effect on the
skirt, you may need to compensate by raising `--skirt-thickness`. Notice that
extra skirt thickness, in turn, can cause neighbouring keys to collide.

Finally, the two parameters work on different scales and in different
directions. Decrementing `--skirt-space` by 1 brings skirt and switch 1 mm closer
on every side, which decreases the width of the keycap by 2 mm on each side.
Decrementing `--error-body-positive` by 1 removes 0.5 mm from the inside of the
skirt, if there is no gap at all.

In general, if you want keycaps that are truly minimal, set `--skirt-space` to
zero and `--error-body-positive` as close to zero as the quality of your
printer will allow. If instead you prefer good-looking prints with even wall
thickness, sacrificing key density, set `--error-body-positive` to zero and
raise `--skirt-space` until your prints are no longer too narrow.

#### The cap shrinks after printing

If exposed to sunlight, even through a window, PLA can get hot enough for
annealing to occur. This can warp a keycap, making it too thin in one
dimension. Observe advice above for narrow caps, or print in a material that
will not warp where you intend to use it.

#### The cap comes loose while printing

Use standard techniques for print bed adhesion with your printer and filament.
For FDM this means e.g. tape, glue stick, alcohol, bed heating, precise
z-offset tuning, extra first-layer height and extrusion width, brim etc.

If that does not help, or if the caps you are printing do not have the skirt
and stem going to the exact same level, run with `--supported` or have your
slicer generate supports.

If that still does not solve the problem, try nozzle lifting in your slicer. In
PrusaSlicer v2.3.0, for example, go into “Printer settings” → “Extruder 1” →
“Retraction” and set “Lift Z” to a positive value so that the printer lifts
before moving between stem and body, reducing shear. If the setting is at zero,
try 0.2 mm.

#### The stem bends while printing

Use standard techniques for print bed adhesion, and run with `--supported`.

Also try `--horizontal-support-height 2` or more. This makes taller buttresses
that stabilize the stem by connecting it more strongly to the skirt. You will
need to snip these off with flush cutters, which gets harder the more you
increase the value.

#### The top of the cap is bumpy

Check general printer settings. In particular, reduce infill extrusion width
closer to the width of your nozzle.

Use ironing in your slicer to smooth out each layer of the top face before
adding the next.

#### Legends are hard to read

Nozzle size in FDM printing is a fundamental limitation to horizontal
resolution. You can work around this a little bit by tweaking slicer settings.
In particular:

* Use reduced extrusion width and ironing.
* Use variable layer height. Reduce the very top layers to the minimum your
  hardware can handle.

When you know what you can achieve this way, design your legends for your
hardware or apply negative `--error-top-negative`/`--error-side-negative`.
Make your figures thick enough that they survive slicing and printing with the
amount of manual cleanup you want to do.
