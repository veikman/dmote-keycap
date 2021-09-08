## Printing advice

Some [parameters](param.md) are intended to aid manufacturability. This
includes the `supported` parameter, which is intended to reduce the need for
building supports in slicing software.

With single-head FDM printing in a material like PLA, a relatively simple way
to get a good result is to print each key in an upright position, with
`supported`. In general, a `minimal`-style cap with a tall top plate (hence
a vaulted ceiling) should need no further support and no brim.

Consider the main alternative: Printing each key upside down. This will often
give you a cleaner stem and skirt, but if the face of the key is not even (i.e.
`bowl-radii` is not `nil`), cleaning up the print will be more difficult. In
particular, even with fairly dense supports added by a slicer, you will
probably find tiny cavities behind the face, to such a depth that a really good
surface finish is hard to achieve even with a suitable rotary tool. Still, if
you intend to paint your prints anyway, or if you have a dual-head printer with
a soluble support material, printing upside down may ultimately be a better
option.

### Troubleshooting

Recommended solutions to common problems.

#### When the printed stem is too thick for the stem of the switch

First, for FDM printing, try reducing extrusion width in your slicer. In
PrusaSlicer v2.3.0, for example, go into “Print settings” → “Advanced” →
“Extrusion width” and reduce e.g. “Perimeters” to a value closer to, but not
smaller than, the actual width of your nozzle. By default, slicers for FDM tend
to overextrude filament to reduce gaps and compensate for thermal contraction.

For SLA printing, the main factor seems to be orientation. Try rotating the cap
and adding supports in your slicer to drain excess resin away from the tip of
the stem on the cap.

If that does not work, try running `dmote-keycap` with error compensation.

* For an `:alps` stem that is too thick, run with `--error-stem-positive 0.1`
  or more.
* For an `:mx` stem that does not fit over the cross of the stem, run with
  `--error-stem-negative -0.1` or less.

#### When the printed cap is too narrow for the body of the switch

First, see the advice on extrusion width and orientation above. If that does
not help, try running with `--error-body-positive -0.7` and extra
`--skirt-thickness` (e.g.  2.5) to compensate for the walls getting thinner
with error compensation. Notice that extra skirt thickness can cause very
tightly placed neighbouring keys to collide.

#### When the printer nozzle dislocates the stem while printing

First, use standard techniques for print bed adhesion with your printer and
filament. For FDM this means e.g. tape, glue stick, alcohol, bed heating,
precise z-offset tuning, extra first-layer height and extrusion width etc.

If that does not help, and/or if the caps you are printing do not have the
skirt and stem going to the exact same level, run with `--supported`.

If that still does not solve the problem, try nozzle lifting in your slicer. In
PrusaSlicer v2.3.0, for example, go into “Printer settings” → “Extruder 1” →
“Retraction” and set “Lift Z” to a positive value so that the printer lifts
before moving between stem and body, reducing shear. If the setting is at zero,
try 0.3 mm.

If you’re still seeing the occasional bent stem, run with
`--horizontal-support-height 2` or more. This makes taller buttresses that
stabilize the stem by connecting it more strongly to the skirt. You will need
to snip these off with flush cutters, which gets harder the more you increase
the value.

#### When legends are hard to read

Nozzle size in FDM printing is a fundamental limitation to horizontal
resolution. You can work around this a little bit by tweaking slicer settings.
In particular:

* Use ironing to smooth out each layer of the top face before adding the next.
* Use variable layer height. Reduce the very top layers to the minimum your
  hardware can handle.
* Extrusion width and retraction; see above.

When you know what you can achieve this way, design your legends for your
hardware. Make your figures thick enough that they survive slicing and printing
with the amount of manual cleanup you want to do.
