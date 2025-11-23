#!/usr/bin/env python3
"""
Create Android TV banner (432x243) from Pixel logo
"""

from PIL import Image

# Android TV banner size
BANNER_WIDTH = 432
BANNER_HEIGHT = 243

# Background color (dark, to match Android TV UI)
BACKGROUND_COLOR = (18, 18, 18, 255)  # Dark grey, fully opaque

def create_tv_banner(logo_path, output_path):
    """
    Create TV banner with logo centered on dark background

    Args:
        logo_path: Path to source Pixel logo
        output_path: Path to save banner PNG
    """
    print(f"Creating TV banner from: {logo_path}")

    # Open source logo
    with Image.open(logo_path) as logo:
        print(f"  Source logo size: {logo.size}")

        # Convert to RGBA if needed
        if logo.mode != 'RGBA':
            logo = logo.convert('RGBA')

        # Create dark background
        banner = Image.new('RGBA', (BANNER_WIDTH, BANNER_HEIGHT), BACKGROUND_COLOR)

        # Calculate logo size to fit banner (maintain aspect ratio)
        # Leave some padding (use 80% of banner height)
        max_logo_height = int(BANNER_HEIGHT * 0.8)
        aspect_ratio = logo.width / logo.height
        logo_height = max_logo_height
        logo_width = int(logo_height * aspect_ratio)

        # Resize logo
        logo_resized = logo.resize((logo_width, logo_height), Image.Resampling.LANCZOS)

        # Calculate position to center logo
        x = (BANNER_WIDTH - logo_width) // 2
        y = (BANNER_HEIGHT - logo_height) // 2

        # Paste logo onto banner (using alpha channel for transparency)
        banner.paste(logo_resized, (x, y), logo_resized)

        # Save as PNG
        banner.save(output_path, 'PNG', optimize=True)

        file_size = __import__('os').path.getsize(output_path)
        print(f"  [OK] Banner created: {BANNER_WIDTH}x{BANNER_HEIGHT}")
        print(f"  [OK] Logo positioned: {logo_width}x{logo_height} at ({x}, {y})")
        print(f"  [OK] Saved to: {output_path} ({file_size:,} bytes)")

if __name__ == '__main__':
    create_tv_banner('logo_pixel.png', 'app/src/main/res/drawable/app_icon_your_company.png')
    print("\n[SUCCESS] TV banner updated with Pixel logo!")
