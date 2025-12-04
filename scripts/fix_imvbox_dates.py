#!/usr/bin/env python3
"""
Fast IMVBox Date Fix Script

Fixes dateAdded in existing imvbox_content.db by:
1. Fetching only listing pages (fast - no detail pages)
2. Building order map from URL slugs
3. Updating existing DB's dateAdded to match website order

Much faster than full re-scrape since no detail pages needed.
"""

import os
import re
import time
import sqlite3
import requests
from bs4 import BeautifulSoup
from pathlib import Path

# Configuration
BASE_URL = "https://www.imvbox.com/en"
MAX_PAGES = 200
RATE_LIMIT_SEC = 0.3  # Faster since we're only fetching list pages

# Use minimal DB (has correct schema without extra columns)
WORKING_DB = Path(__file__).parent / "imvbox_content_working.db"
TARGET_DB = Path("G:/FarsiPlex/app/src/main/assets/databases/imvbox_content.db")

# Session
session = requests.Session()
session.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0',
    'Accept': 'text/html,application/xhtml+xml',
})

last_request_time = 0


def fetch_url(url: str) -> str | None:
    """Fetch URL with rate limiting."""
    global last_request_time

    elapsed = time.time() - last_request_time
    if elapsed < RATE_LIMIT_SEC:
        time.sleep(RATE_LIMIT_SEC - elapsed)

    try:
        response = session.get(url, timeout=30)
        last_request_time = time.time()
        return response.text if response.status_code == 200 else None
    except Exception as e:
        print(f"  [ERROR] {e}")
        return None


def extract_movie_slugs(html: str) -> list[str]:
    """Extract movie URL slugs from listing page."""
    soup = BeautifulSoup(html, 'lxml')
    slugs = []

    cards = soup.select('div.card')
    for card in cards:
        link = card.select_one('a[href*="/movies/"]')
        if not link:
            continue
        href = link.get('href', '')
        if any(x in href for x in ['/trending', '/subtitled', '/feature-film', '/documentary', '/theatre', '?']):
            continue
        slug = href.rstrip('/').split('/')[-1]
        if slug and slug != 'movies':
            slugs.append(slug)

    return slugs


def extract_series_slugs(html: str) -> list[str]:
    """Extract series URL slugs from listing page."""
    soup = BeautifulSoup(html, 'lxml')
    slugs = []

    cards = soup.select('div.card')
    for card in cards:
        link = card.select_one('a[href*="/shows/"]')
        if not link:
            continue
        href = link.get('href', '')
        if any(x in href for x in ['?', '/trending']):
            continue
        slug = href.rstrip('/').split('/')[-1]
        if slug and slug not in ['shows', 'tv-series']:
            slugs.append(slug)

    return slugs


def has_next_page(html: str, page: int) -> bool:
    """Check if there's a next page."""
    soup = BeautifulSoup(html, 'lxml')
    return bool(soup.select_one(f'a[href*="page={page + 1}"]') or soup.select_one('a.page-link[rel="next"]'))


def main():
    print("=" * 50)
    print("IMVBox Date Fix - Fast Order Update")
    print("=" * 50 + "\n")

    # Use working DB (has correct schema)
    if not WORKING_DB.exists():
        print(f"ERROR: Working database not found at {WORKING_DB}")
        print("Run: cp scripts/imvbox_content_minimal.db scripts/imvbox_content_working.db")
        return

    print(f"Using database: {WORKING_DB}")

    # ========== STEP 1: Fetch movie order from listing pages ==========
    print("\n[MOVIES] Fetching order from listing pages...\n")

    movie_order = []  # List of slugs in website order

    for page in range(1, MAX_PAGES + 1):
        url = f"{BASE_URL}/movies?sort_by=new-releases" + (f"&page={page}" if page > 1 else "")
        print(f"  Page {page}...", end=' ', flush=True)

        html = fetch_url(url)
        if not html:
            print("No response")
            break

        slugs = extract_movie_slugs(html)
        if not slugs:
            print("No movies found")
            break

        for slug in slugs:
            if slug not in movie_order:
                movie_order.append(slug)

        print(f"Got {len(slugs)} slugs (total: {len(movie_order)})")

        if not has_next_page(html, page):
            print("  No more pages")
            break

    # ========== STEP 2: Fetch series order from listing pages ==========
    print("\n[SERIES] Fetching order from listing pages...\n")

    series_order = []

    for page in range(1, MAX_PAGES + 1):
        url = f"{BASE_URL}/tv-series?sort_by=new-releases" + (f"&page={page}" if page > 1 else "")
        print(f"  Page {page}...", end=' ', flush=True)

        html = fetch_url(url)
        if not html:
            print("No response")
            break

        slugs = extract_series_slugs(html)
        if not slugs:
            print("No series found")
            break

        for slug in slugs:
            if slug not in series_order:
                series_order.append(slug)

        print(f"Got {len(slugs)} slugs (total: {len(series_order)})")

        if not has_next_page(html, page):
            print("  No more pages")
            break

    # ========== STEP 3: Update database with correct dates ==========
    print("\n[UPDATE] Applying date fixes to database...\n")

    conn = sqlite3.connect(WORKING_DB)
    cursor = conn.cursor()

    # Base time - now
    base_time = int(time.time() * 1000)

    # Update movies - newer items get higher timestamps
    movie_updates = 0
    for index, slug in enumerate(movie_order):
        # Create URL pattern to match
        url_pattern = f"%/movies/{slug}"

        # Calculate date: first item (newest) gets base_time, subsequent items get decreasing times
        # Each item is 1 minute apart
        date_added = base_time - (index * 60 * 1000)

        cursor.execute("""
            UPDATE cached_movies
            SET dateAdded = ?
            WHERE farsilandUrl LIKE ?
        """, (date_added, url_pattern))

        if cursor.rowcount > 0:
            movie_updates += 1

    print(f"  Updated {movie_updates} movies")

    # Update series
    series_updates = 0
    for index, slug in enumerate(series_order):
        url_pattern = f"%/shows/{slug}"
        date_added = base_time - (index * 60 * 1000)

        cursor.execute("""
            UPDATE cached_series
            SET dateAdded = ?
            WHERE farsilandUrl LIKE ?
        """, (date_added, url_pattern))

        if cursor.rowcount > 0:
            series_updates += 1

            # Also update episodes for this series to have same date range
            cursor.execute("""
                UPDATE cached_episodes
                SET dateAdded = ?
                WHERE farsilandUrl LIKE ?
            """, (date_added, url_pattern + "%"))

    print(f"  Updated {series_updates} series (and their episodes)")

    conn.commit()
    conn.close()

    print("\n" + "=" * 50)
    print("DATE FIX COMPLETE")
    print("=" * 50)
    print(f"Movies order mapped: {len(movie_order)}")
    print(f"Series order mapped: {len(series_order)}")
    print(f"Database updated: {WORKING_DB}")

    # Copy to assets folder
    import shutil
    print(f"\nCopying to assets folder...")
    shutil.copy(WORKING_DB, TARGET_DB)
    print(f"Copied to: {TARGET_DB}")

    # Also remove the bad DB from root assets if exists
    bad_db = Path("G:/FarsiPlex/app/src/main/assets/imvbox_content.db")
    if bad_db.exists():
        bad_db.unlink()
        print(f"Removed incorrect DB from root assets: {bad_db}")


if __name__ == '__main__':
    main()
