## Jig mode

The CLI utility’s `--jig-mode` [parameter](param.md) causes the utility to
output a model of a tool, instead of the default model of a keycap.

This tool is intended for use in the manual finishing of printed keycaps.
Specifically, controlled by the standard `--bowl-radii` parameter to the CLI
utility, the jig includes a pad with the same curvature as the bowl that tops
off a `minimal`-style keycap.

The pad on the jig curves outward while the bowl curves inward.
You put a strip of sandpaper in between to make the inside of the bowl
smoother, so it looks good and stays clean without the need for paint or
varnish. Sanding the inside of the bowl is more important than sanding the
sides. The jig will not help you do the latter.

### Parameters

The following parameters to the CLI utility affect only its jig mode:

* `--jig-lanes`. Leave this at the default value for a test print. Set it to 2
  or higher to allow for multiple strips of sandpaper at once, which saves time
  in serial production.
* `--jig-angle`. You might need to adjust this for your workshop, but the
  default value is probably OK.
* `--paper-width`. Try to keep this close both to twice the size of the middle
  number in your `bowl-radii` and to the actual width of any sandpaper strips
  you already have lying around. Minor differences won’t matter.

### Printing

Sanding blocks are commonly made of cork or rubber to help make a smooth
finish. The jig itself should ideally be printed in TPC or any similar,
middling-soft material. A tire-like Shore hardness around D45 works well, with
up to 1 mm top shell thickness and 15–20% gyroid infill. Keep the layers thin
for a smooth pad.

### Using a jig

By mounting a printed jig on a spare piece of wooden board and wrapping a strip
of sandpaper around it, you complete the jig. You then take a printed cap and
rub it on the curved pad.

Wet sanding is key. Dip each keycap in shallow water as you work. For an
FDM-printed PLA keycap, try starting with 60–80 grit and work your way up to at
least 800. For a black keycap or any material that requires a smooth finish to
look and feel good, proceed to 1200–2000 grit.
