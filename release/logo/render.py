"""Re-export all PNGs from the SVG sources in this folder.

Needs: Python 3 with Pillow, and Google Chrome or Microsoft Edge installed
(used headless to rasterise the SVG; nothing is downloaded).

    python render.py
"""
import os
import subprocess
import tempfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BROWSERS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]
ICON_SIZES = [512, 400, 256, 128, 96]
MASTER_RENDER = 1024  # render large, downscale with Lanczos for crisp small sizes


def browser():
    for path in BROWSERS:
        if os.path.exists(path):
            return path
    raise SystemExit("Chrome or Edge not found; install one or edit BROWSERS.")


def rasterise(svg_name, size, out_png, width=None, height=None):
    """Screenshot an HTML page that shows the SVG at width x height, transparent background."""
    width = width or size
    height = height or size
    svg_path = os.path.join(HERE, svg_name).replace("\\", "/")
    html = (
        "<!doctype html><html><head><style>html,body{margin:0;padding:0;background:transparent}"
        f"img{{display:block;width:{width}px;height:{height}px}}</style></head>"
        f'<body><img src="file:///{svg_path}"></body></html>'
    )
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "page.html")
        with open(page, "w", encoding="utf-8") as f:
            f.write(html)
        shot = os.path.join(tmp, "shot.png")
        subprocess.run(
            [
                browser(), "--headless=new", "--disable-gpu", "--hide-scrollbars",
                "--default-background-color=00000000",
                f"--window-size={width},{height}",
                f"--screenshot={shot}", "file:///" + page.replace("\\", "/"),
            ],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        img = Image.open(shot).convert("RGBA")
        img = img.crop((0, 0, width, height))
        img.save(out_png)


def main():
    master = os.path.join(HERE, "_master_1024.png")
    rasterise("disklens-icon.svg", MASTER_RENDER, master)
    big = Image.open(master).convert("RGBA")
    for s in ICON_SIZES:
        big.resize((s, s), Image.LANCZOS).save(os.path.join(HERE, f"disklens-icon-{s}.png"))
    os.remove(master)
    rasterise("disklens-banner.svg", 0, os.path.join(HERE, "disklens-banner.png"), 1200, 300)
    print("PNG exports written to", HERE)


if __name__ == "__main__":
    main()
