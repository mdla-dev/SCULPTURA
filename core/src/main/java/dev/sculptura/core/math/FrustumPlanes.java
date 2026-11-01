package dev.sculptura.core.math;

import java.util.List;

/**
 * Six-plane view frustum for culling holographic preview sub-chunks outside the camera view.
 */
public final class FrustumPlanes {
    public record Plane(double nx, double ny, double nz, double d) {
        public Plane normalized() {
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0e-12) {
                return this;
            }
            return new Plane(nx / len, ny / len, nz / len, d / len);
        }

        public double signedDistance(double x, double y, double z) {
            return nx * x + ny * y + nz * z + d;
        }
    }

    private final List<Plane> planes;

    public FrustumPlanes(List<Plane> planes) {
        this.planes = planes.stream().map(Plane::normalized).toList();
    }

    /**
     * Builds an axis-aligned or camera-directed view frustum from camera position, forward vector, FOV, aspect, near, and far distances.
     */
    public static FrustumPlanes fromCamera(
            Vec3d cameraPos,
            Vec3d forward,
            Vec3d up,
            double verticalFovRadians,
            double aspectRatio,
            double nearPlane,
            double farPlane
    ) {
        Vec3d fwd = forward.normalize();
        Vec3d right = fwd.cross(up).normalize();
        Vec3d trueUp = right.cross(fwd).normalize();

        Vec3d nearCenter = cameraPos.add(fwd.scale(nearPlane));
        Vec3d farCenter = cameraPos.add(fwd.scale(farPlane));

        double halfVSide = farPlane * Math.tan(verticalFovRadians * 0.5);
        double halfHSide = halfVSide * aspectRatio;
        Vec3d frontMultFar = fwd.scale(farPlane);

        Vec3d rightNormal = frontMultFar.subtract(right.scale(halfHSide)).cross(trueUp).normalize();
        Vec3d leftNormal = trueUp.cross(frontMultFar.add(right.scale(halfHSide))).normalize();
        Vec3d topNormal = right.cross(frontMultFar.subtract(trueUp.scale(halfVSide))).normalize();
        Vec3d bottomNormal = frontMultFar.add(trueUp.scale(halfVSide)).cross(right).normalize();

        return new FrustumPlanes(List.of(
                planeFromPointNormal(nearCenter, fwd),
                planeFromPointNormal(farCenter, fwd.scale(-1.0)),
                planeFromPointNormal(cameraPos, leftNormal),
                planeFromPointNormal(cameraPos, rightNormal),
                planeFromPointNormal(cameraPos, topNormal),
                planeFromPointNormal(cameraPos, bottomNormal)
        ));
    }

    private static Plane planeFromPointNormal(Vec3d point, Vec3d normal) {
        Vec3d n = normal.normalize();
        return new Plane(n.x(), n.y(), n.z(), -n.dot(point));
    }

    public boolean intersectsAabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (Plane plane : planes) {
            double px = plane.nx() >= 0 ? maxX : minX;
            double py = plane.ny() >= 0 ? maxY : minY;
            double pz = plane.nz() >= 0 ? maxZ : minZ;
            if (plane.signedDistance(px, py, pz) < 0.0) {
                return false;
            }
        }
        return true;
    }
}
