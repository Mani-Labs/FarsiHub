#!/usr/bin/env python3
"""
Test FarsiPlex search API
The search and glossary APIs require nonces - let's see if we can extract one from a page
"""
import requests
from bs4 import BeautifulSoup
import re
import json

def get_nonce_from_page():
    """Extract nonce from FarsiPlex page"""
    print("1. Fetching page to extract nonce...")
    response = requests.get("https://farsiplex.com/", headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
    })

    # Look for nonce in JavaScript
    # Common patterns: nonce: "abc123", "nonce":"abc123", wp_nonce: "abc123"
    nonce_patterns = [
        r'"nonce"\s*:\s*"([^"]+)"',
        r'nonce\s*:\s*["\']([^"\']+)["\']',
        r'wp_nonce["\']?\s*:\s*["\']([^"\']+)["\']',
        r'_wpnonce["\']?\s*:\s*["\']([^"\']+)["\']',
    ]

    for pattern in nonce_patterns:
        matches = re.findall(pattern, response.text)
        if matches:
            print(f"   Found nonce: {matches[0]}")
            return matches[0]

    print("   No nonce found")
    return None

def test_search_api(nonce=None):
    """Test DooPlay search API"""
    print("\n2. Testing search API...")

    params = {
        'keyword': 'beretta',
        'nonce': nonce
    }

    if nonce:
        print(f"   Using nonce: {nonce}")

    response = requests.get(
        "https://farsiplex.com/wp-json/dooplay/search/",
        params=params,
        headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
    )

    print(f"   Status: {response.status_code}")
    print(f"   Response: {response.text[:500]}")

    try:
        data = response.json()
        return data
    except:
        return None

def test_glossary_api(nonce=None):
    """Test DooPlay glossary API"""
    print("\n3. Testing glossary API...")

    params = {
        'nonce': nonce
    }

    response = requests.get(
        "https://farsiplex.com/wp-json/dooplay/glossary/",
        params=params,
        headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
    )

    print(f"   Status: {response.status_code}")
    print(f"   Response: {response.text[:500]}")

def test_wordpress_api():
    """Test if movies/shows are exposed via WP API"""
    print("\n4. Testing WordPress API for custom content...")

    endpoints = [
        '/wp-json/wp/v2/movies',
        '/wp-json/wp/v2/tvshows',
        '/wp-json/wp/v2/episodes',
        '/wp-json/wp/v2/posts?search=beretta',
    ]

    for endpoint in endpoints:
        url = f"https://farsiplex.com{endpoint}"
        response = requests.get(url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
        })

        print(f"\n   {endpoint}")
        print(f"   Status: {response.status_code}")
        if response.status_code == 200:
            try:
                data = response.json()
                print(f"   Found {len(data)} items")
                if len(data) > 0:
                    print(f"   Sample: {data[0].get('title', {}).get('rendered', 'N/A')}")
            except:
                print(f"   Response: {response.text[:200]}")

if __name__ == '__main__':
    print("=" * 80)
    print("TESTING FARSIPLEX APIS")
    print("=" * 80)

    nonce = get_nonce_from_page()
    test_search_api(nonce)
    test_glossary_api(nonce)
    test_wordpress_api()

    print("\n" + "=" * 80)
