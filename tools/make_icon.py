#!/usr/bin/env python3
"""从一张插画生成 Android 自适应图标（前景/背景/旧版 PNG）。

用法：python3 tools/make_icon.py /path/to/art.jpg

做法：从四边泛洪去掉背景色（保留插画内部同色区域，比如小猪的粉色身体），
裁到内容边界后按安全区比例居中，生成：
  res/drawable-nodpi/ic_launcher_foreground.png
  res/mipmap-*/ic_launcher.png, ic_launcher_round.png
"""

import sys
from collections import deque

from PIL import Image, ImageDraw, ImageFilter

CANVAS = 432          # 108dp @ 4x
CONTENT_RATIO = 0.74  # 内容占画布比例（自适应图标安全区内再留一点呼吸）
BG = (254, 210, 209)  # 与插画一致的粉色背景


def remove_background(img: Image.Image, tolerance: int = 42) -> Image.Image:
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    bg = px[0, 0]
    visited = bytearray(w * h)
    queue = deque()

    def close_to_bg(c):
        return (
            abs(c[0] - bg[0]) <= tolerance
            and abs(c[1] - bg[1]) <= tolerance
            and abs(c[2] - bg[2]) <= tolerance
        )

    for x in range(w):
        for y in (0, h - 1):
            if close_to_bg(px[x, y]) and not visited[y * w + x]:
                visited[y * w + x] = 1
                queue.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if close_to_bg(px[x, y]) and not visited[y * w + x]:
                visited[y * w + x] = 1
                queue.append((x, y))

    while queue:
        x, y = queue.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not visited[ny * w + nx]:
                c = px[nx, ny]
                if close_to_bg(c):
                    visited[ny * w + nx] = 1
                    queue.append((nx, ny))

    alpha = Image.new("L", (w, h), 255)
    ap = alpha.load()
    for y in range(h):
        row = y * w
        for x in range(w):
            if visited[row + x]:
                ap[x, y] = 0

    # 羽化边缘，避免锯齿
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.6))
    img.putalpha(alpha)
    return img


def trimmed(img: Image.Image) -> Image.Image:
    box = img.getbbox()
    return img.crop(box) if box else img


def fit_center(src: Image.Image, canvas: int, ratio: float) -> Image.Image:
    target = int(canvas * ratio)
    w, h = src.size
    scale = target / max(w, h)
    resized = src.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.paste(resized, ((canvas - resized.width) // 2, (canvas - resized.height) // 2), resized)
    return out


def rounded(img: Image.Image, radius_ratio: float = 0.22) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    draw = ImageDraw.Draw(mask)
    r = int(img.width * radius_ratio)
    draw.rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius=r, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("用法: make_icon.py <插画路径> [res 目录]")
    src_path = sys.argv[1]
    res_dir = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/res"

    art = trimmed(remove_background(Image.open(src_path)))

    fg = fit_center(art, CANVAS, CONTENT_RATIO)
    fg.save(f"{res_dir}/drawable-nodpi/ic_launcher_foreground.png")

    colors = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for name, size in colors.items():
        base = Image.new("RGBA", (size, size), BG + (255,))
        art_layer = fit_center(art, size, 0.86)
        base.paste(art_layer, (0, 0), art_layer)
        base.save(f"{res_dir}/mipmap-{name}/ic_launcher.png")
        rounded(base).save(f"{res_dir}/mipmap-{name}/ic_launcher_round.png")

    print("icon generated:", art.size)


if __name__ == "__main__":
    main()
