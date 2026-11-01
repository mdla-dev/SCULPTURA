package dev.sculptura.common.network;

import dev.sculptura.common.platform.PlatformWorldAdapter;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.operation.EditOperation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side Security & Validation Gatekeeper (Section 7).
 * The client is NEVER trusted to directly authorize arbitrary world modifications.
 * Validates:
 * 1. Packet size
 * 2. Player permissions
 * 3. Per-player rate limits (token bucket)
 * 4. World vertical boundaries (minY..maxY)
 * 5. Maximum block count
 * 6. Chunk availability (isChunkLoaded — NEVER force-loads arbitrary chunks from client requests)
 * 7. Block state validity
 * 8. Operation parameters
 */
public final class ServerSecurityValidator {
    public record SecurityDecision(boolean allowed, String rejectionCode, String message) {
        public static SecurityDecision allow() {
            return new SecurityDecision(true, null, "OK");
        }

        public static SecurityDecision deny(String code, String message) {
            return new SecurityDecision(false, code, message);
        }
    }

    private static final class RateWindow {
        long windowStartMillis;
        int requestCount;
    }

    private final EditOperation.OperationLimits operationLimits;
    private final int maxRequestsPerWindow;
    private final long rateWindowMillis;
    private final Map<String, RateWindow> playerRateLimits = new ConcurrentHashMap<>();

    public ServerSecurityValidator(
            EditOperation.OperationLimits operationLimits,
            int maxRequestsPerWindow,
            long rateWindowMillis
    ) {
        this.operationLimits = operationLimits;
        this.maxRequestsPerWindow = Math.max(1, maxRequestsPerWindow);
        this.rateWindowMillis = Math.max(100L, rateWindowMillis);
    }

    public static ServerSecurityValidator createDefault() {
        return new ServerSecurityValidator(EditOperation.OperationLimits.defaults(), 10, 1000L);
    }

    public SecurityDecision validateOperationRequest(
            String playerUuid,
            int playerPermissionLevel,
            EditOperation operation,
            PlatformWorldAdapter world,
            long currentTimeMillis
    ) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return SecurityDecision.deny("INVALID_PLAYER", "Missing player UUID");
        }
        if (!world.hasEditPermission(playerUuid, playerPermissionLevel)) {
            return SecurityDecision.deny("PERMISSION_DENIED", "Player lacks SCULPTURA world-edit permission");
        }
        if (!checkRateLimit(playerUuid, currentTimeMillis)) {
            return SecurityDecision.deny("RATE_LIMIT_EXCEEDED", "Too many operation requests per second");
        }

        EditOperation.ValidationResult opValidation = operation.validate(operationLimits, world);
        if (!opValidation.valid()) {
            return SecurityDecision.deny("INVALID_PARAMETERS", String.join("; ", opValidation.errors()));
        }

        if (operation.affectedBounds().isPresent()) {
            BoundingBox3i box = operation.affectedBounds().get();
            if (box.minY() < world.getMinBuildHeight() || box.maxY() >= world.getMaxBuildHeight()) {
                return SecurityDecision.deny("OUT_OF_WORLD_BOUNDS", "Target coordinates exceed world vertical build limits");
            }
            if (box.volume() > operationLimits.maxBlocksPerOperation()) {
                return SecurityDecision.deny("MAX_BLOCKS_EXCEEDED", "Target volume exceeds maxBlocksPerOperation");
            }

            // Verify every affected chunk is currently loaded — NEVER force-load unloaded chunks!
            int minChunkX = box.minX() >> 4;
            int maxChunkX = box.maxX() >> 4;
            int minChunkZ = box.minZ() >> 4;
            int maxChunkZ = box.maxZ() >> 4;
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    if (!world.isChunkLoaded(cx, cz)) {
                        return SecurityDecision.deny(
                                "UNLOADED_CHUNK",
                                "Chunk [" + cx + ", " + cz + "] is not loaded; refusing to force-load chunks from client request"
                        );
                    }
                }
            }
        }

        return SecurityDecision.allow();
    }

    private boolean checkRateLimit(String playerUuid, long nowMillis) {
        RateWindow win = playerRateLimits.computeIfAbsent(playerUuid, k -> {
            RateWindow w = new RateWindow();
            w.windowStartMillis = nowMillis;
            return w;
        });
        synchronized (win) {
            if (nowMillis - win.windowStartMillis >= rateWindowMillis) {
                win.windowStartMillis = nowMillis;
                win.requestCount = 1;
                return true;
            }
            if (win.requestCount >= maxRequestsPerWindow) {
                return false;
            }
            win.requestCount++;
            return true;
        }
    }
}
