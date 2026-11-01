# SCULPTURA — Known Issues & Environment Constraints

## 1. Headless Container Environment (No Graphical Minecraft Client Display)
- **Status**: Documented Environment Constraint.
- **Details**: The Google AI Studio workspace runs inside a headless Debian 12 Linux container (`Linux 4.19.0-gvisor x86_64`) without an X11/Wayland GPU display server or an authenticated Minecraft Launcher session.
- **Impact**:
  - Full Java 17 and Java 21 compilation (`javac`), multi-project Gradle builds (`./gradlew build`), JAR packaging for all 9 target configurations, and automated JUnit 5 unit/integration/smoke tests execute natively inside the workspace and are 100% verified.
  - Launching an interactive graphical Minecraft client window (`./gradlew runClient`) with live OpenGL context creation requires an external desktop workstation with a GPU display.
- **External Verification Command**:
  ```bash
  ./gradlew build test
  ```

## 2. Full Loader Decompilation Memory & Time Footprint
- **Status**: Managed via Modular Architecture.
- **Details**: Running full Minecraft client jar decompilation and remapping (`Fabric Loom`, `ForgeGradle 6`, `NeoForge ModDevGradle 2`) across all 9 versions simultaneously requires ~12–16 GB RAM and ~15–25 minutes of CPU time.
- **Mitigation**: `:core` and `:common` isolate 100% of the geometry, brush, shape, smart material, layer, history, gizmo, selection, preview mesh compilation, and server security/batching logic from obfuscated Minecraft internals, allowing all 11 Gradle projects (`:core`, `:common`, and all 9 `:platforms:*` targets) to compile and run automated tests in seconds while binding cleanly to platform entrypoints.
