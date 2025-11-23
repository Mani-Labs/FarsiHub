#!/usr/bin/env python3
"""
Create FINAL banners at CORRECT size (160x90)
This is the actual size the Android TV launcher uses
"""

from PIL import Image
import os

LOGOS = {
    'embroidery': 'logo_embroidery.png',
    'origami': 'logo_origami.png',
    'pixel': 'logo_pixel.png',
    'watercolor': 'logo_watercolor.png'
}

# CORRECT size based on test
BANNER_WIDTH = 160
BANNER_HEIGHT = 90

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

def create_final_banner(logo_path, logo_name, output_dir):
    """Create final banner at correct 160x90 size"""

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

        # Create light background
        banner = Image.new('RGBA', (BANNER_WIDTH, BANNER_HEIGHT), BACKGROUND_COLOR)

        # Make logo large (90% of height for visibility)
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

        banner_path = os.path.join(output_dir, f'tv_banner_{logo_name}.png')
        banner.save(banner_path, 'PNG', optimize=True)

        # Also create app_icon version (keep 432x243 for MainActivity)
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
    output = 'phase_b_final_assets'

    print("=" * 70)
    print("Creating FINAL Banners (160x90 - CORRECT SIZE)")
    print("=" * 70)

    for logo_name, logo_file in LOGOS.items():
        if not os.path.exists(logo_file):
            print(f"\n{logo_name}: [ERROR] Not found")
            continue

        print(f"\n{logo_name.upper()}:")
        banner_size, icon_size, lw, lh = create_final_banner(
            logo_file, logo_name, output
        )

        print(f"  [OK] tv_banner:  160x90 (LAUNCHER SIZE) | Logo: {lw}x{lh} | {banner_size:,} bytes")
        print(f"  [OK] app_icon:   432x243 (MainActivity) | {icon_size:,} bytes")

    print("\n" + "=" * 70)
    print("[SUCCESS] Final banners at correct size!")
    print("\nBased on test banner - launcher expects 160x90")
    print("Light background + transparent logo = VISIBLE")
    print("=" * 70)
