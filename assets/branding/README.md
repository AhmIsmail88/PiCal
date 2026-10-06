# PiCal launcher artwork

- Source artwork: [pical-icon.png](pical-icon.png), the transparent PiCal pipe-shaped π mark.
- Android resource packaging: [prepare_launcher_icon.py](../../tools/prepare_launcher_icon.py).
- Adaptive foreground: 432×432 with centered 224×224 artwork; navy background `#14263D`.
- Launcher densities: 48, 72, 96, 144, and 192 pixels.
- In-app mark: 256×256, generated from the same source.
- Android 13+ monochrome artwork is defined in the application's vector resources.

The source was generated with the built-in ImageGen tool. Its prompt and implementation
details are recorded in [PICAL_BRANDING_REVIEW_AR.md](../../PICAL_BRANDING_REVIEW_AR.md).
The app resources use this workspace artwork, independently of the image-generation cache.
