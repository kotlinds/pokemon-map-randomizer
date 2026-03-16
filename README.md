# pokemon-map-randomizer

A Kotlin/Compose Multiplatform desktop port
of [adrienntindall/hgss-map-randomizer](https://github.com/adrienntindall/hgss-map-randomizer), the original C++ map
randomizer for Pokémon HeartGold, SoulSilver, Black 2, and White 2.

Warps between locations are shuffled while guaranteeing the game remains beatable — every gym, story event, and
progression gate is reachable from the start.

## Supported games

| Game               | Code |
|--------------------|------|
| Pokémon HeartGold  | `hg` |
| Pokémon SoulSilver | `ss` |
| Pokémon Black 2    | `b2` |
| Pokémon White 2    | `w2` |

English, French, German, and Spanish ROMs are supported for HGSS. English and German for BW2.

## Usage

### GUI

Launch the app with no arguments. Select your ROM, game, seed (optional), season (BW2 only), and output folder, then
click Randomize.

### CLI

```
./app <rom> <game> [seed|-] [season] <outputDir>
```

| Argument    | Description                                                               |
|-------------|---------------------------------------------------------------------------|
| `rom`       | Path to the `.nds` ROM file                                               |
| `game`      | `hg`, `ss`, `b2`, or `w2`                                                 |
| `seed`      | Numeric seed, or `-` for a random seed (optional)                         |
| `season`    | `spring`, `autumn`, or `winter` — BW2 only (optional, defaults to spring) |
| `outputDir` | Directory where the randomized ROM and log file will be written           |

**Examples:**

```bash
# HeartGold with a fixed seed
./app game.nds hg 12345 ./out

# Black 2, random seed, autumn season
./app game.nds b2 - autumn ./out

# SoulSilver, random seed
./app game.nds ss ./out
```

The output directory will contain:

- `<name>_map_randomized.nds` — the randomized ROM
- `<name>_map_randomized.log` — a log of all warp connections with the seed used

## Building

Requires JDK 17+.

```bash
./gradlew :app:run                  # run from source (GUI)
./gradlew :app:run --args="..."     # run from source (CLI)
./gradlew :core:jvmTest             # run tests
```

## Credits

Original randomization algorithm, block constraint files, and warp data
by [adrienntindall](https://github.com/adrienntindall) — [hgss-map-randomizer](https://github.com/adrienntindall/hgss-map-randomizer).

This project is a clean-room Kotlin/Multiplatform rewrite of that C++ implementation, adding a cross-platform GUI and
CLI interface.
