# Claw Android 🦞

An independent AI agent app for Android with memory, skills, and local tool support.

## Features

- **Chat with Claude** — Streaming responses via Anthropic Messages API (SSE)
- **Memory System** — SOUL.md, USER.md, MEMORY.md, daily notes (OpenClaw compatible)
- **Session Management** — Multiple conversations with history
- **Markdown Rendering** — Rich text in assistant messages
- **Encrypted API Key** — Stored in Android Keystore via EncryptedSharedPreferences
- **Multi-model support** — Claude Opus, Sonnet, Haiku

## Tech Stack

- Kotlin + Jetpack Compose
- Hilt (DI) + Room (DB) + DataStore (Settings)
- OkHttp (Networking) + kotlinx.serialization
- Markwon (Markdown rendering)
- Min SDK 29 (Android 10), Target SDK 35

## Build

```bash
# Clone
git clone https://github.com/houziershi/claw-android.git
cd claw-android

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

## Setup

1. Open the app
2. Go to **Settings** → Enter your Claude API key
3. Select a model
4. Start chatting!

## Project Structure

```
app/src/main/java/com/openclaw/agent/
├── core/
│   ├── llm/          — LLM client (Claude API with SSE streaming)
│   ├── memory/       — File-based memory system
│   ├── runtime/      — Agent runtime (function calling loop)
│   └── session/      — Session management
├── data/
│   ├── db/           — Room database (sessions + messages)
│   └── preferences/  — DataStore + encrypted prefs
├── di/               — Hilt dependency injection
└── ui/
    ├── chat/         — Chat screen with markdown bubbles
    ├── memory/       — Memory file viewer/editor
    ├── sessions/     — Session list
    ├── settings/     — API key + model config
    └── theme/        — Material 3 theming
```

## Roadmap

- [x] Phase 1: Chat + Memory + Sessions
- [ ] Phase 2: Function Calling + Local Tools (Camera, GPS, Calendar)
- [ ] Phase 3: Skill Engine (SKILL.md parsing)
- [ ] Phase 4: Voice (STT/TTS) + Optional Gateway connection

## License

MIT
