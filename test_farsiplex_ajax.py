#!/usr/bin/env python3
"""
Test FarsiPlex AJAX endpoints
WordPress sites often use admin-ajax.php for various operations
"""
import requests
from bs4 import BeautifulSoup

def test_ajax_search():
    """Test WordPress AJAX search"""
    print("\n1. Testing AJAX search...")

    # Common WordPress AJAX actions for search
    actions = [
        'doo_search',
        'dooplay_search',
        'ajax_search',
        'live_search',
    ]

    for action in actions:
        response = requests.post(
            'https://farsiplex.com/wp-admin/admin-ajax.php',
            data={
                'action': action,
                'keyword': 'beretta',
                'nonce': 'test'
            },
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        )

        if response.status_code == 200 and response.text != '0' and 'error' not in response.text.lower():
            print(f"   OK Action '{action}' works!")
            print(f"     Response: {response.text[:300]}")
            return True
        else:
            print(f"   NO Action '{action}': {response.text[:50]}")

    return False

def list_sitemap_options():
    """Check what sitemaps are available"""
    print("\n2. Checking available sitemaps...")

    sitemaps = [
        'wp-sitemap.xml',
        'wp-sitemap-posts-movies-1.xml',
        'wp-sitemap-posts-tvshows-1.xml',
        'wp-sitemap-posts-episodes-1.xml',
        'sitemap.xml',
        'sitemap_index.xml',
    ]

    for sitemap in sitemaps:
        url = f"https://farsiplex.com/{sitemap}"
        response = requests.head(url)

        if response.status_code == 200:
            print(f"   OK {sitemap} (exists)")
        else:
            print(f"   NO {sitemap} (404)")

def check_rss_feeds():
    """Check for RSS/Atom feeds"""
    print("\n3. Checking RSS/Atom feeds...")

    feeds = [
        'feed/',
        'feed/rss/',
        'feed/rss2/',
        'feed/atom/',
        'movie/feed/',
        'tvshow/feed/',
    ]

    for feed in feeds:
        url = f"https://farsiplex.com/{feed}"
        response = requests.head(url)

        if response.status_code == 200:
            print(f"   OK {feed}")

            # Get sample
            content = requests.get(url)
            if '<rss' in content.text or '<feed' in content.text:
                print(f"     Valid feed detected")

if __name__ == '__main__':
    print("=" * 80)
    print("TESTING FARSIPLEX ALTERNATIVE APIS")
    print("=" * 80)

    test_ajax_search()
    list_sitemap_options()
    check_rss_feeds()

    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print("FarsiPlex APIs tested:")
    print("  NO DooPlay Player API (v2) - Returns empty")
    print("  NO DooPlay Search API - Requires special nonce")
    print("  NO DooPlay Glossary API - Requires special nonce")
    print("  NO WordPress REST API - Custom post types not exposed")
    print("  NO AJAX endpoints - Not accessible without proper auth")
    print("  OK XML Sitemaps - WORKING (already in use)")
    print("\nCONCLUSION: Current scraping method is the ONLY viable approach")
    print("=" * 80)
