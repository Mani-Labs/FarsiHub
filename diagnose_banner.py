#!/usr/bin/env python3
"""
Create diagnostic banners to identify the exact issue
"""

from PIL import Image, ImageDraw

# Test different background approaches
def create_diagnostic_banners():
    """Create multiple test banners to diagnose the issue"""

    width, height = 160, 90

    # Test 1: Pure white background
    banner1 = Image.new('RGB', (width, height), (255, 255, 255))
    banner1.save('test_white_bg.png', 'PNG')

    # Test 2: Pure grey background (what we're using)
    banner2 = Image.new('RGB', (width, height), (240, 240, 245))
    banner2.save('test_grey_bg.png', 'PNG')

    # Test 3: Gradient to see if it displays correctly
    banner3 = Image.new('RGB', (width, height), (0, 0, 0))
    draw = ImageDraw.Draw(banner3)
    for y in range(height):
        gray_value = int((y / height) * 255)
        draw.line([(0, y), (width, y)], fill=(gray_value, gray_value, gray_value))
    banner3.save('test_gradient.png', 'PNG')

    # Test 4: Actual logo file check
    logo = Image.open('logo_pixel.png')
    print(f"Source logo size: {logo.size}")
    print(f"Source logo mode: {logo.mode}")

    # Check if logo has transparency in source
    if logo.mode == 'RGBA':
        print("Source has alpha channel")
    else:
        print("Source is RGB only (no transparency)")

    # Sample some pixels
    samples = [
        (0, 0, "Top-left corner"),
        (logo.width//2, logo.height//2, "Center"),
        (logo.width-1, logo.height-1, "Bottom-right corner")
    ]

    print("\nSource logo pixel samples:")
    for x, y, label in samples:
        pixel = logo.getpixel((x, y))
        print(f"  {label}: {pixel}")

    print("\nCreated diagnostic banners:")
    print("  test_white_bg.png - Pure white")
    print("  test_grey_bg.png - Grey (current background color)")
    print("  test_gradient.png - Black to white gradient")
    print("\nCopy test_white_bg.png to tv_banner.png and check:")
    print("  - If WHITE shows = banner displaying correctly")
    print("  - If still looks wrong = issue with banner file itself")

create_diagnostic_banners()
