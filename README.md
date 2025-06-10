# Mindsky

## What's Mindsky?
Mindsky is an Android app that uses on-device machine learning to create personalized "For You" feeds from Bluesky. It is completely serverless and preserves your privacy by keeping all data processing local to your device.

## How does it work?
Background workers run when Wi-Fi is available and the device is idle. The worker fetches posts from the Bluesky and use [MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) to embed post content. The worker then calculates and ranks the similarity of fetched posts to your demonstrated preferences based on your interaction history.

### Key Features
- **Privacy-First**: All machine learning and data processing happens on your device
- **Intelligent Background Sync**: Fetches and processes content when optimal (idle + Wi-Fi)
- **Personalized Ranking**: Learns from your explicit signals (likes, comments, shares) and implicit signals (reading time, scroll patterns)
- **Lightweight ML**: Uses efficient sentence transformers optimized for mobile devices
- **Native**: Uses Jetpack Compose and Kotlin.

### How Personalization Works
1. **Signal Collection**: Tracks and pulls your interactions with posts (likes, comments, post views)
2. **Content Analysis**: Converts post text into mathematical embeddings using MiniLM
3. **Preference Learning**: Builds a profile of your interests based on content you engage with
4. **Smart Ranking**: Scores new posts by similarity to your demonstrated preferences
5. **Continuous Learning**: Adapts to your evolving interests over time

## Privacy & Data
- **Zero Server Dependencies**: No user data ever leaves your device
- **Local Storage Only**: All posts, preferences, and ML models stored locally
- **AT Protocol Direct**: Connects directly to Bluesky's decentralized network
- **Transparent Processing**: All recommendation logic runs locally and can be inspected

## Technical Architecture
- **Android WorkManager**: Handles background processing and sync scheduling
- [**Sentence Embeddings Android**](https://github.com/shubham0204/Sentence-Embeddings-Android): Runs sentence transformer models efficiently on mobile
- [**ObjectBox**](http://objectbox.io/): stores posts vectors/embeddings
- **AT Protocol SDK**: Direct integration with Bluesky's API

## Getting Started

> [!TIP]
> Mindsky is still a work in progress, we'll add installation once we have a suitable release.

## Contributing

> [!NOTE]
> Work in progress, but we're happy to accept any contributions!

## License
Mindsky is lincensed under [GNU Affero General Public License v3.0
](/LICENSE) to ensure that all modifications of the source remain open-source.

---

*Mindsky is an independent project and is not affiliated with Bluesky Social PBC.*
