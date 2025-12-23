# Musical Chairs

A browser-based implementation of the classic Musical Chairs game built with Kotlin Multiplatform and Compose for Web, compiled to WebAssembly.

## Overview

This game simulates 10 players (represented as colored circles) competing for chairs arranged in a 2-column layout. Players circulate around the chairs in an oval path while music plays. When the music stops, players race to claim the nearest available chair. One chair is removed each round, eliminating a player until only one remains.

## Demo

The game is deployed at: https://musicalchairs-gamma.vercel.app/

## Tech Stack

- **Kotlin Multiplatform** - Cross-platform development
- **Jetbrains Compose Multiplatform** - Declarative UI framework
- **WebAssembly (WASM)** - High-performance browser execution
- **Gradle** - Build automation with Kotlin DSL

## Prerequisites

- JDK 11 or higher

## Getting Started

### Run Development Server

```bash
./gradlew wasmJsRun
```

On Windows:
```bash
gradlew.bat wasmJsRun
```

This launches a development server at http://localhost:8080 with hot reload.

### Build for Production

```bash
./gradlew wasmJsProductionExecutableDistribution
```

Production artifacts are generated in the `public/` directory.

## Project Structure

```
MusicalChairs/
├── src/
│   └── wasmJsMain/
│       ├── kotlin/
│       │   └── Main.kt          # Game implementation
│       └── resources/
│           └── audio/           # Audio assets
├── public/                      # Production build output
│   ├── index.html              # Web entry point
│   └── audio/
│       └── happy-life.mp3      # Background music
├── build.gradle.kts            # Gradle configuration
├── settings.gradle.kts         # Project settings
└── gradle.properties           # Build properties
```

## Game Features

- 10 players with distinct colors
- Animated oval circulation path
- Chair claiming based on proximity
- Elimination animations
- Background music that plays during rounds
- Visual game state indicators

## How to Play

1. Click the "Start Game" button
2. Watch players circulate while music plays
3. Music stops randomly - players rush to sit
4. The standing player is eliminated
5. One chair is removed
6. Repeat until one player remains

## Deployment

The project is configured for Vercel deployment. The `vercel.json` configuration serves static files from the `public/` directory.

## License

MIT
