#!/usr/bin/env python3
"""
NAMAKADEH MASTER SCRAPER - All-in-One Content Discovery & Scraping
Version: 2.0
Features:
- Complete catalog scraping (series + movies)
- Verification & gap filling
- Missing data detection
- Checkpoint management utilities
- Progress tracking & statistics
"""

import json
import time
import signal
import threading
import shutil
import os
from datetime import datetime
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Optional

import requests
from bs4 import BeautifulSoup
from selenium import webdriver
from selenium.webdriver.chrome.options import Options

BASE_URL = "https://namakade.com"
CHECKPOINT_FILE = "complete_scraper_checkpoint.json"

# Thread-safe locks
print_lock = threading.Lock()
checkpoint_lock = threading.Lock()

# Global state
should_stop = False
active_workers = 0

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def signal_handler(signum, frame):
    """Handle Ctrl+C gracefully"""
    global should_stop, active_workers
    progress_print("\n" + "="*80)
    progress_print("[!] INTERRUPT SIGNAL RECEIVED (Ctrl+C)")
    progress_print("="*80)
    progress_print(f"Active workers: {active_workers}")
    progress_print("Waiting for current batch to finish...")
    progress_print("DO NOT close terminal - checkpoint will be saved!")
    progress_print("="*80)
    should_stop = True

def progress_print(msg):
    """Thread-safe printing"""
    with print_lock:
        print(msg, flush=True)

def setup_browser(headless: bool = True):
    """Setup browser instance"""
    chrome_options = Options()
    chrome_options.add_argument("--dns-prefetch-disable")
    chrome_options.add_argument("--disable-blink-features=AutomationControlled")

    if headless:
        chrome_options.add_argument("--headless")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")

    chrome_options.add_argument("--disable-extensions")
    chrome_options.add_argument("--disable-popup-blocking")

    return webdriver.Chrome(options=chrome_options)

def load_checkpoint() -> Optional[Dict]:
    """Load existing checkpoint"""
    if os.path.exists(CHECKPOINT_FILE):
        with open(CHECKPOINT_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    return None

def save_checkpoint_safe(checkpoint):
    """Thread-safe checkpoint save"""
    with checkpoint_lock:
        try:
            with open(CHECKPOINT_FILE, 'w', encoding='utf-8') as f:
                json.dump(checkpoint, f, indent=2, ensure_ascii=False)
            return True
        except Exception as e:
            progress_print(f"[ERROR] Failed to save checkpoint: {e}")
            return False

def create_backup(description: str = ""):
    """Create timestamped backup"""
    if not os.path.exists(CHECKPOINT_FILE):
        print("[ERROR] No checkpoint file to backup!")
        return None

    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    desc = f"_{description}" if description else ""
    backup_file = f"complete_scraper_checkpoint_backup{desc}_{timestamp}.json"

    shutil.copy(CHECKPOINT_FILE, backup_file)
    size_mb = os.path.getsize(backup_file) / (1024 * 1024)

    print(f"\n[OK] Backup created: {backup_file} ({size_mb:.2f} MB)")
    return backup_file

# ============================================================================
# VIDEO URL EXTRACTION
# ============================================================================

def extract_video_url(driver, page_url: str, content_type: str = "series") -> str:
    """Extract MP4 URL from episode/movie page"""
    try:
        if not page_url.startswith('http'):
            full_url = f"{BASE_URL}{page_url}"
        else:
            full_url = page_url

        driver.get(full_url)
        time.sleep(5 if content_type == "movie" else 3)

        soup = BeautifulSoup(driver.page_source, 'html.parser')

        # METHOD 1: Check for <video><source> tag (MOVIES use this)
        if content_type == "movie":
            video_tag = soup.find('video', id='videoTag') or soup.find('video', class_='video-js') or soup.find('video')
            if video_tag:
                source_tag = video_tag.find('source')
                if source_tag and source_tag.get('src'):
                    video_url = source_tag['src']
                    video_url = video_url.replace('media.iranproud2.net', 'media.negahestan.com')
                    video_url = video_url.replace('media.iranproud.net', 'media.negahestan.com')
                    return video_url

        # METHOD 2: Check for JavaScript (SERIES use this)
        scripts = soup.find_all('script')
        for script in scripts:
            if script.string and 'seriesepisode_respose' in script.string:
                script_text = script.string

                start = script_text.find('var seriesepisode_respose = ')
                if start != -1:
                    start += len('var seriesepisode_respose = ')
                else:
                    start = script_text.find('var seriesepisode_respose=')
                    if start != -1:
                        start += len('var seriesepisode_respose=')

                if start != -1:
                    end = script_text.find(';', start)
                    json_str = script_text[start:end].strip()

                    try:
                        data = json.loads(json_str)
                        video_urls = data.get('video_url', [])

                        for url_obj in video_urls:
                            if 'android' in url_obj:
                                video_url = url_obj['android']
                                video_url = video_url.replace('media.iranproud2.net', 'media.negahestan.com')
                                video_url = video_url.replace('media.iranproud.net', 'media.negahestan.com')
                                return video_url

                        for url_obj in video_urls:
                            if 'ios' in url_obj:
                                video_url = url_obj['ios']
                                video_url = video_url.replace('media.iranproud2.net', 'media.negahestan.com')
                                video_url = video_url.replace('media.iranproud.net', 'media.negahestan.com')
                                return video_url
                    except:
                        pass

        return ""
    except Exception as e:
        return ""

# ============================================================================
# SERIES SCRAPING
# ============================================================================

def scrape_show_details(driver, show: Dict, worker_id: int) -> Dict:
    """Scrape ONE show completely"""
    global active_workers

    try:
        active_workers += 1

        url = show['url']
        if not url.startswith('http'):
            full_url = f"{BASE_URL}{url}"
        else:
            full_url = url

        driver.get(full_url)
        time.sleep(3)

        soup = BeautifulSoup(driver.page_source, 'html.parser')

        # View count
        view_count = 0
        for id_name in ['divVidDet09', 'divVidDet08', 'divVidDet07']:
            view_div = soup.find('div', {'id': id_name})
            if view_div and 'Views' in view_div.text:
                try:
                    view_count = int(view_div.text.split(':')[-1].strip())
                    break
                except:
                    pass

        show['view_count'] = view_count

        # For movies: extract video URL
        if show['type'] == 'movie':
            video_url = extract_video_url(driver, show['url'], content_type="movie")
            show['video_url'] = video_url

        # For series: get episodes AND extract video URLs
        episodes = []
        if show['type'] == 'series':
            episode_grid = soup.find('ul', id='gridMason2')
            if episode_grid:
                episode_items = episode_grid.find_all('li')
                total_episodes = len(episode_items)

                for idx, item in enumerate(episode_items, 1):
                    if should_stop:
                        break

                    link = item.find('a')
                    if link and link.get('href'):
                        episode_url = link['href']
                        img = item.find('img')

                        # Extract video URL
                        video_url = extract_video_url(driver, episode_url, content_type="series")

                        episode_id = f"{show['id']}_ep{idx}"

                        episodes.append({
                            'id': episode_id,
                            'show_id': show['id'],
                            'episode_number': idx,
                            'slug': episode_url.split('/')[-1],
                            'url': episode_url,
                            'thumbnail': img['src'] if img else '',
                            'video_url': video_url
                        })

                        # Per-episode progress
                        status = "[OK]" if video_url else "[FAIL]"
                        progress_print(
                            f"[W{worker_id}] {episode_id[:40]}: {status} ({idx}/{total_episodes})"
                        )

        return {
            'show': show,
            'episodes': episodes,
            'worker_id': worker_id,
            'success': True,
            'mode': 'full_scrape'
        }

    except Exception as e:
        return {
            'show': show,
            'episodes': [],
            'worker_id': worker_id,
            'success': False,
            'error': str(e),
            'mode': 'full_scrape'
        }
    finally:
        active_workers -= 1
        try:
            driver.quit()
        except:
            pass

def extract_missing_urls_for_show(driver, show_id: str, episodes: List[Dict], worker_id: int) -> Dict:
    """Extract video URLs for existing episodes that are missing them"""
    global active_workers

    try:
        active_workers += 1
        updated_episodes = []

        episodes_needing_urls = [e for e in episodes if not e.get('video_url')]
        total_to_extract = len(episodes_needing_urls)
        extracted_count = 0

        for episode in episodes:
            if should_stop:
                break

            if not episode.get('video_url'):
                video_url = extract_video_url(driver, episode['url'], content_type="series")
                episode['video_url'] = video_url
                extracted_count += 1

                status = "[OK]" if video_url else "[FAIL]"
                progress_print(
                    f"[W{worker_id}] {episode['id'][:40]}: {status} "
                    f"({extracted_count}/{total_to_extract})"
                )

            updated_episodes.append(episode)

        return {
            'show_id': show_id,
            'episodes': updated_episodes,
            'worker_id': worker_id,
            'success': True,
            'mode': 'extract_only',
            'extracted_count': extracted_count,
            'total_count': total_to_extract
        }

    except Exception as e:
        return {
            'show_id': show_id,
            'episodes': episodes,
            'worker_id': worker_id,
            'success': False,
            'error': str(e),
            'mode': 'extract_only',
            'extracted_count': 0,
            'total_count': len(episodes)
        }
    finally:
        active_workers -= 1
        try:
            driver.quit()
        except:
            pass

# ============================================================================
# ANALYSIS & VERIFICATION
# ============================================================================

def analyze_checkpoint_status(checkpoint: Dict) -> Dict:
    """Analyze checkpoint for completeness and issues"""
    shows = checkpoint.get('shows', [])
    episodes = checkpoint.get('episodes', [])

    series = [s for s in shows if s['type'] == 'series']
    movies = [s for s in shows if s['type'] == 'movie']

    # Group episodes by show
    eps_by_show = defaultdict(list)
    for ep in episodes:
        eps_by_show[ep['show_id']].append(ep)

    # Episode URL coverage
    eps_with_urls = sum(1 for e in episodes if e.get('video_url'))
    eps_missing_urls = [e for e in episodes if not e.get('video_url')]

    # Movie URL coverage
    movies_with_urls = [m for m in movies if m.get('video_url')]
    movies_missing_urls = [m for m in movies if not m.get('video_url')]

    # Series with episodes
    series_with_eps = [s for s in series if s['id'] in eps_by_show]
    series_without_eps = [s for s in series if s['id'] not in eps_by_show]

    # Series with missing episode URLs
    series_with_missing_urls = []
    for s in series:
        show_episodes = eps_by_show[s['id']]
        if show_episodes:
            missing = [e for e in show_episodes if not e.get('video_url')]
            if missing:
                series_with_missing_urls.append({
                    'show': s,
                    'show_id': s['id'],
                    'episodes': show_episodes,
                    'missing_count': len(missing),
                    'total_count': len(show_episodes)
                })

    return {
        'total_shows': len(shows),
        'series_count': len(series),
        'movies_count': len(movies),
        'total_episodes': len(episodes),
        'episodes_with_urls': eps_with_urls,
        'episodes_missing_urls': eps_missing_urls,
        'movies_with_urls': len(movies_with_urls),
        'movies_missing_urls': movies_missing_urls,
        'series_with_episodes': len(series_with_eps),
        'series_without_episodes': series_without_eps,
        'series_with_missing_urls': series_with_missing_urls
    }

def print_status_report(checkpoint: Dict):
    """Print comprehensive status report"""
    status = analyze_checkpoint_status(checkpoint)

    print("\n" + "="*80)
    print("CHECKPOINT STATUS REPORT")
    print("="*80)

    print(f"\nSHOWS:")
    print(f"  Total: {status['total_shows']}")
    print(f"  Series: {status['series_count']}")
    print(f"  Movies: {status['movies_count']}")

    print(f"\nSERIES:")
    print(f"  With episodes: {status['series_with_episodes']}/{status['series_count']}")
    if status['series_without_episodes']:
        print(f"  WITHOUT episodes: {len(status['series_without_episodes'])}")
        print(f"    Sample: {[s['id'] for s in status['series_without_episodes'][:5]]}")

    print(f"\nEPISODES:")
    print(f"  Total: {status['total_episodes']}")
    print(f"  With video URLs: {status['episodes_with_urls']}/{status['total_episodes']} ({status['episodes_with_urls']/status['total_episodes']*100:.1f}%)")
    if status['episodes_missing_urls']:
        print(f"  Missing URLs: {len(status['episodes_missing_urls'])}")
        print(f"    Sample: {[e['id'] for e in status['episodes_missing_urls'][:5]]}")

    print(f"\nMOVIES:")
    print(f"  With video URLs: {status['movies_with_urls']}/{status['movies_count']} ({status['movies_with_urls']/status['movies_count']*100 if status['movies_count'] > 0 else 0:.1f}%)")
    if status['movies_missing_urls']:
        print(f"  Missing URLs: {len(status['movies_missing_urls'])}")
        print(f"    Sample: {[m['id'] for m in status['movies_missing_urls'][:5]]}")

    if status['series_with_missing_urls']:
        print(f"\nSERIES WITH MISSING EPISODE URLs:")
        print(f"  Count: {len(status['series_with_missing_urls'])}")
        for s in status['series_with_missing_urls'][:5]:
            print(f"    {s['show_id']}: {s['missing_count']}/{s['total_count']} episodes need URLs")

    print(f"\nCHECKPOINT INFO:")
    print(f"  Phase: {checkpoint.get('phase', 'N/A')}")
    print(f"  Last detail index: {checkpoint.get('last_detail_index', 'N/A')}")

    print("="*80)

def verify_series_completeness(checkpoint: Dict, num_workers: int = 3):
    """Verify series have all episodes by checking website"""
    shows = checkpoint['shows']
    episodes = checkpoint['episodes']

    series = [s for s in shows if s['type'] == 'series']

    eps_by_show = defaultdict(list)
    for ep in episodes:
        eps_by_show[ep['show_id']].append(ep)

    # Find series with potential gaps (small differences only)
    series_to_verify = []
    for s in series:
        actual = len(eps_by_show[s['id']])
        expected = s.get('total_episodes', 0)

        if actual > 0 and actual < expected and (expected - actual) <= 20:
            series_to_verify.append({
                'show': s,
                'existing_episodes': eps_by_show[s['id']],
                'gap': expected - actual
            })

    if not series_to_verify:
        print("\n[OK] No series need verification!")
        return

    print(f"\nFound {len(series_to_verify)} series to verify")
    print(f"Estimated episodes to check: {sum(s['gap'] for s in series_to_verify)}")
    print("Note: Most gaps are metadata errors (~70-80%)")

    confirm = input("\nVerify and scrape gaps? (yes/no): ").strip().lower()
    if confirm != 'yes':
        return

    progress_print("\nStarting verification...")

    start_time = time.time()
    total_verified = 0
    total_new_episodes = 0

    with ThreadPoolExecutor(max_workers=num_workers) as executor:
        futures = {}

        for i, task in enumerate(series_to_verify):
            if should_stop:
                break

            worker_id = (i % num_workers) + 1
            future = executor.submit(
                verify_and_scrape_series,
                setup_browser(headless=True),
                task['show'],
                task['existing_episodes'],
                worker_id
            )
            futures[future] = (i, task['show']['id'])

        for future in as_completed(futures):
            idx, show_id = futures[future]

            try:
                result = future.result(timeout=600)
                total_verified += 1

                if result['success'] and len(result.get('new_episodes', [])) > 0:
                    episodes.extend(result['new_episodes'])
                    total_new_episodes += len(result['new_episodes'])

                    progress_print(
                        f"[W{result['worker_id']}] [{total_verified}/{len(series_to_verify)}] "
                        f"{result['show_id'][:30]}: Added {len(result['new_episodes'])} episodes"
                    )
                else:
                    progress_print(
                        f"[W{result['worker_id']}] [{total_verified}/{len(series_to_verify)}] "
                        f"{result['show_id'][:30]}: Already complete"
                    )

                if total_verified % 10 == 0:
                    checkpoint['episodes'] = episodes
                    save_checkpoint_safe(checkpoint)

            except Exception as e:
                progress_print(f"[ERROR] {show_id}: {str(e)[:50]}")

    checkpoint['episodes'] = episodes
    save_checkpoint_safe(checkpoint)

    elapsed = time.time() - start_time
    print(f"\n[OK] Verification complete in {int(elapsed/60)}m {int(elapsed%60)}s")
    print(f"New episodes added: {total_new_episodes}")

def verify_and_scrape_series(driver, show, existing_episodes, worker_id):
    """Verify episode count and scrape only missing episodes"""
    global active_workers

    try:
        active_workers += 1

        url = show['url']
        full_url = f"{BASE_URL}{url}" if not url.startswith('http') else url

        driver.get(full_url)
        time.sleep(3)

        soup = BeautifulSoup(driver.page_source, 'html.parser')

        episode_grid = soup.find('ul', id='gridMason2')
        if not episode_grid:
            return {
                'show_id': show['id'],
                'worker_id': worker_id,
                'success': False,
                'new_episodes': [],
                'website_count': 0,
                'scraped_count': len(existing_episodes)
            }

        episode_items = episode_grid.find_all('li')
        website_count = len(episode_items)
        scraped_count = len(existing_episodes)

        if website_count <= scraped_count:
            return {
                'show_id': show['id'],
                'worker_id': worker_id,
                'success': True,
                'new_episodes': [],
                'website_count': website_count,
                'scraped_count': scraped_count
            }

        existing_urls = {ep.get('url', '') for ep in existing_episodes}
        new_episodes = []

        for idx, item in enumerate(episode_items, 1):
            if should_stop:
                break

            link = item.find('a')
            if link and link.get('href'):
                episode_url = link['href']

                if episode_url in existing_urls:
                    continue

                img = item.find('img')
                video_url = extract_video_url(driver, episode_url, content_type="series")

                episode_id = f"{show['id']}_ep{idx}"

                new_episodes.append({
                    'id': episode_id,
                    'show_id': show['id'],
                    'episode_number': idx,
                    'slug': episode_url.split('/')[-1],
                    'url': episode_url,
                    'thumbnail': img['src'] if img else '',
                    'video_url': video_url
                })

        return {
            'show_id': show['id'],
            'worker_id': worker_id,
            'success': True,
            'new_episodes': new_episodes,
            'website_count': website_count,
            'scraped_count': scraped_count
        }

    except Exception as e:
        return {
            'show_id': show['id'],
            'worker_id': worker_id,
            'success': False,
            'error': str(e),
            'new_episodes': []
        }
    finally:
        active_workers -= 1
        try:
            driver.quit()
        except:
            pass

# ============================================================================
# CATALOG DISCOVERY
# ============================================================================

def discover_catalog_from_website(checkpoint: Dict, num_workers: int = 2):
    """Discover new shows from all Namakade categories"""
    global should_stop

    signal.signal(signal.SIGINT, signal_handler)

    print("\n" + "="*80)
    print("CATALOG DISCOVERY - Scanning Namakade Website")
    print("="*80)

    # Categories to scrape
    categories = [
        {'name': 'Iranian Shows', 'url': '/iranianshows/', 'type': 'series'},
        {'name': 'Turkish Series', 'url': '/turkseries/', 'type': 'series'},
        {'name': 'Korean Series', 'url': '/koreanseries/', 'type': 'series'},
        {'name': 'Iranian Movies', 'url': '/iran-1-movies/', 'type': 'movie'},
        {'name': 'Foreign Movies', 'url': '/foreign-movies/', 'type': 'movie'},
        {'name': 'Series', 'url': '/series/', 'type': 'series'},
        {'name': 'Anime', 'url': '/anime/', 'type': 'series'},
    ]

    print(f"\nWill scan {len(categories)} categories:")
    for cat in categories:
        print(f"  - {cat['name']}: {cat['url']}")

    confirm = input("\nStart catalog discovery? (yes/no): ").strip().lower()
    if confirm != 'yes':
        return

    existing_shows = checkpoint.get('shows', [])
    existing_urls = {show['url'] for show in existing_shows}

    discovered_shows = []

    print(f"\nStarting discovery with {num_workers} workers...")
    print("="*80 + "\n")

    try:
        driver = setup_browser(headless=True)

        for category in categories:
            if should_stop:
                break

            print(f"\n[Scanning] {category['name']}...")

            try:
                # Try pagination - most categories have paginated listings
                page = 1
                max_pages = 50  # Safety limit
                category_shows = []

                while page <= max_pages:
                    if should_stop:
                        break

                    # Build URL with pagination
                    if '?' in category['url']:
                        page_url = f"{BASE_URL}{category['url']}&page={page}"
                    else:
                        page_url = f"{BASE_URL}{category['url']}?page={page}"

                    driver.get(page_url)
                    time.sleep(2)

                    soup = BeautifulSoup(driver.page_source, 'html.parser')

                    # Find show items - common patterns on Namakade
                    items = soup.select('article.item, div.item, li.item, div.video-item, article.post')

                    if not items:
                        # Try alternative selectors
                        items = soup.select('.poster, .movie-item, .series-item')

                    if not items:
                        print(f"  Page {page}: No items found, ending category")
                        break

                    page_shows = 0
                    for item in items:
                        try:
                            # Find link
                            link_tag = item.find('a')
                            if not link_tag or not link_tag.get('href'):
                                continue

                            url = link_tag['href']

                            # Skip if already exists
                            if url in existing_urls:
                                continue

                            # Extract title
                            title = link_tag.get('title', '')
                            if not title:
                                title = item.select_one('h2, h3, .title')
                                title = title.text.strip() if title else ''

                            if not title:
                                continue

                            # Extract poster
                            img = item.find('img')
                            poster = img['src'] if img and img.get('src') else ''

                            # Create show entry
                            show_id = url.split('/')[-2] if url.endswith('/') else url.split('/')[-1]

                            new_show = {
                                'id': show_id,
                                'title': title,
                                'url': url,
                                'type': category['type'],
                                'poster': poster,
                                'category': category['name'],
                                'discovered_at': datetime.now().isoformat()
                            }

                            discovered_shows.append(new_show)
                            existing_urls.add(url)
                            page_shows += 1

                        except Exception as e:
                            continue

                    print(f"  Page {page}: Found {page_shows} new shows (total: {len(discovered_shows)})")

                    if page_shows == 0:
                        # No new shows on this page, likely end of category
                        break

                    page += 1

                print(f"  [{category['name']}] Total: {len(category_shows)} shows")

            except Exception as e:
                print(f"  [ERROR] {category['name']}: {str(e)[:100]}")

        driver.quit()

    except Exception as e:
        print(f"\n[ERROR] Discovery failed: {e}")

    print("\n" + "="*80)
    print("DISCOVERY COMPLETE")
    print("="*80)
    print(f"New shows discovered: {len(discovered_shows)}")
    print(f"Total shows in catalog: {len(existing_shows) + len(discovered_shows)}")

    if discovered_shows:
        print(f"\nSample discovered shows:")
        for show in discovered_shows[:10]:
            print(f"  - {show['title']} ({show['category']}) - {show['url']}")

        confirm = input(f"\nAdd {len(discovered_shows)} new shows to checkpoint? (yes/no): ").strip().lower()
        if confirm == 'yes':
            checkpoint['shows'] = existing_shows + discovered_shows
            checkpoint['discovery_last_run'] = datetime.now().isoformat()
            save_checkpoint_safe(checkpoint)
            print(f"\n[OK] Added {len(discovered_shows)} new shows to catalog!")

            # Create backup
            create_backup("after_discovery")
        else:
            print("\n[CANCELLED]")
    else:
        print("\n[OK] No new shows found - catalog is up to date!")

# ============================================================================
# MAIN SCRAPING WORKFLOW
# ============================================================================

def run_complete_scrape(checkpoint: Dict, num_workers: int = 3, headless: bool = True):
    """Run complete scraping workflow for series and movies"""
    global should_stop

    signal.signal(signal.SIGINT, signal_handler)

    all_shows = checkpoint['shows']
    all_episodes = checkpoint['episodes']

    # Determine what to scrape
    status = analyze_checkpoint_status(checkpoint)

    tasks_to_scrape = []

    # Movies without URLs
    if status['movies_missing_urls']:
        tasks_to_scrape.extend(status['movies_missing_urls'])
        progress_print(f"Will scrape {len(status['movies_missing_urls'])} movies")

    # Series without episodes
    if status['series_without_episodes']:
        tasks_to_scrape.extend(status['series_without_episodes'])
        progress_print(f"Will scrape {len(status['series_without_episodes'])} series")

    # Episodes needing URLs
    if status['series_with_missing_urls']:
        progress_print(f"Will extract URLs for {len(status['series_with_missing_urls'])} series")

    if not tasks_to_scrape and not status['series_with_missing_urls']:
        print("\n[OK] Nothing to scrape! All complete.")
        return

    confirm = input(f"\nStart scraping {len(tasks_to_scrape)} shows + {len(status['series_with_missing_urls'])} URL extractions? (yes/no): ").strip().lower()
    if confirm != 'yes':
        return

    start_time = time.time()
    tasks_completed = 0
    total_tasks = len(tasks_to_scrape) + len(status['series_with_missing_urls'])

    progress_print(f"\nStarting with {num_workers} workers...")
    progress_print("="*80 + "\n")

    try:
        with ThreadPoolExecutor(max_workers=num_workers) as executor:
            futures = {}

            # Submit full scrape tasks
            for i, show in enumerate(tasks_to_scrape):
                if should_stop:
                    break

                worker_id = (i % num_workers) + 1
                future = executor.submit(
                    scrape_show_details,
                    setup_browser(headless),
                    show,
                    worker_id
                )
                futures[future] = (i, show['id'], 'scrape')

            # Submit URL extraction tasks
            for i, task in enumerate(status['series_with_missing_urls']):
                if should_stop:
                    break

                worker_id = (i % num_workers) + 1
                future = executor.submit(
                    extract_missing_urls_for_show,
                    setup_browser(headless),
                    task['show_id'],
                    task['episodes'],
                    worker_id
                )
                futures[future] = (len(tasks_to_scrape) + i, task['show_id'], 'extract')

            # Process results
            for future in as_completed(futures):
                idx, identifier, mode = futures[future]

                try:
                    result = future.result(timeout=600)

                    if result['success']:
                        tasks_completed += 1

                        with checkpoint_lock:
                            if mode == 'extract':
                                for updated_ep in result['episodes']:
                                    for j, ep in enumerate(all_episodes):
                                        if ep['id'] == updated_ep['id']:
                                            all_episodes[j] = updated_ep
                                            break

                                progress_print(
                                    f"[W{result['worker_id']}] [{tasks_completed}/{total_tasks}] "
                                    f"{result['show_id'][:30]}: {result['extracted_count']}/{result['total_count']} URLs"
                                )
                            else:
                                # Update show
                                for j, s in enumerate(all_shows):
                                    if s['id'] == identifier:
                                        all_shows[j] = result['show']
                                        break

                                # Add episodes (with deduplication)
                                existing_episode_ids = {ep['id'] for ep in all_episodes}
                                new_count = 0
                                for new_ep in result['episodes']:
                                    if new_ep['id'] not in existing_episode_ids:
                                        all_episodes.append(new_ep)
                                        existing_episode_ids.add(new_ep['id'])
                                        new_count += 1
                                    else:
                                        for j, ep in enumerate(all_episodes):
                                            if ep['id'] == new_ep['id']:
                                                all_episodes[j] = new_ep
                                                break

                                eps_with_video = sum(1 for ep in result['episodes'] if ep.get('video_url'))
                                progress_print(
                                    f"[W{result['worker_id']}] [{tasks_completed}/{total_tasks}] "
                                    f"{result['show']['id'][:25]}: {len(result['episodes'])} eps, {eps_with_video} URLs"
                                )
                    else:
                        tasks_completed += 1
                        progress_print(f"[W{result['worker_id']}] [{tasks_completed}/{total_tasks}] FAILED: {identifier}")

                    # Save checkpoint periodically
                    if tasks_completed % 10 == 0:
                        checkpoint['shows'] = all_shows
                        checkpoint['episodes'] = all_episodes
                        if save_checkpoint_safe(checkpoint):
                            elapsed = time.time() - start_time
                            progress_print(
                                f"\n--- CHECKPOINT SAVED ---\n"
                                f"Progress: {tasks_completed}/{total_tasks}\n"
                                f"Time: {int(elapsed/60)}m {int(elapsed%60)}s\n"
                                f"{'---'*20}\n"
                            )

                except Exception as e:
                    tasks_completed += 1
                    progress_print(f"[ERROR] [{tasks_completed}/{total_tasks}] {str(e)[:50]}")

    finally:
        pass

    # Final save
    checkpoint['shows'] = all_shows
    checkpoint['episodes'] = all_episodes
    checkpoint['phase'] = 'complete'
    save_checkpoint_safe(checkpoint)

    elapsed = time.time() - start_time

    print("\n" + "="*80)
    print("SCRAPING COMPLETE!")
    print("="*80)
    print(f"Total time: {int(elapsed/60)}m {int(elapsed%60)}s")
    print(f"Tasks completed: {tasks_completed}/{total_tasks}")
    print(f"Total episodes: {len(all_episodes)}")

    eps_with_urls = sum(1 for e in all_episodes if e.get('video_url'))
    print(f"Episodes with URLs: {eps_with_urls}/{len(all_episodes)} ({eps_with_urls/len(all_episodes)*100:.1f}%)")

# ============================================================================
# CHECKPOINT MANAGEMENT
# ============================================================================

def reset_checkpoint_phase():
    """Reset checkpoint phase to allow re-scraping"""
    checkpoint = load_checkpoint()
    if not checkpoint:
        print("[ERROR] No checkpoint found!")
        return

    print(f"\nCurrent phase: {checkpoint.get('phase', 'N/A')}")
    print("\nAvailable phases:")
    print("  1. detail_scraping - Active scraping mode")
    print("  2. complete - Marks as finished")

    choice = input("\nSet phase to (1/2): ").strip()

    if choice == '1':
        checkpoint['phase'] = 'detail_scraping'
        save_checkpoint_safe(checkpoint)
        print("[OK] Phase set to: detail_scraping")
    elif choice == '2':
        checkpoint['phase'] = 'complete'
        save_checkpoint_safe(checkpoint)
        print("[OK] Phase set to: complete")
    else:
        print("[ERROR] Invalid choice")

def clean_duplicates():
    """Remove duplicate episodes from checkpoint"""
    checkpoint = load_checkpoint()
    if not checkpoint:
        print("[ERROR] No checkpoint found!")
        return

    episodes = checkpoint['episodes']
    original_count = len(episodes)

    print(f"\nOriginal episode count: {original_count}")

    # Create backup first
    create_backup("before_cleanup")

    # Deduplicate
    seen_ids = {}
    for episode in episodes:
        ep_id = episode['id']
        seen_ids[ep_id] = episode  # Keeps last occurrence

    unique_episodes = list(seen_ids.values())
    duplicates_removed = original_count - len(unique_episodes)

    print(f"Unique episodes: {len(unique_episodes)}")
    print(f"Duplicates removed: {duplicates_removed}")

    if duplicates_removed > 0:
        confirm = input(f"\nRemove {duplicates_removed} duplicates? (yes/no): ").strip().lower()
        if confirm == 'yes':
            checkpoint['episodes'] = unique_episodes
            save_checkpoint_safe(checkpoint)
            print("[OK] Duplicates removed!")
        else:
            print("[CANCELLED]")
    else:
        print("[OK] No duplicates found!")

# ============================================================================
# MAIN MENU
# ============================================================================

def show_menu():
    """Display main menu"""
    print("\n" + "="*80)
    print("NAMAKADEH MASTER SCRAPER v2.1")
    print("="*80)
    print("\nCATALOG DISCOVERY:")
    print("  1. Discover new shows from website (scans all categories)")
    print("\nSCRAPING:")
    print("  2. Check status & statistics")
    print("  3. Run complete scrape (series + movies)")
    print("  4. Verify & fill gaps (check actual website)")
    print("  5. Extract missing video URLs only")
    print("\nUTILITIES:")
    print("  6. Create backup")
    print("  7. Reset checkpoint phase")
    print("  8. Clean duplicate episodes")
    print("  9. Export catalog to JSON")
    print("\n  0. Exit")
    print("="*80)

def main():
    """Main application entry point"""

    while True:
        show_menu()
        choice = input("\nSelect option: ").strip()

        if choice == '0':
            print("\nGoodbye!")
            break

        elif choice == '1':
            # Discover new shows from website
            checkpoint = load_checkpoint()
            if not checkpoint:
                print("\n[INFO] No checkpoint found. Creating new one...")
                checkpoint = {
                    'shows': [],
                    'episodes': [],
                    'phase': 'catalog_discovery',
                    'created_at': datetime.now().isoformat()
                }
                save_checkpoint_safe(checkpoint)

            discover_catalog_from_website(checkpoint, num_workers=2)

        elif choice == '2':
            # Check status
            checkpoint = load_checkpoint()
            if checkpoint:
                print_status_report(checkpoint)
            else:
                print("\n[ERROR] No checkpoint found! Run discovery first (option 1).")

        elif choice == '3':
            # Run complete scrape
            checkpoint = load_checkpoint()
            if not checkpoint:
                print("\n[ERROR] No checkpoint found!")
                print("Please run catalog discovery first (option 1).")
                continue

            print("\n[!] This will scrape all missing content")
            workers = input("Number of workers (1-5, default 3): ").strip() or "3"
            try:
                workers = int(workers)
                if 1 <= workers <= 5:
                    run_complete_scrape(checkpoint, num_workers=workers)
                else:
                    print("[ERROR] Workers must be 1-5")
            except ValueError:
                print("[ERROR] Invalid number")

        elif choice == '4':
            # Verify & fill gaps
            checkpoint = load_checkpoint()
            if not checkpoint:
                print("\n[ERROR] No checkpoint found!")
                continue

            workers = input("Number of workers (1-5, default 3): ").strip() or "3"
            try:
                workers = int(workers)
                if 1 <= workers <= 5:
                    verify_series_completeness(checkpoint, num_workers=workers)
                else:
                    print("[ERROR] Workers must be 1-5")
            except ValueError:
                print("[ERROR] Invalid number")

        elif choice == '5':
            # Extract missing URLs only
            checkpoint = load_checkpoint()
            if not checkpoint:
                print("\n[ERROR] No checkpoint found!")
                continue

            status = analyze_checkpoint_status(checkpoint)

            if not status['episodes_missing_urls'] and not status['movies_missing_urls']:
                print("\n[OK] All videos have URLs! (100%)")
                continue

            print(f"\nFound:")
            print(f"  Episodes missing URLs: {len(status['episodes_missing_urls'])}")
            print(f"  Movies missing URLs: {len(status['movies_missing_urls'])}")

            confirm = input("\nExtract missing URLs? (yes/no): ").strip().lower()
            if confirm == 'yes':
                workers = input("Number of workers (1-5, default 3): ").strip() or "3"
                try:
                    workers = int(workers)
                    if 1 <= workers <= 5:
                        run_complete_scrape(checkpoint, num_workers=workers)
                    else:
                        print("[ERROR] Workers must be 1-5")
                except ValueError:
                    print("[ERROR] Invalid number")

        elif choice == '6':
            # Create backup
            description = input("Backup description (optional): ").strip()
            create_backup(description)

        elif choice == '7':
            # Reset phase
            reset_checkpoint_phase()

        elif choice == '8':
            # Clean duplicates
            clean_duplicates()

        elif choice == '9':
            # Export catalog
            checkpoint = load_checkpoint()
            if not checkpoint:
                print("\n[ERROR] No checkpoint found!")
                continue

            timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            final_data = {
                'shows': checkpoint['shows'],
                'episodes': checkpoint['episodes'],
                'generated_at': timestamp
            }

            output_file = 'namakade_complete_catalog.json'
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(final_data, f, indent=2, ensure_ascii=False)

            size_mb = os.path.getsize(output_file) / (1024 * 1024)
            print(f"\n[OK] Catalog exported to: {output_file} ({size_mb:.2f} MB)")

        else:
            print("\n[ERROR] Invalid option!")

if __name__ == "__main__":
    main()
