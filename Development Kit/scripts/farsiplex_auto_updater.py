#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FarsiPlex Auto-Updater
Automatically checks for content updates every 10-15 minutes and syncs database

Features:
- Runs as background service
- Checks sitemap for updates every 10-15 minutes
- Only scrapes new/updated content (incremental updates)
- Exports updated database for app deployment
- Logs all activity

Usage:
    python farsiplex_auto_updater.py
    python farsiplex_auto_updater.py --interval 10  # Check every 10 minutes
    python farsiplex_auto_updater.py --once          # Run once and exit
"""

import time
import sys
import argparse
import logging
from datetime import datetime
from pathlib import Path

# Import the DooPlay scraper (more reliable for video extraction)
from farsiplex_scraper_dooplay import FarsiPlexDooPlayScraper


class FarsiPlexAutoUpdater:
    """Automated content updater for FarsiPlex"""

    def __init__(self, db_path: str = "Farsiplex.db", check_interval: int = 15, delay: float = 2.0):
        """
        Initialize auto-updater

        Args:
            db_path: Path to SQLite database
            check_interval: Minutes between update checks (default: 15)
            delay: Delay between requests in seconds
        """
        self.db_path = db_path
        self.check_interval = check_interval * 60  # Convert to seconds
        self.scraper = FarsiPlexDooPlayScraper(db_path=db_path, delay=delay)
        self.setup_logging()

    def setup_logging(self):
        """Setup logging to file and console"""
        log_dir = Path("logs")
        log_dir.mkdir(exist_ok=True)

        log_file = log_dir / f"farsiplex_updater_{datetime.now().strftime('%Y%m%d')}.log"

        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler(log_file),
                logging.StreamHandler(sys.stdout)
            ]
        )
        self.logger = logging.getLogger(__name__)

    def check_for_updates(self) -> dict:
        """
        Check sitemaps for new/updated content

        Returns:
            Dict with lists of new/updated content
        """
        self.logger.info("=" * 60)
        self.logger.info("Checking for updates...")

        updates = {
            'new_movies': [],
            'updated_movies': [],
            'new_tvshows': [],
            'updated_tvshows': []
        }

        try:
            import sqlite3

            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()

            # Check movies
            movie_urls = self.scraper.fetch_sitemap(self.scraper.MOVIES_SITEMAP)
            for item in movie_urls:
                url = item['loc']
                slug = url.rstrip('/').split('/')[-1]
                lastmod = item['lastmod']

                cursor.execute("SELECT last_modified FROM movies WHERE slug = ?", (slug,))
                result = cursor.fetchone()

                if not result:
                    updates['new_movies'].append({'url': url, 'slug': slug, 'lastmod': lastmod})
                elif result[0] != lastmod:
                    updates['updated_movies'].append({'url': url, 'slug': slug, 'lastmod': lastmod})

            # Check TV shows
            tvshow_urls = self.scraper.fetch_sitemap(self.scraper.TVSHOWS_SITEMAP)
            for item in tvshow_urls:
                url = item['loc']
                slug = url.rstrip('/').split('/')[-1]
                lastmod = item['lastmod']

                cursor.execute("SELECT last_modified FROM tvshows WHERE slug = ?", (slug,))
                result = cursor.fetchone()

                if not result:
                    updates['new_tvshows'].append({'url': url, 'slug': slug, 'lastmod': lastmod})
                elif result[0] != lastmod:
                    updates['updated_tvshows'].append({'url': url, 'slug': slug, 'lastmod': lastmod})

            conn.close()

            # Log results
            total_updates = sum(len(v) for v in updates.values())
            self.logger.info(f"New movies: {len(updates['new_movies'])}")
            self.logger.info(f"Updated movies: {len(updates['updated_movies'])}")
            self.logger.info(f"New TV shows: {len(updates['new_tvshows'])}")
            self.logger.info(f"Updated TV shows: {len(updates['updated_tvshows'])}")
            self.logger.info(f"Total changes: {total_updates}")

            return updates

        except Exception as e:
            self.logger.error(f"Error checking updates: {e}")
            return updates

    def apply_updates(self, updates: dict):
        """
        Apply updates by scraping new/changed content

        Args:
            updates: Dict from check_for_updates()
        """
        total_updates = sum(len(v) for v in updates.values())

        if total_updates == 0:
            self.logger.info("✓ No updates needed - database is current")
            return

        self.logger.info(f"Applying {total_updates} updates...")

        # Update movies
        for i, movie_item in enumerate(updates['new_movies'] + updates['updated_movies'], 1):
            self.logger.info(f"[{i}/{len(updates['new_movies']) + len(updates['updated_movies'])}] Movie: {movie_item['slug']}")
            try:
                movie_data = self.scraper.scrape_movie(movie_item)
                if movie_data:
                    self.scraper.save_movie_to_db(movie_data)
            except Exception as e:
                self.logger.error(f"  ✗ Error: {e}")

        # Update TV shows
        for i, tvshow_item in enumerate(updates['new_tvshows'] + updates['updated_tvshows'], 1):
            self.logger.info(f"[{i}/{len(updates['new_tvshows']) + len(updates['updated_tvshows'])}] TV Show: {tvshow_item['slug']}")
            try:
                tvshow_data = self.scraper.scrape_tvshow(tvshow_item)
                if tvshow_data:
                    self.scraper.save_tvshow_to_db(tvshow_data)
            except Exception as e:
                self.logger.error(f"  ✗ Error: {e}")

        self.logger.info(f"✓ Updates applied successfully")

    def export_for_app(self):
        """Export database to app-compatible format"""
        self.logger.info("Exporting database for app deployment...")

        try:
            # Run converter script
            import subprocess
            import os

            converter_path = Path("farsiplex.com/convert_farsiplex_to_app_db.py")
            if converter_path.exists():
                os.chdir("farsiplex.com")
                result = subprocess.run([sys.executable, "convert_farsiplex_to_app_db.py"],
                                        capture_output=True, text=True)

                if result.returncode == 0:
                    self.logger.info("✓ Database converted for app")

                    # Copy to app assets
                    import shutil
                    source = Path("farsiplex.com/farsiplex_content.db")
                    dest = Path("app/src/main/assets/databases/farsiplex_content.db")

                    if source.exists():
                        shutil.copy2(source, dest)
                        self.logger.info(f"✓ Copied to {dest}")
                    else:
                        self.logger.warning("Converted database not found")
                else:
                    self.logger.error(f"Conversion failed: {result.stderr}")
            else:
                self.logger.warning("Converter script not found - skipping export")

        except Exception as e:
            self.logger.error(f"Export error: {e}")

    def run_update_cycle(self):
        """Run one update cycle"""
        self.logger.info("=" * 60)
        self.logger.info(f"Update cycle started at {datetime.now()}")
        self.logger.info("=" * 60)

        try:
            # Check for updates
            updates = self.check_for_updates()

            # Apply updates if any
            if sum(len(v) for v in updates.values()) > 0:
                self.apply_updates(updates)
                self.export_for_app()
            else:
                self.logger.info("✓ No updates needed")

            self.logger.info(f"✓ Update cycle completed at {datetime.now()}")

        except Exception as e:
            self.logger.error(f"Update cycle failed: {e}")
            import traceback
            self.logger.error(traceback.format_exc())

    def run_forever(self):
        """Run update checks continuously"""
        self.logger.info("=" * 60)
        self.logger.info("FarsiPlex Auto-Updater - Starting...")
        self.logger.info(f"Check interval: {self.check_interval / 60:.0f} minutes")
        self.logger.info(f"Database: {self.db_path}")
        self.logger.info("=" * 60)

        while True:
            try:
                self.run_update_cycle()

                # Wait until next check
                self.logger.info(f"Sleeping for {self.check_interval / 60:.0f} minutes...")
                self.logger.info("=" * 60)
                time.sleep(self.check_interval)

            except KeyboardInterrupt:
                self.logger.info("\n✓ Auto-updater stopped by user")
                break
            except Exception as e:
                self.logger.error(f"Fatal error: {e}")
                self.logger.info("Retrying in 5 minutes...")
                time.sleep(300)  # Wait 5 minutes before retry


def main():
    """Main entry point"""
    parser = argparse.ArgumentParser(description='FarsiPlex Auto-Updater')
    parser.add_argument('--interval', type=int, default=15,
                        help='Minutes between update checks (default: 15)')
    parser.add_argument('--delay', type=float, default=2.0,
                        help='Delay between requests in seconds (default: 2.0)')
    parser.add_argument('--once', action='store_true',
                        help='Run once and exit (no continuous monitoring)')
    parser.add_argument('--db', type=str, default='Farsiplex.db',
                        help='Database path (default: Farsiplex.db)')
    args = parser.parse_args()

    updater = FarsiPlexAutoUpdater(
        db_path=args.db,
        check_interval=args.interval,
        delay=args.delay
    )

    if args.once:
        # Run once and exit
        updater.run_update_cycle()
    else:
        # Run continuously
        updater.run_forever()


if __name__ == "__main__":
    main()
