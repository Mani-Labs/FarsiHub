#!/usr/bin/env python3
"""
Analyze missing episodes to find their parent TV shows
"""

import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import sqlite3
from collections import defaultdict

missing_episodes = [
    'https://farsiplex.com/episode/shab-ahangi-se03-ep24-c86c3fc3/',
    'https://farsiplex.com/episode/gerye-nakon-istanbul-ep01-a9490669/',
    'https://farsiplex.com/episode/sedato-se03-ep05-912956b0/',
    'https://farsiplex.com/episode/jazromad-ep01-93c1b76c/',
    'https://farsiplex.com/episode/jazromad-ep02-912d7562/',
    'https://farsiplex.com/episode/jazromad-ep03-e00d751b/',
    'https://farsiplex.com/episode/jazromad-ep04-084c134d/',
    'https://farsiplex.com/episode/karnaval-opening-new-f77f7f2c/',
    'https://farsiplex.com/episode/jazromad-ep05-5447b191/',
    'https://farsiplex.com/episode/jazromad-ep06-ef62794e/',
    'https://farsiplex.com/episode/jazromad-ep07-3bd1b66f/',
    'https://farsiplex.com/episode/jazromad-ep08-1ed012e7/',
    'https://farsiplex.com/episode/jazromad-ep09-1fa10d95/',
    'https://farsiplex.com/episode/karnaval-ep1-369fc17f/',
    'https://farsiplex.com/episode/karnavaltar-opening-new-28197445/',
    'https://farsiplex.com/episode/jazromad-ep10-97785ddc/',
    'https://farsiplex.com/episode/jazromad-ep11-e01b80fa/',
    'https://farsiplex.com/episode/karnaval-ep2-efcbc8fc/',
    'https://farsiplex.com/episode/jazromad-ep12-9ac238cf/',
    'https://farsiplex.com/episode/jazromad-ep13-67da491b/',
    'https://farsiplex.com/episode/karnaval-ep3-4945c211/',
    'https://farsiplex.com/episode/jazromad-ep14-9437e35e/',
    'https://farsiplex.com/episode/jazromad-ep15-9a5a7a3e/',
    'https://farsiplex.com/episode/karnaval-ep4-c728b3ab/',
    'https://farsiplex.com/episode/jazromad-ep16-7c32c329/',
    'https://farsiplex.com/episode/jazromad-ep17-8800567d/',
    'https://farsiplex.com/episode/jazromad-ep18-49c0f8e6/',
    'https://farsiplex.com/episode/karnaval-ep5-4a14b9fa/',
    'https://farsiplex.com/episode/jazromad-ep19-9366dcf4/',
    'https://farsiplex.com/episode/karnaval-ep6-656444f7/',
    'https://farsiplex.com/episode/jazromad-ep20-e6905e93/',
    'https://farsiplex.com/episode/karnaval-ep7-3b4c7f65/',
    'https://farsiplex.com/episode/jazromad-ep21-4d6d3486/',
    'https://farsiplex.com/episode/karnaval-ep8-f3cc0325/',
    'https://farsiplex.com/episode/jazromad-ep22-a0f8dbdf/',
    'https://farsiplex.com/episode/karnaval-ep9-185fed96/',
    'https://farsiplex.com/episode/karnaval-ep-10-a78d644c/',
    'https://farsiplex.com/episode/karnaval-ep-11-e1983758/',
    'https://farsiplex.com/episode/karnaval-ep-12-18985a45/',
    'https://farsiplex.com/episode/jazromad-ep23-eaf873a8/',
    'https://farsiplex.com/episode/jazromad-ep24-40d2d5be/',
    'https://farsiplex.com/episode/karnaval-ep-13-087e607a/',
    'https://farsiplex.com/episode/karnaval-ep-14-62128add/',
    'https://farsiplex.com/episode/mahkoum-ep09-faba1aa7/',
    'https://farsiplex.com/episode/karnaval-ep-15-b1e75375/',
    'https://farsiplex.com/episode/karnaval-16/',
]

# Extract show name from episode URL
show_episodes = defaultdict(list)
for ep_url in missing_episodes:
    # Extract slug part
    slug = ep_url.split('/episode/')[-1].rstrip('/')

    # Try to extract show name
    # Format: showname-ep01-hash or showname-se02-ep01-hash
    parts = slug.split('-')

    # Find where episode info starts (ep01, se02, etc.)
    show_name_parts = []
    for part in parts:
        if part.startswith('ep') or part.startswith('se'):
            break
        show_name_parts.append(part)

    show_name = '-'.join(show_name_parts) if show_name_parts else 'unknown'
    show_episodes[show_name].append(ep_url)

print(f"Missing episodes grouped by show:\n")
for show_name, episodes in sorted(show_episodes.items()):
    print(f"{show_name}: {len(episodes)} episodes")
    for ep in episodes[:3]:  # Show first 3
        print(f"  - {ep}")
    if len(episodes) > 3:
        print(f"  ... and {len(episodes) - 3} more")
    print()

# Check database for these shows
db_path = 'G:/FarsiPlex/Farsiplex.db'
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

print("\nChecking which shows exist in database:")
for show_name in sorted(show_episodes.keys()):
    # Search for show in database
    cursor.execute("SELECT slug, title, url FROM tvshows WHERE slug LIKE ?", (f'%{show_name}%',))
    results = cursor.fetchall()

    if results:
        for slug, title, url in results:
            print(f"  FOUND: {show_name} -> {title} ({slug})")
            print(f"         URL: {url}")

            # Count episodes in database
            tvshow_id = hash(slug) % (10 ** 8)
            cursor.execute("SELECT COUNT(*) FROM episodes WHERE tvshow_id = ?", (tvshow_id,))
            ep_count = cursor.fetchone()[0]
            print(f"         Episodes in DB: {ep_count}")
    else:
        print(f"  NOT FOUND: {show_name}")

conn.close()

print("\n\nRECOMMENDATION:")
print("The missing episodes belong to shows that may not be fully scraped.")
print("We should re-scrape the entire TV shows that contain these episodes.")
