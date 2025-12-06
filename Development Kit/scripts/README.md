# Content Database Generator

This script converts FarsiFlow's PostgreSQL database to SQLite for bundling in the Android APK.

## Prerequisites

1. **Start FarsiFlow Docker container:**
   ```bash
   cd G:\Farsiflow
   docker-compose up -d
   ```

2. **Install Python dependencies:**
   ```bash
   pip install psycopg2-binary
   ```

## Usage

### Option 1: Using Gradle (Recommended)
```bash
cd G:\farsiland_wrapper
./gradlew generateContentDatabase
```

### Option 2: Run Python script directly
```bash
cd G:\farsiland_wrapper
python scripts/generate_content_database.py
```

## What It Does

1. Connects to FarsiFlow PostgreSQL database
2. Extracts all movies (2,876), series (315), and episodes (7,574)
3. Converts data to SQLite format
4. Handles fractional episodes (14.5 → 145)
5. Maps genres from PostgreSQL arrays to comma-separated strings
6. Optionally caches video MP4 URLs from quality variants
7. Outputs `app/src/main/assets/databases/farsiland_content.db` (~14MB)

## Output Database Schema

- **cached_movies** - 2,876 movies with metadata
- **cached_series** - 315 TV series with season/episode counts
- **cached_episodes** - 7,574 episodes linked to series
- **cached_genres** - Genre taxonomy
- **cached_video_urls** - Pre-extracted MP4 URLs (optional)

## Next Steps

After generating the database:

1. Build APK with bundled database:
   ```bash
   ./gradlew assembleDebug
   ```

2. Install on Android TV device

3. On first launch, the app will copy the database from assets (~1-2 seconds)

4. Browse entire catalog offline!

## Troubleshooting

### "Connection refused" error
- Make sure FarsiFlow Docker container is running:
  ```bash
  docker ps | grep farsiflow
  ```

### "ModuleNotFoundError: No module named 'psycopg2'"
- Install Python PostgreSQL driver:
  ```bash
  pip install psycopg2-binary
  ```

### Database file not created
- Check Python script output for errors
- Verify G:\farsiland_wrapper\app\src\main\assets\databases\ directory exists
