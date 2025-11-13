#!/usr/bin/env python3
"""
Example usage scripts for FarsiPlex Scraper
"""

from farsiplex_scraper import FarsiPlexScraper
import sqlite3
import json


def example_full_scrape():
    """Example: Run full scrape"""
    print("=== Full Scrape Example ===\n")

    scraper = FarsiPlexScraper(db_path="Farsiplex.db", delay=2.0)
    scraper.run_full_scrape()


def example_check_updates():
    """Example: Check for new content"""
    print("=== Check Updates Example ===\n")

    scraper = FarsiPlexScraper(db_path="Farsiplex.db", delay=2.0)
    updates = scraper.check_for_updates()

    print(f"\nFound {len(updates['new_movies'])} new movies:")
    for movie in updates['new_movies']:
        print(f"  - {movie['title']}")

    print(f"\nFound {len(updates['new_shows'])} new TV shows:")
    for show in updates['new_shows']:
        print(f"  - {show['title']}")

    print(f"\nFound {len(updates['new_episodes'])} new episodes:")
    for episode in updates['new_episodes']:
        print(f"  - {episode['title']}")


def example_scrape_specific_movie():
    """Example: Scrape a specific movie"""
    print("=== Scrape Specific Movie Example ===\n")

    scraper = FarsiPlexScraper(db_path="Farsiplex.db", delay=2.0)

    # Search for a movie
    results = scraper.search_content("coal")

    if results:
        movie_item = results[0]
        print(f"Scraping: {movie_item.get('title')}\n")

        movie_data = scraper.scrape_movie(movie_item)

        if movie_data:
            print("\nMovie Data:")
            print(json.dumps(movie_data, indent=2, ensure_ascii=False))

            # Save to database
            scraper.save_movie_to_db(movie_data)
            print("\n✓ Saved to database")


def example_query_database():
    """Example: Query the database"""
    print("=== Database Query Example ===\n")

    conn = sqlite3.connect("Farsiplex.db")
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()

    # Get top rated movies
    print("Top 10 Rated Movies:")
    cursor.execute("""
        SELECT title, rating, votes, release_date
        FROM movies
        WHERE rating IS NOT NULL
        ORDER BY rating DESC
        LIMIT 10
    """)

    for row in cursor.fetchall():
        print(f"  {row['title']}: {row['rating']} ({row['votes']} votes) - {row['release_date']}")

    # Get TV shows with episode counts
    print("\n\nTV Shows with Episode Counts:")
    cursor.execute("""
        SELECT t.title, COUNT(e.id) as episode_count, t.rating
        FROM tvshows t
        LEFT JOIN episodes e ON t.id = e.tvshow_id
        GROUP BY t.id
        ORDER BY episode_count DESC
        LIMIT 10
    """)

    for row in cursor.fetchall():
        print(f"  {row['title']}: {row['episode_count']} episodes (Rating: {row['rating']})")

    # Get video sources for movies
    print("\n\nMovies with Video Sources:")
    cursor.execute("""
        SELECT m.title, mv.quality, mv.cdn_source, mv.url
        FROM movies m
        JOIN movie_videos mv ON m.id = mv.movie_id
        LIMIT 5
    """)

    for row in cursor.fetchall():
        print(f"  {row['title']} [{row['quality']}] via {row['cdn_source']}")
        print(f"    URL: {row['url'][:80]}...")

    conn.close()


def example_export_json():
    """Example: Export database to JSON"""
    print("=== JSON Export Example ===\n")

    scraper = FarsiPlexScraper(db_path="Farsiplex.db")
    scraper.export_to_json(output_dir=".")

    print("\n✓ Exported to:")
    print("  - farsiplex_movies.json")
    print("  - farsiplex_tvshows.json")


def example_incremental_update():
    """Example: Incremental update workflow"""
    print("=== Incremental Update Workflow ===\n")

    scraper = FarsiPlexScraper(db_path="Farsiplex.db", delay=2.0)

    # Check for updates
    print("Step 1: Checking for new content...")
    updates = scraper.check_for_updates()

    # Process new movies
    if updates['new_movies']:
        print(f"\nStep 2: Processing {len(updates['new_movies'])} new movies...")
        for i, movie_item in enumerate(updates['new_movies'], 1):
            print(f"  [{i}/{len(updates['new_movies'])}] {movie_item['title']}...", end=' ')
            movie_data = scraper.scrape_movie(movie_item)
            if movie_data:
                scraper.save_movie_to_db(movie_data)
                print("✓")

    # Process new TV shows
    if updates['new_shows']:
        print(f"\nStep 3: Processing {len(updates['new_shows'])} new TV shows...")
        for i, show_item in enumerate(updates['new_shows'], 1):
            print(f"  [{i}/{len(updates['new_shows'])}] {show_item['title']}...", end=' ')
            show_data = scraper.scrape_tvshow(show_item)
            if show_data:
                scraper.save_tvshow_to_db(show_data)
                print("✓")

    # Export updated data
    print("\nStep 4: Exporting to JSON...")
    scraper.export_to_json()

    print("\n✓ Update workflow complete!")


def example_get_show_hierarchy():
    """Example: Display TV show hierarchy"""
    print("=== TV Show Hierarchy Example ===\n")

    conn = sqlite3.connect("Farsiplex.db")
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()

    # Get first TV show
    cursor.execute("SELECT * FROM tvshows LIMIT 1")
    tvshow = cursor.fetchone()

    if tvshow:
        print(f"TV Show: {tvshow['title']}")
        print(f"Rating: {tvshow['rating']} ({tvshow['votes']} votes)")
        print(f"URL: {tvshow['url']}\n")

        # Get seasons
        cursor.execute("""
            SELECT * FROM seasons
            WHERE tvshow_id = ?
            ORDER BY season_number
        """, (tvshow['id'],))

        seasons = cursor.fetchall()
        print(f"Seasons: {len(seasons)}\n")

        for season in seasons:
            print(f"  Season {season['season_number']} - {season['release_date']}")

            # Get episodes
            cursor.execute("""
                SELECT * FROM episodes
                WHERE season_id = ?
                ORDER BY episode_number
            """, (season['id'],))

            episodes = cursor.fetchall()

            for episode in episodes:
                print(f"    Episode {episode['episode_number']}: {episode['title']}")
                print(f"      URL: {episode['url']}")

                # Get video sources
                cursor.execute("""
                    SELECT * FROM episode_videos
                    WHERE episode_id = ?
                """, (episode['id'],))

                videos = cursor.fetchall()
                if videos:
                    for video in videos:
                        print(f"      Video: {video['quality']} via {video['cdn_source']}")
                        print(f"        {video['url'][:70]}...")

    conn.close()


if __name__ == "__main__":
    import sys

    examples = {
        "1": ("Full Scrape", example_full_scrape),
        "2": ("Check for Updates", example_check_updates),
        "3": ("Scrape Specific Movie", example_scrape_specific_movie),
        "4": ("Query Database", example_query_database),
        "5": ("Export to JSON", example_export_json),
        "6": ("Incremental Update Workflow", example_incremental_update),
        "7": ("Show TV Show Hierarchy", example_get_show_hierarchy),
    }

    if len(sys.argv) > 1:
        choice = sys.argv[1]
        if choice in examples:
            print(f"\nRunning: {examples[choice][0]}\n")
            examples[choice][1]()
        else:
            print(f"Invalid choice. Use 1-{len(examples)}")
    else:
        print("\nFarsiPlex Scraper - Examples")
        print("=" * 60)
        print("\nUsage: python example_usage.py <number>\n")
        print("Examples:")
        for key, (name, _) in examples.items():
            print(f"  {key}. {name}")
        print("\nOr run specific example functions:")
        print("  from example_usage import *")
        print("  example_check_updates()")
