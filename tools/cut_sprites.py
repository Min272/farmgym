"""Cắt sprite sheet. Chạy: python3 -I cut_sprites.py giếng.png gà.png trứng.png thư_mục_xuất"""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

well_src, chicken_src, egg_src, OUT = sys.argv[1:5]
WELL_LEVEL = 0   # 0..3: trái trên, phải trên, trái dưới, phải dưới

def frames(sheet, bx, by, cell, ids):
    out = []
    for i in ids:
        x, y = round(bx + (i % 4) * cell), round(by + (i // 4) * cell)
        out.append(sheet.crop((x, y, x + int(cell), y + int(cell))))
    return out

def common_square(fs):
    """Cắt chung một khung cho mọi frame để hình không bị giật."""
    boxes = [f.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox() for f in fs]
    l, t = min(b[0] for b in boxes), min(b[1] for b in boxes)
    r, b = max(b[2] for b in boxes), max(b[3] for b in boxes)
    s = max(r - l, b - t) + 2
    cx, cy = (l + r) // 2, (t + b) // 2
    return [f.crop((cx - s // 2, cy - s // 2, cx - s // 2 + s, cy - s // 2 + s)) for f in fs]

def save(im, name, size):
    im.resize((size, size), Image.LANCZOS).save(f'{OUT}/{name}.png', optimize=True)

# Gà: mỗi khối 256px, ô 64px
ch = Image.open(chicken_src).convert('RGBA')
eat = frames(ch, 515, 258, 64, range(8, 16))
walk = frames(ch, 258, 1, 64, range(0, 16, 2))
idle = frames(ch, 515, 1, 64, [0])
cut = common_square(eat + walk + idle)
for i in range(8):
    save(cut[i], f'chicken_eat{i}', 128)
    h = cut[8 + i].resize((128, 128), Image.LANCZOS)
    d = ImageDraw.Draw(h)   # dấu ! báo đói
    d.ellipse([96, 4, 124, 32], fill=(220, 40, 30, 255), outline=(255, 255, 255, 255), width=2)
    d.rectangle([108, 9, 112, 21], fill='white')
    d.rectangle([108, 24, 112, 28], fill='white')
    h.save(f'{OUT}/chicken_hungry{i}.png', optimize=True)
save(cut[16], 'chicken_idle', 128)

# Giếng: mỗi khối 712px, ô 178px
we = Image.open(well_src).convert('RGBA')
bx, by = [(1, 1), (714, 1), (1, 714), (714, 714)][WELL_LEVEL]
cut = common_square(frames(we, bx, by, 178, range(0, 16, 2)))
for i, f in enumerate(cut):
    save(f, f'well_pump{i}', 192)
save(cut[0], 'well_full', 192)
gray = ImageEnhance.Brightness(cut[0].convert('LA').convert('RGBA')).enhance(0.75)
gray.putalpha(cut[0].getchannel('A'))
save(gray, 'well_empty', 192)

# Trứng: ảnh chụp nền xám đặc -> tô loang nền từ viền
egg = Image.open(egg_src).convert('RGB')
w, h = egg.size
bg = egg.getpixel((0, 0))
mask = Image.new('L', (w, h), 0)
for y in range(h):
    for x in range(w):
        if max(abs(a - b) for a, b in zip(egg.getpixel((x, y)), bg)) > 7:
            mask.putpixel((x, y), 255)
pad = Image.new('L', (w + 2, h + 2), 0)
pad.paste(mask, (1, 1))
ImageDraw.floodfill(pad, (0, 0), 128)
mask = pad.point(lambda v: 0 if v == 128 else 255).crop((1, 1, w + 1, h + 1))
mask = mask.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(0.6))
e = egg.convert('RGBA')
e.putalpha(mask)
save(common_square([e])[0], 'egg', 128)
