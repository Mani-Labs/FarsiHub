#!/usr/bin/env python3
"""
FarsiPlex DooPlay API Scraper
Uses DooPlay theme's native methods for video extraction
More reliable than HTML scraping
"""

import requests
import sqlite3
import json
import time
import re
import hashlib
import xml.etree.ElementTree as ET
from datetime import datetime
from typing import Dict, List, Optional, Tuple
from urllib.parse import urljoin, urlparse, parse_qs, unquote
from bs4 import BeautifulSoup
from pathlib import Path
from threading import Lock


class FarsiPlexDooPlayScraper:
    """DooPlay-aware scraper for FarsiPlex"""

    BASE_URL = "https://farsiplex.com"
    MOVIES_SITEMAP = f"{BASE_URL}/wp-sitemap-posts-movies-1.xml"
    TVSHOWS_SITEMAP = f"{BASE_URL}/wp-sitemap-posts-tvshows-1.xml"
    EPISODES_SITEMAP = f"{BASE_URL}/wp-sitemap-posts-episodes-1.xml"

    def __init__(self, db_path: str = "Farsiplex.db", delay: float = 2.0):
        self.db_path = db_path
        self.delay = delay
        self.db_lock = Lock()  # Thread-safe database access
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Referer': self.BASE_URL
        })
        self.init_database()

    def generate_stable_id(self, slug: str) -> int:
        """
        Generate deterministic ID using MD5 hash

        AUDIT FIX C1: Replaced Python's hash() which randomizes per process
        Uses MD5 for stable, consistent IDs across scraper runs

        Args:
            slug: Content slug (e.g., "breaking-bad", "game-of-thrones-s01e01")

        Returns:
            Deterministic integer ID (max 8 digits)
        """
        hash_object = hashlib.md5(slug.encode('utf-8'))
        return int(hash_object.hexdigest(), 16) % (10 ** 8)

    def init_database(self):
        """Initialize database with migration support"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()

        # Movies table - AUDIT FIX: Renamed to match Android app expectations
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS cached_movies (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                title_en TEXT,
                title_fa TEXT,
                slug TEXT UNIQUE NOT NULL,
                url TEXT NOT NULL,
                poster_url TEXT,
                release_date TEXT,
                country TEXT,
                rating REAL,
                votes INTEGER,
                synopsis_en TEXT,
                synopsis_fa TEXT,
                duration TEXT,
                quality TEXT,
                last_modified TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS movie_genres (
                movie_id INTEGER,
                genre TEXT,
                FOREIGN KEY (movie_id) REFERENCES cached_movies(id),
                PRIMARY KEY (movie_id, genre)
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS movie_videos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                movie_id INTEGER,
                quality TEXT,
                url TEXT NOT NULL,
                cdn_source TEXT,
                player_type TEXT,
                FOREIGN KEY (movie_id) REFERENCES cached_movies(id)
            )
        """)

        # TV Shows, Seasons, Episodes tables - AUDIT FIX: Renamed to match Android app expectations
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS cached_series (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                title_en TEXT,
                title_fa TEXT,
                slug TEXT UNIQUE NOT NULL,
                url TEXT NOT NULL,
                poster_url TEXT,
                release_date TEXT,
                country TEXT,
                rating REAL,
                votes INTEGER,
                synopsis_en TEXT,
                synopsis_fa TEXT,
                status TEXT,
                last_modified TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS tvshow_genres (
                tvshow_id INTEGER,
                genre TEXT,
                FOREIGN KEY (tvshow_id) REFERENCES cached_series(id),
                PRIMARY KEY (tvshow_id, genre)
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS seasons (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tvshow_id INTEGER,
                season_number INTEGER,
                release_date TEXT,
                FOREIGN KEY (tvshow_id) REFERENCES cached_series(id),
                UNIQUE (tvshow_id, season_number)
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS cached_episodes (
                id INTEGER PRIMARY KEY,
                tvshow_id INTEGER,
                season_id INTEGER,
                episode_number INTEGER,
                title TEXT,
                slug TEXT UNIQUE NOT NULL,
                url TEXT NOT NULL,
                thumbnail_url TEXT,
                release_date TEXT,
                synopsis TEXT,
                last_modified TEXT,
                FOREIGN KEY (tvshow_id) REFERENCES cached_series(id),
                FOREIGN KEY (season_id) REFERENCES seasons(id),
                UNIQUE (season_id, episode_number)
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS episode_videos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                episode_id INTEGER,
                quality TEXT,
                url TEXT NOT NULL,
                cdn_source TEXT,
                player_type TEXT,
                FOREIGN KEY (episode_id) REFERENCES cached_episodes(id)
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS scraper_metadata (
                key TEXT PRIMARY KEY,
                value TEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        conn.commit()

        # Migrate existing tables - AUDIT FIX: Updated table names
        try:
            cursor.execute("PRAGMA table_info(cached_movies)")
            columns = [col[1] for col in cursor.fetchall()]
            if 'last_modified' not in columns:
                print("Migrating cached_movies table...")
                cursor.execute("ALTER TABLE cached_movies ADD COLUMN last_modified TEXT")
                conn.commit()
        except:
            pass

        try:
            cursor.execute("PRAGMA table_info(cached_series)")
            columns = [col[1] for col in cursor.fetchall()]
            if 'last_modified' not in columns:
                print("Migrating cached_series table...")
                cursor.execute("ALTER TABLE cached_series ADD COLUMN last_modified TEXT")
                conn.commit()
        except:
            pass

        try:
            cursor.execute("PRAGMA table_info(cached_episodes)")
            columns = [col[1] for col in cursor.fetchall()]
            if 'last_modified' not in columns:
                print("Migrating cached_episodes table...")
                cursor.execute("ALTER TABLE cached_episodes ADD COLUMN last_modified TEXT")
                conn.commit()
        except:
            pass

        conn.close()
        print(f"✓ Database initialized: {self.db_path}")

    def fetch_sitemap(self, sitemap_url: str) -> List[Dict[str, str]]:
        """Fetch and parse sitemap"""
        try:
            response = self.session.get(sitemap_url)
            response.raise_for_status()
            root = ET.fromstring(response.content)
            namespace = {'ns': 'http://www.sitemaps.org/schemas/sitemap/0.9'}

            urls = []
            for url_element in root.findall('ns:url', namespace):
                loc = url_element.find('ns:loc', namespace)
                lastmod = url_element.find('ns:lastmod', namespace)
                if loc is not None:
                    urls.append({
                        'loc': loc.text,
                        'lastmod': lastmod.text if lastmod is not None else None
                    })
            return urls
        except Exception as e:
            print(f"✗ Error fetching sitemap: {e}")
            return []

    def extract_dooplay_form_data(self, soup: BeautifulSoup) -> Optional[Dict]:
        """
        Extract DooPlay form data from page
        Returns: {post_id, nonce, referer}
        """
        # Find the hidden form (usually id="watch-XXXXX")
        forms = soup.find_all('form', id=re.compile(r'watch-\d+'))

        for form in forms:
            post_id = form.get('id', '').replace('watch-', '')
            nonce_input = form.find('input', {'name': 'watch_episode_nonce'})
            referer_input = form.find('input', {'name': '_wp_http_referer'})

            if post_id and nonce_input:
                return {
                    'post_id': post_id,
                    'nonce': nonce_input.get('value'),
                    'referer': referer_input.get('value') if referer_input else ''
                }

        return None

    def extract_video_sources_dooplay(self, content_url: str) -> List[Dict]:
        """
        Extract video sources using DooPlay method

        DooPlay stores video quality options as <li> elements with:
        - data-post: post ID
        - data-type: "tv" or "movie"
        - data-nume: quality number (1=480p, 2=720p, 3=1080p)
        """
        video_sources = []

        try:
            # Get content page
            response = self.session.get(content_url)
            response.raise_for_status()
            soup = BeautifulSoup(response.text, 'html.parser')

            # Extract form data for accessing player
            form_data = self.extract_dooplay_form_data(soup)
            if not form_data:
                print("  ⚠ No DooPlay form found")
                return video_sources

            # Submit form to get to /play/ page
            play_url = f"{self.BASE_URL}/play/"
            play_response = self.session.post(play_url, data={
                'id': form_data['post_id'],
                'watch_episode_nonce': form_data['nonce'],
                '_wp_http_referer': form_data['referer']
            }, headers={'Referer': content_url})

            play_response.raise_for_status()
            play_soup = BeautifulSoup(play_response.text, 'html.parser')

            # Method 1: Extract from all iframe elements (most reliable)
            iframes = play_soup.find_all('iframe')
            for iframe in iframes:
                iframe_src = iframe.get('src', '')
                if 'source=' in iframe_src:
                    parsed = urlparse(iframe_src)
                    query_params = parse_qs(parsed.query)
                    if 'source' in query_params:
                        video_url = unquote(query_params['source'][0])
                        quality = self._detect_quality(video_url)
                        cdn = self._detect_cdn(video_url)

                        # Avoid duplicates
                        if not any(v['url'] == video_url for v in video_sources):
                            video_sources.append({
                                'url': video_url,
                                'quality': quality,
                                'cdn_source': cdn,
                                'player_type': 'jwplayer'
                            })

            # Method 2: Extract from JavaScript/HTML source using regex (backup method)
            import re
            html_content = play_response.text

            # Find all encoded video URLs in the page source
            # Pattern: /jwplayer/?source=ENCODED_URL or direct URLs
            encoded_urls = re.findall(r'source=([^&\'"]+)', html_content)
            for encoded_url in encoded_urls:
                try:
                    video_url = unquote(encoded_url)
                    if video_url.startswith('http') and ('.mp4' in video_url or '.m3u8' in video_url):
                        quality = self._detect_quality(video_url)
                        cdn = self._detect_cdn(video_url)

                        # Avoid duplicates
                        if not any(v['url'] == video_url for v in video_sources):
                            video_sources.append({
                                'url': video_url,
                                'quality': quality,
                                'cdn_source': cdn,
                                'player_type': 'jwplayer'
                            })
                except:
                    continue

            # Deduplicate by URL
            seen_urls = set()
            unique_sources = []
            for source in video_sources:
                if source['url'] not in seen_urls:
                    seen_urls.add(source['url'])
                    unique_sources.append(source)

            return unique_sources

        except Exception as e:
            print(f"  ✗ Error extracting videos: {e}")
            return video_sources

    def _detect_quality(self, video_url: str) -> str:
        """Detect video quality from URL"""
        url_lower = video_url.lower()
        if '1080' in url_lower or 'fhd' in url_lower:
            return '1080p'
        elif '720' in url_lower or 'hd' in url_lower:
            return '720p'
        elif '480' in url_lower:
            return '480p'
        elif '360' in url_lower:
            return '360p'
        else:
            return 'HD'

    def _detect_cdn(self, video_url: str) -> str:
        """Detect CDN from URL"""
        parsed = urlparse(video_url)
        domain = parsed.netloc

        if 'farsiland' in domain:
            return 'farsiland'
        elif 'farsicdn' in domain:
            return 'farsicdn'
        else:
            return domain

    def scrape_movie(self, movie_item: Dict) -> Optional[Dict]:
        """Scrape movie using DooPlay method"""
        url = movie_item.get('url')
        slug = url.rstrip('/').split('/')[-1]

        print(f"Scraping: {slug}...", end=' ')

        try:
            response = self.session.get(url)
            response.raise_for_status()
            soup = BeautifulSoup(response.text, 'html.parser')

            movie_data = {
                'url': url,
                'slug': slug,
                'last_modified': movie_item.get('lastmod')
            }

            # Extract metadata (same as before)
            title_element = soup.find('h1')
            if title_element:
                movie_data['title'] = title_element.text.strip()
                movie_data['title_en'] = title_element.text.strip()

            poster = soup.find('img', class_=re.compile(r'poster'))
            if not poster:
                poster = soup.find('div', class_=re.compile(r'poster'))
                if poster:
                    poster = poster.find('img')
            if poster:
                movie_data['poster_url'] = poster.get('src') or poster.get('data-src')

            original_title = soup.find('span', string=re.compile(r'Original title'))
            if original_title and original_title.find_next_sibling():
                movie_data['title_fa'] = original_title.find_next_sibling().text.strip()

            # Extract synopsis
            synopsis_elements = soup.find_all('p')
            for p in synopsis_elements:
                text = p.text.strip()
                if len(text) > 100 and not movie_data.get('synopsis_en'):
                    movie_data['synopsis_en'] = text
                elif re.search(r'[\u0600-\u06FF]', text) and len(text) > 50:
                    movie_data['synopsis_fa'] = text

            # Extract genres
            genres = []
            genre_links = soup.find_all('a', href=re.compile(r'/genres/'))
            for link in genre_links:
                genres.append(link.text.strip())
            movie_data['genres'] = genres

            # Extract rating
            rating_element = soup.find('span', class_=re.compile(r'rating'))
            if rating_element:
                try:
                    movie_data['rating'] = float(rating_element.text.strip())
                except:
                    pass

            # Extract video sources using DooPlay method
            video_sources = self.extract_video_sources_dooplay(url)
            movie_data['video_sources'] = video_sources

            print(f"✓ ({len(video_sources)} videos)")
            time.sleep(self.delay)
            return movie_data

        except Exception as e:
            print(f"✗ Error: {e}")
            return None

    def save_movie_to_db(self, movie_data: Dict):
        """Save movie to database (thread-safe)"""
        with self.db_lock:  # Ensure thread-safe database access
            conn = sqlite3.connect(self.db_path, timeout=30.0)  # Increase timeout
            cursor = conn.cursor()

            try:
                # AUDIT FIX C1: Use deterministic ID generation
                movie_id = self.generate_stable_id(movie_data['slug'])

                cursor.execute("""
                    INSERT OR REPLACE INTO cached_movies (
                        id, title, title_en, title_fa, slug, url, poster_url,
                        release_date, country, rating, votes, synopsis_en, synopsis_fa,
                        last_modified, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, (
                    movie_id,
                    movie_data.get('title'),
                    movie_data.get('title_en'),
                    movie_data.get('title_fa'),
                    movie_data['slug'],
                    movie_data['url'],
                    movie_data.get('poster_url'),
                    movie_data.get('release_date'),
                    movie_data.get('country'),
                    movie_data.get('rating'),
                    movie_data.get('votes'),
                    movie_data.get('synopsis_en'),
                    movie_data.get('synopsis_fa'),
                    movie_data.get('last_modified')
                ))

                # Save genres
                for genre in movie_data.get('genres', []):
                    cursor.execute("""
                        INSERT OR IGNORE INTO movie_genres (movie_id, genre)
                        VALUES (?, ?)
                    """, (movie_id, genre))

                # Save video sources
                for video in movie_data.get('video_sources', []):
                    cursor.execute("""
                        INSERT INTO movie_videos (movie_id, quality, url, cdn_source, player_type)
                        VALUES (?, ?, ?, ?, ?)
                    """, (
                        movie_id,
                        video.get('quality'),
                        video['url'],
                        video.get('cdn_source'),
                        video.get('player_type')
                    ))

                conn.commit()

            except Exception as e:
                print(f"  ✗ DB Error: {e}")
                conn.rollback()
            finally:
                conn.close()

    def scrape_tvshow(self, tvshow_item: Dict) -> Optional[Dict]:
        """Scrape TV show with all seasons and episodes"""
        url = tvshow_item.get('url')
        slug = url.rstrip('/').split('/')[-1]

        print(f"Scraping: {slug}...", end=' ')

        try:
            response = self.session.get(url)
            response.raise_for_status()
            soup = BeautifulSoup(response.text, 'html.parser')

            tvshow_data = {
                'url': url,
                'slug': slug,
                'last_modified': tvshow_item.get('lastmod'),
                'seasons': []
            }

            # Extract metadata
            title_element = soup.find('h1')
            if title_element:
                tvshow_data['title'] = title_element.text.strip()
                tvshow_data['title_en'] = title_element.text.strip()

            poster = soup.find('img', class_=re.compile(r'poster'))
            if not poster:
                poster = soup.find('div', class_=re.compile(r'poster'))
                if poster:
                    poster = poster.find('img')
            if poster:
                tvshow_data['poster_url'] = poster.get('src') or poster.get('data-src')

            # Extract genres
            genres = []
            genre_links = soup.find_all('a', href=re.compile(r'/genres/'))
            for link in genre_links:
                genres.append(link.text.strip())
            tvshow_data['genres'] = genres

            # Extract rating
            rating_element = soup.find('span', class_=re.compile(r'rating'))
            if rating_element:
                try:
                    tvshow_data['rating'] = float(rating_element.text.strip())
                except:
                    pass

            # Extract seasons and episodes
            season_containers = soup.find_all('div', class_=re.compile(r'se-c'))

            for season_container in season_containers:
                season_data = self._extract_season_data(season_container, tvshow_data)
                if season_data:
                    tvshow_data['seasons'].append(season_data)

            print(f"✓ ({len(tvshow_data['seasons'])} seasons, {sum(len(s['episodes']) for s in tvshow_data['seasons'])} episodes)")
            time.sleep(self.delay)
            return tvshow_data

        except Exception as e:
            print(f"✗ Error: {e}")
            return None

    def _extract_season_data(self, season_container, tvshow_data: Dict) -> Optional[Dict]:
        """Extract season and episodes from season container"""
        try:
            # Find season number
            season_text = season_container.find('span', class_='se-t')
            season_number = 1

            if season_text:
                text = season_text.text.strip()
                match = re.search(r'Season\s+(\d+)', text, re.I)
                if match:
                    season_number = int(match.group(1))

            season_data = {
                'season_number': season_number,
                'episodes': []
            }

            # Find release date
            date_span = season_container.find('span', class_='date')
            if date_span:
                season_data['release_date'] = date_span.text.strip()

            # Extract episodes
            episode_list = season_container.find('ul', class_='episodios')
            if episode_list:
                episode_items = episode_list.find_all('li')

                for episode_item in episode_items:
                    episode_data = self._extract_episode_data(episode_item, tvshow_data, season_number)
                    if episode_data:
                        season_data['episodes'].append(episode_data)

            return season_data if season_data['episodes'] else None

        except Exception as e:
            print(f"  ✗ Season error: {e}")
            return None

    def _extract_episode_data(self, episode_item, tvshow_data: Dict, season_number: int) -> Optional[Dict]:
        """Extract episode data"""
        try:
            episode_link = episode_item.find('a', href=re.compile(r'/episode/'))
            if not episode_link:
                return None

            episode_url = episode_link.get('href')
            if not episode_url.startswith('http'):
                episode_url = urljoin(self.BASE_URL, episode_url)

            episode_title = episode_link.text.strip()
            slug = episode_url.rstrip('/').split('/')[-1]

            # Extract episode number from "1 - 3" format
            numerando = episode_item.find('div', class_='numerando')
            episode_number = None

            if numerando:
                match = re.search(r'(\d+)\s*-\s*(\d+)', numerando.text)
                if match:
                    episode_number = int(match.group(2))

            if not episode_number:
                # Fallback: count episodes
                # AUDIT FIX M5: Guard against empty seasons list (IndexError)
                seasons = tvshow_data.get('seasons', [])
                if not seasons:
                    print(f"  ⚠ Warning: TV show has no seasons data, skipping episode")
                    continue  # Skip this episode
                episode_number = len(seasons[-1].get('episodes', [])) + 1

            episode_data = {
                'episode_number': episode_number,
                'title': episode_title,
                'url': episode_url,
                'slug': slug
            }

            # Extract thumbnail
            img = episode_item.find('img')
            if img:
                episode_data['thumbnail_url'] = img.get('src') or img.get('data-src')

            # Extract release date
            date_span = episode_item.find('span', class_='date')
            if date_span:
                episode_data['release_date'] = date_span.text.strip()

            return episode_data

        except Exception as e:
            return None

    def scrape_episode_videos(self, episode_url: str) -> List[Dict]:
        """Scrape video sources for an episode"""
        return self.extract_video_sources_dooplay(episode_url)

    def save_tvshow_to_db(self, tvshow_data: Dict):
        """Save TV show with parent/child relationships (thread-safe)"""
        with self.db_lock:  # Ensure thread-safe database access
            conn = sqlite3.connect(self.db_path, timeout=30.0)  # Increase timeout
            cursor = conn.cursor()

            try:
                # AUDIT FIX C1: Use deterministic ID generation
                tvshow_id = self.generate_stable_id(tvshow_data['slug'])

                # Save TV show (parent)
                cursor.execute("""
                    INSERT OR REPLACE INTO cached_series (
                        id, title, title_en, title_fa, slug, url, poster_url,
                        release_date, country, rating, votes, last_modified, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, (
                    tvshow_id,
                    tvshow_data.get('title'),
                    tvshow_data.get('title_en'),
                    tvshow_data.get('title_fa'),
                    tvshow_data['slug'],
                    tvshow_data['url'],
                    tvshow_data.get('poster_url'),
                    tvshow_data.get('release_date'),
                    tvshow_data.get('country'),
                    tvshow_data.get('rating'),
                    tvshow_data.get('votes'),
                    tvshow_data.get('last_modified')
                ))

                # Save genres
                for genre in tvshow_data.get('genres', []):
                    cursor.execute("""
                        INSERT OR IGNORE INTO tvshow_genres (tvshow_id, genre)
                        VALUES (?, ?)
                    """, (tvshow_id, genre))

                # Save seasons and episodes (children)
                for season_data in tvshow_data.get('seasons', []):
                    # Save season (child of TV show)
                    cursor.execute("""
                        INSERT OR REPLACE INTO seasons (tvshow_id, season_number, release_date)
                        VALUES (?, ?, ?)
                    """, (
                        tvshow_id,
                        season_data['season_number'],
                        season_data.get('release_date')
                    ))
                    season_id = cursor.lastrowid

                    # Save episodes (children of season)
                    for episode_data in season_data.get('episodes', []):
                        # AUDIT FIX C1: Use deterministic ID generation
                        episode_id = self.generate_stable_id(episode_data['slug'])

                        cursor.execute("""
                            INSERT OR REPLACE INTO cached_episodes (
                                id, tvshow_id, season_id, episode_number, title,
                                slug, url, thumbnail_url, release_date
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, (
                            episode_id,
                            tvshow_id,  # Parent TV show ID
                            season_id,  # Parent season ID
                            episode_data['episode_number'],
                            episode_data.get('title'),
                            episode_data['slug'],
                            episode_data['url'],
                            episode_data.get('thumbnail_url'),
                            episode_data.get('release_date')
                        ))

                        # Scrape episode videos
                        print(f"    Scraping {episode_data.get('title')}...", end=' ')
                        video_sources = self.scrape_episode_videos(episode_data['url'])

                        for video in video_sources:
                            cursor.execute("""
                                INSERT INTO episode_videos (episode_id, quality, url, cdn_source, player_type)
                                VALUES (?, ?, ?, ?, ?)
                            """, (
                                episode_id,
                                video.get('quality'),
                                video['url'],
                                video.get('cdn_source'),
                                video.get('player_type')
                            ))

                        print(f"✓ ({len(video_sources)} videos)")
                        time.sleep(self.delay)

                conn.commit()

            except Exception as e:
                print(f"  ✗ DB Error: {e}")
                conn.rollback()
            finally:
                conn.close()

    def run_full_scrape(self, parallel=False, max_workers=5):
        """
        Run full scrape of movies and TV shows

        Args:
            parallel: If True, use parallel processing (much faster)
            max_workers: Number of parallel workers (default: 5)
        """
        print("\n" + "="*60)
        print("FarsiPlex DooPlay Scraper")
        if parallel:
            print(f"Mode: PARALLEL ({max_workers} workers)")
        else:
            print("Mode: SEQUENTIAL")
        print("="*60)

        start_time = time.time()

        # Fetch sitemaps
        print("\nFetching movies sitemap...")
        movies = self.fetch_sitemap(self.MOVIES_SITEMAP)
        movies = [{'url': item['loc'], 'lastmod': item['lastmod']} for item in movies]
        print(f"✓ Found {len(movies)} movies")

        print("\nFetching TV shows sitemap...")
        tvshows = self.fetch_sitemap(self.TVSHOWS_SITEMAP)
        tvshows = [{'url': item['loc'], 'lastmod': item['lastmod']} for item in tvshows]
        print(f"✓ Found {len(tvshows)} TV shows\n")

        if parallel:
            self._run_parallel_scrape(movies, tvshows, max_workers)
        else:
            self._run_sequential_scrape(movies, tvshows)

        elapsed = time.time() - start_time
        print("\n" + "="*60)
        print(f"✓ Complete! Time: {elapsed/60:.1f} minutes")
        print("="*60)

    def _run_sequential_scrape(self, movies, tvshows):
        """Sequential scraping (original method)"""
        # Scrape movies
        print(f"=== Scraping {len(movies)} Movies ===")
        for i, movie_item in enumerate(movies, 1):
            print(f"[{i}/{len(movies)}] ", end='')
            movie_data = self.scrape_movie(movie_item)
            if movie_data:
                self.save_movie_to_db(movie_data)

        # Scrape TV shows
        print(f"\n=== Scraping {len(tvshows)} TV Shows ===")
        for i, tvshow_item in enumerate(tvshows, 1):
            print(f"[{i}/{len(tvshows)}] ", end='')
            tvshow_data = self.scrape_tvshow(tvshow_item)
            if tvshow_data:
                self.save_tvshow_to_db(tvshow_data)

    def _run_parallel_scrape(self, movies, tvshows, max_workers):
        """Parallel scraping using ThreadPoolExecutor"""
        from concurrent.futures import ThreadPoolExecutor, as_completed
        from threading import Lock

        print_lock = Lock()
        completed = {'movies': 0, 'tvshows': 0}

        def scrape_and_save_movie(movie_item):
            try:
                movie_data = self.scrape_movie(movie_item)
                if movie_data:
                    self.save_movie_to_db(movie_data)
                with print_lock:
                    completed['movies'] += 1
                    print(f"\r[{completed['movies']}/{len(movies)}] Movies scraped", end='', flush=True)
                return True
            except Exception as e:
                with print_lock:
                    print(f"\n✗ Error scraping movie {movie_item['url']}: {e}")
                return False

        def scrape_and_save_tvshow(tvshow_item):
            try:
                tvshow_data = self.scrape_tvshow(tvshow_item)
                if tvshow_data:
                    self.save_tvshow_to_db(tvshow_data)
                with print_lock:
                    completed['tvshows'] += 1
                    print(f"\r[{completed['tvshows']}/{len(tvshows)}] TV Shows scraped", end='', flush=True)
                return True
            except Exception as e:
                with print_lock:
                    print(f"\n✗ Error scraping TV show {tvshow_item['url']}: {e}")
                return False

        # Scrape movies in parallel
        print(f"=== Scraping {len(movies)} Movies ({max_workers} parallel workers) ===")
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = [executor.submit(scrape_and_save_movie, movie) for movie in movies]
            for future in as_completed(futures):
                pass  # Progress printed in callback
        print()  # New line after progress

        # Scrape TV shows in parallel
        print(f"\n=== Scraping {len(tvshows)} TV Shows ({max_workers} parallel workers) ===")
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = [executor.submit(scrape_and_save_tvshow, tvshow) for tvshow in tvshows]
            for future in as_completed(futures):
                pass  # Progress printed in callback
        print()  # New line after progress


def main():
    import argparse
    parser = argparse.ArgumentParser(description='FarsiPlex Scraper')
    parser.add_argument('--parallel', action='store_true', help='Use parallel scraping (faster)')
    parser.add_argument('--workers', type=int, default=5, help='Number of parallel workers (default: 5)')
    parser.add_argument('--delay', type=float, default=2.0, help='Delay between requests in seconds (default: 2.0)')
    args = parser.parse_args()

    scraper = FarsiPlexDooPlayScraper(db_path="Farsiplex.db", delay=args.delay)
    scraper.run_full_scrape(parallel=args.parallel, max_workers=args.workers)


if __name__ == "__main__":
    main()
