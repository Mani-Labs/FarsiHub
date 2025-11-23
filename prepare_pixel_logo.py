#!/usr/bin/env python3
"""
Script to prepare Pixel logo for Android mipmap folders
Resizes logo_pixel.png to 5 densities and converts to WebP
"""

from PIL import Image
import os

# Android launcher icon standard sizes
MIPMAP_SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

def resize_and_convert_logo(source_path, output_dir):
    """
    Resize logo to all mipmap densities and convert to WebP

    Args:
        source_path: Path to source logo_pixel.png
        output_dir: Directory to save resized WebP files
    """
    print(f"Loading source image: {source_path}")

    # Open source image
    with Image.open(source_path) as img:
        print(f"Source image size: {img.size}")
        print(f"Source image mode: {img.mode}")

        # Convert to RGBA if not already (for transparency support)
        if img.mode != 'RGBA':
            img = img.convert('RGBA')

        # Create output directory if it doesn't exist
        os.makedirs(output_dir, exist_ok=True)

        # Resize and save for each density
        for density, size in MIPMAP_SIZES.items():
            # Resize using high-quality Lanczos resampling
            resized = img.resize((size, size), Image.Resampling.LANCZOS)

            # Output path
            output_path = os.path.join(output_dir, f'ic_launcher_{density}.webp')

            # Save as WebP with high quality
            resized.save(output_path, 'WEBP', quality=90, method=6)

            # Get file size
            file_size = os.path.getsize(output_path)
            print(f"[OK] Created {density}: {size}x{size}px -> {output_path} ({file_size:,} bytes)")

if __name__ == '__main__':
    source = 'logo_pixel.png'
    output = 'prepared_logos'

    if not os.path.exists(source):
        print(f"ERROR: {source} not found!")
        exit(1)

    print("=" * 60)
    print("Preparing Pixel Logo for Android Mipmap Folders")
    print("=" * 60)

    resize_and_convert_logo(source, output)

    print("\n" + "=" * 60)
    print("[SUCCESS] Logo preparation complete!")
    print(f"WebP files saved to: {output}/")
    print("=" * 60)
