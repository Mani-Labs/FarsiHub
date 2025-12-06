#!/usr/bin/env python3
"""
IMVBox YouTube ID Extraction Test

Tests extracting YouTube video IDs from a few random IMVBox movies.
Uses Playwright for JavaScript execution.

Usage:
    pip install playwright
    playwright install chromium
    python imvbox_youtube_test.py
"""

import asyncio
import re
import sys
import io

# Fix Windows console encoding
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

from playwright.async_api import async_playwright

# Test movies - 5 random movies for testing
TEST_MOVIES = [
    ("33 Days", "https://www.imvbox.com/en/movies/33-days-33-rooz/play"),
    ("Minoo Tower", "https://www.imvbox.com/en/movies/minoo-tower-borje-minoo/play"),
    ("Rhino", "https://www.imvbox.com/en/movies/rhino-kargadan/play"),
    ("The Grasshopper", "https://www.imvbox.com/en/movies/the-grasshopper-malakh/play"),
    ("Lottery", "https://www.imvbox.com/en/movies/lottery-bakht-azmayee/play"),
]

# Test TV series episodes
TEST_EPISODES = []


async def extract_youtube_id(page, url: str, title: str) -> dict:
    """Extract YouTube video ID from IMVBox play page."""
    result = {
        "title": title,
        "url": url,
        "youtube_id": None,
        "hls_url": None,
        "error": None
    }

    try:
        print(f"\n{'='*60}")
        print(f"Testing: {title}")
        print(f"URL: {url}")
        print("Loading page...")

        # Navigate and wait for network to settle
        await page.goto(url, wait_until="networkidle", timeout=30000)

        # Wait for YouTube iframe to appear (max 20 seconds)
        print("Waiting for YouTube iframe...")
        try:
            await page.wait_for_selector('iframe[src*="youtube.com/embed"]', timeout=20000)
            print("YouTube iframe detected!")
        except Exception as e:
            print(f"Waiting longer for dynamic content...")
            await asyncio.sleep(5)  # Extra wait for JS to execute

        # Debug: Check what's on the page
        debug_info = await page.evaluate("""
            () => {
                const iframes = Array.from(document.querySelectorAll('iframe'));
                const videos = Array.from(document.querySelectorAll('video'));
                const players = Array.from(document.querySelectorAll('[class*="player"]'));
                return {
                    iframeCount: iframes.length,
                    iframeSrcs: iframes.map(f => f.src).slice(0, 5),
                    videoCount: videos.length,
                    playerClasses: players.map(p => p.className).slice(0, 3),
                    bodyText: document.body.innerText.substring(0, 500)
                };
            }
        """)
        print(f"DEBUG: {debug_info['iframeCount']} iframes, {debug_info['videoCount']} videos")
        for src in debug_info['iframeSrcs']:
            print(f"  iframe: {src[:80]}...")

        # Method 1: Look for YouTube iframe
        youtube_iframe = await page.query_selector('iframe[src*="youtube.com/embed"]')
        if youtube_iframe:
            src = await youtube_iframe.get_attribute('src')
            if src:
                # Extract video ID from iframe src
                match = re.search(r'youtube\.com/embed/([a-zA-Z0-9_-]{11})', src)
                if match:
                    result["youtube_id"] = match.group(1)
                    print(f"OK Found YouTube ID: {result['youtube_id']}")

        # Method 2: JavaScript fallback
        if not result["youtube_id"]:
            youtube_id = await page.evaluate("""
                () => {
                    const ytIframe = document.querySelector('iframe[src*="youtube.com/embed"]');
                    if (ytIframe) {
                        const match = ytIframe.src.match(/youtube\\.com\\/embed\\/([a-zA-Z0-9_-]{11})/);
                        if (match) return match[1];
                    }
                    return null;
                }
            """)
            if youtube_id:
                result["youtube_id"] = youtube_id
                print(f"OK Found YouTube ID (JS): {result['youtube_id']}")

        if not result["youtube_id"]:
            print("X No YouTube video found")
            result["error"] = "No YouTube video found"

    except Exception as e:
        result["error"] = str(e)
        print(f"✗ Error: {e}")

    return result


async def main():
    print("="*60)
    print("IMVBox YouTube ID Extraction Test")
    print("="*60)
    print("\nThis script connects to your existing Chrome browser.")
    print("Before running, start Chrome with: chrome --remote-debugging-port=9222")
    print("Then navigate to any IMVBox page in Chrome.\n")

    async with async_playwright() as p:
        try:
            # Connect to existing Chrome browser (avoids bot detection)
            print("Connecting to Chrome on port 9222...")
            browser = await p.chromium.connect_over_cdp("http://localhost:9222")
            context = browser.contexts[0]
            page = context.pages[0] if context.pages else await context.new_page()
            print("Connected to Chrome!")
        except Exception as e:
            print(f"ERROR: Could not connect to Chrome: {e}")
            print("\nPlease start Chrome with remote debugging enabled:")
            print('  chrome --remote-debugging-port=9222')
            print("\nOr on Windows:")
            print('  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" --remote-debugging-port=9222')
            return

        results = []

        # Test movies
        print("\n" + "="*60)
        print("TESTING MOVIES")
        print("="*60)

        for title, url in TEST_MOVIES:
            result = await extract_youtube_id(page, url, title)
            results.append(result)

        # Test episodes
        print("\n" + "="*60)
        print("TESTING TV EPISODES")
        print("="*60)

        for title, url in TEST_EPISODES:
            result = await extract_youtube_id(page, url, title)
            results.append(result)

        # Don't close - user's browser
        print("\nDone! Browser left open.")

        # Summary
        print("\n" + "="*60)
        print("SUMMARY")
        print("="*60)

        success = 0
        for r in results:
            status = "OK" if r["youtube_id"] else "X"
            video_info = r["youtube_id"] or r["error"]
            print(f"{status} {r['title']}: {video_info}")
            if r["youtube_id"]:
                success += 1

        print(f"\nSuccess rate: {success}/{len(results)} ({100*success//len(results)}%)")

        # Verify YouTube IDs with oEmbed
        print("\n" + "="*60)
        print("VERIFYING YOUTUBE IDs")
        print("="*60)

        import aiohttp
        async with aiohttp.ClientSession() as session:
            for r in results:
                if r["youtube_id"]:
                    oembed_url = f"https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v={r['youtube_id']}&format=json"
                    try:
                        async with session.get(oembed_url) as resp:
                            if resp.status == 200:
                                data = await resp.json()
                                print(f"✓ {r['title']}")
                                print(f"  YouTube Title: {data.get('title', 'N/A')}")
                                print(f"  Channel: {data.get('author_name', 'N/A')}")
                            else:
                                print(f"✗ {r['title']}: YouTube video not accessible")
                    except Exception as e:
                        print(f"✗ {r['title']}: Verification failed - {e}")


if __name__ == "__main__":
    asyncio.run(main())
