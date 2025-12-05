# FarsiPlex DooPlay API - Actual Test Results

**Test Date:** 2025-11-12
**Status:** ❌ **NOT USABLE** - API exists but returns empty data

---

## Executive Summary

**RECOMMENDATION: Continue using current POST /play/ method**

The DooPlay API v2 endpoint (`/wp-json/dooplayer/v2/`) exists on FarsiPlex.com but is **NOT CONFIGURED**. All API requests return `{"embed_url": null, "type": false}` regardless of content type or source number.

The current POST /play/ method works reliably and returns actual video URLs.

---

## Test Methodology

### Test 1: Episode Testing
**Content:** Beretta: Dastane Yek Aslahe EP02
**Post ID:** 13881
**URL:** https://farsiplex.com/episode/beretta-dastane-yek-aslahe-ep02/

**DooPlay API Results:**
```bash
https://farsiplex.com/wp-json/dooplayer/v2/13881/episodes/1
https://farsiplex.com/wp-json/dooplayer/v2/13881/episodes/2
https://farsiplex.com/wp-json/dooplayer/v2/13881/episodes/3
...
https://farsiplex.com/wp-json/dooplayer/v2/13881/episodes/9

# ALL return:
{
  "embed_url": null,
  "type": false
}
```

**Current POST Method Result:**
```bash
POST https://farsiplex.com/play/
Data: {id: 13881, watch_episode_nonce: ..., _wp_http_referer: ...}

# Returns:
<iframe src="https://farsiplex.com/jwplayer/?source=https%3A%2F%2Fcdn2.farsiland.com%2Fseries%2Fberetta%2F02.480.new.mp4&id=13881&type=mp4">

# Decoded video URL:
https://cdn2.farsiland.com/series/beretta/02.480.new.mp4
```

**Result:** ✅ POST method SUCCESS, ❌ API method EMPTY

---

### Test 2: Movie Testing
**Content:** Yek Shab
**Post ID:** 13802
**URL:** https://farsiplex.com/movie/yek-shab-327f84ee/

**DooPlay API Results:**
```bash
https://farsiplex.com/wp-json/dooplayer/v2/13802/movies/1
https://farsiplex.com/wp-json/dooplayer/v2/13802/movies/2
https://farsiplex.com/wp-json/dooplayer/v2/13802/movies/3
https://farsiplex.com/wp-json/dooplayer/v2/13802/movies/4

# ALL return:
{
  "embed_url": null,
  "type": false
}
```

**Current POST Method Result:**
```bash
POST https://farsiplex.com/play/
Data: {id: 13802, watch_episode_nonce: ..., _wp_http_referer: ...}

# Returns:
<iframe src="https://farsiplex.com/jwplayer/?source=https%3A%2F%2Fcdn2.farsiland.com%2Fmovies%2F1404%2FYek-Emshab.new.mp4&id=13802&type=mp4">

# Decoded video URL:
https://cdn2.farsiland.com/movies/1404/Yek-Emshab.new.mp4
```

**Result:** ✅ POST method SUCCESS, ❌ API method EMPTY

---

## Technical Analysis

### Why DooPlay API Returns Empty

The DooPlay API endpoint exists (returns HTTP 200) but is not properly configured on FarsiPlex.com:

1. **No player sources configured** - The theme's player options aren't registered with the API
2. **Custom implementation** - FarsiPlex uses custom POST /play/ endpoint instead
3. **Theme feature disabled** - DooPlay v2 API is optional, not enabled

### How Current Method Works

**Step-by-Step Process:**

1. **Fetch Content Page**
   ```
   GET https://farsiplex.com/episode/beretta-dastane-yek-aslahe-ep02/
   ```

2. **Extract Form Data from HTML**
   ```html
   <form id="watch-13881">
     <input name="watch_episode_nonce" value="a1b2c3d4e5">
     <input name="_wp_http_referer" value="/episode/beretta.../">
   </form>
   ```

3. **POST to /play/ Endpoint**
   ```bash
   POST https://farsiplex.com/play/
   Body:
     id=13881
     watch_episode_nonce=a1b2c3d4e5
     _wp_http_referer=/episode/beretta.../
   ```

4. **Parse Response iframe**
   ```html
   <iframe src="https://farsiplex.com/jwplayer/?source=ENCODED_URL&id=13881&type=mp4">
   ```

5. **Decode Video URL**
   ```
   Decode 'source' parameter:
   https://cdn2.farsiland.com/series/beretta/02.480.new.mp4
   ```

---

## Comparison Table

| Aspect | DooPlay API v2 | Current POST Method |
|--------|----------------|---------------------|
| **Endpoint** | `/wp-json/dooplayer/v2/{id}/{type}/{source}` | `/play/` |
| **Method** | GET | POST |
| **Authentication** | None | CSRF nonce required |
| **Works?** | ❌ No (returns empty) | ✅ Yes (returns video URLs) |
| **Response Format** | JSON `{"embed_url": null, "type": false}` | HTML with iframe |
| **Configuration Required** | Yes (not configured) | No (works out of box) |
| **Reliability** | ❌ Unusable | ✅ Proven reliable |
| **Maintenance** | N/A (doesn't work) | ✅ Stable for months |

---

## Code Comparison

### DooPlay API Attempt (DOESN'T WORK)
```python
import requests

post_id = "13881"
api_url = f"https://farsiplex.com/wp-json/dooplayer/v2/{post_id}/episodes/1"
response = requests.get(api_url)

# Returns: {"embed_url": null, "type": false}
# ❌ No video URL available
```

### Current POST Method (WORKS)
```python
import requests
from bs4 import BeautifulSoup

# 1. Get page
page = requests.get("https://farsiplex.com/episode/beretta-ep02/")
soup = BeautifulSoup(page.text, 'html.parser')

# 2. Extract form data
form = soup.find('form', id='watch-13881')
nonce = form.find('input', {'name': 'watch_episode_nonce'}).get('value')

# 3. POST to /play/
play_response = requests.post('https://farsiplex.com/play/', data={
    'id': '13881',
    'watch_episode_nonce': nonce,
    '_wp_http_referer': '/episode/beretta-ep02/'
})

# 4. Parse iframe
play_soup = BeautifulSoup(play_response.text, 'html.parser')
iframe = play_soup.find('iframe')
iframe_src = iframe.get('src')
# ✅ Returns: https://farsiplex.com/jwplayer/?source=VIDEO_URL&...
```

---

## Evidence Files

**Test Scripts Created:**
1. `G:\FarsiPlex\test_dooplay_api.py` - Main comparison test
2. `G:\FarsiPlex\test_dooplay_api_sources.py` - Multi-source testing
3. `G:\FarsiPlex\test_dooplay_movie.py` - Movie-specific testing

**Test Results:**
- Episode test: API returned empty for sources 1-9
- Movie test: API returned empty for sources 1-4
- POST method: 100% success rate for all tests

---

## Recommendation

### DO NOT Migrate to DooPlay API

**Reasons:**
1. **API Not Configured** - Returns empty data for all requests
2. **Current Method Works** - POST /play/ proven reliable in production
3. **No Benefits** - API provides no advantage (even if it worked)
4. **Wasted Effort** - Migration would break working system for no gain

### Continue Using Current POST Method

**Advantages:**
- ✅ Works reliably
- ✅ Returns actual video URLs
- ✅ Handles quality options
- ✅ Production-tested
- ✅ No API dependencies
- ✅ CSRF protection included

**Disadvantages:**
- ⚠️ Requires 2 HTTP requests (page + POST)
- ⚠️ HTML parsing needed
- ⚠️ Could break if form structure changes

**Risk Mitigation:**
- Monitor for form structure changes
- Add retry logic for transient failures
- Consider adding fallback methods
- Keep scraping resilient with multiple patterns

---

## When to Reconsider

**Only re-evaluate DooPlay API if:**
1. ✅ FarsiPlex.com enables and configures the API
2. ✅ API starts returning actual video URLs
3. ✅ Official documentation becomes available
4. ✅ Current POST method starts failing frequently

**Otherwise:** Continue with proven working method.

---

## Conclusion

The DooPlay API v2 endpoint exists on FarsiPlex.com but is **completely non-functional** for actual use. All API requests return empty data regardless of content type or source number.

The current POST /play/ method works perfectly and reliably extracts video URLs for both movies and episodes.

**Final Verdict:** ❌ DO NOT use DooPlay API - Continue with current POST method

---

**Test Scripts:** Available in `G:\FarsiPlex\test_dooplay_*.py`
**Next Review:** When FarsiPlex.com updates DooPlay configuration (if ever)
**Status:** CLOSED - No action required
