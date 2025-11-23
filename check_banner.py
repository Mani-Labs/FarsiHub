from PIL import Image

img = Image.open('app/src/main/res/drawable/tv_banner.png')

# Check corner (should be background)
corner = img.getpixel((10, 10))
print(f"Corner (background): {corner}")

# Check center (should be logo)
cx, cy = img.width // 2, img.height // 2
center = img.getpixel((cx, cy))
print(f"Center (logo area): {center}")

# Compare
if corner == center:
    print("\n[PROBLEM] Center = Background - LOGO IS MISSING!")
else:
    print("\n[OK] Logo pixels detected")

# Check a few more spots
samples = [
    (cx - 50, cy),
    (cx + 50, cy),
    (cx, cy - 50),
    (cx, cy + 50)
]

logo_pixels = 0
for x, y in samples:
    if img.getpixel((x, y)) != corner:
        logo_pixels += 1

print(f"\nLogo pixels found: {logo_pixels}/4 sample points")
