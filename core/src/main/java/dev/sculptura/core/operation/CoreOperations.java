package dev.sculptura.core.operation;

import dev.sculptura.core.brush.BrushSettings;
import dev.sculptura.core.brush.BrushType;
import dev.sculptura.core.brush.SculptBrushEngine;
import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.geometry.VoxelWorldView;
import dev.sculptura.core.material.GradientMaterialPattern;
import dev.sculptura.core.material.MaterialPattern;
import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.MirrorPlane;
import dev.sculptura.core.math.Rotation90;
import dev.sculptura.core.shape.ShapeGenerator;
import dev.sculptura.core.shape.ShapeParameters;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Concrete implementations of all 14 required world editing operations specified in Section 5.D:
 * 1. SetBlocksOperation
 * 2. ReplaceBlocksOperation
 * 3. FillShapeOperation
 * 4. SphereOperation
 * 5. CylinderOperation
 * 6. StackOperation
 * 7. CopyOperation
 * 8. MoveOperation
 * 9. RotateOperation
 * 10. MirrorOperation
 * 11. PaintOperation
 * 12. SmoothOperation
 * 13. FlattenOperation
 * 14. GradientOperation
 */
public final class CoreOperations {
    private CoreOperations() {}

    private static EditOperation.ValidationResult validateBox(BoundingBox3i box, EditOperation.OperationLimits limits) {
        if (box == null) {
            return EditOperation.ValidationResult.error("Selection bounding box is null");
        }
        if (box.volume() > limits.maxBlocksPerOperation()) {
            return EditOperation.ValidationResult.error(
                    "Selection volume " + box.volume() + " exceeds max limit " + limits.maxBlocksPerOperation()
            );
        }
        if (box.minY() < limits.minBuildY() || box.maxY() >= limits.maxBuildY()) {
            return EditOperation.ValidationResult.error(
                    "Selection Y range [" + box.minY() + ".." + box.maxY() + "] is outside world height limits ["
                            + limits.minBuildY() + ".." + (limits.maxBuildY() - 1) + "]"
            );
        }
        return EditOperation.ValidationResult.ok();
    }

    // 1. SetBlocksOperation
    public record SetBlocksOperation(BoundingBox3i selection, MaterialPattern material, long seed) implements EditOperation {
        @Override
        public String operationId() { return "set_blocks"; }

        @Override
        public String displayName() { return "Set Blocks"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() { return Optional.of(selection); }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            return validateBox(selection, limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            for (BlockPos3i pos : selection) {
                if (cancellationToken != null && cancellationToken.get()) break;
                buf.setBlock(pos, material.sample(pos, selection, seed));
            }
            return buf;
        }
    }

    // 2. ReplaceBlocksOperation
    public record ReplaceBlocksOperation(
            BoundingBox3i selection,
            BlockStateRef targetFilter,
            MaterialPattern replacementMaterial,
            long seed
    ) implements EditOperation {
        @Override
        public String operationId() { return "replace_blocks"; }

        @Override
        public String displayName() { return "Replace Blocks"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() { return Optional.of(selection); }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            return validateBox(selection, limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            for (BlockPos3i pos : selection) {
                if (cancellationToken != null && cancellationToken.get()) break;
                BlockStateRef existing = world.getBlockState(pos);
                if (targetFilter == null ? !existing.isAir() : existing.equals(targetFilter) || existing.blockId().equals(targetFilter.blockId())) {
                    buf.setBlock(pos, replacementMaterial.sample(pos, selection, seed));
                }
            }
            return buf;
        }
    }

    // 3. FillShapeOperation
    public record FillShapeOperation(ShapeParameters shapeParameters, MaterialPattern material) implements EditOperation {
        @Override
        public String operationId() { return "fill_shape"; }

        @Override
        public String displayName() { return "Fill Shape (" + shapeParameters.type().name() + ")"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            BlockPos3i o = shapeParameters.origin();
            int rx = shapeParameters.radiusX();
            int ry = Math.max(shapeParameters.radiusY(), shapeParameters.height());
            int rz = shapeParameters.radiusZ();
            return Optional.of(new BoundingBox3i(o.x() - rx, o.y() - ry, o.z() - rz, o.x() + rx, o.y() + ry, o.z() + rz));
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            if (shapeParameters.radiusX() > limits.maxRadius()
                    || shapeParameters.radiusY() > limits.maxRadius()
                    || shapeParameters.radiusZ() > limits.maxRadius()) {
                return ValidationResult.error("Shape radius exceeds maximum allowed radius " + limits.maxRadius());
            }
            return ValidationResult.ok();
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            return ShapeGenerator.generate(shapeParameters, material);
        }
    }

    // 4. SphereOperation
    public record SphereOperation(BlockPos3i center, int radius, boolean hollow, MaterialPattern material, long seed) implements EditOperation {
        @Override
        public String operationId() { return "sphere"; }

        @Override
        public String displayName() { return hollow ? "Hollow Sphere" : "Solid Sphere"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            return Optional.of(new BoundingBox3i(
                    center.x() - radius, center.y() - radius, center.z() - radius,
                    center.x() + radius, center.y() + radius, center.z() + radius
            ));
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            if (radius <= 0 || radius > limits.maxRadius()) {
                return ValidationResult.error("Sphere radius " + radius + " out of valid range [1.." + limits.maxRadius() + "]");
            }
            return validateBox(affectedBounds().orElseThrow(), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            return ShapeGenerator.generate(ShapeParameters.sphere(center, radius, hollow), material);
        }
    }

    // 5. CylinderOperation
    public record CylinderOperation(
            BlockPos3i baseCenter,
            int radius,
            int height,
            boolean hollow,
            Axis3D axis,
            MaterialPattern material
    ) implements EditOperation {
        @Override
        public String operationId() { return "cylinder"; }

        @Override
        public String displayName() { return hollow ? "Hollow Cylinder" : "Solid Cylinder"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            return Optional.of(new BoundingBox3i(
                    baseCenter.x() - radius, baseCenter.y(), baseCenter.z() - radius,
                    baseCenter.x() + radius, baseCenter.y() + height - 1, baseCenter.z() + radius
            ));
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            if (radius <= 0 || radius > limits.maxRadius() || height <= 0) {
                return ValidationResult.error("Invalid cylinder dimensions");
            }
            return validateBox(affectedBounds().orElseThrow(), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            return ShapeGenerator.generate(ShapeParameters.cylinder(baseCenter, radius, height, hollow, axis), material);
        }
    }

    // 6. StackOperation
    public record StackOperation(
            BoundingBox3i sourceSelection,
            Direction3D direction,
            int count,
            int spacing,
             boolean copyAir
    ) implements EditOperation {
        @Override
        public String operationId() { return "stack"; }

        @Override
        public String displayName() { return "Stack Selection"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            int stepDist = sourceSelection.size(direction.axis()) + Math.max(0, spacing);
            BlockPos3i totalOffset = direction.normalOffset().multiply(stepDist * Math.max(1, count));
            return Optional.of(sourceSelection.union(sourceSelection.translate(totalOffset)));
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            if (count <= 0) {
                return ValidationResult.error("Stack count must be >= 1");
            }
            long totalVol = sourceSelection.volume() * count;
            if (totalVol > limits.maxBlocksPerOperation()) {
                return ValidationResult.error("Stacked volume " + totalVol + " exceeds max limit " + limits.maxBlocksPerOperation());
            }
            return validateBox(affectedBounds().orElseThrow(), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            int stepDist = sourceSelection.size(direction.axis()) + Math.max(0, spacing);
            for (int i = 1; i <= count; i++) {
                if (cancellationToken != null && cancellationToken.get()) break;
                BlockPos3i offset = direction.normalOffset().multiply(stepDist * i);
                for (BlockPos3i pos : sourceSelection) {
                    BlockStateRef state = world.getBlockState(pos);
                    if (copyAir || !state.isAir()) {
                        buf.setBlock(pos.add(offset), state);
                    }
                }
            }
            return buf;
        }
    }

    // 7. CopyOperation (Paste / Duplicate to target offset)
    public record CopyOperation(
            BoundingBox3i sourceSelection,
            BlockPos3i translationOffset,
            boolean includeAir
    ) implements EditOperation {
        @Override
        public String operationId() { return "copy"; }

        @Override
        public String displayName() { return "Copy / Duplicate Selection"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            return Optional.of(sourceSelection.translate(translationOffset));
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            return validateBox(affectedBounds().orElseThrow(), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            for (BlockPos3i pos : sourceSelection) {
                if (cancellationToken != null && cancellationToken.get()) break;
                BlockStateRef state = world.getBlockState(pos);
                if (includeAir || !state.isAir()) {
                    buf.setBlock(pos.add(translationOffset), state);
                }
            }
            return buf;
        }
    }

    // 8. MoveOperation
    public record MoveOperation(BoundingBox3i sourceSelection, BlockPos3i translationOffset) implements EditOperation {
        @Override
        public String operationId() { return "move"; }

        @Override
        public String displayName() { return "Move Selection"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            return Optional.of(sourceSelection.union(sourceSelection.translate(translationOffset)));
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            if (sourceSelection.volume() * 2 > limits.maxBlocksPerOperation()) {
                return ValidationResult.error("Move volume exceeds max limit " + limits.maxBlocksPerOperation());
            }
            return validateBox(sourceSelection.translate(translationOffset), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            VoxelBuffer snapshot = new VoxelBuffer();
            for (BlockPos3i pos : sourceSelection) {
                BlockStateRef existing = world.getBlockState(pos);
                if (!existing.isAir()) {
                    snapshot.setBlock(pos, existing);
                    buf.setBlock(pos, BlockStateRef.AIR);
                }
            }
            for (Map.Entry<BlockPos3i, BlockStateRef> entry : snapshot.entries().entrySet()) {
                if (cancellationToken != null && cancellationToken.get()) break;
                buf.setBlock(entry.getKey().add(translationOffset), entry.getValue());
            }
            return buf;
        }
    }

    // 9. RotateOperation
    public record RotateOperation(
            BoundingBox3i sourceSelection,
            BlockPos3i pivot,
            Rotation90 rotation,
            Axis3D axis,
            boolean clearOriginal
    ) implements EditOperation {
        @Override
        public String operationId() { return "rotate"; }

        @Override
        public String displayName() { return "Rotate Selection (" + rotation.degrees() + " deg)"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            BlockPos3i c1 = rotation.transform(sourceSelection.min(), pivot, axis);
            BlockPos3i c2 = rotation.transform(sourceSelection.max(), pivot, axis);
            BoundingBox3i rotatedBox = BoundingBox3i.fromCorners(c1, c2);
            return Optional.of(clearOriginal ? sourceSelection.union(rotatedBox) : rotatedBox);
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            return validateBox(affectedBounds().orElseThrow(), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            VoxelBuffer rotated = new VoxelBuffer();
            for (BlockPos3i pos : sourceSelection) {
                if (cancellationToken != null && cancellationToken.get()) break;
                BlockStateRef state = world.getBlockState(pos);
                if (!state.isAir()) {
                    if (clearOriginal) {
                        buf.setBlock(pos, BlockStateRef.AIR);
                    }
                    BlockPos3i dest = rotation.transform(pos, pivot, axis);
                    rotated.setBlock(dest, state.rotate(rotation, axis));
                }
            }
            buf.mergeFrom(rotated);
            return buf;
        }
    }

    // 10. MirrorOperation
    public record MirrorOperation(
            BoundingBox3i sourceSelection,
            BlockPos3i pivot,
            MirrorPlane mirrorPlane,
            boolean clearOriginal
    ) implements EditOperation {
        @Override
        public String operationId() { return "mirror"; }

        @Override
        public String displayName() { return "Mirror Selection (" + mirrorPlane.name() + ")"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            BlockPos3i c1 = mirrorPlane.transform(sourceSelection.min(), pivot);
            BlockPos3i c2 = mirrorPlane.transform(sourceSelection.max(), pivot);
            BoundingBox3i mirroredBox = BoundingBox3i.fromCorners(c1, c2);
            return Optional.of(clearOriginal ? sourceSelection.union(mirroredBox) : mirroredBox);
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            return validateBox(affectedBounds().orElseThrow(), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            VoxelBuffer mirrored = new VoxelBuffer();
            for (BlockPos3i pos : sourceSelection) {
                if (cancellationToken != null && cancellationToken.get()) break;
                BlockStateRef state = world.getBlockState(pos);
                if (!state.isAir()) {
                    if (clearOriginal) {
                        buf.setBlock(pos, BlockStateRef.AIR);
                    }
                    BlockPos3i dest = mirrorPlane.transform(pos, pivot);
                    mirrored.setBlock(dest, state.mirror(mirrorPlane));
                }
            }
            buf.mergeFrom(mirrored);
            return buf;
        }
    }

    // 11. PaintOperation
    public record PaintOperation(BoundingBox3i selection, MaterialPattern material, boolean surfaceOnly, long seed) implements EditOperation {
        @Override
        public String operationId() { return "paint"; }

        @Override
        public String displayName() { return "Paint Blocks"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() { return Optional.of(selection); }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            return validateBox(selection, limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            for (BlockPos3i pos : selection) {
                if (cancellationToken != null && cancellationToken.get()) break;
                if (world.isAir(pos)) continue;
                if (surfaceOnly && !hasAdjacentAir(world, pos)) continue;
                buf.setBlock(pos, material.sample(pos, selection, seed));
            }
            return buf;
        }

        private static boolean hasAdjacentAir(VoxelWorldView world, BlockPos3i pos) {
            for (Direction3D dir : Direction3D.values()) {
                if (world.isAir(pos.offset(dir))) {
                    return true;
                }
            }
            return false;
        }
    }

    // 12. SmoothOperation
    public record SmoothOperation(BlockPos3i center, int radius, double strength, MaterialPattern material) implements EditOperation {
        @Override
        public String operationId() { return "smooth"; }

        @Override
        public String displayName() { return "Smooth Terrain"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            return Optional.of(new BoundingBox3i(
                    center.x() - radius, center.y() - radius, center.z() - radius,
                    center.x() + radius, center.y() + radius, center.z() + radius
            ));
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            if (radius <= 0 || radius > limits.maxRadius()) {
                return ValidationResult.error("Smooth radius out of bounds");
            }
            return validateBox(affectedBounds().orElseThrow(), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            BrushSettings settings = BrushSettings.defaults(BrushType.SMOOTH, radius, strength, material);
            return SculptBrushEngine.applyBrush(center, settings, world);
        }
    }

    // 13. FlattenOperation
    public record FlattenOperation(BlockPos3i center, int radius, int targetHeight, double strength, MaterialPattern material) implements EditOperation {
        @Override
        public String operationId() { return "flatten"; }

        @Override
        public String displayName() { return "Flatten Terrain"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() {
            return Optional.of(new BoundingBox3i(
                    center.x() - radius, Math.min(center.y(), targetHeight) - radius, center.z() - radius,
                    center.x() + radius, Math.max(center.y(), targetHeight) + radius, center.z() + radius
            ));
        }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            if (radius <= 0 || radius > limits.maxRadius()) {
                return ValidationResult.error("Flatten radius out of bounds");
            }
            return validateBox(affectedBounds().orElseThrow(), limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            BrushSettings base = BrushSettings.defaults(BrushType.FLATTEN, radius, strength, material);
            BrushSettings withHeight = new BrushSettings(
                    base.type(), base.shape(), base.radius(), base.strength(), base.falloff(),
                    base.falloffCurve(), base.spacing(), base.frequency(), base.noiseAmplitude(),
                    targetHeight, base.material(), base.replaceTarget(), base.randomSeed(), base.raycastRange()
            );
            return SculptBrushEngine.applyBrush(center, withHeight, world);
        }
    }

    // 14. GradientOperation
    public record GradientOperation(
            BoundingBox3i selection,
            GradientMaterialPattern gradientPattern,
            boolean replaceSolidOnly,
            long seed
    ) implements EditOperation {
        @Override
        public String operationId() { return "gradient"; }

        @Override
        public String displayName() { return "Gradient Fill (" + gradientPattern.mode().name() + ")"; }

        @Override
        public Optional<BoundingBox3i> affectedBounds() { return Optional.of(selection); }

        @Override
        public ValidationResult validate(OperationLimits limits, VoxelWorldView world) {
            return validateBox(selection, limits);
        }

        @Override
        public VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken) {
            VoxelBuffer buf = new VoxelBuffer();
            for (BlockPos3i pos : selection) {
                if (cancellationToken != null && cancellationToken.get()) break;
                if (replaceSolidOnly && world.isAir(pos)) continue;
                buf.setBlock(pos, gradientPattern.sample(pos, selection, seed));
            }
            return buf;
        }
    }
}
