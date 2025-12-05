<?php
/**
 * IMVBox.com Content Scraper
 *
 * Scrapes movies and TV series from IMVBox.com and generates
 * an SQLite database compatible with FarsiPlex/FarsiHub app.
 *
 * Usage: php imvbox_scraper.php
 *
 * Output: imvbox_content.db (copy to app/src/main/assets/databases/)
 */

// Configuration
define('BASE_URL', 'https://www.imvbox.com/en');
define('ASSETS_URL', 'https://assets.imvbox.com');
define('DB_FILE', __DIR__ . '/imvbox_content.db');
define('RATE_LIMIT_MS', 500); // 500ms between requests
define('MAX_PAGES', 50); // Max pages to scrape per category

// Initialize database
function initDatabase(): PDO {
    if (file_exists(DB_FILE)) {
        unlink(DB_FILE);
    }

    $db = new PDO('sqlite:' . DB_FILE);
    $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

    // Create tables matching ContentDatabase schema
    $db->exec("
        CREATE TABLE IF NOT EXISTS cached_movies (
            id INTEGER PRIMARY KEY,
            title TEXT NOT NULL,
            poster_url TEXT,
            farsiland_url TEXT NOT NULL UNIQUE,
            description TEXT,
            year INTEGER,
            rating REAL,
            runtime INTEGER,
            director TEXT,
            cast TEXT,
            genres TEXT,
            date_added INTEGER,
            last_updated INTEGER
        );

        CREATE TABLE IF NOT EXISTS cached_series (
            id INTEGER PRIMARY KEY,
            title TEXT NOT NULL,
            poster_url TEXT,
            backdrop_url TEXT,
            farsiland_url TEXT NOT NULL UNIQUE,
            description TEXT,
            year INTEGER,
            rating REAL,
            total_seasons INTEGER DEFAULT 1,
            total_episodes INTEGER DEFAULT 0,
            cast TEXT,
            genres TEXT,
            date_added INTEGER,
            last_updated INTEGER
        );

        CREATE TABLE IF NOT EXISTS cached_episodes (
            id INTEGER PRIMARY KEY,
            series_id INTEGER,
            title TEXT NOT NULL,
            thumbnail_url TEXT,
            farsiland_url TEXT NOT NULL UNIQUE,
            season INTEGER NOT NULL,
            episode INTEGER NOT NULL,
            date_added INTEGER,
            FOREIGN KEY (series_id) REFERENCES cached_series(id)
        );

        CREATE TABLE IF NOT EXISTS cached_genres (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE
        );

        CREATE INDEX IF NOT EXISTS idx_movies_year ON cached_movies(year);
        CREATE INDEX IF NOT EXISTS idx_movies_date ON cached_movies(date_added);
        CREATE INDEX IF NOT EXISTS idx_series_year ON cached_series(year);
        CREATE INDEX IF NOT EXISTS idx_episodes_series ON cached_episodes(series_id);

        -- FTS tables for search
        CREATE VIRTUAL TABLE IF NOT EXISTS movie_fts USING fts4(
            title,
            content='cached_movies',
            tokenize=unicode61
        );

        CREATE VIRTUAL TABLE IF NOT EXISTS series_fts USING fts4(
            title,
            content='cached_series',
            tokenize=unicode61
        );
    ");

    return $db;
}

// HTTP request with rate limiting
function fetchUrl(string $url): ?string {
    static $lastRequest = 0;

    $elapsed = (microtime(true) * 1000) - $lastRequest;
    if ($elapsed < RATE_LIMIT_MS) {
        usleep((RATE_LIMIT_MS - $elapsed) * 1000);
    }

    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL => $url,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_TIMEOUT => 30,
        CURLOPT_USERAGENT => 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        CURLOPT_HTTPHEADER => [
            'Accept: text/html,application/xhtml+xml',
            'Accept-Language: en-US,en;q=0.9',
        ],
    ]);

    $html = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    $lastRequest = microtime(true) * 1000;

    if ($httpCode !== 200) {
        echo "  [WARN] HTTP $httpCode for $url\n";
        return null;
    }

    return $html;
}

// Parse movie list page
function parseMovieListPage(string $html): array {
    $movies = [];

    $dom = new DOMDocument();
    @$dom->loadHTML($html, LIBXML_NOERROR);
    $xpath = new DOMXPath($dom);

    // Find all movie cards - IMVBox uses .card structure
    $cards = $xpath->query("//div[contains(@class, 'card')]");

    foreach ($cards as $card) {
        $movie = parseMovieCard($card, $xpath);
        if ($movie) {
            $movies[] = $movie;
        }
    }

    return $movies;
}

// Parse individual movie card
function parseMovieCard(DOMElement $card, DOMXPath $xpath): ?array {
    // Find link to movie detail page
    $links = $xpath->query(".//a[contains(@href, '/movies/')]", $card);
    if ($links->length === 0) return null;

    $link = $links->item(0);
    $href = $link->getAttribute('href');

    // Extract slug
    $slug = basename(rtrim($href, '/'));
    if (empty($slug) || $slug === 'movies') return null;

    // Full URL
    $url = strpos($href, 'http') === 0 ? $href : BASE_URL . '/movies/' . $slug;

    // Title from card-title or link title attribute
    $title = null;
    $titleNodes = $xpath->query(".//*[contains(@class, 'card-title')]", $card);
    if ($titleNodes->length > 0) {
        $title = trim($titleNodes->item(0)->textContent);
    }
    if (!$title) {
        $title = $link->getAttribute('title');
        // Extract from "Watch 'Movie Name' movie" format
        if (preg_match("/['\"]([^'\"]+)['\"]/", $title, $m)) {
            $title = $m[1];
        }
    }
    if (!$title) {
        $title = ucwords(str_replace('-', ' ', $slug));
    }

    // Poster URL from img
    $posterUrl = null;
    $imgs = $xpath->query(".//img", $card);
    if ($imgs->length > 0) {
        $img = $imgs->item(0);
        $posterUrl = $img->getAttribute('data-src') ?: $img->getAttribute('src');
    }
    // Fallback to constructed URL
    if (!$posterUrl || strpos($posterUrl, 'http') !== 0) {
        $posterUrl = ASSETS_URL . '/movies/' . $slug . 'pos.jpg';
    }

    return [
        'slug' => $slug,
        'title' => $title,
        'url' => $url,
        'posterUrl' => $posterUrl,
    ];
}

// Fetch detailed movie metadata
function fetchMovieDetails(string $url): array {
    $details = [
        'description' => null,
        'year' => null,
        'rating' => null,
        'runtime' => null,
        'director' => null,
        'cast' => null,
        'genres' => [],
        'posterUrl' => null,
        'backdropUrl' => null,
    ];

    $html = fetchUrl($url);
    if (!$html) return $details;

    $dom = new DOMDocument();
    @$dom->loadHTML($html, LIBXML_NOERROR);
    $xpath = new DOMXPath($dom);

    // Description from meta og:description
    $metaDesc = $xpath->query("//meta[@property='og:description']");
    if ($metaDesc->length > 0) {
        $details['description'] = $metaDesc->item(0)->getAttribute('content');
    }

    // Poster from og:image
    $metaImg = $xpath->query("//meta[@property='og:image']");
    if ($metaImg->length > 0) {
        $details['posterUrl'] = $metaImg->item(0)->getAttribute('content');
    }

    // Year - look for year in various places
    $yearNodes = $xpath->query("//*[contains(@class, 'year')]");
    if ($yearNodes->length > 0) {
        $yearText = $yearNodes->item(0)->textContent;
        if (preg_match('/(\d{4})/', $yearText, $m)) {
            $details['year'] = (int)$m[1];
        }
    }

    // Rating - IMDB style
    $ratingNodes = $xpath->query("//*[contains(@class, 'imdb') or contains(@class, 'rating')]");
    foreach ($ratingNodes as $node) {
        $text = $node->textContent;
        if (preg_match('/(\d+\.?\d*)/', $text, $m)) {
            $rating = (float)$m[1];
            if ($rating > 0 && $rating <= 10) {
                $details['rating'] = $rating;
                break;
            }
        }
    }

    // Genres from links
    $genreLinks = $xpath->query("//a[contains(@href, '/genre/')]");
    foreach ($genreLinks as $link) {
        $genre = trim($link->textContent);
        if ($genre && !in_array($genre, $details['genres'])) {
            $details['genres'][] = $genre;
        }
    }

    // Director
    $directorNodes = $xpath->query("//*[contains(text(), 'Director')]/following-sibling::*[1]");
    if ($directorNodes->length > 0) {
        $details['director'] = trim($directorNodes->item(0)->textContent);
    }

    // Duration from JSON-LD
    $jsonLdNodes = $xpath->query("//script[@type='application/ld+json']");
    foreach ($jsonLdNodes as $node) {
        $json = json_decode($node->textContent, true);
        if ($json && isset($json['duration'])) {
            // Parse PT1H37M format
            if (preg_match('/PT(\d+)H(\d+)M/', $json['duration'], $m)) {
                $details['runtime'] = ((int)$m[1] * 60) + (int)$m[2];
            } elseif (preg_match('/PT(\d+)M/', $json['duration'], $m)) {
                $details['runtime'] = (int)$m[1];
            }
        }
    }

    return $details;
}

// Parse series list page
function parseSeriesListPage(string $html): array {
    $series = [];

    $dom = new DOMDocument();
    @$dom->loadHTML($html, LIBXML_NOERROR);
    $xpath = new DOMXPath($dom);

    // Find all series cards
    $cards = $xpath->query("//div[contains(@class, 'card')]");

    foreach ($cards as $card) {
        $item = parseSeriesCard($card, $xpath);
        if ($item) {
            $series[] = $item;
        }
    }

    return $series;
}

// Parse individual series card
function parseSeriesCard(DOMElement $card, DOMXPath $xpath): ?array {
    // Find link to show detail page
    $links = $xpath->query(".//a[contains(@href, '/shows/')]", $card);
    if ($links->length === 0) return null;

    $link = $links->item(0);
    $href = $link->getAttribute('href');

    // Extract slug
    $slug = basename(rtrim($href, '/'));
    if (empty($slug) || $slug === 'shows') return null;

    // Full URL
    $url = strpos($href, 'http') === 0 ? $href : BASE_URL . '/shows/' . $slug;

    // Title
    $title = null;
    $titleNodes = $xpath->query(".//*[contains(@class, 'card-title')]", $card);
    if ($titleNodes->length > 0) {
        $title = trim($titleNodes->item(0)->textContent);
    }
    if (!$title) {
        $title = $link->getAttribute('title');
        if (preg_match("/['\"]([^'\"]+)['\"]/", $title, $m)) {
            $title = $m[1];
        }
    }
    if (!$title) {
        $title = ucwords(str_replace('-', ' ', $slug));
    }

    // Poster URL
    $posterUrl = null;
    $imgs = $xpath->query(".//img", $card);
    if ($imgs->length > 0) {
        $img = $imgs->item(0);
        $posterUrl = $img->getAttribute('data-src') ?: $img->getAttribute('src');
    }
    if (!$posterUrl || strpos($posterUrl, 'http') !== 0) {
        $posterUrl = ASSETS_URL . '/shows/' . $slug . 'Th.webp';
    }

    return [
        'slug' => $slug,
        'title' => $title,
        'url' => $url,
        'posterUrl' => $posterUrl,
    ];
}

// Fetch series details including episodes
function fetchSeriesDetails(string $url, string $slug): array {
    $details = [
        'description' => null,
        'year' => null,
        'rating' => null,
        'totalSeasons' => 1,
        'totalEpisodes' => 0,
        'genres' => [],
        'posterUrl' => null,
        'episodes' => [],
    ];

    $html = fetchUrl($url);
    if (!$html) return $details;

    $dom = new DOMDocument();
    @$dom->loadHTML($html, LIBXML_NOERROR);
    $xpath = new DOMXPath($dom);

    // Description
    $metaDesc = $xpath->query("//meta[@property='og:description']");
    if ($metaDesc->length > 0) {
        $details['description'] = $metaDesc->item(0)->getAttribute('content');
    }

    // Poster
    $metaImg = $xpath->query("//meta[@property='og:image']");
    if ($metaImg->length > 0) {
        $details['posterUrl'] = $metaImg->item(0)->getAttribute('content');
    }

    // Year
    $yearNodes = $xpath->query("//*[contains(@class, 'year')]");
    if ($yearNodes->length > 0) {
        if (preg_match('/(\d{4})/', $yearNodes->item(0)->textContent, $m)) {
            $details['year'] = (int)$m[1];
        }
    }

    // Genres
    $genreLinks = $xpath->query("//a[contains(@href, '/genre/')]");
    foreach ($genreLinks as $link) {
        $genre = trim($link->textContent);
        if ($genre && !in_array($genre, $details['genres'])) {
            $details['genres'][] = $genre;
        }
    }

    // Find season links to count seasons
    $seasonLinks = $xpath->query("//a[contains(@href, '/season-')]");
    $seasons = [];
    foreach ($seasonLinks as $link) {
        if (preg_match('/season-(\d+)/', $link->getAttribute('href'), $m)) {
            $seasons[(int)$m[1]] = true;
        }
    }
    $details['totalSeasons'] = count($seasons) ?: 1;

    // Fetch episodes for each season
    for ($season = 1; $season <= $details['totalSeasons']; $season++) {
        $seasonUrl = BASE_URL . '/shows/' . $slug . '/season-' . $season;
        $episodes = fetchSeasonEpisodes($seasonUrl, $slug, $season);
        $details['episodes'] = array_merge($details['episodes'], $episodes);
    }

    $details['totalEpisodes'] = count($details['episodes']);

    return $details;
}

// Fetch episodes for a season
function fetchSeasonEpisodes(string $url, string $showSlug, int $season): array {
    $episodes = [];

    $html = fetchUrl($url);
    if (!$html) return $episodes;

    $dom = new DOMDocument();
    @$dom->loadHTML($html, LIBXML_NOERROR);
    $xpath = new DOMXPath($dom);

    // Find episode links
    $epLinks = $xpath->query("//a[contains(@href, '/episode-')]");
    $seen = [];

    foreach ($epLinks as $link) {
        $href = $link->getAttribute('href');
        if (preg_match('/episode-(\d+)/', $href, $m)) {
            $epNum = (int)$m[1];
            if (isset($seen[$epNum])) continue;
            $seen[$epNum] = true;

            $epUrl = BASE_URL . '/shows/' . $showSlug . '/season-' . $season . '/episode-' . $epNum;

            // Get thumbnail - episodes use different URL pattern
            $thumbnail = ASSETS_URL . '/shows/' . $showSlug . '/s' . $season . 'e' . $epNum . 'Th.webp';

            $episodes[] = [
                'season' => $season,
                'episode' => $epNum,
                'title' => "Episode $epNum",
                'url' => $epUrl,
                'thumbnailUrl' => $thumbnail,
            ];
        }
    }

    // Sort by episode number
    usort($episodes, fn($a, $b) => $a['episode'] - $b['episode']);

    return $episodes;
}

// Check if there are more pages
function hasNextPage(string $html): bool {
    return strpos($html, 'rel="next"') !== false ||
           preg_match('/page=\d+["\']/', $html);
}

// Main scraping function
function scrape(): void {
    echo "=== IMVBox Content Scraper ===\n\n";

    $db = initDatabase();
    $movieStmt = $db->prepare("
        INSERT OR REPLACE INTO cached_movies
        (id, title, poster_url, farsiland_url, description, year, rating, runtime, director, genres, date_added, last_updated)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ");
    $seriesStmt = $db->prepare("
        INSERT OR REPLACE INTO cached_series
        (id, title, poster_url, farsiland_url, description, year, rating, total_seasons, total_episodes, genres, date_added, last_updated)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ");
    $episodeStmt = $db->prepare("
        INSERT OR REPLACE INTO cached_episodes
        (id, series_id, title, thumbnail_url, farsiland_url, season, episode, date_added)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ");

    $now = time() * 1000; // milliseconds
    $movieCount = 0;
    $seriesCount = 0;
    $episodeCount = 0;

    // Scrape movies
    echo "[MOVIES] Starting...\n";
    $seenMovies = [];

    for ($page = 1; $page <= MAX_PAGES; $page++) {
        $url = BASE_URL . '/movies' . ($page > 1 ? "?page=$page" : '');
        echo "  Page $page: $url\n";

        $html = fetchUrl($url);
        if (!$html) break;

        $movies = parseMovieListPage($html);
        if (empty($movies)) {
            echo "  No movies found, stopping.\n";
            break;
        }

        foreach ($movies as $movie) {
            if (isset($seenMovies[$movie['slug']])) continue;
            $seenMovies[$movie['slug']] = true;

            echo "    - {$movie['title']}... ";

            // Fetch details
            $details = fetchMovieDetails($movie['url']);

            $id = crc32('imvbox_movie_' . $movie['slug']);
            $posterUrl = $details['posterUrl'] ?: $movie['posterUrl'];

            $movieStmt->execute([
                $id,
                $movie['title'],
                $posterUrl,
                $movie['url'],
                $details['description'],
                $details['year'],
                $details['rating'],
                $details['runtime'],
                $details['director'],
                implode(',', $details['genres']),
                $now,
                $now,
            ]);

            $movieCount++;
            echo "OK\n";
        }

        if (!hasNextPage($html)) break;
    }

    echo "\n[TV SERIES] Starting...\n";
    $seenSeries = [];

    for ($page = 1; $page <= MAX_PAGES; $page++) {
        $url = BASE_URL . '/tv-series' . ($page > 1 ? "?page=$page" : '');
        echo "  Page $page: $url\n";

        $html = fetchUrl($url);
        if (!$html) break;

        $seriesList = parseSeriesListPage($html);
        if (empty($seriesList)) {
            echo "  No series found, stopping.\n";
            break;
        }

        foreach ($seriesList as $series) {
            if (isset($seenSeries[$series['slug']])) continue;
            $seenSeries[$series['slug']] = true;

            echo "    - {$series['title']}... ";

            // Fetch details including episodes
            $details = fetchSeriesDetails($series['url'], $series['slug']);

            $seriesId = crc32('imvbox_series_' . $series['slug']);
            $posterUrl = $details['posterUrl'] ?: $series['posterUrl'];

            $seriesStmt->execute([
                $seriesId,
                $series['title'],
                $posterUrl,
                $series['url'],
                $details['description'],
                $details['year'],
                $details['rating'],
                $details['totalSeasons'],
                $details['totalEpisodes'],
                implode(',', $details['genres']),
                $now,
                $now,
            ]);

            $seriesCount++;

            // Insert episodes
            foreach ($details['episodes'] as $ep) {
                $epId = crc32('imvbox_ep_' . $series['slug'] . '_s' . $ep['season'] . 'e' . $ep['episode']);

                $episodeStmt->execute([
                    $epId,
                    $seriesId,
                    $ep['title'],
                    $ep['thumbnailUrl'],
                    $ep['url'],
                    $ep['season'],
                    $ep['episode'],
                    $now,
                ]);

                $episodeCount++;
            }

            echo "OK ({$details['totalEpisodes']} episodes)\n";
        }

        if (!hasNextPage($html)) break;
    }

    // Populate FTS tables
    echo "\n[FTS] Building search index...\n";
    $db->exec("INSERT INTO movie_fts(rowid, title) SELECT id, title FROM cached_movies");
    $db->exec("INSERT INTO series_fts(rowid, title) SELECT id, title FROM cached_series");

    // Extract unique genres
    echo "[GENRES] Extracting...\n";
    $genres = [];
    foreach ($db->query("SELECT DISTINCT genres FROM cached_movies WHERE genres != ''") as $row) {
        foreach (explode(',', $row['genres']) as $g) {
            $g = trim($g);
            if ($g) $genres[$g] = true;
        }
    }
    foreach ($db->query("SELECT DISTINCT genres FROM cached_series WHERE genres != ''") as $row) {
        foreach (explode(',', $row['genres']) as $g) {
            $g = trim($g);
            if ($g) $genres[$g] = true;
        }
    }

    $genreStmt = $db->prepare("INSERT OR IGNORE INTO cached_genres (name) VALUES (?)");
    foreach (array_keys($genres) as $genre) {
        $genreStmt->execute([$genre]);
    }

    // Summary
    echo "\n=== COMPLETE ===\n";
    echo "Movies:   $movieCount\n";
    echo "Series:   $seriesCount\n";
    echo "Episodes: $episodeCount\n";
    echo "Genres:   " . count($genres) . "\n";
    echo "Database: " . DB_FILE . "\n";
    echo "\nCopy to: app/src/main/assets/databases/imvbox_content.db\n";
}

// Run
scrape();
