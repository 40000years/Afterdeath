"""Generates 32x32 pixel art textures, Java item/block models, and Bedrock/Geyser definitions for all 30 Evergarden crops."""
import math

CROPS = [
    # Tier 1: Common Farm (6 crops)
    {'id': 'mana_dew_berry', 'title': 'Mana Dew Berry', 'tier': 1, 'seed': 'beetroot_seeds', 'food': 'sweet_berries',
     'pri': (50, 200, 255), 'sec': (20, 100, 210), 'acc': (210, 250, 255), 'shape': 'BERRY_BUNCH'},
    {'id': 'chameleon_leaf', 'title': 'Chameleon Leaf', 'tier': 1, 'seed': 'wheat_seeds', 'food': 'dried_kelp',
     'pri': (80, 175, 70), 'sec': (45, 105, 40), 'acc': (180, 215, 55), 'shape': 'LEAF_FROND'},
    {'id': 'fairy_mushroom', 'title': 'Fairy Mushroom', 'tier': 1, 'seed': 'beetroot_seeds', 'food': 'cookie',
     'pri': (250, 130, 185), 'sec': (180, 60, 115), 'acc': (255, 240, 250), 'shape': 'MUSHROOM'},
    {'id': 'magnetic_squash', 'title': 'Magnetic Squash', 'tier': 1, 'seed': 'pumpkin_seeds', 'food': 'pumpkin_pie',
     'pri': (55, 110, 205), 'sec': (25, 55, 125), 'acc': (235, 70, 65), 'shape': 'MELON_SQUASH'},
    {'id': 'mountain_walker_bamboo', 'title': 'Mountain Walker Bamboo', 'tier': 1, 'seed': 'wheat_seeds', 'food': 'carrot',
     'pri': (150, 185, 60), 'sec': (80, 115, 30), 'acc': (170, 120, 75), 'shape': 'BAMBOO_STALK'},
    {'id': 'demeters_melon', 'title': "Demeter's Melon", 'tier': 1, 'seed': 'melon_seeds', 'food': 'melon_slice',
     'pri': (40, 180, 85), 'sec': (20, 100, 45), 'acc': (255, 215, 50), 'shape': 'MELON_SQUASH'},

    # Tier 2: Combat & Slaying (6 crops)
    {'id': 'blood_thorn_tomato', 'title': 'Blood Thorn Tomato', 'tier': 2, 'seed': 'pumpkin_seeds', 'food': 'apple',
     'pri': (220, 30, 40), 'sec': (125, 15, 20), 'acc': (45, 45, 55), 'shape': 'ROUND_FRUIT'},
    {'id': 'frostbite_radish', 'title': 'Frostbite Radish', 'tier': 2, 'seed': 'beetroot_seeds', 'food': 'carrot',
     'pri': (120, 220, 255), 'sec': (60, 140, 210), 'acc': (255, 255, 255), 'shape': 'ROOT_TUBER'},
    {'id': 'thunder_kernel_corn', 'title': 'Thunder Kernel Corn', 'tier': 2, 'seed': 'wheat_seeds', 'food': 'bread',
     'pri': (255, 215, 30), 'sec': (190, 140, 10), 'acc': (100, 235, 255), 'shape': 'CORN_EAR'},
    {'id': 'reapers_garlic', 'title': "Reaper's Garlic", 'tier': 2, 'seed': 'beetroot_seeds', 'food': 'golden_carrot',
     'pri': (230, 230, 240), 'sec': (130, 120, 160), 'acc': (60, 30, 90), 'shape': 'BULB_GARLIC'},
    {'id': 'titan_pumpkin', 'title': 'Titan Pumpkin', 'tier': 2, 'seed': 'pumpkin_seeds', 'food': 'pumpkin_pie',
     'pri': (235, 125, 25), 'sec': (145, 65, 10), 'acc': (95, 115, 135), 'shape': 'MELON_SQUASH'},
    {'id': 'kinetic_pea_pod', 'title': 'Kinetic Pea Pod', 'tier': 2, 'seed': 'wheat_seeds', 'food': 'sweet_berries',
     'pri': (110, 220, 40), 'sec': (50, 130, 20), 'acc': (215, 255, 75), 'shape': 'POD'},

    # Tier 3: Dimension & Survival (6 crops)
    {'id': 'soul_ward_bulb', 'title': 'Soul Ward Bulb', 'tier': 3, 'seed': 'beetroot_seeds', 'food': 'golden_carrot',
     'pri': (60, 220, 200), 'sec': (20, 120, 130), 'acc': (255, 255, 220), 'shape': 'BULB_GARLIC'},
    {'id': 'void_feather_blossom', 'title': 'Void Feather Blossom', 'tier': 3, 'seed': 'torchflower_seeds', 'food': 'dried_kelp',
     'pri': (190, 120, 250), 'sec': (105, 40, 165), 'acc': (245, 210, 255), 'shape': 'BLOSSOM'},
    {'id': 'lodestone_gourd', 'title': 'Lodestone Gourd', 'tier': 3, 'seed': 'melon_seeds', 'food': 'apple',
     'pri': (125, 130, 145), 'sec': (65, 70, 85), 'acc': (220, 90, 50), 'shape': 'ROUND_FRUIT'},
    {'id': 'abyssal_kelp', 'title': 'Abyssal Kelp', 'tier': 3, 'seed': 'wheat_seeds', 'food': 'dried_kelp',
     'pri': (25, 140, 150), 'sec': (10, 65, 80), 'acc': (60, 235, 215), 'shape': 'LEAF_FROND'},
    {'id': 'glider_spore', 'title': 'Glider Spore', 'tier': 3, 'seed': 'beetroot_seeds', 'food': 'cookie',
     'pri': (225, 220, 195), 'sec': (155, 145, 125), 'acc': (105, 195, 255), 'shape': 'SPORE'},
    {'id': 'star_anise', 'title': 'Star Anise', 'tier': 3, 'seed': 'pitcher_pod', 'food': 'golden_carrot',
     'pri': (185, 125, 70), 'sec': (115, 70, 35), 'acc': (255, 240, 180), 'shape': 'STAR'},

    # Tier 4: Mining & Utility (6 crops)
    {'id': 'fortune_beet', 'title': 'Fortune Beet', 'tier': 4, 'seed': 'beetroot_seeds', 'food': 'carrot',
     'pri': (210, 35, 115), 'sec': (120, 15, 60), 'acc': (95, 235, 255), 'shape': 'ROOT_TUBER'},
    {'id': 'lumberjack_acorn', 'title': 'Lumberjack Acorn', 'tier': 4, 'seed': 'wheat_seeds', 'food': 'cookie',
     'pri': (175, 110, 55), 'sec': (100, 55, 25), 'acc': (225, 175, 105), 'shape': 'ACORN'},
    {'id': 'prism_shard_carrot', 'title': 'Prism Shard Carrot', 'tier': 4, 'seed': 'pitcher_pod', 'food': 'golden_carrot',
     'pri': (75, 185, 175), 'sec': (35, 105, 100), 'acc': (245, 130, 65), 'shape': 'ROOT_TUBER'},
    {'id': 'goldleaf_herb', 'title': 'Goldleaf Herb', 'tier': 4, 'seed': 'torchflower_seeds', 'food': 'golden_apple',
     'pri': (250, 205, 50), 'sec': (180, 135, 20), 'acc': (90, 210, 95), 'shape': 'LEAF_FROND'},
    {'id': 'twilight_grape', 'title': 'Twilight Grape', 'tier': 4, 'seed': 'melon_seeds', 'food': 'sweet_berries',
     'pri': (115, 50, 180), 'sec': (60, 20, 105), 'acc': (205, 135, 255), 'shape': 'BERRY_BUNCH'},
    {'id': 'chrono_pepper', 'title': 'Chrono Pepper', 'tier': 4, 'seed': 'pumpkin_seeds', 'food': 'apple',
     'pri': (255, 140, 20), 'sec': (190, 50, 10), 'acc': (255, 230, 100), 'shape': 'PEPPER_CHILI'},

    # Tier 5: Mythic Arcana (6 crops)
    {'id': 'ancient_astral_root', 'title': 'Ancient Astral Root', 'tier': 5, 'seed': 'torchflower_seeds', 'food': 'golden_apple',
     'pri': (130, 90, 240), 'sec': (60, 30, 140), 'acc': (255, 220, 90), 'shape': 'ROOT_TUBER'},
    {'id': 'yggdrasil_sprout', 'title': 'Yggdrasil Sprout', 'tier': 5, 'seed': 'torchflower_seeds', 'food': 'golden_apple',
     'pri': (40, 195, 95), 'sec': (20, 110, 50), 'acc': (255, 215, 60), 'shape': 'SPROUT_TREE'},
    {'id': 'void_overcharge_fig', 'title': 'Void Overcharge Fig', 'tier': 5, 'seed': 'pitcher_pod', 'food': 'golden_carrot',
     'pri': (175, 40, 225), 'sec': (85, 15, 125), 'acc': (255, 135, 245), 'shape': 'ROUND_FRUIT'},
    {'id': 'ethereal_mint', 'title': 'Ethereal Mint', 'tier': 5, 'seed': 'wheat_seeds', 'food': 'apple',
     'pri': (80, 240, 195), 'sec': (30, 140, 115), 'acc': (235, 255, 250), 'shape': 'LEAF_FROND'},
    {'id': 'bloodburn_chili', 'title': 'Bloodburn Chili', 'tier': 5, 'seed': 'pumpkin_seeds', 'food': 'apple',
     'pri': (240, 45, 25), 'sec': (130, 15, 10), 'acc': (255, 190, 40), 'shape': 'PEPPER_CHILI'},
    {'id': 'omni_pomegranate', 'title': 'Omni Pomegranate', 'tier': 5, 'seed': 'torchflower_seeds', 'food': 'golden_apple',
     'pri': (220, 40, 80), 'sec': (110, 15, 40), 'acc': (80, 225, 245), 'shape': 'ROUND_FRUIT'},
]


for c in CROPS:
    for k in ('pri', 'sec', 'acc'):
        if len(c[k]) == 3:
            c[k] = c[k] + (255,)

def blend(c1, c2, ratio):
    return tuple(int(round(a * (1.0 - ratio) + b * ratio)) for a, b in zip(c1[:3], c2[:3])) + (255,)

def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color[:3]) + (color[3] if len(color) > 3 else 255,)

def new_canvas(size=32):
    return [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

def draw_seed(info):
    """Draws a 32x32 mystic seed packet / enchanted seed bulb."""
    p = new_canvas()
    pri, sec, acc = info['pri'], info['sec'], info['acc']
    border = (25, 20, 35, 255)
    pouch_base = (200, 180, 140, 255) if info['tier'] <= 2 else blend((100, 80, 120, 255), pri, 0.3)

    # Seed pouch silhouette
    for y in range(8, 26):
        w = 7 if 12 <= y <= 21 else (5 if y in (10, 11, 22, 23) else 3)
        for x in range(16 - w, 16 + w + 1):
            dist_x = abs(x - 16) / max(1, w)
            dist_y = abs(y - 17) / 9.0
            factor = 1.15 - (dist_x * 0.4 + dist_y * 0.3)
            p[y][x] = shade(pouch_base, factor)

    # Outline
    for y in range(7, 27):
        for x in range(8, 25):
            if p[y][x][3] > 0:
                for dy, dx in ((-1,0),(1,0),(0,-1),(0,1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < 32 and 0 <= nx < 32 and p[ny][nx][3] == 0:
                        p[ny][nx] = border

    # Glowing seed gem in center of pouch
    for y in range(13, 20):
        for x in range(13, 20):
            d = (x - 16)**2 + (y - 16.5)**2
            if d <= 9:
                p[y][x] = acc if d <= 2 else pri
            elif d <= 14:
                p[y][x] = sec

    # Pouch neck ribbon and seal
    for x in range(13, 20):
        p[10][x] = acc
        p[11][x] = shade(acc, 0.8)

    # Top seed sprout tips
    for dy in range(4, 9):
        p[dy][16] = pri
        if dy <= 6:
            p[dy][15] = sec
            p[dy][17] = sec
    p[4][14] = acc
    p[4][18] = acc

    # Sparkles for higher tiers
    if info['tier'] >= 3:
        p[7][11] = acc
        p[6][21] = acc
        p[24][11] = acc
        p[23][21] = acc
    return p

def draw_food(info):
    """Draws a 32x32 fruit / vegetable / root / mushroom / crop."""
    p = new_canvas()
    pri, sec, acc = info['pri'], info['sec'], info['acc']
    border = (20, 15, 25, 255)
    shape = info['shape']

    if shape == 'ROUND_FRUIT':
        cx, cy, r = 16, 17, 8
        for y in range(cy - r - 1, cy + r + 2):
            for x in range(cx - r - 1, cx + r + 1):
                d = (x - cx)**2 + (y - cy)**2
                if d <= r * r:
                    dx, dy = (x - (cx - 3)), (y - (cy - 3))
                    f = 1.25 - (dx**2 + dy**2) / (2.2 * r * r)
                    p[y][x] = shade(pri, f)
        p[cy - r][cx] = (0, 0, 0, 0)
        p[cy - r][cx - 1] = shade(sec, 0.7)
        p[cy - r][cx + 1] = shade(sec, 0.7)
        for y in range(cy - r - 4, cy - r):
            p[y][cx] = (100, 70, 40, 255)
        for lx, ly in ((cx + 1, cy - r - 3), (cx + 2, cy - r - 3), (cx + 3, cy - r - 4), (cx + 2, cy - r - 5)):
            p[ly][lx] = (80, 180, 60, 255)
        for ox, oy in ((-3, -2), (3, -1), (0, 3), (-2, 2)):
            p[cy + oy][cx + ox] = acc

    elif shape == 'BERRY_BUNCH':
        berries = [(12, 18, 5), (20, 18, 5), (16, 13, 5)]
        for bx, by, br in berries:
            for y in range(by - br, by + br + 1):
                for x in range(bx - br, bx + br + 1):
                    if (x - bx)**2 + (y - by)**2 <= br * br:
                        f = 1.3 - ((x - (bx - 1.5))**2 + (y - (by - 1.5))**2) / (br * br * 1.5)
                        p[y][x] = shade(pri, f)
            p[by - 2][bx - 1] = acc
            p[by - 2][bx - 2] = acc
        for y in range(6, 11):
            p[y][16] = (90, 60, 30, 255)
        p[10][14] = (90, 60, 30, 255)
        p[10][18] = (90, 60, 30, 255)
        p[6][14] = (70, 170, 50, 255)
        p[5][13] = (70, 170, 50, 255)

    elif shape == 'ROOT_TUBER':
        for y in range(8, 27):
            w = int(round(6.5 * (1.0 - (y - 8) / 20.0))) + 1
            curv = int(math.sin(y * 0.35) * 1.5)
            for x in range(16 + curv - w, 16 + curv + w + 1):
                f = 1.2 - abs(x - (15 + curv)) / (w + 1.0)
                p[y][x] = shade(pri, f)
            if y % 4 == 0:
                p[y][16 + curv] = sec
        p[27][16] = sec; p[28][17] = sec; p[29][17] = acc
        for y in range(4, 8):
            p[y][15] = (60, 160, 50, 255)
            p[y][17] = (70, 180, 55, 255)
        p[3][14] = acc; p[3][18] = acc

    elif shape == 'PEPPER_CHILI':
        for y in range(8, 26):
            curv = int((y - 8) * (y - 8) * 0.025)
            w = 5 if 10 <= y <= 18 else (3 if y < 10 or y <= 22 else 1)
            for x in range(14 + curv - w, 14 + curv + w + 1):
                f = 1.25 - abs(x - (13 + curv)) / (w + 1.0)
                p[y][x] = shade(pri, f)
        for y in range(11, 20):
            curv = int((y - 8) * (y - 8) * 0.025)
            p[y][14 + curv] = acc
        for y in range(4, 9):
            p[y][14] = (60, 160, 45, 255)
        p[5][13] = (60, 160, 45, 255); p[4][12] = (60, 160, 45, 255)

    elif shape == 'MUSHROOM':
        cx, cy, rx, ry = 16, 14, 9, 7
        for y in range(cy - ry, cy + ry):
            for x in range(cx - rx, cx + rx + 1):
                if ((x - cx)**2) / (rx*rx) + ((y - cy)**2) / (ry*ry) <= 1.0:
                    f = 1.2 - ((x - (cx - 2))**2 + (y - (cy - 2))**2) / 80.0
                    p[y][x] = shade(pri, f)
        for sx, sy in ((12, 11), (19, 11), (15, 9), (16, 14), (11, 15), (21, 15)):
            p[sy][sx] = acc
        for y in range(cy + 1, 26):
            for x in range(14, 19):
                p[y][x] = shade((240, 235, 220, 255), 1.1 - abs(x - 16) * 0.15)

    elif shape == 'LEAF_FROND':
        for y in range(6, 26):
            w = int(round(7.5 * math.sin((y - 5) / 21.0 * math.pi)))
            for x in range(16 - w, 16 + w + 1):
                if (x + y) % 3 == 0 and (x in (16 - w, 16 + w)):
                    continue
                f = 1.2 - abs(x - 16) / (w + 1.0)
                p[y][x] = shade(pri, f)
        for y in range(7, 26):
            p[y][16] = acc
            if y % 3 == 0:
                p[y][15] = sec; p[y][17] = sec
        for y in range(25, 28):
            p[y][16] = sec

    elif shape == 'MELON_SQUASH':
        cx, cy, r = 16, 17, 8
        for y in range(cy - r, cy + r + 1):
            for x in range(cx - r, cx + r + 1):
                d = (x - cx)**2 + (y - cy)**2
                if d <= r * r:
                    rib = abs((x - cx) % 4 - 2)
                    f = (1.1 if rib == 0 else 0.85) - ((y - cy)**2) / 120.0
                    p[y][x] = shade(pri if rib != 0 else sec, f)
        p[cy][cx] = acc; p[cy - 2][cx] = acc; p[cy + 2][cx] = acc
        for y in range(cy - r - 3, cy - r + 1):
            p[y][cx] = (80, 150, 50, 255)
        p[cy - r - 2][cx + 1] = (80, 150, 50, 255)

    elif shape == 'BULB_GARLIC':
        cx, cy = 16, 17
        for y in range(11, 23):
            w = 7 if 13 <= y <= 19 else 5
            for x in range(cx - w, cx + w + 1):
                seg = abs(x - cx) in (0, 3, 6)
                f = 1.15 if not seg else 0.85
                p[y][x] = shade(pri if not seg else sec, f)
        for y in range(6, 12):
            p[y][16] = acc
            if y <= 9: p[y][17] = sec
        for y in range(23, 27):
            p[y][14] = (160, 150, 130, 255); p[y][16] = (160, 150, 130, 255); p[y][18] = (160, 150, 130, 255)

    elif shape == 'CORN_EAR':
        for y in range(8, 24):
            w = 4 if 10 <= y <= 21 else 3
            for x in range(16 - w, 16 + w + 1):
                kernel = (x + y) % 2 == 0
                p[y][x] = shade(pri if kernel else sec, 1.1)
                if (x * 7 + y * 13) % 9 == 0:
                    p[y][x] = acc
        for y in range(19, 27):
            for x in range(16 - (y - 18), 16 + (y - 18) + 1):
                if x in (16 - (y - 18), 16 + (y - 18)):
                    p[y][x] = (60, 170, 50, 255)
        for y in range(5, 9):
            p[y][15] = acc; p[y][17] = acc

    elif shape == 'POD':
        for y in range(8, 25):
            curv = int(math.sin(y * 0.25) * 2.5)
            w = 4 if y % 4 in (1, 2) else 3
            for x in range(16 + curv - w, 16 + curv + w + 1):
                f = 1.2 if w == 4 else 0.95
                p[y][x] = shade(pri, f)
        for y in (11, 15, 19):
            curv = int(math.sin(y * 0.25) * 2.5)
            p[y][16 + curv] = acc; p[y][15 + curv] = acc
        p[6][14] = (50, 140, 30, 255); p[7][15] = (50, 140, 30, 255)

    elif shape == 'ACORN':
        for y in range(14, 26):
            w = int(round(7.0 * (1.0 - ((y - 14) / 12.0)**1.5)))
            for x in range(16 - w, 16 + w + 1):
                p[y][x] = shade(pri, 1.15 - abs(x - 16) * 0.08)
        p[26][16] = acc
        for y in range(8, 15):
            w = 8 if y >= 11 else 6
            for x in range(16 - w, 16 + w + 1):
                hatch = (x + y) % 2 == 0
                p[y][x] = shade(sec if hatch else acc, 1.0)
        for y in range(4, 9):
            p[y][16] = sec

    elif shape == 'SPORE':
        for y in range(7, 16):
            w = int(round(9.0 * math.sin((y - 6) / 10.0 * math.pi)))
            for x in range(16 - w, 16 + w + 1):
                p[y][x] = shade(pri, 1.2 - abs(x - 16) * 0.06)
        for y in range(16, 27):
            for tx in (12, 14, 16, 18, 20):
                if (y + tx) % 3 != 0:
                    p[y][tx] = acc if y % 4 == 0 else sec

    elif shape == 'STAR':
        cx, cy = 16, 16
        for angle in range(0, 360, 60):
            rad = math.radians(angle)
            for d in range(2, 9):
                px = int(round(cx + math.cos(rad) * d))
                py = int(round(cy + math.sin(rad) * d))
                p[py][px] = pri
                ox = int(round(-math.sin(rad) * 1.2))
                oy = int(round(math.cos(rad) * 1.2))
                if d <= 6:
                    p[py + oy][px + ox] = sec
                    p[py - oy][px - ox] = sec
                if d == 5:
                    p[py][px] = acc
        p[cy][cx] = acc

    elif shape == 'SPROUT_TREE':
        for y in range(15, 27):
            p[y][15] = sec; p[y][16] = sec; p[y][17] = (160, 110, 40, 255)
        for y in range(6, 17):
            w = int(round(8.0 * math.sin((y - 5) / 12.0 * math.pi)))
            for x in range(16 - w, 16 + w + 1):
                p[y][x] = shade(pri, 1.15 - ((x - 16)**2 + (y - 11)**2) / 80.0)
        for sx, sy in ((12, 9), (19, 10), (16, 7), (14, 14), (18, 14)):
            p[sy][sx] = acc

    elif shape == 'BLOSSOM':
        cx, cy = 16, 16
        for y in range(cy - 8, cy + 9):
            for x in range(cx - 8, cx + 9):
                d = (x - cx)**2 + (y - cy)**2
                if 9 <= d <= 64:
                    p[y][x] = shade(pri, 1.1 - d / 80.0)
        for y in range(cy - 2, cy + 3):
            for x in range(cx - 2, cx + 3):
                p[y][x] = acc
        p[cy - 9][cx] = acc; p[cy + 9][cx] = acc; p[cy][cx - 9] = acc; p[cy][cx + 9] = acc

    elif shape == 'BAMBOO_STALK':
        for y in range(6, 27):
            for x in range(14, 19):
                ring = y % 7 == 0
                p[y][x] = shade(acc if ring else pri, 1.1 - abs(x - 16) * 0.1)
        for ly in (10, 17):
            p[ly][19] = sec; p[ly - 1][20] = pri; p[ly - 2][21] = acc
            p[ly][13] = sec; p[ly - 1][12] = pri; p[ly - 2][11] = acc

    # Outline pass (collect first, then apply to avoid cascading smear)
    border_pixels = []
    for y in range(1, 31):
        for x in range(1, 31):
            if p[y][x][3] > 0:
                for dy, dx in ((-1,0),(1,0),(0,-1),(0,1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < 32 and 0 <= nx < 32 and p[ny][nx][3] == 0:
                        border_pixels.append((ny, nx))
    for ny, nx in border_pixels:
        p[ny][nx] = border

    if info['tier'] >= 4:
        p[3][5] = acc; p[4][6] = acc; p[28][26] = acc
    if info['tier'] == 5:
        p[3][26] = (255, 255, 255, 255); p[28][5] = (255, 255, 255, 255)
    return p

def draw_crop_stage(info, stage):
    """Draws 32x32 cross-model texture for crop stages (0=sprout, 1=growing, 2=mature)."""
    p = new_canvas()
    pri, sec, acc = info['pri'], info['sec'], info['acc']
    border = (20, 15, 25, 255)

    # Soil mound at the base
    for y in range(29, 32):
        w = 12 - (y - 29) * 2
        for x in range(16 - w, 16 + w + 1):
            p[y][x] = (95, 60, 35, 255) if (x + y) % 2 == 0 else (75, 45, 25, 255)

    if stage == 0:
        for y in range(23, 29):
            p[y][16] = (90, 185, 65, 255)
        p[22][15] = (80, 200, 70, 255); p[21][14] = (80, 200, 70, 255); p[20][13] = (100, 225, 80, 255)
        p[22][17] = (80, 200, 70, 255); p[21][18] = (80, 200, 70, 255); p[20][19] = (100, 225, 80, 255)
        p[20][16] = pri; p[19][16] = acc

    elif stage == 1:
        for y in range(14, 29):
            p[y][16] = (85, 175, 55, 255)
            if y in (16, 21):
                p[y][15] = (85, 175, 55, 255); p[y][17] = (85, 175, 55, 255)
        for d in range(1, 5):
            p[22 - d//2][16 - d] = (75, 185, 60, 255); p[22 - d//2][16 + d] = (75, 185, 60, 255)
        p[20][11] = (90, 210, 75, 255); p[20][21] = (90, 210, 75, 255)
        for d in range(1, 4):
            p[17 - d//2][16 - d] = (75, 185, 60, 255); p[17 - d//2][16 + d] = (75, 185, 60, 255)
        p[15][12] = (90, 210, 75, 255); p[15][20] = (90, 210, 75, 255)
        p[13][16] = (85, 195, 60, 255); p[12][16] = (95, 215, 70, 255)
        p[14][14] = sec; p[14][18] = sec
        p[11][16] = pri; p[10][16] = acc

    elif stage == 2:
        for y in range(6, 29):
            p[y][16] = (80, 165, 50, 255)
            if y % 3 == 0:
                p[y][15] = (70, 150, 45, 255); p[y][17] = (90, 180, 55, 255)

        for by, bx_span in ((21, 6), (16, 7), (11, 6), (7, 4)):
            for d in range(1, bx_span + 1):
                sy = by - int(d * 0.5)
                p[sy][16 - d] = (75, 180, 55, 255); p[sy][16 + d] = (85, 195, 65, 255)
                if d >= bx_span - 1:
                    p[sy - 1][16 - d] = (95, 215, 75, 255); p[sy - 1][16 + d] = (95, 215, 75, 255)

        fruit_coords = [(10, 17), (22, 17), (16, 6), (12, 12), (20, 12)]
        for fx, fy in fruit_coords:
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    if dx*dx + dy*dy <= 2:
                        p[fy + dy][fx + dx] = pri
            p[fy][fx] = acc
            p[fy - 1][fx] = shade(pri, 1.25)
            p[fy + 1][fx] = sec

        p[5][16] = acc; p[4][16] = acc
        p[5][15] = pri; p[5][17] = pri

        if info['tier'] >= 4:
            p[3][16] = (255, 255, 255, 255)
            p[8][8] = acc; p[8][24] = acc
        if info['tier'] == 5:
            p[2][16] = acc; p[14][5] = acc; p[14][27] = acc

    # Outline pass (collect first, then apply to avoid cascading smear)
    border_pixels = []
    for y in range(1, 31):
        for x in range(1, 31):
            if p[y][x][3] > 0 and y < 29:
                for dy, dx in ((-1,0),(1,0),(0,-1),(0,1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < 32 and 0 <= nx < 32 and p[ny][nx][3] == 0:
                        border_pixels.append((ny, nx))
    for ny, nx in border_pixels:
        p[ny][nx] = border
    return p

def register_crop_assets(java, bedrock, textures, mappings, selectors, write_json, png):
    """Generates all 150 crop assets (30 seeds, 30 foods, 90 plant stages) into packs."""
    # Display transform for plant models in item displays and UI
    cross_display = {
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.75, 0.75, 0.75]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]}
    }

    for crop in CROPS:
        cid = crop['id']
        title = crop['title']
        base_seed = crop['seed']
        base_food = crop['food']

        # 1. Seed Item
        seed_name = 'seed_' + cid
        seed_pixels = draw_seed(crop)
        png(java / f'assets/voidscape/textures/item/{seed_name}.png', seed_pixels)
        png(bedrock / f'textures/items/{seed_name}.png', seed_pixels)
        textures['voidscape.' + seed_name] = {'textures': 'textures/items/' + seed_name}

        write_json(java / f'assets/voidscape/models/item/{seed_name}.json', {
            'parent': 'minecraft:item/generated',
            'textures': {'layer0': 'voidscape:item/' + seed_name}
        })
        write_json(java / f'assets/voidscape/items/{seed_name}.json', {
            'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + seed_name}
        })
        selectors.setdefault(base_seed, []).append({
            'when': 'voidscape:' + seed_name,
            'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + seed_name}
        })
        mappings['items'].setdefault('minecraft:' + base_seed, []).append({
            'type': 'definition',
            'model': 'minecraft:' + base_seed,
            'predicate': {'type': 'match', 'property': 'custom_model_data', 'index': 0, 'value': 'voidscape:' + seed_name},
            'bedrock_identifier': 'voidscape:' + seed_name,
            'display_name': 'Seed of ' + title,
            'bedrock_options': {'icon': 'voidscape.' + seed_name, 'allow_offhand': True, 'display_handheld': False, 'creative_category': 'items'}
        })

        # 2. Food Item
        food_name = 'crop_' + cid
        food_pixels = draw_food(crop)
        png(java / f'assets/voidscape/textures/item/{food_name}.png', food_pixels)
        png(bedrock / f'textures/items/{food_name}.png', food_pixels)
        textures['voidscape.' + food_name] = {'textures': 'textures/items/' + food_name}

        write_json(java / f'assets/voidscape/models/item/{food_name}.json', {
            'parent': 'minecraft:item/generated',
            'textures': {'layer0': 'voidscape:item/' + food_name}
        })
        write_json(java / f'assets/voidscape/items/{food_name}.json', {
            'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + food_name}
        })
        selectors.setdefault(base_food, []).append({
            'when': 'voidscape:' + food_name,
            'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + food_name}
        })
        mappings['items'].setdefault('minecraft:' + base_food, []).append({
            'type': 'definition',
            'model': 'minecraft:' + base_food,
            'predicate': {'type': 'match', 'property': 'custom_model_data', 'index': 0, 'value': 'voidscape:' + food_name},
            'bedrock_identifier': 'voidscape:' + food_name,
            'display_name': title,
            'bedrock_options': {'icon': 'voidscape.' + food_name, 'allow_offhand': True, 'display_handheld': False, 'creative_category': 'items'}
        })

        # 3. Growth Stages 0, 1, 2 (using CARROT base item for plant display)
        for stage in (0, 1, 2):
            stage_name = f'crop_{cid}_stage_{stage}'
            stage_pixels = draw_crop_stage(crop, stage)
            # Write to both block and item texture paths for Java
            png(java / f'assets/voidscape/textures/block/{stage_name}.png', stage_pixels)
            png(java / f'assets/voidscape/textures/item/{stage_name}.png', stage_pixels)
            png(bedrock / f'textures/items/{stage_name}.png', stage_pixels)
            textures['voidscape.' + stage_name] = {'textures': 'textures/items/' + stage_name}

            stage_model = {
                'parent': 'minecraft:block/cross',
                'textures': {'cross': 'voidscape:block/' + stage_name},
                'display': cross_display
            }
            write_json(java / f'assets/voidscape/models/item/{stage_name}.json', stage_model)
            write_json(java / f'assets/voidscape/items/{stage_name}.json', {
                'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + stage_name}
            })
            selectors.setdefault('carrot', []).append({
                'when': 'voidscape:' + stage_name,
                'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + stage_name}
            })
            mappings['items'].setdefault('minecraft:carrot', []).append({
                'type': 'definition',
                'model': 'minecraft:carrot',
                'predicate': {'type': 'match', 'property': 'custom_model_data', 'index': 0, 'value': 'voidscape:' + stage_name},
                'bedrock_identifier': 'voidscape:' + stage_name,
                'display_name': f'{title} (Stage {stage})',
                'bedrock_options': {'icon': 'voidscape.' + stage_name, 'allow_offhand': True, 'display_handheld': False, 'creative_category': 'items'}
            })
