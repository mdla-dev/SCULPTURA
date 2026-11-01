package dev.sculptura.core.math;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public record Ray3d(Vec3d origin, Vec3d direction) {
    public static final double DEFAULT_SCULPT_RANGE = 120.0;

    public Ray3d {
        direction = direction.normalize();
    }

    public Vec3d pointAt(double t) {
        return origin.add(direction.scale(t));
    }

    public record AabbHit(double tNear, double tFar, Direction3D hitFace, Vec3d hitPoint) {}

    public Optional<AabbHit> intersectAabb(Vec3d min, Vec3d max, double maxDistance) {
        double tMin = 0.0;
        double tMax = maxDistance;
        Direction3D hitFace = Direction3D.UP;

        Axis3D[] axes = Axis3D.values();
        for (Axis3D axis : axes) {
            double o = origin.getComponent(axis);
            double d = direction.getComponent(axis);
            double bMin = min.getComponent(axis);
            double bMax = max.getComponent(axis);

            if (Math.abs(d) < 1.0e-12) {
                if (o < bMin || o > bMax) {
                    return Optional.empty();
                }
            } else {
                double invD = 1.0 / d;
                double t1 = (bMin - o) * invD;
                double t2 = (bMax - o) * invD;
                Direction3D nearFace = Direction3D.fromAxisAndSign(axis, d > 0 ? -1 : 1);

                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                if (t1 > tMin) {
                    tMin = t1;
                    hitFace = nearFace;
                }
                if (t2 < tMax) {
                    tMax = t2;
                }
                if (tMin > tMax) {
                    return Optional.empty();
                }
            }
        }

        if (tMin < 0.0 || tMin > maxDistance) {
            return Optional.empty();
        }
        return Optional.of(new AabbHit(tMin, tMax, hitFace, pointAt(tMin)));
    }

    public Optional<Double> intersectPlane(Vec3d planePoint, Vec3d planeNormal, double maxDistance) {
        double denom = planeNormal.dot(direction);
        if (Math.abs(denom) < 1.0e-9) {
            return Optional.empty();
        }
        double t = planePoint.subtract(origin).dot(planeNormal) / denom;
        if (t < 0.0 || t > maxDistance) {
            return Optional.empty();
        }
        return Optional.of(t);
    }

    /**
     * Computes closest approach between this ray and a 3D line segment (used for gizmo axis handles and edges).
     */
    public record SegmentHit(double rayT, double segmentU, double distance) {}

    public Optional<SegmentHit> intersectSegmentTube(Vec3d segStart, Vec3d segEnd, double tubeRadius, double maxDistance) {
        Vec3d u = direction;
        Vec3d v = segEnd.subtract(segStart);
        Vec3d w = origin.subtract(segStart);

        double a = u.dot(u);
        double b = u.dot(v);
        double c = v.dot(v);
        double d = u.dot(w);
        double e = v.dot(w);
        double denom = a * c - b * b;

        double sN;
        double sD = denom;
        double tN;
        double tD = denom;

        if (denom < 1.0e-9) {
            sN = 0.0;
            sD = 1.0;
            tN = e;
            tD = c;
        } else {
            sN = (b * e - c * d);
            tN = (a * e - b * d);
            if (sN < 0.0) {
                sN = 0.0;
                tN = e;
                tD = c;
            }
        }

        if (tN < 0.0) {
            tN = 0.0;
            if (-d < 0.0) {
                sN = 0.0;
            } else {
                sN = -d;
                sD = a;
            }
        } else if (tN > tD) {
            tN = tD;
            if ((-d + b) < 0.0) {
                sN = 0.0;
            } else {
                sN = -d + b;
                sD = a;
            }
        }

        double sc = Math.abs(sN) < 1.0e-9 ? 0.0 : sN / sD;
        double tc = Math.abs(tN) < 1.0e-9 ? 0.0 : tN / tD;

        if (sc < 0.0 || sc > maxDistance) {
            return Optional.empty();
        }

        Vec3d dP = w.add(u.scale(sc)).subtract(v.scale(tc));
        double dist = dP.length();
        if (dist <= tubeRadius) {
            return Optional.of(new SegmentHit(sc, tc, dist));
        }
        return Optional.empty();
    }

    /**
     * Intersects a circular rotation ring lying in the plane perpendicular to ringAxis centered at pivot.
     */
    public Optional<Double> intersectRotationRing(Vec3d pivot, Axis3D ringAxis, double ringRadius, double ringThickness, double maxDistance) {
        Vec3d normal = ringAxis.unitVector();
        Optional<Double> planeHit = intersectPlane(pivot, normal, maxDistance);
        if (planeHit.isEmpty()) {
            return Optional.empty();
        }
        double t = planeHit.get();
        Vec3d hitPoint = pointAt(t);
        double radialDist = hitPoint.subtract(pivot).length();
        if (Math.abs(radialDist - ringRadius) <= ringThickness) {
            return Optional.of(t);
        }
        return Optional.empty();
    }

    public record VoxelRaycastHit(BlockPos3i blockPos, Direction3D hitFace, Vec3d hitPosition, double distance) {}

    /**
     * Exact Amanatides & Woo 3D DDA voxel traversal up to maxRange (default 120 blocks).
     */
    public Optional<VoxelRaycastHit> raycastVoxels(double maxRange, Predicate<BlockPos3i> isSolidVoxel) {
        double ox = origin.x();
        double oy = origin.y();
        double oz = origin.z();
        double dx = direction.x();
        double dy = direction.y();
        double dz = direction.z();

        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);

        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);

        double tMaxX = stepX == 0 ? Double.MAX_VALUE : ((stepX > 0 ? (x + 1.0 - ox) : (ox - x)) * tDeltaX);
        double tMaxY = stepY == 0 ? Double.MAX_VALUE : ((stepY > 0 ? (y + 1.0 - oy) : (oy - y)) * tDeltaY);
        double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? (z + 1.0 - oz) : (oz - z)) * tDeltaZ);

        double t = 0.0;
        Direction3D lastFace = Direction3D.UP;

        while (t <= maxRange) {
            BlockPos3i current = new BlockPos3i(x, y, z);
            if (isSolidVoxel.test(current)) {
                return Optional.of(new VoxelRaycastHit(current, lastFace, pointAt(t), t));
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    t = tMaxX;
                    tMaxX += tDeltaX;
                    lastFace = stepX > 0 ? Direction3D.WEST : Direction3D.EAST;
                } else {
                    z += stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    lastFace = stepZ > 0 ? Direction3D.NORTH : Direction3D.SOUTH;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    t = tMaxY;
                    tMaxY += tDeltaY;
                    lastFace = stepY > 0 ? Direction3D.DOWN : Direction3D.UP;
                } else {
                    z += stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    lastFace = stepZ > 0 ? Direction3D.NORTH : Direction3D.SOUTH;
                }
            }
        }
        return Optional.empty();
    }

    public List<BlockPos3i> collectTraversedVoxels(double maxRange) {
        List<BlockPos3i> visited = new ArrayList<>();
        raycastVoxels(maxRange, pos -> {
            visited.add(pos);
            return false;
        });
        return visited;
    }
}
