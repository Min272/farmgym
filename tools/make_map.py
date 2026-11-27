"""Dọn ảnh map, tạo 3 dáng (ngang/vuông/dọc), tách nền cỏ.
Chạy: python3 -I make_map.py map.png co.png xe.png thư_mục_xuất"""
import sys, random
from PIL import Image, ImageDraw, ImageFilter, ImageStat

map_src, grass_src, truck_src, OUT = sys.argv[1:5]
random.seed(5)
m = Image.open(map_src).convert('RGB')

def clone(dst_box, src_box, mask, match=False):
    w, h = dst_box[2] - dst_box[0], dst_box[3] - dst_box[1]
    src = m.crop(src_box)
    tile = Image.new('RGB', (w, h))
    for i, x in enumerate(range(0, w, src.width)):
        for j, y in enumerate(range(0, h, src.height)):
            t = src
            if i % 2: t = t.transpose(Image.FLIP_LEFT_RIGHT)
            if j % 2: t = t.transpose(Image.FLIP_TOP_BOTTOM)
            tile.paste(t, (x, y))
    if match:   # chỉnh tông màu bản chép cho khớp đất xung quanh
        want = ImageStat.Stat(m.crop((150, 160, 215, 200))).mean
        have = ImageStat.Stat(tile).mean
        tile = Image.merge('RGB', [ch.point(lambda v, d=want[i] - have[i]: v + d) for i, ch in enumerate(tile.split())])
    m.paste(tile, dst_box[:2], mask)

# 1) phần đất bị loang phẳng ở đáy -> phủ vân đất thật
mk = Image.new('L', (330, 52), 0)
ImageDraw.Draw(mk).rounded_rectangle([14, 12, 316, 52], 18, fill=255)
clone((140, 312, 470, 364), (220, 235, 330, 275), mk.filter(ImageFilter.GaussianBlur(7)))
# 2) mảnh công trình sót ở mép dưới -> viền cỏ
mk = Image.new('L', (440, 47), 0)
edge = [(0, 47)] + [(x, 20 + random.randint(-7, 5) + (12 if x < 30 or x > 410 else 0)) for x in range(0, 441, 8)] + [(440, 47)]
ImageDraw.Draw(mk).polygon(edge, fill=255)
clone((80, 340, 520, 387), (0, 318, 62, 387), mk.filter(ImageFilter.GaussianBlur(2.5)))
# 3) mảng nâu phẳng và ô vá ở nửa trên bãi -> phủ vân đất thật
mk = Image.new('L', (649, 387), 0)
ImageDraw.Draw(mk).polygon([(160, 150), (164, 132), (178, 122), (192, 108), (219, 85), (236, 72), (300, 69), (392, 72),
                            (410, 90), (432, 103), (450, 130), (428, 172), (350, 178), (190, 150)], fill=255)
clone((150, 60, 465, 190), (170, 250, 290, 296), mk.crop((150, 60, 465, 190)).filter(ImageFilter.GaussianBlur(5)), match=True)
# ô vuông lệch màu giữa bãi
mk = Image.new('L', (72, 66), 0)
ImageDraw.Draw(mk).rounded_rectangle([8, 8, 64, 58], 12, fill=255)
clone((294, 179, 366, 245), (200, 250, 272, 296), mk.filter(ImageFilter.GaussianBlur(4)))
# 4) mảng xanh phẳng ở đỉnh bãi -> phủ cỏ thật lấy ngay bên cạnh
mk = Image.new('L', (132, 52), 0)
ImageDraw.Draw(mk).rounded_rectangle([6, 5, 126, 44], 12, fill=255)
clone((273, 31, 405, 83), (180, 31, 246, 83), mk.filter(ImageFilter.GaussianBlur(3)))

m = m.crop((0, 6, 649, 387))          # bỏ vệt trắng trên cùng

W, H, BAND = 649, 381, 70             # phải khớp Scene.kt
for name, crop, aspect in [('wide', 0.0, W / H), ('square', 0.07, 1.05), ('tall', 0.12, 0.78)]:
    part = m.crop((round(W * crop), 0, round(W * (1 - crop)), H))
    nh = round(W * (1 - 2 * crop) / aspect)
    out = Image.new('RGB', (part.width, nh))
    out.paste(part.crop((0, 0, part.width, BAND)), (0, 0))   # dải trời giữ nguyên
    out.paste(part.crop((0, BAND, part.width, H)).resize((part.width, nh - BAND), Image.LANCZOS), (0, BAND))
    rgba = out.convert('RGBA')
    a = Image.new('L', rgba.size, 0)
    ImageDraw.Draw(a).rounded_rectangle([0, 0, rgba.width - 1, rgba.height - 1], 30, fill=255)
    rgba.putalpha(a)
    rgba.save(f'{OUT}/map_{name}.png', optimize=True)

# Cỏ: nền xám đặc 224 -> trong suốt, giữ bóng mờ
g = Image.open(grass_src).convert('RGB')
cw, ch = g.width / 4, 192 / 4
for i in range(16):
    x, y = (i % 4) * cw, (i // 4) * ch
    cell = g.crop((round(x) + 1, round(y) + 1, round(x + cw) - 1, round(y + ch) - 1))
    out = Image.new('RGBA', cell.size)
    for px in range(cell.width):
        for py in range(cell.height):
            r, gg, b = cell.getpixel((px, py))
            sat, lum = max(r, gg, b) - min(r, gg, b), (r + gg + b) / 3
            fg = max(min(1, (sat - 8) / 36), min(1, (lum - 230) / 12), 0)
            shadow = max(0, (220 - lum) / 220) * 0.7
            if fg >= shadow:
                # khử viền xám
                col = tuple(max(0, min(255, round((v - 224 * (1 - fg)) / fg))) if fg > 0.35 else v for v in (r, gg, b))
                out.putpixel((px, py), col + (round(fg * 255),))
            else:
                out.putpixel((px, py), (30, 25, 10, round(shadow * 255)))
    out.resize((96, 96), Image.LANCZOS).save(f'{OUT}/grass_{i}.png', optimize=True)

Image.open(truck_src).convert('RGBA').resize((144, 144), Image.LANCZOS).save(f'{OUT}/truck.png')
