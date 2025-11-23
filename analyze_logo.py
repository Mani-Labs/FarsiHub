#!/usr/bin/env python3
from PIL import Image
import sys

logo_path = 'logo_pixel.png'

img = Image.open(logo_path)
print(f"Image size: {img.size}")
print(f"Image mode: {img.mode}")

# Sample center area
cx, cy = img.width // 2, img.height // 2
sample_size = 100
sample = img.crop((cx - sample_size, cy - sample_size, cx + sample_size, cy + sample_size))

# Get average color
pixels = list(sample.getdata())
if img.mode == 'RGBA':
    avg_r = sum(p[0] for p in pixels) / len(pixels)
    avg_g = sum(p[1] for p in pixels) / len(pixels)
    avg_b = sum(p[2] for p in pixels) / len(pixels)
    avg_a = sum(p[3] for p in pixels) / len(pixels)
    print(f"\nCenter area average color: RGB({avg_r:.0f}, {avg_g:.0f}, {avg_b:.0f})")
    print(f"Average alpha: {avg_a:.0f}")
else:
    avg_r = sum(p[0] for p in pixels) / len(pixels)
    avg_g = sum(p[1] for p in pixels) / len(pixels)
    avg_b = sum(p[2] for p in pixels) / len(pixels)
    print(f"\nCenter area average color: RGB({avg_r:.0f}, {avg_g:.0f}, {avg_b:.0f})")

brightness = (avg_r + avg_g + avg_b) / 3
print(f"Average brightness: {brightness:.0f}/255")

if brightness < 100:
    print("\n[PROBLEM] Logo is DARK (brightness < 100)")
    print("Dark logo on dark background = INVISIBLE!")
elif brightness > 200:
    print("\n[OK] Logo is BRIGHT")
else:
    print("\n[WARNING] Logo is MID-TONE")
