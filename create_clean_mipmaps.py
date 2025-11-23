#!/usr/bin/env python3
"""
Create clean mipmap icons with dark background removed
This is what the launcher favorites row actually uses!
"""

from PIL import Image
import os

LOGOS = {
    'embroidery': 'logo_embroidery.png',
    'origami': 'logo_origami.png',
    'pixel': 'logo_pixel.png',
    'watercolor': 'logo_watercolor.png'
}

MIPMAP_SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

# LIGHT background for dark logos
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

def create_clean_mipmap(logo_path, logo_name, output_dir):
    """Create mipmap with dark background removed"""

    with Image.open(logo_path) as logo:
        # Remove dark background
        logo_clean = remove_dark_background(logo, threshold=120)

        # Crop to content
        bbox = logo_clean.getbbox()
        if bbox:
            logo_cropped = logo_clean.crop(bbox)
        else:
            logo_cropped = logo_clean

        # Create all mipmap densities
        for density, size in MIPMAP_SIZES.items():
            # Create light background square
            mipmap = Image.new('RGBA', (size, size), BACKGROUND_COLOR)

            # Resize logo to fit (use 85% of size)
            logo_size = int(size * 0.85)
            aspect_ratio = logo_cropped.width / logo_cropped.height
            if aspect_ratio > 1:
                # Wider than tall
                logo_width = logo_size
                logo_height = int(logo_size / aspect_ratio)
            else:
                # Taller than wide
                logo_height = logo_size
                logo_width = int(logo_size * aspect_ratio)

            logo_resized = logo_cropped.resize((logo_width, logo_height), Image.Resampling.LANCZOS)

            # Center logo
            x = (size - logo_width) // 2
            y = (size - logo_height) // 2

            # Paste logo
            mipmap.paste(logo_resized, (x, y), logo_resized)

            # Save as WebP
            mipmap_dir = os.path.join(output_dir, f'mipmap-{density}')
            os.makedirs(mipmap_dir, exist_ok=True)

            output_path = os.path.join(mipmap_dir, f'ic_launcher_{logo_name}.webp')
            mipmap.save(output_path, 'WEBP', quality=90, method=6)

            file_size = os.path.getsize(output_path)
            print(f"    {density:8} {size:3}x{size:<3} | {file_size:>6,} bytes")

if __name__ == '__main__':
    output = 'phase_b_mipmap_clean'

    print("=" * 70)
    print("Creating Clean Mipmaps (Dark Background Removed)")
    print("=" * 70)

    for logo_name, logo_file in LOGOS.items():
        if not os.path.exists(logo_file):
            print(f"\n{logo_name}: [ERROR] Not found")
            continue

        print(f"\n{logo_name.upper()}:")
        create_clean_mipmap(logo_file, logo_name, output)

    print("\n" + "=" * 70)
    print("[SUCCESS] Clean mipmaps created!")
    print("\nThese are what Android TV favorites row actually uses!")
    print("Light background + transparent logo = VISIBLE on launcher")
    print("=" * 70)
