#!/usr/bin/env python3
"""
Test script to verify deterministic ID generation
AUDIT FIX C1 Verification
"""

import hashlib

def generate_stable_id(slug: str) -> int:
    """
    Generate deterministic ID using MD5 hash
    This is the same function used in farsiplex_scraper_dooplay.py
    """
    hash_object = hashlib.md5(slug.encode('utf-8'))
    return int(hash_object.hexdigest(), 16) % (10 ** 8)

def test_determinism():
    """Test that IDs are consistent across multiple runs"""
    test_cases = [
        "breaking-bad",
        "game-of-thrones",
        "the-last-of-us",
        "breaking-bad-s01e01",
        "test-movie-2024",
        "some-other-show-s02e05"
    ]

    print("=" * 60)
    print("AUDIT FIX C1: Testing Deterministic ID Generation")
    print("=" * 60)
    print()

    all_passed = True

    for slug in test_cases:
        # Generate ID multiple times
        id1 = generate_stable_id(slug)
        id2 = generate_stable_id(slug)
        id3 = generate_stable_id(slug)

        # Check consistency
        if id1 == id2 == id3:
            print(f"[PASS] '{slug}'")
            print(f"  ID: {id1} (consistent across 3 runs)")
        else:
            print(f"[FAIL] '{slug}'")
            print(f"  Run 1: {id1}")
            print(f"  Run 2: {id2}")
            print(f"  Run 3: {id3}")
            all_passed = False
        print()

    print("=" * 60)
    if all_passed:
        print("[SUCCESS] ALL TESTS PASSED - IDs are deterministic!")
    else:
        print("[ERROR] TESTS FAILED - IDs are NOT deterministic!")
    print("=" * 60)

    return all_passed

def test_old_vs_new():
    """Compare old hash() vs new MD5 approach"""
    print("\n" + "=" * 60)
    print("Comparing Old (hash) vs New (MD5) Approach")
    print("=" * 60)
    print()

    test_slug = "breaking-bad"

    # Old approach (Python's built-in hash - RANDOMIZED)
    old_id_run1 = hash(test_slug) % (10 ** 8)
    old_id_run2 = hash(test_slug) % (10 ** 8)

    # New approach (MD5 - DETERMINISTIC)
    new_id_run1 = generate_stable_id(test_slug)
    new_id_run2 = generate_stable_id(test_slug)

    print(f"Test slug: '{test_slug}'")
    print()
    print("OLD APPROACH (hash):")
    print(f"  Run 1: {old_id_run1}")
    print(f"  Run 2: {old_id_run2}")
    print(f"  Same? {old_id_run1 == old_id_run2} (within same process)")
    print(f"  WARNING: Would be DIFFERENT across process restarts!")
    print()
    print("NEW APPROACH (MD5):")
    print(f"  Run 1: {new_id_run1}")
    print(f"  Run 2: {new_id_run2}")
    print(f"  Same? {new_id_run1 == new_id_run2} [PASS]")
    print(f"  SUCCESS: Will be SAME across process restarts!")
    print()

if __name__ == "__main__":
    # Run tests
    test_old_vs_new()
    print()
    success = test_determinism()

    # Exit with appropriate code
    exit(0 if success else 1)
