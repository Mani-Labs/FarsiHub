#!/usr/bin/env python3
"""
Create STANDARD Android TV banner (320x180 - EXACT spec)
The launcher is cropping our 464x260 banners - use exact standard size
"""

from PIL import Image, ImageDraw
import os

LOGOS = {
    'embroidery': 'logo_embroidery.png',
    'origami': 'logo_origami.png',
    'pixel': 'logo_pixel.png',
    'watercolor': 'logo_watercolor.png'
}

# EXACT Android TV standard banner size
BANNER_WIDTH = 320
BANNER_HEIGHT = 180

# Light background
BACKGROUND_COLOR = (240, 240, 245, 255)

def remove_dark_background(img, threshold=120):
    """Remove dark background from logo"""
    if img.mode != 'RGBA':
        img = img.convert('RGBA')

    pixels = img.load()
    width, height = img.size

    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            brightness = (r + g + b) / 3

            if brightness < threshold:
                pixels[x, y] = (r, g, b, 0)

    return img

def create_standard_banner(logo_path, logo_name, output_dir):
    """
    Create EXACT 320x180 Android TV banner
    """
    with Image.open(logo_path) as logo:
        print(f"  Processing {logo_name}...")

        # Remove dark background
        logo_clean = remove_dark_background(logo, threshold=120)

        # Crop to content
        bbox = logo_clean.getbbox()
        if bbox:
            logo_cropped = logo_clean.crop(bbox)
        else:
            logo_cropped = logo_clean

        # Create light background (no rounded corners - might cause cropping issues)
        banner = Image.new('RGBA', (BANNER_WIDTH, BANNER_HEIGHT), BACKGROUND_COLOR)

        # Make logo FILL most of the banner (90% of height)
        max_logo_height = int(BANNER_HEIGHT * 0.90)
        aspect_ratio = logo_cropped.width / logo_cropped.height
        logo_height = max_logo_height
        logo_width = int(logo_height * aspect_ratio)

        # Resize logo
        logo_resized = logo_cropped.resize((logo_width, logo_height), Image.Resampling.LANCZOS)

        # Center logo
        x = (BANNER_WIDTH - logo_width) // 2
        y = (BANNER_HEIGHT - logo_height) // 2

        # Paste logo
        banner.paste(logo_resized, (x, y), logo_resized)

        # Save
        os.makedirs(output_dir, exist_ok=True)

        # Save EXACT 320x180
        banner_path = os.path.join(output_dir, f'tv_banner_{logo_name}.png')
        banner.save(banner_path, 'PNG', optimize=True)

        # Also create 432x243 for app_icon (same process)
        banner_large = Image.new('RGBA', (432, 243), BACKGROUND_COLOR)
        max_logo_height_large = int(243 * 0.90)
        logo_height_large = max_logo_height_large
        logo_width_large = int(logo_height_large * aspect_ratio)
        logo_resized_large = logo_cropped.resize((logo_width_large, logo_height_large), Image.Resampling.LANCZOS)
        x_large = (432 - logo_width_large) // 2
        y_large = (243 - logo_height_large) // 2
        banner_large.paste(logo_resized_large, (x_large, y_large), logo_resized_large)

        icon_path = os.path.join(output_dir, f'app_icon_{logo_name}.png')
        banner_large.save(icon_path, 'PNG', optimize=True)

        banner_size = os.path.getsize(banner_path)
        icon_size = os.path.getsize(icon_path)

        return banner_size, icon_size, logo_width, logo_height

if __name__ == '__main__':
    output = 'phase_b_banner_standard'

    print("=" * 70)
    print("Creating STANDARD Android TV Banners (320x180 EXACT)")
    print("=" * 70)

    for logo_name, logo_file in LOGOS.items():
        if not os.path.exists(logo_file):
            print(f"\n{logo_name}: [ERROR] {logo_file} not found!")
            continue

        print(f"\n{logo_name.upper()}:")
        banner_size, icon_size, lw, lh = create_standard_banner(
            logo_file, logo_name, output
        )

        print(f"  [OK] tv_banner:  320x180 (STANDARD) | Logo: {lw}x{lh} | {banner_size:,} bytes")
        print(f"  [OK] app_icon:   432x243 | {icon_size:,} bytes")

    print("\n" + "=" * 70)
    print("[SUCCESS] Standard size banners created!")
    print("\nFixed:")
    print("  - EXACT 320x180 size (Android TV standard)")
    print("  - No rounded corners (avoids cropping)")
    print("  - Logo fills 90% of height")
    print("  - Should display FULL banner, not cropped corner")
    print("=" * 70)
