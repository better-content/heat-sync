# Changelog

All notable changes to this project are documented in this file.

## [0.1.0] - 2026-04-29

### Added
- Initial release of HeatSync for Forge `1.20.1`.
- Cold Sweat block temperature bridge registration for Create: New Age heat pipes.
- Pipe thermal update controller that applies ambient blending, network equalization, passive loss, and nearby cold-source pull.
- Configurable thermal mapping and behavior via Forge common config.
- Block tags for `heatsync:pipe_cold_sources` and `heatsync:pipe_radiators`.
- Unit tests for mapping math and thermal step behavior.
