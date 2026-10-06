"""Package the PiCal ImageGen artwork into Android launcher densities."""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = Image.open(root / "assets/branding/pical-icon.png").convert("RGBA")
res = root / "app/src/main/res"
background = (20, 38, 61, 255)
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    canvas = Image.new("RGBA", (size, size), background)
    artwork = source.resize((size, size), Image.Resampling.LANCZOS)
    canvas.alpha_composite(artwork)
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        canvas.save(res / f"mipmap-{density}" / name)

# 108dp adaptive layer at 4x. Artwork fits the safe region of circular masks.
foreground = Image.new("RGBA", (432, 432))
artwork = source.resize((224, 224), Image.Resampling.LANCZOS)
foreground.alpha_composite(artwork, ((432 - 224) // 2, (432 - 224) // 2))
(res / "drawable-nodpi").mkdir(exist_ok=True)
foreground.save(res / "drawable-nodpi/ic_launcher_foreground_image.png")
source.resize((256, 256), Image.Resampling.LANCZOS).save(res / "drawable-nodpi/pical_brand_mark.png")
