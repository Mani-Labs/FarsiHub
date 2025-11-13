#!/usr/bin/env python3
"""
Test DooPlay API with a movie
"""
import requests
from bs4 import BeautifulSoup
import re
import json

def test_movie_api(movie_url):
    print(f"Testing movie: {movie_url}\n")

    # Step 1: Get page and extract post ID
    response = requests.get(movie_url, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
    })
    soup = BeautifulSoup(response.text, 'html.parser')

    # Find form
    forms = soup.find_all('form', id=re.compile(r'watch-\d+'))
    if not forms:
        print("ERROR: No form found")
        return

    form_id = forms[0].get('id', '')
    post_id = form_id.replace('watch-', '')
    print(f"Post ID: {post_id}")

    # Step 2: Test DooPlay API with different sources
    print(f"\nTesting DooPlay API:")
    for source in range(1, 5):
        api_url = f"https://farsiplex.com/wp-json/dooplayer/v2/{post_id}/movies/{source}"
        api_response = requests.get(api_url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
        })

        if api_response.status_code == 200:
            data = api_response.json()
            if data.get('embed_url') or data.get('type'):
                print(f"  Source {source}: {json.dumps(data, indent=4)}")
            else:
                print(f"  Source {source}: Empty")
        else:
            print(f"  Source {source}: ERROR {api_response.status_code}")

    # Step 3: Test current POST method
    print(f"\nTesting POST /play/ method:")
    nonce_input = forms[0].find('input', {'name': 'watch_episode_nonce'})
    referer_input = forms[0].find('input', {'name': '_wp_http_referer'})

    if not nonce_input:
        print("ERROR: No nonce found")
        return

    play_response = requests.post(
        'https://farsiplex.com/play/',
        data={
            'id': post_id,
            'watch_episode_nonce': nonce_input.get('value'),
            '_wp_http_referer': referer_input.get('value') if referer_input else ''
        },
        headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
            'Referer': movie_url
        }
    )

    if play_response.status_code == 200:
        play_soup = BeautifulSoup(play_response.text, 'html.parser')
        iframes = play_soup.find_all('iframe')
        if iframes:
            iframe_src = iframes[0].get('src', '')
            print(f"  SUCCESS: Found iframe")
            print(f"  URL: {iframe_src}")

            # Decode the source parameter
            if 'source=' in iframe_src:
                from urllib.parse import parse_qs, urlparse
                parsed = urlparse(iframe_src)
                params = parse_qs(parsed.query)
                if 'source' in params:
                    video_url = params['source'][0]
                    print(f"  Video URL: {video_url}")
        else:
            print("  ERROR: No iframe found")
    else:
        print(f"  ERROR: Status {play_response.status_code}")

if __name__ == '__main__':
    test_movie_api("https://farsiplex.com/movie/yek-shab-327f84ee/")
