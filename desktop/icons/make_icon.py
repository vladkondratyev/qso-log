# Desktop icons, run: python3 desktop/icons/make_icon.py (needs Pillow).
# Design: QSO / blue bar / LOG in Russo One, slanted 8°, in a white circle. Same design as the Android icon.
from PIL import Image, ImageDraw, ImageFont
import math, os, subprocess, shutil, tempfile
HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "../src/main/resources/font/russo_one.ttf")
INK = (0x14, 0x20, 0x2B, 255); BLUE = (0x0A, 0x5C, 0x8A, 255)

def glyphs(size=2048):
    """Word block on a transparent square, slanted, tightly cropped."""
    f = ImageFont.truetype(FONT, 600)
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    def word(text, y):
        b = d.textbbox((0, 0), text, font=f)
        w = b[2] - b[0]
        d.text(((size - w) / 2 - b[0], y - b[1]), text, font=f, fill=INK)
        return b[3] - b[1]
    h1 = word("QSO", 300)
    bar_y = 300 + h1 + 110
    bw = 1150; bh = 70
    d.rounded_rectangle(((size - bw) / 2, bar_y, (size + bw) / 2, bar_y + bh), radius=bh / 2, fill=BLUE)
    word("LOG", bar_y + bh + 110)
    k = math.tan(math.radians(8))
    im = im.transform(im.size, Image.AFFINE, (1, k, -k * size / 2, 0, 1, 0), resample=Image.BICUBIC)
    return im.crop(im.getbbox())

def icon(px):
    ss = 4; S = px * ss
    base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(base)
    m = S * 0.03
    d.ellipse((m, m, S - m, S - m), fill=(255, 255, 255, 255), outline=(0xD5, 0xDE, 0xE7, 255), width=max(1, int(S * 0.012)))
    g = glyphs()
    # The word block fits a square inscribed in the circle, with some air.
    box = (S - 2 * m) * 0.62
    scale = box / max(g.size)
    g = g.resize((int(g.width * scale), int(g.height * scale)), Image.LANCZOS)
    base.alpha_composite(g, ((S - g.width) // 2, (S - g.height) // 2))
    return base.resize((px, px), Image.LANCZOS)

# icon.png (Linux, window icon), icon.ico (Windows), icon.icns (macOS, needs iconutil).
big = icon(1024)
icon(512).save(os.path.join(HERE, "icon.png"))
big.save(os.path.join(HERE, "icon.ico"), sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])
if shutil.which("iconutil"):
    with tempfile.TemporaryDirectory() as tmp:
        iconset = os.path.join(tmp, "QSO.iconset")
        os.mkdir(iconset)
        for s in (16, 32, 128, 256, 512):
            big.resize((s, s), Image.LANCZOS).save(os.path.join(iconset, f"icon_{s}x{s}.png"))
            big.resize((2 * s, 2 * s), Image.LANCZOS).save(os.path.join(iconset, f"icon_{s}x{s}@2x.png"))
        subprocess.run(["iconutil", "-c", "icns", iconset, "-o", os.path.join(HERE, "icon.icns")], check=True)
