#!/usr/bin/env python3
"""
Create ALL TV banners for Phase B logo selection
- Creates both 320x180 (tv_banner) and 432x243 (app_icon) sizes
- Makes logos MUCH LARGER and more visible
- Creates all 4 logo variants
"""

from PIL import Image
import os

# Logo files
LOGOS = {
    'embroidery': 'logo_embroidery.png',
    'origami': 'logo_origami.png',
    'pixel': 'logo_pixel.png',
    'watercolor': 'logo_watercolor.png'
}

# Android TV banner sizes
BANNER_SIZES = {
    'tv_banner': (320, 180),      # Standard TV banner
    'app_icon': (432, 243)         # MainActivity icon/logo
}

# Dark background
BACKGROUND_COLOR = (18, 18, 18, 255)

def create_banner(logo_path, logo_name, banner_type, output_dir):
    """
    Create TV banner with LARGE logo for visibility

    Args:
        logo_path: Path to source logo PNG
        logo_name: Logo identifier (embroidery, pixel, etc.)
        banner_type: 'tv_banner' or 'app_icon'
        output_dir: Directory to save banner
    """
    width, height = BANNER_SIZES[banner_type]

    # Open source logo
    with Image.open(logo_path) as logo:
        # Convert to RGBA
        if logo.mode != 'RGBA':
            logo = logo.convert('RGBA')

        # Create dark background
        banner = Image.new('RGBA', (width, height), BACKGROUND_COLOR)

        # Make logo MUCH LARGER - use 85% of banner height for better visibility
        max_logo_height = int(height * 0.85)
        aspect_ratio = logo.width / logo.height
        logo_height = max_logo_height
        logo_width = int(logo_height * aspect_ratio)

        # Resize logo (high quality)
        logo_resized = logo.resize((logo_width, logo_height), Image.Resampling.LANCZOS)

        # Center logo on banner
        x = (width - logo_width) // 2
        y = (height - logo_height) // 2

        # Paste logo with alpha channel
        banner.paste(logo_resized, (x, y), logo_resized)

        # Create output directory
        os.makedirs(output_dir, exist_ok=True)

        # Save banner
        output_filename = f"{banner_type}_{logo_name}.png"
        output_path = os.path.join(output_dir, output_filename)
        banner.save(output_path, 'PNG', optimize=True)

        file_size = os.path.getsize(output_path)
        return output_path, file_size, logo_width, logo_height

if __name__ == '__main__':
    output_base = 'phase_b_banner_assets'

    print("=" * 70)
    print("Creating ALL TV Banners for Phase B Logo Selection")
    print("=" * 70)

    for logo_name, logo_file in LOGOS.items():
        print(f"\n{logo_name.upper()} Logo:")

        if not os.path.exists(logo_file):
            print(f"  [ERROR] {logo_file} not found!")
            continue

        for banner_type in BANNER_SIZES.keys():
            path, size, logo_w, logo_h = create_banner(
                logo_file, logo_name, banner_type, output_base
            )
            banner_w, banner_h = BANNER_SIZES[banner_type]
            print(f"  [OK] {banner_type:12} {banner_w}x{banner_h} | Logo: {logo_w}x{logo_h} | {size:,} bytes")

    print("\n" + "=" * 70)
    print("[SUCCESS] All banners created!")
    print(f"\nBanners saved to: {output_base}/")
    print("\nFiles created:")
    print("  - tv_banner_embroidery.png    (320x180)")
    print("  - tv_banner_origami.png       (320x180)")
    print("  - tv_banner_pixel.png         (320x180)")
    print("  - tv_banner_watercolor.png    (320x180)")
    print("  - app_icon_embroidery.png     (432x243)")
    print("  - app_icon_origami.png        (432x243)")
    print("  - app_icon_pixel.png          (432x243)")
    print("  - app_icon_watercolor.png     (432x243)")
    print("\nTotal: 8 banner files (2 sizes × 4 logos)")
    print("=" * 70)
