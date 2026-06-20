<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120"/>
</p>

<h1 align="center">SpatialFlow</h1>

<p align="center">
  A modern, unified Android music player built with Jetpack Compose, Material Design 3 Expressive, and advanced audio processing for both local libraries and online streaming.
</p>

<p align="center">
  <b>Hybrid Streaming • Material You • Dynamic Theming • Volume Normalization • Open Source</b>
</p>

<p align="center">
  <!-- Downloads & Release -->
  <img src="https://img.shields.io/github/downloads/MythicalSHUB/SpatialFlow/total?color=5C7AEA&style=for-the-badge" />
  <img src="https://img.shields.io/github/v/release/MythicalSHUB/SpatialFlow?color=4ADE80&style=for-the-badge" />
  <img src="https://img.shields.io/github/actions/workflow/status/MythicalSHUB/SpatialFlow/release.yml?style=for-the-badge&label=BUILD" />

  <!-- Repo Health -->
  <img src="https://img.shields.io/github/issues/MythicalSHUB/SpatialFlow?color=EF4444&style=for-the-badge" />

  <!-- Tech Stack -->
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />

  <!-- License -->
  <img src="https://img.shields.io/github/license/MythicalSHUB/SpatialFlow?color=10B981&style=for-the-badge" />
</p>

---

## About SpatialFlow

**SpatialFlow** is a next-generation Android audio experience that breaks the boundary between local music and online streaming. Built entirely with Kotlin and Jetpack Compose, it offers an immersive UI that extracts dynamic colors from album art in real-time to generate cohesive Material Design 3 themes.

Whether listening to high-fidelity files from internal storage or streaming directly from YouTube Music, SpatialFlow delivers premium audio via ExoPlayer, coupled with studio-grade volume normalization, custom crossfading, and advanced system haptics.

---

## Features

### Hybrid Playback System
* **Local Library:** Deep storage scanning with support for MP3, FLAC, AAC, WAV, and standard audio formats.
* **Online Streaming:** Built-in InnerTube API (WebRemix) integration to search, explore, and stream audio directly from YouTube Music without requiring accounts.
* **Unified Queue:** Mix local files and online streams seamlessly in the same playback queue.

### Advanced Audio Engineering
* **Real-time Volume Normalization (LUFS):** Automatically calculates and applies gain adjustments on-the-fly to ensure every track—offline or online—sounds equally loud (Default: -14 LUFS).
* **Custom Crossfade:** Configurable gapless, overlapping transitions between tracks.
* **Built-in Audio Effects:** Native Equalizer with configurable frequency bands, Bass Boost, Loudness Enhancer, and Environmental Reverb.

### Premium Immersive UI
* **Material Design 3 Expressive:** Modern glassmorphism panels, spring-physics animations, and deeply responsive layouts.
* **Dynamic Album Theming:** The application's color scheme adapts instantaneously to the currently playing song's artwork using Compose runtime surface color derivation.
* **Configurable Appearance:** Support for AMOLED pure black dark mode, dynamic navigation labels, and fluid skeleton loading shimmers.

---

## Screenshots

<p align="center">
  <img src="AppScreenShot/1.png" width="30%">
  <img src="AppScreenShot/2.png" width="30%">
  <img src="AppScreenShot/3.png" width="30%">
</p>
<p align="center">
  <img src="AppScreenShot/4.png" width="30%">
  <img src="AppScreenShot/5.png" width="30%">
  <img src="AppScreenShot/6.png" width="30%">
</p>
<p align="center">
  <img src="AppScreenShot/7.png" width="30%">
  <img src="AppScreenShot/8.png" width="30%">
  <img src="AppScreenShot/9.png" width="30%">
</p>
<p align="center">
  <img src="AppScreenShot/10.png" width="30%">
  <img src="AppScreenShot/11.png" width="30%">
  <img src="AppScreenShot/12.png" width="30%">
</p>
<p align="center">
  <img src="AppScreenShot/13.png" width="30%">
</p>

---

## Tech Stack & Architecture

| Component | Technology |
| :--- | :--- |
| **Language** | Kotlin 1.9+ |
| **UI Toolkit** | Jetpack Compose (Material 3 Expressive) |
| **Architecture** | MVI (Model-View-Intent) Presentation Layer |
| **Media Engine** | AndroidX Media3 (ExoPlayer) |
| **Dependency Injection** | Koin for Android |
| **Networking** | Ktor Client + Coroutines/StateFlow |
| **Extraction** | InnerTube Models + NewPipe Extractor integration |

---

## Requirements

| Component | Minimum Version |
| :--- | :--- |
| **Min SDK** | 24 (Android 7.0 Nougat) |
| **Target SDK**| 35 (Android 15) |
| **Java** | JDK 17 |

---

## Installation

### Download APK
You can download the latest production-ready APK directly from GitHub Releases:
https://github.com/MythicalSHUB/SpatialFlow/releases

### Build from Source
```bash
git clone https://github.com/MythicalSHUB/SpatialFlow.git
cd SpatialFlow
```
1. Open the project in Android Studio (Koala or newer recommended).
2. Wait for Gradle Sync to complete.
3. Build and deploy to your device.

---

## In-App Updater

SpatialFlow includes an automatic update mechanism:
* Checks the GitHub Releases API on launch.
* Prompts to download the latest APK directly within the application.
* Handled seamlessly via the Android Package Installer.

---

## Contributing

Contributions are welcome. If you find a bug or have an idea to improve the audio engine or UI:
1. Check the Issues tab on GitHub.
2. Fork the repository.
3. Submit a Pull Request.

---

## Credits & Acknowledgements

SpatialFlow is built upon the incredible work of the open-source community. Special thanks to the following projects and libraries:

* **[AndroidX Media3 (ExoPlayer)](https://github.com/androidx/media):** The robust audio playback engine powering SpatialFlow.
* **[NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor):** Providing the foundation for YouTube stream extraction and metadata parsing.
* **[Koin](https://github.com/InsertKoinIO/koin):** A pragmatic lightweight dependency injection framework for Kotlin.
* **[Ktor](https://github.com/ktorio/ktor):** An asynchronous framework for creating HTTP clients.
* **[Coil](https://github.com/coil-kt/coil):** Image loading backed by Kotlin Coroutines.
* **[InnerTune](https://github.com/z-huang/InnerTune):** The foundational base project that heavily inspired the architectural layout and core player models of SpatialFlow.
* **[OuterTune](https://github.com/dddlol/OuterTune):** Providing essential logic and structural inspiration for handling complex InnerTube API calls and data fetching.
* **[PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer):** Providing the design inspiration and logic for the seamless Mini Player and the smooth Onboarding flow UI.
* **[Material Design 3](https://m3.material.io/):** Google's expressive design system that powers the UI.

---

## Developer

**Shubham Karande**
Dedicated to building fluid, premium, and open-source Android experiences.

---

## License (MIT)

```text
MIT License

Copyright (c) 2026 Shubham Karande

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software...
```

---

## Keywords
SpatialFlow, SpatialFlow Android App, Jetpack Compose Music Player, Material You Audio Player, YouTube Music Streaming App, Android Media3 Player, ExoPlayer Android App, Open Source Spotify Alternative, Kotlin Music Player, LUFS Normalization Android, Equalizer Bass Boost App.
