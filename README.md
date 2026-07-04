# HeatSync

HeatSync is a Minecraft `1.20.1` Forge mod built with Kotlin. It exposes Create: New Age heat pipes to Cold Sweat as ambient block temperature sources, so cold pipe networks can affect nearby entities.

## Behavior

- Registers a Cold Sweat `BlockTemp` handler when Cold Sweat is installed.
- Maps pipe heat values onto Cold Sweat world temperature with a configurable offset/scale model.
- Rebalances loaded heat pipes every second using Cold Sweat ambient temperature, neighboring pipe heat, and nearby tagged cold sources.
- Ships default tags under `data/heatsync/tags/blocks` for radiator and cold-source classification.

## Configuration

The common config defines:

- Heat-to-temperature mapping bounds and scale
- Ambient blending and pipe equalization rates
- Cold-source target heat values
- Pipe-emitted Cold Sweat range and maximum effect

## Commands

```bash
./gradlew runClient
./gradlew runServer
./gradlew runData
./gradlew verifyFast
./gradlew verifyFull
```

## Dependencies

Required at runtime:

- Minecraft Forge `47.4.10` (`1.20.1`)
- Kotlin for Forge `4.11.0`
- Create `6.0.8`
- Create: New Age `1.1.7f`
- ChemLib `2.0.19`
- Alchemylib `1.0.30`
- Alchemistry `2.3.4`

Optional integrations:

- Cold Sweat `2.3.13` (enables ambient/world temperature bridge)
- EMI `1.1.3+1.20.1+forge` (client-side compatibility)

## Release

```bash
./gradlew verifyFull
```

Build outputs:

- `build/libs/heatsync-<version>.jar`
- `build/libs/heatsync-<version>-sources.jar`

Coverage outputs:

- `build/reports/jacoco/test/html/index.html`

## Notes

- Cold Sweat is optional. Without it, HeatSync loads but does not register the bridge hooks.
- Development dependencies are resolved from Forge, Create, Modrinth, Curse Maven, and Kotlin for Forge repositories declared in `build.gradle.kts`.
