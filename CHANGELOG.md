# Changelog

All notable changes to this project are documented in this file.

## [0.1.0] - 2026-04-29

### Added
- Initial release of HeatSync for Forge `1.20.1`.
- Cold Sweat block temperature bridge registration for native Heat Sync heat pipes.
- Pipe thermal update controller that applies ambient blending, network equalization, passive loss, and nearby cold-source pull.
- Configurable thermal mapping and behavior via Forge common config.
- Block tags for `heat_sync:pipe_cold_sources` and `heat_sync:pipe_radiators`.
- Unit tests for mapping math and thermal step behavior.

## Unreleased

- Reconciled dependency and user-facing terminology with Heat Sync's native transport ownership; retired Create: New Age, Alchemylib, and Alchemistry are not runtime dependencies.
- Normalize the project identity to `heat-sync / heat_sync (formerly heatsync)`; this is a clean break with no legacy aliases or migrations.
