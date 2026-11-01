package dev.sculptura.common.gizmo;

import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.MirrorPlane;
import dev.sculptura.core.math.Ray3d;
import dev.sculptura.core.math.Vec3d;

import java.util.Optional;

/**
 * 3D Transformation Gizmo (Section 5.B) supporting:
 * - Translate, Scale, Rotate modes
 * - Axis handles (X, Y, Z), Plane handles (XY, XZ, YZ), Rotation rings (X, Y, Z)
 * - Pivot editing, World & Local coordinate spaces, Grid & Angle snapping, Hover highlighting
 * - Accurate 3D Ray intersection testing
 */
public final class TransformGizmoController {
    public enum GizmoMode {
        TRANSLATE,
        SCALE,
        ROTATE
    }

    public enum CoordinateSpace {
        WORLD,
        LOCAL
    }

    public enum GizmoHandle {
        NONE,
        AXIS_X,
        AXIS_Y,
        AXIS_Z,
        PLANE_XY,
        PLANE_XZ,
        PLANE_YZ,
        RING_X,
        RING_Y,
        RING_Z,
        PIVOT_CENTER
    }

    public record GizmoIntersection(GizmoHandle handle, double distance, Vec3d hitPoint) {}

    private GizmoMode mode = GizmoMode.TRANSLATE;
    private CoordinateSpace coordinateSpace = CoordinateSpace.WORLD;
    private Vec3d pivot = Vec3d.ZERO;
    private Vec3d localEulerRadians = Vec3d.ZERO;
    private double handleScale = 3.0;
    private int gridSnapStep = 1;
    private double rotationSnapDegrees = 90.0;
    private GizmoHandle hoveredHandle = GizmoHandle.NONE;
    private GizmoHandle activeHandle = GizmoHandle.NONE;

    public GizmoMode mode() {
        return mode;
    }

    public void setMode(GizmoMode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }

    public CoordinateSpace coordinateSpace() {
        return coordinateSpace;
    }

    public void setCoordinateSpace(CoordinateSpace space) {
        if (space != null) {
            this.coordinateSpace = space;
        }
    }

    public Vec3d pivot() {
        return pivot;
    }

    public void setPivot(Vec3d pivot) {
        if (pivot != null) {
            this.pivot = pivot;
        }
    }

    public void setPivotFromBlock(BlockPos3i pos) {
        if (pos != null) {
            this.pivot = pos.center();
        }
    }

    public double handleScale() {
        return handleScale;
    }

    public void setHandleScale(double handleScale) {
        this.handleScale = Math.max(1.0, Math.min(16.0, handleScale));
    }

    public int gridSnapStep() {
        return gridSnapStep;
    }

    public void setGridSnapStep(int gridSnapStep) {
        this.gridSnapStep = Math.max(1, gridSnapStep);
    }

    public double rotationSnapDegrees() {
        return rotationSnapDegrees;
    }

    public void setRotationSnapDegrees(double rotationSnapDegrees) {
        this.rotationSnapDegrees = Math.max(1.0, rotationSnapDegrees);
    }

    public GizmoHandle hoveredHandle() {
        return hoveredHandle;
    }

    public GizmoHandle activeHandle() {
        return activeHandle;
    }

    public void beginDrag(GizmoHandle handle) {
        this.activeHandle = handle == null ? GizmoHandle.NONE : handle;
    }

    public void endDrag() {
        this.activeHandle = GizmoHandle.NONE;
    }

    public Vec3d axisDirection(Axis3D axis) {
        Vec3d base = axis.unitVector();
        if (coordinateSpace == CoordinateSpace.WORLD) {
            return base;
        }
        return base
                .rotateAroundAxis(Axis3D.X, localEulerRadians.x())
                .rotateAroundAxis(Axis3D.Y, localEulerRadians.y())
                .rotateAroundAxis(Axis3D.Z, localEulerRadians.z())
                .normalize();
    }

    /**
     * Performs accurate 3D ray intersection against axis tubes, plane quads, and rotation rings.
     */
    public Optional<GizmoIntersection> intersectRay(Ray3d ray, double maxDistance) {
        GizmoHandle bestHandle = GizmoHandle.NONE;
        double bestT = maxDistance;
        double tubeRadius = 0.25;

        // 1. Check center pivot sphere/box
        Optional<Ray3d.AabbHit> pivotHit = ray.intersectAabb(
                pivot.add(-0.3, -0.3, -0.3),
                pivot.add(0.3, 0.3, 0.3),
                bestT
        );
        if (pivotHit.isPresent()) {
            bestT = pivotHit.get().tNear();
            bestHandle = GizmoHandle.PIVOT_CENTER;
        }

        // 2. Check rotation rings when in ROTATE mode (or always checkable after axis handles)
        if (mode == GizmoMode.ROTATE) {
            double ringR = handleScale * 0.85;
            double ringThickness = 0.28;
            Optional<Double> rx = ray.intersectRotationRing(pivot, Axis3D.X, ringR, ringThickness, bestT);
            if (rx.isPresent()) {
                bestT = rx.get();
                bestHandle = GizmoHandle.RING_X;
            }
            Optional<Double> ry = ray.intersectRotationRing(pivot, Axis3D.Y, ringR, ringThickness, bestT);
            if (ry.isPresent()) {
                bestT = ry.get();
                bestHandle = GizmoHandle.RING_Y;
            }
            Optional<Double> rz = ray.intersectRotationRing(pivot, Axis3D.Z, ringR, ringThickness, bestT);
            if (rz.isPresent()) {
                bestT = rz.get();
                bestHandle = GizmoHandle.RING_Z;
            }
        } else {
            // 3. Axis handles for TRANSLATE and SCALE
            Optional<Ray3d.SegmentHit> hx = ray.intersectSegmentTube(
                    pivot, pivot.add(axisDirection(Axis3D.X).scale(handleScale)), tubeRadius, bestT
            );
            if (hx.isPresent()) {
                bestT = hx.get().rayT();
                bestHandle = GizmoHandle.AXIS_X;
            }
            Optional<Ray3d.SegmentHit> hy = ray.intersectSegmentTube(
                    pivot, pivot.add(axisDirection(Axis3D.Y).scale(handleScale)), tubeRadius, bestT
            );
            if (hy.isPresent()) {
                bestT = hy.get().rayT();
                bestHandle = GizmoHandle.AXIS_Y;
            }
            Optional<Ray3d.SegmentHit> hz = ray.intersectSegmentTube(
                    pivot, pivot.add(axisDirection(Axis3D.Z).scale(handleScale)), tubeRadius, bestT
            );
            if (hz.isPresent()) {
                bestT = hz.get().rayT();
                bestHandle = GizmoHandle.AXIS_Z;
            }

            // 4. Plane handles (XY, XZ, YZ quads near pivot)
            double pMin = handleScale * 0.25;
            double pMax = handleScale * 0.55;
            Optional<Ray3d.AabbHit> pXY = ray.intersectAabb(
                    pivot.add(pMin, pMin, -0.1), pivot.add(pMax, pMax, 0.1), bestT
            );
            if (pXY.isPresent()) {
                bestT = pXY.get().tNear();
                bestHandle = GizmoHandle.PLANE_XY;
            }
            Optional<Ray3d.AabbHit> pXZ = ray.intersectAabb(
                    pivot.add(pMin, -0.1, pMin), pivot.add(pMax, 0.1, pMax), bestT
            );
            if (pXZ.isPresent()) {
                bestT = pXZ.get().tNear();
                bestHandle = GizmoHandle.PLANE_XZ;
            }
            Optional<Ray3d.AabbHit> pYZ = ray.intersectAabb(
                    pivot.add(-0.1, pMin, pMin), pivot.add(0.1, pMax, pMax), bestT
            );
            if (pYZ.isPresent()) {
                bestT = pYZ.get().tNear();
                bestHandle = GizmoHandle.PLANE_YZ;
            }
        }

        this.hoveredHandle = bestHandle;
        if (bestHandle == GizmoHandle.NONE) {
            return Optional.empty();
        }
        return Optional.of(new GizmoIntersection(bestHandle, bestT, ray.pointAt(bestT)));
    }

    /**
     * Computes grid-snapped integer translation delta from a raw drag vector along the active handle.
     */
    public BlockPos3i computeSnappedTranslation(Vec3d rawDragDelta) {
        double dx = 0.0, dy = 0.0, dz = 0.0;
        switch (activeHandle) {
            case AXIS_X -> dx = rawDragDelta.x();
            case AXIS_Y -> dy = rawDragDelta.y();
            case AXIS_Z -> dz = rawDragDelta.z();
            case PLANE_XY -> { dx = rawDragDelta.x(); dy = rawDragDelta.y(); }
            case PLANE_XZ -> { dx = rawDragDelta.x(); dz = rawDragDelta.z(); }
            case PLANE_YZ -> { dy = rawDragDelta.y(); dz = rawDragDelta.z(); }
            case PIVOT_CENTER -> { dx = rawDragDelta.x(); dy = rawDragDelta.y(); dz = rawDragDelta.z(); }
            default -> {}
        }
        int ix = (int) Math.round(dx / gridSnapStep) * gridSnapStep;
        int iy = (int) Math.round(dy / gridSnapStep) * gridSnapStep;
        int iz = (int) Math.round(dz / gridSnapStep) * gridSnapStep;
        return new BlockPos3i(ix, iy, iz);
    }

    public double snapRotationAngleDegrees(double rawAngleDegrees) {
        return Math.round(rawAngleDegrees / rotationSnapDegrees) * rotationSnapDegrees;
    }

    public MirrorPlane mirrorPlaneForHandle(GizmoHandle handle) {
        return switch (handle) {
            case PLANE_XY -> MirrorPlane.XY;
            case PLANE_XZ -> MirrorPlane.XZ;
            case PLANE_YZ -> MirrorPlane.YZ;
            default -> MirrorPlane.NONE;
        };
    }
}
