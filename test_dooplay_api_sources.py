#!/usr/bin/env python3
"""
Test DooPlay API with different source numbers
"""
import requests
import json

POST_ID = "13881"  # Beretta EP02
CONTENT_TYPE = "episodes"

print("Testing DooPlay API with different source numbers")
print("=" * 80)

for source in range(1, 10):
    api_url = f"https://farsiplex.com/wp-json/dooplayer/v2/{POST_ID}/{CONTENT_TYPE}/{source}"

    try:
        response = requests.get(api_url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
        })

        if response.status_code == 200:
            data = response.json()
            embed_url = data.get('embed_url')
            type_val = data.get('type')

            if embed_url or type_val:
                print(f"\nSource {source}: SUCCESS")
                print(f"  embed_url: {embed_url}")
                print(f"  type: {type_val}")
            else:
                print(f"Source {source}: Empty (null/false)")
        else:
            print(f"Source {source}: ERROR {response.status_code}")

    except Exception as e:
        print(f"Source {source}: EXCEPTION {e}")

print("\n" + "=" * 80)
print("Now checking what the page itself uses...")
print("=" * 80)

# Check the actual page for player data
response = requests.get("https://farsiplex.com/episode/beretta-dastane-yek-aslahe-ep02/")
html = response.text

# Look for data-post attributes
import re
data_post_matches = re.findall(r'data-post="(\d+)"', html)
data_nume_matches = re.findall(r'data-nume="(\d+)"', html)
data_playerid_matches = re.findall(r'data-playerid="(\d+)"', html)

print(f"\nFound in HTML:")
print(f"  data-post values: {set(data_post_matches)}")
print(f"  data-nume values: {set(data_nume_matches)}")
print(f"  data-playerid values: {set(data_playerid_matches)}")

# Look for player list items
player_items = re.findall(r'<li[^>]*data-post="13881"[^>]*data-nume="(\d+)"[^>]*>(.*?)</li>', html, re.DOTALL)
if player_items:
    print(f"\nPlayer quality options found:")
    for nume, content in player_items:
        # Extract quality text
        quality_match = re.search(r'<span[^>]*>(.*?)</span>', content)
        quality = quality_match.group(1) if quality_match else "Unknown"
        print(f"  Source {nume}: {quality}")
