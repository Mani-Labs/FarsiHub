#!/usr/bin/env python3
"""
IMVBox Test Scraper - Quick test with 2 movie pages + 1 TV page
"""

import os
import re
import json
import sqlite3
import hashlib
import asyncio
import aiohttp
from bs4 import BeautifulSoup
from typing import Dict, List, Optional, Tuple
from pathlib import Path
import time

BASE_URL = "https://www.imvbox.com/en"
DB_FILE = Path(__file__).parent / "imvbox_test.db"
MAX_CONCURRENT = 5

stats = {'requests': 0}


async def fetch_url(session: aiohttp.ClientSession, url: str, semaphore: asyncio.Semaphore) -> Optional[str]:
    async with semaphore:
        try:
            stats['requests'] += 1
            print(f"    Fetching: {url[:60]}...")
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=30)) as response:
                if response.status != 200:
                    print(f"    [WARN] HTTP {response.status}")
                    return None
                return await response.text()
        except Exception as e:
            print(f"    [ERROR] {e}")
            return None


def generate_id(prefix: str, slug: str) -> int:
    return int(hashlib.md5(f"{prefix}:{slug}".encode()).hexdigest()[:8], 16)


def extract_poster(element) -> Optional[str]:
    if not element:
        return None
    data_img = element.select_one('data-img')
    if data_img and data_img.get('src'):
        return data_img.get('src')
    img = element.select_one('img')
    if img:
        return img.get('data-src') or img.get('src')
    return None


def parse_movie_list(html: str) -> List[Dict]:
    soup = BeautifulSoup(html, 'lxml')
    movies = []
    for card in soup.select('div.card'):
        link = card.select_one('a[href*="/movies/"]')
        if not link:
            continue
        href = link.get('href', '')
        if any(x in href for x in ['/trending', '/subtitled', '/feature-film', '?']):
            continue
        slug = href.rstrip('/').split('/')[-1]
        if not slug or slug == 'movies':
            continue
        title_elem = card.select_one('h5.card-title')
        title = title_elem.get_text(strip=True) if title_elem else slug
        poster = extract_poster(card.select_one('picture'))
        movies.append({'slug': slug, 'title': title, 'url': f"{BASE_URL}/movies/{slug}", 'poster_url': poster})
    return movies


def parse_movie_details(html: str) -> Dict:
    details = {'description': None, 'year': None, 'rating': None, 'runtime': None,
               'director': None, 'cast': None, 'genres': [], 'poster_url': None}
    if not html:
        return details

    soup = BeautifulSoup(html, 'lxml')
    page_text = soup.get_text()

    meta = soup.select_one('meta[property="og:description"]')
    if meta:
        details['description'] = meta.get('content')

    meta = soup.select_one('meta[property="og:image"]')
    if meta:
        details['poster_url'] = meta.get('content')

    # Year from page text (format: "2022 | 90 min")
    year_match = re.search(r'\b(19\d{2}|20\d{2})\s*\|', page_text)
    if year_match:
        details['year'] = int(year_match.group(1))

    # Runtime from JSON-LD
    json_ld = soup.select_one('script[type="application/ld+json"]')
    if json_ld:
        try:
            data = json.loads(json_ld.string)
            if isinstance(data, list):
                data = data[0]
            if 'duration' in data:
                match = re.match(r'PT(?:(\d+)H)?(?:(\d+)M)?', data['duration'])
                if match:
                    details['runtime'] = int(match.group(1) or 0) * 60 + int(match.group(2) or 0)
        except:
            pass

    # Director from page text
    director_match = re.search(r'Director[:\s]+([A-Za-z\s]+?)(?:\s*\(|$|\n)', page_text)
    if director_match:
        details['director'] = director_match.group(1).strip()

    # Cast from person links
    cast_names = []
    for link in soup.select('a[href*="/people/"]')[:10]:
        name = link.get_text(strip=True)
        if name and re.match(r'^[A-Za-z\s]+$', name) and name not in cast_names:
            cast_names.append(name)
    if cast_names:
        details['cast'] = ', '.join(cast_names)

    # Genres
    for link in soup.select('a[href*="/genre/"]'):
        genre = link.get_text(strip=True)
        if genre and genre not in details['genres']:
            details['genres'].append(genre)

    return details


def parse_series_list(html: str) -> List[Dict]:
    soup = BeautifulSoup(html, 'lxml')
    series = []
    for card in soup.select('div.card'):
        link = card.select_one('a[href*="/shows/"]')
        if not link:
            continue
        href = link.get('href', '')
        if '?' in href:
            continue
        slug = href.rstrip('/').split('/')[-1]
        if not slug or slug in ['shows', 'tv-series']:
            continue
        title_elem = card.select_one('h5.card-title')
        title = title_elem.get_text(strip=True) if title_elem else slug
        poster = extract_poster(card.select_one('picture'))
        series.append({'slug': slug, 'title': title, 'url': f"{BASE_URL}/shows/{slug}", 'poster_url': poster})
    return series


def parse_series_details(html: str, url: str) -> Dict:
    details = {'description': None, 'poster_url': None, 'total_seasons': 1, 'episodes': []}
    if not html:
        return details

    soup = BeautifulSoup(html, 'lxml')

    meta = soup.select_one('meta[property="og:description"]')
    if meta:
        details['description'] = meta.get('content')

    meta = soup.select_one('meta[property="og:image"]')
    if meta:
        details['poster_url'] = meta.get('content')

    # Find episodes
    seen = set()
    for link in soup.select('a[href*="/episode-"]'):
        href = link.get('href', '')
        match = re.search(r'/season-(\d+)/episode-(\d+)', href)
        if match:
            s, e = int(match.group(1)), int(match.group(2))
            if (s, e) not in seen:
                seen.add((s, e))
                details['episodes'].append({'season': s, 'episode': e, 'title': f"Episode {e}", 'url': f"{url}/season-{s}/episode-{e}"})

    return details


async def main():
    print("=" * 50)
    print("IMVBox TEST Scraper")
    print("Testing: 2 movie pages + 1 TV series page")
    print("=" * 50 + "\n")

    start = time.time()
    semaphore = asyncio.Semaphore(MAX_CONCURRENT)

    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0'}

    async with aiohttp.ClientSession(headers=headers) as session:

        # === TEST MOVIES ===
        print("[MOVIES] Testing 2 pages...\n")
        all_movies = []

        for page in [1, 2]:
            url = f"{BASE_URL}/movies?sort_by=new-releases" + (f"&page={page}" if page > 1 else "")
            print(f"  Page {page}:")
            html = await fetch_url(session, url, semaphore)
            if not html:
                print("    FAILED to fetch list page!")
                continue

            movies = parse_movie_list(html)
            print(f"    Found {len(movies)} movies on list")

            # Fetch details for first 3 movies
            test_movies = movies[:3]
            print(f"    Fetching details for {len(test_movies)} movies...")

            tasks = [fetch_url(session, m['url'], semaphore) for m in test_movies]
            detail_htmls = await asyncio.gather(*tasks)

            for movie, detail_html in zip(test_movies, detail_htmls):
                details = parse_movie_details(detail_html)
                movie.update(details)
                all_movies.append(movie)
                print(f"      - {movie['title'][:40]}")
                print(f"        Year: {details.get('year')}, Rating: {details.get('rating')}, Runtime: {details.get('runtime')}min")
                print(f"        Director: {details.get('director', 'N/A')[:30] if details.get('director') else 'N/A'}")
                print(f"        Poster: {'YES' if details.get('poster_url') else 'NO'}")

        # === TEST TV SERIES ===
        print("\n[TV SERIES] Testing 1 page...\n")

        url = f"{BASE_URL}/tv-series?sort_by=new-releases"
        print(f"  Page 1:")
        html = await fetch_url(session, url, semaphore)
        if not html:
            print("    FAILED to fetch list page!")
        else:
            series_list = parse_series_list(html)
            print(f"    Found {len(series_list)} series on list")

            # Test first 2 series
            test_series = series_list[:2]
            print(f"    Fetching details for {len(test_series)} series...")

            for series in test_series:
                # Fetch season-1 page
                s1_url = f"{series['url']}/season-1"
                html = await fetch_url(session, s1_url, semaphore)
                details = parse_series_details(html, series['url'])

                print(f"      - {series['title'][:40]}")
                print(f"        Poster: {'YES' if details.get('poster_url') else 'NO'}")
                print(f"        Episodes found: {len(details.get('episodes', []))}")
                if details.get('episodes'):
                    for ep in details['episodes'][:3]:
                        print(f"          S{ep['season']}E{ep['episode']}: {ep['title']}")

    elapsed = time.time() - start
    print("\n" + "=" * 50)
    print("TEST COMPLETE")
    print("=" * 50)
    print(f"Total requests: {stats['requests']}")
    print(f"Time: {elapsed:.1f} seconds")
    print(f"\nMovies tested: {len(all_movies)}")

    # Show sample data
    if all_movies:
        print("\n--- Sample Movie Data ---")
        m = all_movies[0]
        print(f"Title: {m['title']}")
        print(f"URL: {m['url']}")
        poster = m.get('poster_url') or 'N/A'
        print(f"Poster: {poster[:60] if poster else 'N/A'}...")
        print(f"Year: {m.get('year')}")
        print(f"Rating: {m.get('rating')}")
        print(f"Runtime: {m.get('runtime')} min")
        print(f"Director: {m.get('director') or 'N/A'}")
        cast = m.get('cast') or 'N/A'
        print(f"Cast: {cast[:60] if cast else 'N/A'}...")
        print(f"Genres: {m.get('genres')}")

    print("\n✓ If data looks good, run: python imvbox_scraper_async.py")


if __name__ == '__main__':
    asyncio.run(main())
