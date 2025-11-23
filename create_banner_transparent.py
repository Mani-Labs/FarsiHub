#!/usr/bin/env python3
"""
Create TV banners with proper transparency handling
Removes dark background from logos and creates clean banners
"""

from PIL import Image, ImageDraw
import os

LOGOS = {
    'embroidery': 'logo_embroidery.png',
    'origami': 'logo_origami.png',
    'pixel': 'logo_pixel.png',
    'watercolor': 'logo_watercolor.png'
}

BANNER_WIDTH = 464
BANNER_HEIGHT = 260
BACKGROUND_COLOR = (240, 240, 245, 255)  # Light grey
CORNER_RADIUS = 16

def remove_dark_background(img, threshold=120):
    """
    Remove dark background from logo by making dark pixels transparent

    Args:
        img: PIL Image
        threshold: Brightness threshold (pixels darker than this become transparent)

    Returns:
        Image with transparent background
    """
    # Convert to RGBA if needed
    if img.mode != 'RGBA':
        img = img.convert('RGBA')

    # Get pixel data
    pixels = img.load()
    width, height = img.size

    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]

            # Calculate brightness
            brightness = (r + g + b) / 3

            # If pixel is dark (likely background), make it transparent
            if brightness < threshold:
                pixels[x, y] = (r, g, b, 0)  # Set alpha to 0 (transparent)

    return img

def create_rounded_rectangle(size, radius, color):
    """Create rounded rectangle background"""
    width, height = size
    img = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle([(0, 0), (width, height)], radius=radius, fill=color)
    return img

def create_clean_banner(logo_path, logo_name, output_dir):
    """
    Create banner with dark background removed from logo
    """
    # Open and process logo
    with Image.open(logo_path) as logo:
        print(f"  Processing {logo_name}...")

        # Remove dark background
        logo_clean = remove_dark_background(logo, threshold=120)

        # Get bounding box of non-transparent pixels
        bbox = logo_clean.getbbox()
        if bbox:
            # Crop to actual logo content
            logo_cropped = logo_clean.crop(bbox)
        else:
            logo_cropped = logo_clean

        # Create rounded background
        banner = create_rounded_rectangle(
            (BANNER_WIDTH, BANNER_HEIGHT),
            CORNER_RADIUS,
            BACKGROUND_COLOR
        )

        # Calculate logo size (80% of banner height)
        max_logo_height = int(BANNER_HEIGHT * 0.80)
        aspect_ratio = logo_cropped.width / logo_cropped.height
        logo_height = max_logo_height
        logo_width = int(logo_height * aspect_ratio)

        # Resize logo
        logo_resized = logo_cropped.resize((logo_width, logo_height), Image.Resampling.LANCZOS)

        # Center logo on banner
        x = (BANNER_WIDTH - logo_width) // 2
        y = (BANNER_HEIGHT - logo_height) // 2

        # Paste logo using its alpha channel as mask
        banner.paste(logo_resized, (x, y), logo_resized)

        # Save banners
        os.makedirs(output_dir, exist_ok=True)

        # Save 464x260 banner
        banner_path = os.path.join(output_dir, f'tv_banner_{logo_name}.png')
        banner.save(banner_path, 'PNG', optimize=True)

        # Save 432x243 version
        app_icon = banner.resize((432, 243), Image.Resampling.LANCZOS)
        icon_path = os.path.join(output_dir, f'app_icon_{logo_name}.png')
        app_icon.save(icon_path, 'PNG', optimize=True)

        banner_size = os.path.getsize(banner_path)
        icon_size = os.path.getsize(icon_path)

        return banner_size, icon_size, logo_width, logo_height

if __name__ == '__main__':
    output = 'phase_b_banner_clean'

    print("=" * 70)
    print("Creating Clean Banners (Dark Background Removed)")
    print("=" * 70)

    for logo_name, logo_file in LOGOS.items():
        if not os.path.exists(logo_file):
            print(f"\n{logo_name}: [ERROR] {logo_file} not found!")
            continue

        print(f"\n{logo_name.upper()}:")
        banner_size, icon_size, lw, lh = create_clean_banner(
            logo_file, logo_name, output
        )

        print(f"  [OK] tv_banner:  {BANNER_WIDTH}x{BANNER_HEIGHT} | Logo: {lw}x{lh} | {banner_size:,} bytes")
        print(f"  [OK] app_icon:   432x243 | {icon_size:,} bytes")

    print("\n" + "=" * 70)
    print("[SUCCESS] Clean banners created!")
    print("\nFixed:")
    print("  - Dark background removed from logos")
    print("  - Logos cropped to actual content")
    print("  - Transparent logos on light banner background")
    print("  - No more dark squares!")
    print("=" * 70)
