#!/usr/bin/env python3
"""
Resize high-res banners to 1280x720 (correct xxxhdpi size)
Android TV banner spec: 320x180 dp × 4 (xxxhdpi) = 1280x720 px
"""

from PIL import Image
import os

# Source banners
BANNERS = {
    'pixel': 'banner_pixel.png',
    'embroidery': 'banner_embroidery.png',
    'watercolor': 'banner_watercolor.png',
}

TARGET_WIDTH = 1280
TARGET_HEIGHT = 720

def resize_banner(source_path, logo_name, output_dir):
    """Resize banner to 1280x720"""

    with Image.open(source_path) as img:
        print(f"  {logo_name}: {img.size} -> {TARGET_WIDTH}x{TARGET_HEIGHT}")

        # Resize with high-quality Lanczos
        resized = img.resize((TARGET_WIDTH, TARGET_HEIGHT), Image.Resampling.LANCZOS)

        # Save
        os.makedirs(output_dir, exist_ok=True)
        output_path = os.path.join(output_dir, f'tv_banner_{logo_name}.png')
        resized.save(output_path, 'PNG', optimize=True)

        # Also save as app_icon
        icon_path = os.path.join(output_dir, f'app_icon_{logo_name}.png')
        resized.save(icon_path, 'PNG', optimize=True)

        file_size = os.path.getsize(output_path)
        return file_size

# Also create origami from logo file
def create_origami_banner(logo_path, output_dir):
    """Create 1280x720 banner from logo_origami.png"""

    with Image.open(logo_path) as logo:
        print(f"  origami: Creating from {logo_path} ({logo.size})")

        # Create banner with same style as others (assuming they have backgrounds)
        # For now, just resize the logo file if it exists
        resized = logo.resize((TARGET_WIDTH, TARGET_HEIGHT), Image.Resampling.LANCZOS)

        os.makedirs(output_dir, exist_ok=True)
        output_path = os.path.join(output_dir, 'tv_banner_origami.png')
        resized.save(output_path, 'PNG', optimize=True)

        icon_path = os.path.join(output_dir, 'app_icon_origami.png')
        resized.save(icon_path, 'PNG', optimize=True)

        file_size = os.path.getsize(output_path)
        return file_size

if __name__ == '__main__':
    output = 'phase_b_banners_1280x720'

    print("=" * 70)
    print("Resizing Banners to 1280x720 (xxxhdpi Android TV standard)")
    print("=" * 70)
    print()

    for logo_name, banner_file in BANNERS.items():
        if not os.path.exists(banner_file):
            print(f"{logo_name}: [SKIP] {banner_file} not found")
            continue

        file_size = resize_banner(banner_file, logo_name, output)
        print(f"    [OK] {file_size:,} bytes")

    # Try to create origami
    if os.path.exists('logo_origami.png'):
        file_size = create_origami_banner('logo_origami.png', output)
        print(f"    [OK] {file_size:,} bytes")
    else:
        print("  origami: [SKIP] Source not found")

    print()
    print("=" * 70)
    print("[SUCCESS] Banners resized to 1280x720!")
    print(f"\nSaved to: {output}/")
    print("\nCorrect size for:")
    print("  - Nvidia Shield TV (xxxhdpi, 640 dpi)")
    print("  - 1080p Android TV devices")
    print("  - 320x180 dp × 4 = 1280x720 px")
    print("=" * 70)
