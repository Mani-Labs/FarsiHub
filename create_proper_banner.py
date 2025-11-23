#!/usr/bin/env python3
"""
Create proper Android TV banner that:
1. Uses LIGHT background for dark logos (visible contrast)
2. Larger size to fill launcher card completely
3. Rounded corners to match launcher style
"""

from PIL import Image, ImageDraw
import os

LOGOS = {
    'embroidery': 'logo_embroidery.png',
    'origami': 'logo_origami.png',
    'pixel': 'logo_pixel.png',
    'watercolor': 'logo_watercolor.png'
}

# Use larger banner size to fill Android TV launcher cards better
BANNER_WIDTH = 464   # Larger than standard 320
BANNER_HEIGHT = 260  # Larger than standard 180

# LIGHT background for dark logos (high contrast!)
BACKGROUND_COLOR = (240, 240, 245, 255)  # Very light grey/white

# Rounded corner radius
CORNER_RADIUS = 16

def create_rounded_rectangle(size, radius, color):
    """Create rounded rectangle background"""
    width, height = size
    img = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Draw rounded rectangle
    draw.rounded_rectangle(
        [(0, 0), (width, height)],
        radius=radius,
        fill=color
    )

    return img

def create_visible_banner(logo_path, logo_name, output_dir):
    """
    Create TV banner with LIGHT background for DARK logos

    Args:
        logo_path: Path to source logo
        logo_name: Logo identifier
        output_dir: Output directory
    """
    # Open source logo
    with Image.open(logo_path) as logo:
        # Convert to RGBA
        if logo.mode != 'RGBA':
            logo = logo.convert('RGBA')

        # Create rounded background
        banner = create_rounded_rectangle(
            (BANNER_WIDTH, BANNER_HEIGHT),
            CORNER_RADIUS,
            BACKGROUND_COLOR
        )

        # Make logo large (80% of height)
        max_logo_height = int(BANNER_HEIGHT * 0.80)
        aspect_ratio = logo.width / logo.height
        logo_height = max_logo_height
        logo_width = int(logo_height * aspect_ratio)

        # Resize logo
        logo_resized = logo.resize((logo_width, logo_height), Image.Resampling.LANCZOS)

        # Center logo
        x = (BANNER_WIDTH - logo_width) // 2
        y = (BANNER_HEIGHT - logo_height) // 2

        # Paste logo
        banner.paste(logo_resized, (x, y), logo_resized)

        # Save both banner sizes
        os.makedirs(output_dir, exist_ok=True)

        # Large banner (464x260) for tv_banner
        banner_path = os.path.join(output_dir, f'tv_banner_{logo_name}.png')
        banner.save(banner_path, 'PNG', optimize=True)

        # Also create 432x243 version for app_icon
        app_icon = banner.resize((432, 243), Image.Resampling.LANCZOS)
        icon_path = os.path.join(output_dir, f'app_icon_{logo_name}.png')
        app_icon.save(icon_path, 'PNG', optimize=True)

        banner_size = os.path.getsize(banner_path)
        icon_size = os.path.getsize(icon_path)

        return banner_size, icon_size, logo_width, logo_height

if __name__ == '__main__':
    output = 'phase_b_banner_assets_fixed'

    print("=" * 70)
    print("Creating VISIBLE TV Banners (Light Background for Dark Logos)")
    print("=" * 70)
    print(f"\nBackground: LIGHT (RGB 240,240,245) - High contrast!")
    print(f"Banner size: {BANNER_WIDTH}x{BANNER_HEIGHT} (fills launcher card)")
    print(f"Corner radius: {CORNER_RADIUS}px (rounded like launcher)")

    for logo_name, logo_file in LOGOS.items():
        print(f"\n{logo_name.upper()}:")

        if not os.path.exists(logo_file):
            print(f"  [ERROR] {logo_file} not found!")
            continue

        banner_size, icon_size, lw, lh = create_visible_banner(
            logo_file, logo_name, output
        )

        print(f"  [OK] tv_banner:  {BANNER_WIDTH}x{BANNER_HEIGHT} | Logo: {lw}x{lh} | {banner_size:,} bytes")
        print(f"  [OK] app_icon:   432x243 | {icon_size:,} bytes")

    print("\n" + "=" * 70)
    print("[SUCCESS] All banners created with LIGHT background!")
    print(f"\nSaved to: {output}/")
    print("\nFeatures:")
    print("  ✓ LIGHT background (240,240,245) - dark logos now VISIBLE")
    print("  ✓ Larger size (464x260) - fills launcher card completely")
    print("  ✓ Rounded corners (16px radius) - matches launcher style")
    print("  ✓ 80% logo size - prominent and clear")
    print("=" * 70)
