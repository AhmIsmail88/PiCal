"""Package the user-supplied PiCal PNG into legacy launcher and in-app sizes.

Adaptive and monochrome layers are native vectors in res/drawable and are
maintained separately from these raster density assets.
"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = Image.open(root / "assets/branding/supplied-20261017/PiCal_icon_1024.png").convert("RGBA")
res = root / "app/src/main/res"
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    artwork = source.resize((size, size), Image.Resampling.LANCZOS)
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        artwork.save(res / f"mipmap-{density}" / name)
source.save(res / "drawable-nodpi/pical_brand_mark.png")
