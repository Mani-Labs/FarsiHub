#!/usr/bin/env python3
"""
Create proper Android TV banner (320x180) from Pixel logo
This is the standard TV banner size that appears in the launcher
"""

from PIL import Image, ImageDraw

# Standard Android TV banner size
BANNER_WIDTH = 320
BANNER_HEIGHT = 180

# Dark background to match Android TV UI
BACKGROUND_COLOR = (18, 18, 18, 255)

def create_proper_tv_banner(logo_path, output_path):
    """
    Create standard Android TV banner with logo

    Args:
        logo_path: Path to source Pixel logo
        output_path: Path to save banner PNG
    """
    print(f"Creating proper TV banner from: {logo_path}")

    # Open source logo
    with Image.open(logo_path) as logo:
        print(f"  Source logo size: {logo.size}")

        # Convert to RGBA
        if logo.mode != 'RGBA':
            logo = logo.convert('RGBA')

        # Create banner with dark background
        banner = Image.new('RGBA', (BANNER_WIDTH, BANNER_HEIGHT), BACKGROUND_COLOR)

        # Calculate logo size (use 70% of banner height)
        max_logo_height = int(BANNER_HEIGHT * 0.70)
        aspect_ratio = logo.width / logo.height
        logo_height = max_logo_height
        logo_width = int(logo_height * aspect_ratio)

        # Resize logo
        logo_resized = logo.resize((logo_width, logo_height), Image.Resampling.LANCZOS)

        # Center logo on banner
        x = (BANNER_WIDTH - logo_width) // 2
        y = (BANNER_HEIGHT - logo_height) // 2

        # Paste logo (with alpha channel)
        banner.paste(logo_resized, (x, y), logo_resized)

        # Save as PNG
        banner.save(output_path, 'PNG', optimize=True)

        import os
        file_size = os.path.getsize(output_path)
        print(f"  [OK] Banner: {BANNER_WIDTH}x{BANNER_HEIGHT}")
        print(f"  [OK] Logo: {logo_width}x{logo_height} at ({x}, {y})")
        print(f"  [OK] Saved: {output_path} ({file_size:,} bytes)")

if __name__ == '__main__':
    create_proper_tv_banner(
        'logo_pixel.png',
        'app/src/main/res/drawable/tv_banner.png'
    )
    print("\n[SUCCESS] TV banner created!")
