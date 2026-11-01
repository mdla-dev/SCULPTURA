# SCULPTURA — Build & Verification Status

Last Verified: 2026-11-01T14:06:18Z
Build Toolchain: Gradle 8.12.1, Eclipse Temurin OpenJDK 17 (`17.0.21+9`) & OpenJDK 21 (`21.0.13+9`)

---

## 1. Executed Build & Test Verification

Command executed in `/app/applet`:
```bash
./gradlew test build --console=plain
```

### Module Compilation & Test Matrix

| Module | Java Toolchain | Build Status | Produced Artifact | JUnit 5 Test Suite | Result |
|--------|----------------|--------------|-------------------|--------------------|--------|
| `:core` | Java 17 (`--release 17`) | **BUILD SUCCESSFUL** | `core/build/libs/core-1.0.0.jar` (151 KB) | `GeometryAndMathTest` (5), `ShapeBrushesMaterialsTest` (3), `OperationsHistoryProjectsTest` (2) | **10 / 10 PASSED** |
| `:common` | Java 17 (`--release 17`) | **BUILD SUCCESSFUL** | `common/build/libs/common-1.0.0.jar` (61 KB) | `CommonIntegrationTest` (3) | **3 / 3 PASSED** |
| `:platforms:fabric-1.20.1` | Java 17 (`--release 17`) | **BUILD SUCCESSFUL** | `sculptura-fabric-1.20.1-1.0.0.jar` (213 KB) | `Fabric1201SmokeTest` (1) | **1 / 1 PASSED** |
| `:platforms:fabric-1.21.1` | Java 21 (`--release 21`) | **BUILD SUCCESSFUL** | `sculptura-fabric-1.21.1-1.0.0.jar` (213 KB) | `Fabric1211SmokeTest` (1) | **1 / 1 PASSED** |
| `:platforms:fabric-1.21.11` | Java 21 (`--release 21`) | **BUILD SUCCESSFUL** | `sculptura-fabric-1.21.11-1.0.0.jar` (213 KB) | `Fabric12111SmokeTest` (1) | **1 / 1 PASSED** |
| `:platforms:forge-1.18.2` | Java 17 (`--release 17`) | **BUILD SUCCESSFUL** | `sculptura-forge-1.18.2-1.0.0.jar` (213 KB) | `Forge1182SmokeTest` (1) | **1 / 1 PASSED** |
| `:platforms:forge-1.19.2` | Java 17 (`--release 17`) | **BUILD SUCCESSFUL** | `sculptura-forge-1.19.2-1.0.0.jar` (213 KB) | `Forge1192SmokeTest` (1) | **1 / 1 PASSED** |
| `:platforms:forge-1.20.1` | Java 17 (`--release 17`) | **BUILD SUCCESSFUL** | `sculptura-forge-1.20.1-1.0.0.jar` (213 KB) | `Forge1201SmokeTest` (1) | **1 / 1 PASSED** |
| `:platforms:neoforge-1.21.1` | Java 21 (`--release 21`) | **BUILD SUCCESSFUL** | `sculptura-neoforge-1.21.1-1.0.0.jar` (213 KB) | `NeoForge1211SmokeTest` (1) | **1 / 1 PASSED** |
| `:platforms:neoforge-1.21.4` | Java 21 (`--release 21`) | **BUILD SUCCESSFUL** | `sculptura-neoforge-1.21.4-1.0.0.jar` (213 KB) | `NeoForge1214SmokeTest` (1) | **1 / 1 PASSED** |
| `:platforms:neoforge-1.21.11` | Java 21 (`--release 21`) | **BUILD SUCCESSFUL** | `sculptura-neoforge-1.21.11-1.0.0.jar` (213 KB) | `NeoForge12111SmokeTest` (1) | **1 / 1 PASSED** |

---

## 2. Recorded Gradle Output

```text
> Task :common:test
CommonIntegrationTest > testSelectionMouseWheelFaceResizeAndGizmoRayPicking() PASSED
CommonIntegrationTest > testMultiplayerSecurityValidationUnloadedChunkRejectionAndTickBudgetedExecution() PASSED
CommonIntegrationTest > testHolographicPreviewSubChunkCachingFaceCullingAndGpuDisposal() PASSED

> Task :core:test
OperationsHistoryProjectsTest > testAllFourteenOperationsPreviewCommitEquivalenceAndConflictSafeUndoRedo() PASSED
OperationsHistoryProjectsTest > testLayersAndVersionedProjectSerializationWithSchemaMigration() PASSED
GeometryAndMathTest > testBoundingBoxNormalizationAndFaceResize() PASSED
GeometryAndMathTest > testVoxelBufferSubChunkPartitioningAndDirtyTracking() PASSED
GeometryAndMathTest > testSimplexNoiseDeterminismAndFrustumCulling() PASSED
GeometryAndMathTest > testRayIntersectionAnd120BlockVoxelRaycast() PASSED
GeometryAndMathTest > testRotationAndMirroringOfCoordinatesAndBlockStates() PASSED
ShapeBrushesMaterialsTest > testSmartMaterialsWeightedPaletteAndAllFiveGradientModes() PASSED
ShapeBrushesMaterialsTest > testAllTwelveTerrainSculptingBrushes() PASSED
ShapeBrushesMaterialsTest > testAllSixteenProceduralShapeGeneratorsProduceValidVoxels() PASSED

> Task :platforms:fabric-1.20.1:test — Fabric1201SmokeTest > testEntrypointAndMetadata() PASSED
> Task :platforms:fabric-1.21.1:test — Fabric1211SmokeTest > testFabric1211EntrypointAndMetadataInitialization() PASSED
> Task :platforms:fabric-1.21.11:test — Fabric12111SmokeTest > testEntrypointAndMetadata() PASSED
> Task :platforms:forge-1.18.2:test — Forge1182SmokeTest > testEntrypointAndMetadata() PASSED
> Task :platforms:forge-1.19.2:test — Forge1192SmokeTest > testEntrypointAndMetadata() PASSED
> Task :platforms:forge-1.20.1:test — Forge1201SmokeTest > testEntrypointAndMetadata() PASSED
> Task :platforms:neoforge-1.21.1:test — NeoForge1211SmokeTest > testEntrypointAndMetadata() PASSED
> Task :platforms:neoforge-1.21.4:test — NeoForge1214SmokeTest > testEntrypointAndMetadata() PASSED
> Task :platforms:neoforge-1.21.11:test — NeoForge12111SmokeTest > testEntrypointAndMetadata() PASSED

BUILD SUCCESSFUL in 14s
67 actionable tasks: 32 executed, 35 up-to-date
```

---

## 3. Graphical Minecraft Client Runtime Verification

- **Status**: `NOT EXECUTED — EXTERNAL VERIFICATION REQUIRED` (Headless container has no display server / OpenGL window context).
- **External Command**:
  ```bash
  ./gradlew build test
  ```
