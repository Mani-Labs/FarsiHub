#!/usr/bin/env python3
"""
Update Checker for FarsiPlex
Monitors for new content and optionally auto-scrapes it

Usage:
    python check_updates.py              # Just check for updates
    python check_updates.py --auto       # Check and auto-scrape new content
    python check_updates.py --notify     # Check and save notification file
"""

import sys
import json
from datetime import datetime
from pathlib import Path
from farsiplex_scraper import FarsiPlexScraper


def check_and_notify(auto_scrape=False, save_notification=False):
    """
    Check for updates and optionally auto-scrape or save notifications

    Args:
        auto_scrape: Automatically scrape new content
        save_notification: Save notification to file
    """
    print("=" * 70)
    print("FarsiPlex Update Checker")
    print(f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    scraper = FarsiPlexScraper(db_path="Farsiplex.db", delay=2.0)

    # Check for updates
    updates = scraper.check_for_updates()

    total_new = (
        len(updates['new_movies']) +
        len(updates['new_shows']) +
        len(updates['new_episodes'])
    )

    if total_new == 0:
        print("\n✓ No new content found. Database is up to date.\n")
        return

    # Display updates
    print(f"\n🎬 NEW CONTENT DETECTED: {total_new} items\n")

    if updates['new_movies']:
        print(f"📺 NEW MOVIES ({len(updates['new_movies'])}):")
        for movie in updates['new_movies']:
            print(f"   • {movie['title']}")
            print(f"     {movie['url']}")

    if updates['new_shows']:
        print(f"\n📺 NEW TV SHOWS ({len(updates['new_shows'])}):")
        for show in updates['new_shows']:
            print(f"   • {show['title']}")
            print(f"     {show['url']}")

    if updates['new_episodes']:
        print(f"\n📺 NEW EPISODES ({len(updates['new_episodes'])}):")
        for episode in updates['new_episodes']:
            print(f"   • {episode['title']}")
            print(f"     {episode['url']}")

    # Save notification file
    if save_notification:
        notification_file = Path("updates_notification.json")
        notification_data = {
            'timestamp': datetime.now().isoformat(),
            'total_new': total_new,
            'updates': updates
        }

        with open(notification_file, 'w', encoding='utf-8') as f:
            json.dump(notification_data, f, indent=2, ensure_ascii=False)

        print(f"\n✓ Notification saved to: {notification_file}")

    # Auto-scrape if requested
    if auto_scrape:
        print("\n" + "=" * 70)
        print("AUTO-SCRAPING NEW CONTENT")
        print("=" * 70)

        # Scrape new movies
        if updates['new_movies']:
            print(f"\n📥 Scraping {len(updates['new_movies'])} new movies...")
            for i, movie_item in enumerate(updates['new_movies'], 1):
                print(f"[{i}/{len(updates['new_movies'])}] ", end='')
                movie_data = scraper.scrape_movie(movie_item)
                if movie_data:
                    scraper.save_movie_to_db(movie_data)

        # Scrape new TV shows
        if updates['new_shows']:
            print(f"\n📥 Scraping {len(updates['new_shows'])} new TV shows...")
            for i, show_item in enumerate(updates['new_shows'], 1):
                print(f"[{i}/{len(updates['new_shows'])}] ", end='')
                show_data = scraper.scrape_tvshow(show_item)
                if show_data:
                    scraper.save_tvshow_to_db(show_data)

        # Note: New episodes are usually handled when scraping their parent TV show
        # If you want to handle standalone episode updates, implement here

        print("\n📤 Exporting to JSON...")
        scraper.export_to_json()

        print("\n✓ Auto-scrape complete!")

    print("\n" + "=" * 70)


def main():
    """Main entry point"""
    auto_scrape = '--auto' in sys.argv
    save_notification = '--notify' in sys.argv

    if '--help' in sys.argv or '-h' in sys.argv:
        print(__doc__)
        return

    check_and_notify(auto_scrape=auto_scrape, save_notification=save_notification)


if __name__ == "__main__":
    main()
