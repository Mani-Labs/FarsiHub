#!/usr/bin/env python3
"""
Test FarsiPlex DooPlay API v2
Try to get video URLs from the API endpoint
"""
import requests
from bs4 import BeautifulSoup
import re
import json

def extract_post_id_from_page(url):
    """Extract WordPress post ID from episode/movie page"""
    print(f"\n1. Fetching page: {url}")
    response = requests.get(url, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
    })
    response.raise_for_status()

    soup = BeautifulSoup(response.text, 'html.parser')

    # Method 1: Look for form with id="watch-{POST_ID}"
    forms = soup.find_all('form', id=re.compile(r'watch-\d+'))
    if forms:
        form_id = forms[0].get('id', '')
        post_id = form_id.replace('watch-', '')
        print(f"   Found post_id from form: {post_id}")
        return post_id

    # Method 2: Look for data-post attributes
    elements_with_post = soup.find_all(attrs={'data-post': True})
    if elements_with_post:
        post_id = elements_with_post[0]['data-post']
        print(f"   Found post_id from data-post: {post_id}")
        return post_id

    # Method 3: Look for body class post-{ID}
    body = soup.find('body')
    if body:
        classes = body.get('class', [])
        for cls in classes:
            if cls.startswith('postid-'):
                post_id = cls.replace('postid-', '')
                print(f"   Found post_id from body class: {post_id}")
                return post_id

    print("   ERROR: Could not find post_id!")
    return None

def test_dooplay_api(post_id, content_type, source=1):
    """Test DooPlay API endpoint"""
    api_url = f"https://farsiplex.com/wp-json/dooplayer/v2/{post_id}/{content_type}/{source}"
    print(f"\n2. Testing DooPlay API:")
    print(f"   URL: {api_url}")

    try:
        response = requests.get(api_url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
        })

        print(f"   Status: {response.status_code}")
        print(f"   Content-Type: {response.headers.get('Content-Type')}")

        if response.status_code == 200:
            print(f"\n   Response Body:")
            # Try to parse as JSON
            try:
                data = response.json()
                print(json.dumps(data, indent=2))
                return data
            except:
                # Not JSON, show text
                print(response.text[:1000])
                return response.text
        else:
            print(f"   ERROR: {response.text}")
            return None

    except Exception as e:
        print(f"   ERROR: {e}")
        return None

def test_current_post_method(post_id, page_url):
    """Test current POST /play/ method for comparison"""
    print(f"\n3. Testing current POST /play/ method:")

    # First get the page to extract form data
    response = requests.get(page_url, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
    })
    soup = BeautifulSoup(response.text, 'html.parser')

    # Find form data
    form = soup.find('form', id=f'watch-{post_id}')
    if not form:
        print("   ERROR: Form not found")
        return None

    nonce_input = form.find('input', {'name': 'watch_episode_nonce'})
    referer_input = form.find('input', {'name': '_wp_http_referer'})

    if not nonce_input:
        print("   ERROR: Nonce not found")
        return None

    form_data = {
        'id': post_id,
        'watch_episode_nonce': nonce_input.get('value'),
        '_wp_http_referer': referer_input.get('value') if referer_input else ''
    }

    print(f"   Posting to /play/ with id={post_id}")

    play_response = requests.post(
        'https://farsiplex.com/play/',
        data=form_data,
        headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
            'Referer': page_url
        }
    )

    if play_response.status_code == 200:
        print(f"   Status: {play_response.status_code}")
        # Extract iframe
        play_soup = BeautifulSoup(play_response.text, 'html.parser')
        iframes = play_soup.find_all('iframe')
        if iframes:
            iframe_src = iframes[0].get('src', '')
            print(f"   Found iframe: {iframe_src[:100]}...")
            return iframe_src
        else:
            print("   ERROR: No iframe found in response")
            print(f"   Response preview: {play_response.text[:500]}")
            return None
    else:
        print(f"   ERROR: Status {play_response.status_code}")
        return None

if __name__ == '__main__':
    # Test with Beretta Episode 2
    episode_url = "https://farsiplex.com/episode/beretta-dastane-yek-aslahe-ep02/"

    print("=" * 80)
    print("TESTING DOOPLAY API vs CURRENT METHOD")
    print("=" * 80)

    # Step 1: Extract post ID
    post_id = extract_post_id_from_page(episode_url)

    if not post_id:
        print("\nFAILED: Could not extract post ID")
        exit(1)

    # Step 2: Test DooPlay API
    api_result = test_dooplay_api(post_id, 'episodes', 1)

    # Step 3: Test current method
    current_result = test_current_post_method(post_id, episode_url)

    # Step 4: Compare
    print("\n" + "=" * 80)
    print("COMPARISON")
    print("=" * 80)
    print(f"Post ID: {post_id}")
    print(f"\nDooPlay API Result:")
    print(f"  Success: {api_result is not None}")
    if api_result:
        print(f"  Type: {type(api_result)}")

    print(f"\nCurrent POST Method Result:")
    print(f"  Success: {current_result is not None}")
    if current_result:
        print(f"  Type: {type(current_result)}")
        print(f"  Contains video URL: {'.mp4' in current_result if isinstance(current_result, str) else False}")

    print("\n" + "=" * 80)
