# SpatialFlow Download System Architecture

SpatialFlow features a robust, high-fidelity audio download system designed to provide a seamless offline listening experience. This document outlines the technical implementation of how songs are extracted, downloaded, tagged, and stored.

## 🏗 Core Architecture

The download system is primarily managed by two key components:
- **`SongDownloader`**: Orchestrates the extraction, download, and metadata tagging logic.
- **`AudioFileManager`**: Handles file system interactions, directory management, and MediaStore integration.

---

## 📥 The Download Workflow

### 1. Stream Extraction
When a user triggers a download, the app first needs a direct audio stream URL.
- **Extractor**: The app uses `NewPipeStreamExtractor` to fetch the high-quality audio stream associated with a `videoId`.
- **Validation**: If the extraction fails (e.g., due to network issues or restricted content), the user is notified immediately via Toast, and the process is safely aborted.

### 2. File Initialization
- **Naming Convention**: Files are named using the format `"{Clean Title} - {Clean Artist}.mp3"`.
- **Sanitization**: All filenames are sanitized to remove illegal characters (e.g., `?`, `*`, `:`) to ensure compatibility across all Android versions.
- **Pathing**:
    - **Android 10+**: Files are initially created in the app's internal cache for processing.
    - **Android 9 & Below**: Files are created directly in `Downloads/SpatialFlow`.

### 3. High-Speed Download
- **Mechanism**: The app utilizes `HttpURLConnection` to stream data from the extracted URL.
- **Buffer Management**: Data is read in **8KB chunks** to balance memory usage and write speed.
- **Progress Tracking**: Progress is updated via a background notification. To ensure smooth UI performance, notification updates are throttled to every **400ms**.

---

## 🏷 Metadata & ID3 Tagging

SpatialFlow ensures that downloaded files look professional in any media player by embedding rich metadata directly into the `.mp3` file using the **JAudioTagger** library.

### 🧹 Title & Artist Cleaning
The app automatically "cleans" titles to remove common YouTube suffixes that clutter your library:
- Removes: `[Official Music Video]`, `(Lyrics)`, `[Lyric Video]`, `(Official Audio)`, etc.
- Removes trailing dashes and extra whitespace.
- Simplifies artist names by removing ` - Topic`.

### 🖼 Artwork Processing
A song isn't complete without its cover art.
1. **Fetch**: The thumbnail is fetched using **Glide**.
2. **Crop**: The image is automatically **auto-cropped to a 1:1 aspect ratio** from the center to fit standard album art displays.
3. **Compress**: The image is converted to a high-quality (95%) JPEG.
4. **Embed**: The artwork is written directly into the ID3v2 tag of the file.

---

## 📂 Storage & Media Integration

### 📁 Storage Locations
SpatialFlow respects modern Android storage guidelines:
- **Primary Location**: `Music/SpatialFlow/`
- **Fallback (Legacy)**: `Downloads/SpatialFlow/`

### 🔍 MediaStore Synchronization
To ensure the downloaded songs appear instantly in SpatialFlow and other music apps:
1. **Android 10+**: The app uses the `MediaStore` API to insert a new entry. It sets `IS_PENDING = 1` while copying, then releases it so the system scanner can index it immediately.
2. **Legacy**: The app triggers a `MEDIA_SCANNER_SCAN_FILE` broadcast to force the system to refresh the media database.

---

## 🔔 User Experience & Feedback

- **Live Progress**: A persistent notification shows the exact percentage of the download.
- **Completion Alert**: A high-priority notification appears when the download is finished.
- **Toasts**: Instant feedback for starting, completing, or failing a download.

---

> [!TIP]
> **Why MP3?** While the app may extract different formats, it standardizes on `.mp3` for the widest compatibility with ID3 tagging and external player support.

> [!IMPORTANT]
> Downloads are performed on a background **IO Coroutine Scope**, ensuring that the app remains perfectly responsive even during large file transfers.
