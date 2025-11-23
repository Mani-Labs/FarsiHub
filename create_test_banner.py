#!/usr/bin/env python3
"""
Create TEST banner with visible border and measurements
This will show us EXACTLY what area the launcher displays
"""

from PIL import Image, ImageDraw, ImageFont

# Try different sizes
SIZES = [
    (320, 180, "Standard"),
    (240, 135, "Small"),
    (160, 90, "Tiny"),
]

def create_test_banner(width, height, label):
    """Create test banner with visible border and size label"""

    # Create banner
    banner = Image.new('RGB', (width, height), (240, 240, 245))  # Light grey
    draw = ImageDraw.Draw(banner)

    # Draw RED border (5px thick)
    border_width = 5
    draw.rectangle(
        [0, 0, width-1, height-1],
        outline=(255, 0, 0),
        width=border_width
    )

    # Draw BLUE border inside (to see if corners are cropped)
    draw.rectangle(
        [10, 10, width-11, height-11],
        outline=(0, 0, 255),
        width=3
    )

    # Draw size text in center
    text = f"{width}x{height}\n{label}"

    # Calculate text position (rough center)
    text_bbox = draw.textbbox((0, 0), text)
    text_width = text_bbox[2] - text_bbox[0]
    text_height = text_bbox[3] - text_bbox[1]

    text_x = (width - text_width) // 2
    text_y = (height - text_height) // 2

    draw.text((text_x, text_y), text, fill=(0, 0, 0))

    return banner

# Create test banners
for width, height, label in SIZES:
    banner = create_test_banner(width, height, label)
    filename = f'test_banner_{width}x{height}.png'
    banner.save(filename, 'PNG')
    print(f"Created: {filename}")

print("\nNow copy test_banner_320x180.png to tv_banner.png and see:")
print("  - RED border visible? = Full banner showing")
print("  - BLUE border visible? = Corners not cropped")
print("  - Only WHITE corner? = Banner too large, launcher cropping")
print("  - Size text visible? = Can see center of banner")
