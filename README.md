# AutoTune - Intelligent Graphics Optimizer for Minecraft

AutoTune benchmarks your hardware, learns what your PC can handle, and sets every single graphics setting to its mathematically optimal value. Then it keeps watching and adjusts live while you play.

## Features

- **30-phase exhaustive benchmark** that profiles your GPU, CPU, memory, storage, and thermals inside the actual Minecraft render engine
- **Per-setting optimization** -- not presets or tiers. Every setting is individually calculated from your benchmark data using interpolation curves
- **Live adaptive mode** -- continuously monitors FPS and dynamically adjusts settings in real time to maintain your target framerate
- **Smart questionnaire** -- tells AutoTune your priorities (FPS vs visuals vs render distance) and it respects your preferences
- **Sodium & Iris aware** -- benchmarks and optimizes every Sodium toggle and finds the best shaderpack + shadow resolution
- **Detailed reports** -- see exactly why each setting was chosen
- **Mod Menu integration** -- full tabbed configuration UI
- **Profile system** -- save, share, and switch between optimized configs

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) (0.15+)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download AutoTune from [Modrinth](https://modrinth.com/mod/autotune)
4. Place the JAR in your `mods/` folder
5. Launch Minecraft and press F10

## Quick Start

1. Press **F10** or open from Mod Menu
2. Run the benchmark (Full: ~15 min, Quick: ~3 min)
3. Answer a few questions about how you play
4. AutoTune calculates the perfect settings and shows you every change
5. Enable Live Mode and forget about it

## Keybindings

| Key | Action |
|-----|--------|
| F10 | Open AutoTune |
| F9 | Start Benchmark |
| F8 | Toggle Live Mode |
| F7 | Toggle FPS Overlay |

## Compatibility

- Minecraft 1.21 through 1.21.11
- Fabric & Quilt
- Client-side only
- Works with: Sodium, Iris, Lithium, Starlight, Indium, Entity Culling, FerriteCore, ModernFix, Distant Horizons, and more

## Building from Source

```bash
# Build for all versions
./gradlew buildAll

# Build for a specific version
./gradlew :versions-1_21_4:build
```

Requires JDK 21+.

## License

MIT License. See [LICENSE](LICENSE).
