# Heat Sync

Heat Sync is a Minecraft `1.20.1` Forge mod built with Kotlin. It owns native heat pipes, thermal machinery, liquid coolant conversion, and the optional Cold Sweat ambient bridge.

## Behavior

- Registers a Cold Sweat `BlockTemp` handler when Cold Sweat is installed.
- Maps pipe heat values onto Cold Sweat world temperature with a configurable offset/scale model.
- Rebalances loaded heat pipes every second using Cold Sweat ambient temperature, neighboring pipe heat, and nearby tagged cold sources.
- Uses the configured neutral heat baseline as ambient when Cold Sweat is absent; native heat transport remains available.
- Ships default tags under `data/heat_sync/tags/blocks` for radiator and cold-source classification.
- Provides native heat pipes, coolant exchangers, thermal fireboxes, boiler heaters, and creative heat sources without a Create: New Age dependency.

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

- Minecraft Forge `47.4.13` (`1.20.1`)
- Kotlin for Forge `4.11.0`
- Create `6.0.8`
- ChemLib `2.0.19`

Optional integrations:

- Applied Energistics 2 and PneumaticCraft: Repressurized
- Cold Sweat `2.3.13+` (enables ambient/world temperature bridge; verification is pinned to the pack's `2.4` runtime)
- EMI `1.1.3+1.20.1+forge` (client-side compatibility)

## Release

```bash
./gradlew verifyFull
```

Build outputs:

- `build/libs/heat-sync-<version>.jar`
- `build/libs/heat-sync-<version>-sources.jar`

Coverage outputs:

- `build/reports/jacoco/test/html/index.html`

## Notes

- Cold Sweat is optional. `verifyFull` runs the headless GameTests once with Cold Sweat `2.4` and once without Cold Sweat.
- Development dependencies are resolved from Forge, Create, Modrinth, Curse Maven, and Kotlin for Forge repositories declared in `build.gradle.kts`.

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Identity

The clean-break canonical identity is repository/artifact `heat-sync`, mod ID and resource namespace `heat_sync`, and Maven group `com.bettercontent`. Legacy `heatsync` worlds and configs are not migrated.
