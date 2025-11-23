#!/usr/bin/env python3
"""
Script to prepare ALL 4 logos for Android mipmap folders
Resizes logo_*.png files to 5 densities and converts to WebP
"""

from PIL import Image
import os

# Logo files to process
LOGOS = [
    'logo_embroidery.png',
    'logo_origami.png',
    'logo_pixel.png',
    'logo_watercolor.png'
]

# Android launcher icon standard sizes
MIPMAP_SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

def resize_and_convert_logo(source_path, logo_name, output_dir):
    """
    Resize logo to all mipmap densities and convert to WebP

    Args:
        source_path: Path to source logo PNG file
        logo_name: Name identifier (e.g., 'embroidery', 'pixel')
        output_dir: Directory to save resized WebP files
    """
    print(f"\nProcessing: {source_path}")

    # Open source image
    with Image.open(source_path) as img:
        print(f"  Source size: {img.size}")
        print(f"  Source mode: {img.mode}")

        # Convert to RGBA if not already (for transparency support)
        if img.mode != 'RGBA':
            img = img.convert('RGBA')

        # Create output directory for this logo
        logo_output_dir = os.path.join(output_dir, logo_name)
        os.makedirs(logo_output_dir, exist_ok=True)

        total_size = 0

        # Resize and save for each density
        for density, size in MIPMAP_SIZES.items():
            # Resize using high-quality Lanczos resampling
            resized = img.resize((size, size), Image.Resampling.LANCZOS)

            # Output path
            output_path = os.path.join(logo_output_dir, f'ic_launcher_{density}.webp')

            # Save as WebP with high quality
            resized.save(output_path, 'WEBP', quality=90, method=6)

            # Get file size
            file_size = os.path.getsize(output_path)
            total_size += file_size
            print(f"  [OK] {density:8} {size:3}x{size:<3} -> {file_size:>6,} bytes")

        print(f"  Total size: {total_size:,} bytes ({total_size/1024:.1f} KB)")

if __name__ == '__main__':
    output = 'prepared_logos_all'

    print("=" * 70)
    print("Preparing ALL Logos for Android Mipmap Folders (Phase B)")
    print("=" * 70)

    # Check all logos exist
    missing_logos = [logo for logo in LOGOS if not os.path.exists(logo)]
    if missing_logos:
        print(f"\nERROR: Missing logo files:")
        for logo in missing_logos:
            print(f"  - {logo}")
        exit(1)

    print(f"\nFound all {len(LOGOS)} logo files")
    print(f"Output directory: {output}/\n")

    # Process each logo
    for logo_file in LOGOS:
        # Extract logo name (e.g., 'logo_embroidery.png' -> 'embroidery')
        logo_name = logo_file.replace('logo_', '').replace('.png', '')
        resize_and_convert_logo(logo_file, logo_name, output)

    print("\n" + "=" * 70)
    print("[SUCCESS] All logos prepared!")
    print(f"\nWebP files organized by logo type in: {output}/")
    print("\nDirectory structure:")
    print(f"  {output}/")
    print("    embroidery/")
    print("      ic_launcher_mdpi.webp ... ic_launcher_xxxhdpi.webp")
    print("    origami/")
    print("      ic_launcher_mdpi.webp ... ic_launcher_xxxhdpi.webp")
    print("    pixel/")
    print("      ic_launcher_mdpi.webp ... ic_launcher_xxxhdpi.webp")
    print("    watercolor/")
    print("      ic_launcher_mdpi.webp ... ic_launcher_xxxhdpi.webp")
    print("\nTotal: 20 WebP files (4 logos x 5 densities)")
    print("=" * 70)
