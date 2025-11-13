#!/usr/bin/env python3
"""
Fix Farsiland database timestamps by fetching correct dates from WordPress API

This script:
1. Fetches all TV shows from Farsiland WordPress API (with pagination)
2. Extracts the 'modified' timestamp from each entry
3. Matches API data to database entries by ID and URL
4. Updates the lastUpdated field in farsiland_content.db

Usage:
    python fix_farsiland_timestamps.py
"""
import sqlite3
import requests
from datetime import datetime
from typing import List, Dict, Optional, Tuple

# Configuration
DB_PATH = r'G:\FarsiPlex\app\src\main\assets\databases\farsiland_content.db'
API_BASE_URL = 'https://farsiland.com/wp-json/wp/v2/tvshows'
PER_PAGE = 100  # Maximum allowed by WordPress API


def parse_farsiland_date(date_str: str) -> Optional[int]:
    """
    Parse Farsiland date format to Unix timestamp in milliseconds.

    Format: "2025-10-31T10:00:14" (NO timezone, unlike FarsiPlex)

    Args:
        date_str: Date string in format "yyyy-MM-dd'T'HH:mm:ss"

    Returns:
        Unix timestamp in milliseconds, or None if parsing fails
    """
    if not date_str:
        return None

    try:
        # Farsiland format: "2025-10-31T10:00:14" (no timezone)
        dt = datetime.strptime(date_str, '%Y-%m-%dT%H:%M:%S')
        return int(dt.timestamp() * 1000)
    except ValueError:
        # Try with timezone info (in case format changes)
        try:
            dt = datetime.fromisoformat(date_str.replace('+00:00', '+0000'))
            return int(dt.timestamp() * 1000)
        except:
            return None
    except Exception:
        return None


def fetch_all_series() -> List[Dict]:
    """
    Fetch all TV shows from Farsiland WordPress API with pagination.

    The API returns a maximum of 100 items per page. This function
    continues fetching pages until it receives an empty response.

    Returns:
        List of dictionaries containing series data with keys:
        - id: Series ID
        - modified: Last modified date string
        - link: Series URL
        - title: Series title (rendered)
    """
    all_series = []
    page = 1

    while True:
        print(f"Fetching page {page} from Farsiland API...")

        params = {
            'per_page': PER_PAGE,
            'page': page,
            'orderby': 'id',
            'order': 'asc'
        }

        try:
            response = requests.get(API_BASE_URL, params=params, timeout=30)
            response.raise_for_status()

            data = response.json()

            # If empty response or less than full page, we're done
            if not data or len(data) == 0:
                break

            # Extract relevant fields
            for item in data:
                all_series.append({
                    'id': item.get('id'),
                    'modified': item.get('modified'),
                    'link': item.get('link'),
                    'title': item.get('title', {}).get('rendered', 'Unknown')
                })

            # If we got less than a full page, we're done
            if len(data) < PER_PAGE:
                break

            page += 1

        except requests.exceptions.RequestException as e:
            print(f"  ERROR: Failed to fetch page {page}: {e}")
            break
        except ValueError as e:
            print(f"  ERROR: Failed to parse JSON from page {page}: {e}")
            break

    print(f"Found {len(all_series)} series from API\n")
    return all_series


def update_database_timestamps(series_data: List[Dict]) -> Tuple[int, int, int]:
    """
    Update database with correct timestamps from API data.

    Matching strategy:
    1. Try to match by ID (most reliable)
    2. Fall back to matching by URL if ID doesn't match

    Args:
        series_data: List of series dictionaries from API

    Returns:
        Tuple of (updated_count, skipped_count, error_count)
    """
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Get all series from database
    cursor.execute("SELECT id, title, farsilandUrl, lastUpdated FROM cached_series")
    db_series = cursor.fetchall()

    print(f"Found {len(db_series)} series in database\n")
    print("Updating database timestamps...")

    updated = 0
    skipped = 0
    errors = 0

    # Create lookup dictionaries for fast matching
    api_by_id = {item['id']: item for item in series_data}
    api_by_url = {item['link']: item for item in series_data}

    for db_id, db_title, db_url, old_timestamp in db_series:
        # Try matching by ID first (most reliable)
        api_item = api_by_id.get(db_id)

        # Fall back to URL matching if ID doesn't match
        if not api_item and db_url:
            api_item = api_by_url.get(db_url)

        if not api_item:
            skipped += 1
            continue

        # Parse the modified date from API
        modified_date_str = api_item['modified']
        new_timestamp = parse_farsiland_date(modified_date_str)

        if not new_timestamp:
            print(f"  SKIP {db_title}: Failed to parse date '{modified_date_str}'")
            errors += 1
            continue

        # Only update if timestamp actually changed
        if new_timestamp != old_timestamp:
            try:
                cursor.execute(
                    "UPDATE cached_series SET lastUpdated = ? WHERE id = ?",
                    (new_timestamp, db_id)
                )

                # Format dates for display
                old_dt = datetime.fromtimestamp(old_timestamp / 1000)
                new_dt = datetime.fromtimestamp(new_timestamp / 1000)

                print(f"  OK {db_title}: {old_dt.strftime('%Y-%m-%d %H:%M:%S')} -> {new_dt.strftime('%Y-%m-%d %H:%M:%S')}")
                updated += 1

            except sqlite3.Error as e:
                print(f"  ERROR {db_title}: Database update failed: {e}")
                errors += 1

    # Commit all changes
    conn.commit()
    conn.close()

    return updated, skipped, errors


def main():
    """Main execution flow"""
    print("=" * 70)
    print("Farsiland Database Timestamp Fix")
    print("=" * 70)
    print()

    try:
        # Step 1: Fetch all series from API
        series_data = fetch_all_series()

        if not series_data:
            print("ERROR: No series data fetched from API. Exiting.")
            return 1

        # Step 2: Update database with correct timestamps
        updated, skipped, errors = update_database_timestamps(series_data)

        # Step 3: Print summary
        print()
        print("=" * 70)
        print("Summary:")
        print(f"  Updated: {updated} series")
        print(f"  Skipped: {skipped} series (not found in API)")
        print(f"  Errors:  {errors} series (parse/update failures)")
        print("=" * 70)

        if errors > 0:
            print("\nWARNING: Some errors occurred during processing.")
            return 1

        print("\nDatabase timestamps fixed successfully!")
        return 0

    except sqlite3.Error as e:
        print(f"\nERROR: Database error: {e}")
        import traceback
        traceback.print_exc()
        return 1
    except Exception as e:
        print(f"\nERROR: Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return 1


if __name__ == '__main__':
    exit(main())
