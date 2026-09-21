# Minecraft 26.3 NeoForge port: experimental test build

This checkout builds and renders Complementary Reimagined in a development client.
It is an unofficial experimental port with limited hardware and pack coverage.
The upstream README describes the original release rather than this port.

## Baseline and target

- Upstream: https://github.com/avpbynf/Vitrail-Shaders
- Commit: a9eb63cef04157c1530996e74121c29a961f4734 (0.11.0-beta).
- Minecraft 26.3, NeoForm 26.3-1, NeoForge 26.3.0.0-beta.
- Sodium 0.9.2+mc26.3, Java 25, local Gradle 9.6.1.
- Development version: 0.11.0-beta.neoforge26.3.3.
- Fabric is not the target and has not been validated.

## Implemented migration

- RenderPearl GPU APIs, compiled pipeline ownership, SPIR-V reflection/cache,
  geometry stages, and Vulkan storage bindings.
- Shared scene-pass suspension and replacement passes for terrain, entities,
  sky, clouds, weather, particles, hands, and shadows.
- Updated Sodium signatures and program selection; removed the old arena
  workaround because the 26.3 allocator already queries the current mesh format.
- SDL input/file dialog migration and removal of obsolete early-window hooks.
- Pack shader location assignment, stage linking by name, Vulkan built-in aliases,
  terrain push-constant range declarations, and state replay ordering.

## Verified on 2026-09-21

- Final NeoForge build and text checks passed (port-build-11.log).
- Subsequent development-client runs compiled newer changes successfully.
- Vulkan initialized on an NVIDIA RTX 5070 Ti.
- Entered the independent creative world Vitrail 26.3 Port Test with shaders
  disabled; terrain and first-person hand rendered (port-client-10.log).
- Opened shader settings with I. Fixed and verified pack selection by mouse.
- Complementary Reimagined r5.9.3 was selected and applied through the UI.
- Fixed the fence/pass ordering failure; shadow updates continued over many
  frames in runs 15 through 17.
- Run 17 visually verified terrain, sky, water reflections, shadows, empty hand,
  held diamond sword, rain, and End terrain/entities/sky.
- Shader reload (R), resource reload (F3+T), disabling/re-enabling shaders, and
  Overworld-to-Nether-to-End transitions completed without new shader or draw
  exceptions. Nether loading passed, but the initial view was obscured by lava.
- The 26.3 DynamicGpuData.Transform block uses matrix, texture matrix, colour,
  and offset order; the translator and its cache key now match this layout.
- Successful compilation does not establish full visual correctness.

## Coverage limits

Testing used one NVIDIA GPU and the Gradle development client.
The additional BSL, Photon and iterationRP compatibility results are recorded in
shaderpack-compatibility-26.3-zh.md. Test build 3 fixes iterationRP's severe daytime
darkness with graphics colorimg bindings and allocation, and resolves runtime atlas references.
Other GPUs, packs, multiplayer, mod compatibility (including Distant Horizons),
and long sessions are unverified. Compute paths were exercised with Photon colored lights and iterationRP;
iterationRP was checked at noon, after shader and resource reloads; complete visual parity
and third-party PBR resource packs remain unverified. Nether visual correctness needs a
better test location. No standalone launcher test has been performed.

Local game sources, downloaded shader packs, test saves, logs, and migration
scripts are diagnostic material and must not be redistributed with source
artifacts. Original licensing and attribution remain intact.
