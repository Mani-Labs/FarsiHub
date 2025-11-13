#!/usr/bin/env python3
"""
Fix FarsiPlex database timestamps by fetching correct dates from sitemap
"""
import sqlite3
import requests
import xml.etree.ElementTree as ET
from datetime import datetime

DB_PATH = r'G:\FarsiPlex\app\src\main\assets\databases\farsiplex_content.db'
SITEMAP_URL = 'https://farsiplex.com/wp-sitemap-posts-tvshows-1.xml'

def parse_iso_date(date_str):
    """Parse ISO 8601 date to Unix timestamp (milliseconds)"""
    if not date_str:
        return None

    # Try with timezone
    try:
        dt = datetime.fromisoformat(date_str.replace('+00:00', '+0000'))
        return int(dt.timestamp() * 1000)
    except:
        pass

    # Try without timezone
    try:
        dt = datetime.strptime(date_str, '%Y-%m-%dT%H:%M:%S')
        return int(dt.timestamp() * 1000)
    except:
        pass

    # Try date only
    try:
        dt = datetime.strptime(date_str, '%Y-%m-%d')
        return int(dt.timestamp() * 1000)
    except:
        return None

def fetch_sitemap_dates():
    """Fetch sitemap and extract URL -> lastmod mapping"""
    print(f"Fetching sitemap from {SITEMAP_URL}...")
    response = requests.get(SITEMAP_URL)
    response.raise_for_status()

    # Parse XML
    root = ET.fromstring(response.content)
    ns = {'sm': 'http://www.sitemaps.org/schemas/sitemap/0.9'}

    url_dates = {}
    for url_elem in root.findall('sm:url', ns):
        loc = url_elem.find('sm:loc', ns)
        lastmod = url_elem.find('sm:lastmod', ns)

        if loc is not None and lastmod is not None:
            url_dates[loc.text] = lastmod.text

    print(f"Found {len(url_dates)} URLs in sitemap")
    return url_dates

def update_database_timestamps(url_dates):
    """Update database with correct timestamps"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Get all series
    cursor.execute("SELECT id, title, farsilandUrl, lastUpdated FROM cached_series")
    series = cursor.fetchall()

    print(f"\nFound {len(series)} series in database")

    updated = 0
    for series_id, title, url, old_timestamp in series:
        if url in url_dates:
            sitemap_date_str = url_dates[url]
            new_timestamp = parse_iso_date(sitemap_date_str)

            if new_timestamp and new_timestamp != old_timestamp:
                cursor.execute(
                    "UPDATE cached_series SET lastUpdated = ? WHERE id = ?",
                    (new_timestamp, series_id)
                )

                old_dt = datetime.fromtimestamp(old_timestamp / 1000)
                new_dt = datetime.fromtimestamp(new_timestamp / 1000)
                print(f"  OK {title}: {old_dt.date()} -> {new_dt.date()}")
                updated += 1

    conn.commit()
    conn.close()

    print(f"\nUpdated {updated} series timestamps")

if __name__ == '__main__':
    try:
        url_dates = fetch_sitemap_dates()
        update_database_timestamps(url_dates)
        print("\nDatabase timestamps fixed successfully!")
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
