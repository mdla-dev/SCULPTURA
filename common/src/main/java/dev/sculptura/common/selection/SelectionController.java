package dev.sculptura.common.selection;

import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.MirrorPlane;
import dev.sculptura.core.math.Ray3d;
import dev.sculptura.core.math.Rotation90;
import dev.sculptura.core.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 3D Selection System (Section 5.A) supporting:
 * - Point selection & Box selection
 * - Face selection & Face dragging
 * - Edge manipulation (12 edges) & Corner manipulation (8 corners)
 * - Mouse wheel expansion/contraction along active face normal
 * - Translation, Duplication, Rotation, Mirroring
 * - Grid snapping & Selection locking
 * - 3D visual descriptors (outlines, 6 face handles, coordinate axes, dimension labels, corner markers, active face highlight)
 */
public final class SelectionController {
    public record Line3D(Vec3d start, Vec3d end, int colorArgb) {}
    public record FaceHandleVisual(Direction3D face, Vec3d center, boolean active, boolean hovered, int colorArgb) {}
    public record DimensionLabel(String text, Vec3d worldPosition, Axis3D axis) {}

    public record SelectionVisualDescriptor(
            BoundingBox3i bounds,
            List<Line3D> outlineSegments,
            List<Line3D> coordinateAxes,
            List<FaceHandleVisual> faceHandles,
            List<Vec3d> cornerMarkers,
            List<DimensionLabel> dimensionLabels,
            Direction3D activeFace,
            Direction3D hoveredFace
    ) {}

    private BlockPos3i primaryPoint;
    private BlockPos3i secondaryPoint;
    private Direction3D activeFace = Direction3D.UP;
    private Direction3D hoveredFace = null;
    private int activeCornerIndex = -1;
    private int activeEdgeIndex = -1;
    private int gridSnapSize = 1;
    private boolean locked = false;

    public void selectPoint(BlockPos3i point) {
        if (locked || point == null) return;
        BlockPos3i snapped = point.snapToGrid(gridSnapSize);
        this.primaryPoint = snapped;
        this.secondaryPoint = snapped;
    }

    public void selectBox(BlockPos3i cornerA, BlockPos3i cornerB) {
        if (locked || cornerA == null || cornerB == null) return;
        this.primaryPoint = cornerA.snapToGrid(gridSnapSize);
        this.secondaryPoint = cornerB.snapToGrid(gridSnapSize);
    }

    public Optional<BoundingBox3i> bounds() {
        if (primaryPoint == null || secondaryPoint == null) {
            return Optional.empty();
        }
        return Optional.of(BoundingBox3i.fromCorners(primaryPoint, secondaryPoint));
    }

    public boolean hasSelection() {
        return primaryPoint != null && secondaryPoint != null;
    }

    public void clearSelection() {
        if (locked) return;
        primaryPoint = null;
        secondaryPoint = null;
        hoveredFace = null;
    }

    public Direction3D activeFace() {
        return activeFace;
    }

    public void setActiveFace(Direction3D face) {
        if (face != null) {
            this.activeFace = face;
        }
    }

    public Direction3D hoveredFace() {
        return hoveredFace;
    }

    public int gridSnapSize() {
        return gridSnapSize;
    }

    public void setGridSnapSize(int gridSnapSize) {
        this.gridSnapSize = Math.max(1, gridSnapSize);
    }

    public boolean locked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * Raycasts against the 6 face handles and bounding box of the current selection.
     */
    public Optional<Direction3D> updateHoverFromRay(Ray3d ray, double maxDistance) {
        Optional<BoundingBox3i> opt = bounds();
        if (opt.isEmpty()) {
            hoveredFace = null;
            return Optional.empty();
        }
        BoundingBox3i box = opt.get();
        Direction3D bestFace = null;
        double bestT = maxDistance;

        for (Direction3D face : Direction3D.values()) {
            Vec3d hc = box.faceCenter(face);
            Vec3d min = hc.add(-0.35, -0.35, -0.35);
            Vec3d max = hc.add(0.35, 0.35, 0.35);
            Optional<Ray3d.AabbHit> hit = ray.intersectAabb(min, max, bestT);
            if (hit.isPresent()) {
                bestT = hit.get().tNear();
                bestFace = face;
            }
        }

        if (bestFace == null) {
            Vec3d min = new Vec3d(box.minX(), box.minY(), box.minZ());
            Vec3d max = new Vec3d(box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0);
            Optional<Ray3d.AabbHit> boxHit = ray.intersectAabb(min, max, maxDistance);
            if (boxHit.isPresent()) {
                bestFace = boxHit.get().hitFace();
            }
        }

        hoveredFace = bestFace;
        return Optional.ofNullable(bestFace);
    }

    /**
     * Mouse wheel expands (scrollDelta > 0) or shrinks (scrollDelta < 0) the selected/active face
     * along its outward normal axis, respecting gridSnapSize and selection lock.
     */
    public boolean onMouseWheelScroll(int scrollDelta) {
        if (locked || scrollDelta == 0 || !hasSelection()) {
            return false;
        }
        Direction3D targetFace = hoveredFace != null ? hoveredFace : activeFace;
        if (targetFace == null) {
            targetFace = Direction3D.UP;
        }
        int step = (scrollDelta > 0 ? 1 : -1) * gridSnapSize;
        dragFace(targetFace, step);
        return true;
    }

    public void dragFace(Direction3D face, int deltaBlocks) {
        if (locked || face == null || !hasSelection()) return;
        BoundingBox3i resized = bounds().orElseThrow().resizeFace(face, deltaBlocks);
        this.primaryPoint = resized.min();
        this.secondaryPoint = resized.max();
        this.activeFace = face;
    }

    public void moveCorner(int cornerIndex, BlockPos3i newCornerPos) {
        if (locked || !hasSelection() || cornerIndex < 0 || cornerIndex >= 8) return;
        BoundingBox3i box = bounds().orElseThrow();
        List<BlockPos3i> corners = box.eightCorners();
        BlockPos3i opposite = corners.get(7 - cornerIndex);
        this.activeCornerIndex = cornerIndex;
        selectBox(opposite, newCornerPos);
    }

    public void dragEdge(Axis3D edgeAxis, Direction3D faceA, Direction3D faceB, int deltaA, int deltaB) {
        if (locked || !hasSelection()) return;
        if (faceA.axis() != edgeAxis) {
            dragFace(faceA, deltaA);
        }
        if (faceB.axis() != edgeAxis) {
            dragFace(faceB, deltaB);
        }
    }

    public void translate(BlockPos3i offset) {
        if (locked || !hasSelection() || offset == null) return;
        BlockPos3i snapped = offset.snapToGrid(gridSnapSize);
        primaryPoint = primaryPoint.add(snapped);
        secondaryPoint = secondaryPoint.add(snapped);
    }

    public Optional<BoundingBox3i> duplicateSelection(BlockPos3i offset) {
        if (!hasSelection()) return Optional.empty();
        BoundingBox3i dup = bounds().orElseThrow().translate(offset.snapToGrid(gridSnapSize));
        if (!locked) {
            primaryPoint = dup.min();
            secondaryPoint = dup.max();
        }
        return Optional.of(dup);
    }

    public void rotateSelection(Rotation90 rotation, Axis3D axis) {
        if (locked || !hasSelection()) return;
        BoundingBox3i box = bounds().orElseThrow();
        BlockPos3i pivot = box.centerBlockPos();
        BlockPos3i rMin = rotation.transform(box.min(), pivot, axis);
        BlockPos3i rMax = rotation.transform(box.max(), pivot, axis);
        BoundingBox3i next = BoundingBox3i.fromCorners(rMin, rMax);
        primaryPoint = next.min();
        secondaryPoint = next.max();
    }

    public void mirrorSelection(MirrorPlane plane) {
        if (locked || !hasSelection()) return;
        BoundingBox3i box = bounds().orElseThrow();
        BlockPos3i pivot = box.centerBlockPos();
        BlockPos3i mMin = plane.transform(box.min(), pivot);
        BlockPos3i mMax = plane.transform(box.max(), pivot);
        BoundingBox3i next = BoundingBox3i.fromCorners(mMin, mMax);
        primaryPoint = next.min();
        secondaryPoint = next.max();
    }

    public Optional<SelectionVisualDescriptor> buildVisualDescriptor() {
        Optional<BoundingBox3i> opt = bounds();
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        BoundingBox3i b = opt.get();
        double x0 = b.minX();
        double y0 = b.minY();
        double z0 = b.minZ();
        double x1 = b.maxX() + 1.0;
        double y1 = b.maxY() + 1.0;
        double z1 = b.maxZ() + 1.0;

        int wireColor = locked ? 0xFFEF4444 : 0xFF38BDF8;
        List<Line3D> outlines = List.of(
                // Bottom rectangle
                new Line3D(new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0), wireColor),
                new Line3D(new Vec3d(x1, y0, z0), new Vec3d(x1, y0, z1), wireColor),
                new Line3D(new Vec3d(x1, y0, z1), new Vec3d(x0, y0, z1), wireColor),
                new Line3D(new Vec3d(x0, y0, z1), new Vec3d(x0, y0, z0), wireColor),
                // Top rectangle
                new Line3D(new Vec3d(x0, y1, z0), new Vec3d(x1, y1, z0), wireColor),
                new Line3D(new Vec3d(x1, y1, z0), new Vec3d(x1, y1, z1), wireColor),
                new Line3D(new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1), wireColor),
                new Line3D(new Vec3d(x0, y1, z1), new Vec3d(x0, y1, z0), wireColor),
                // Vertical pillars
                new Line3D(new Vec3d(x0, y0, z0), new Vec3d(x0, y1, z0), wireColor),
                new Line3D(new Vec3d(x1, y0, z0), new Vec3d(x1, y1, z0), wireColor),
                new Line3D(new Vec3d(x1, y0, z1), new Vec3d(x1, y1, z1), wireColor),
                new Line3D(new Vec3d(x0, y0, z1), new Vec3d(x0, y1, z1), wireColor)
        );

        Vec3d origin = new Vec3d(x0, y0, z0);
        List<Line3D> axes = List.of(
                new Line3D(origin, origin.add(2.0, 0, 0), 0xFFEF4444), // X Red
                new Line3D(origin, origin.add(0, 2.0, 0), 0xFF22C55E), // Y Green
                new Line3D(origin, origin.add(0, 0, 2.0), 0xFF3B82F6)  // Z Blue
        );

        List<FaceHandleVisual> handles = new ArrayList<>(6);
        for (Direction3D face : Direction3D.values()) {
            boolean isAct = face == activeFace;
            boolean isHov = face == hoveredFace;
            int color = isHov ? 0xFFFACC15 : (isAct ? 0xFFF59E0B : 0xCC94A3B8);
            handles.add(new FaceHandleVisual(face, b.faceCenter(face), isAct, isHov, color));
        }

        List<Vec3d> corners = b.eightCorners().stream()
                .map(BlockPos3i::center)
                .toList();

        List<DimensionLabel> labels = List.of(
                new DimensionLabel("X: " + b.sizeX(), new Vec3d((x0 + x1) * 0.5, y1 + 0.35, z0), Axis3D.X),
                new DimensionLabel("Y: " + b.sizeY(), new Vec3d(x1 + 0.35, (y0 + y1) * 0.5, z0), Axis3D.Y),
                new DimensionLabel("Z: " + b.sizeZ(), new Vec3d(x0, y1 + 0.35, (z0 + z1) * 0.5), Axis3D.Z)
        );

        return Optional.of(new SelectionVisualDescriptor(
                b, outlines, axes, handles, corners, labels, activeFace, hoveredFace
        ));
    }
}
