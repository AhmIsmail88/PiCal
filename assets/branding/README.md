# FlowTrack launcher artwork

- User reference: `user-icon-reference.png` (preserved original attachment).
- Project source: `flowtrack-icon-cutout.png` — transparent edit from the built-in ImageGen tool.
- Previous launcher PNGs: `previous/`.
- Android packaging: `tools/prepare_launcher_icon.py`; Pillow is used only for density conversion and positioning, after the ImageGen edit.
- Adaptive foreground: 432×432 with centered 224×224 artwork for mask safety; background `#E6EAED`.
- Legacy PNG densities: 48, 72, 96, 144, 192 pixels.

Actual ImageGen prompt:

> Edit target: supplied FlowTrack app icon photo. Use case: background-extraction. Extract only the existing dark metallic rounded square pipe monogram. Preserve its exact design, cyan and amber illuminated tubes, metallic surfaces, lettering, proportions and viewing angle. Remove the entire gray studio background, ground and cast shadow. Do not redesign or add anything. Output a square transparent PNG with the object tightly centered, filling about 90% of the square, with all object edges intact. This will be the source for Android launcher icons.

The generated output was copied into the project; app resources do not depend on the Codex image cache.
