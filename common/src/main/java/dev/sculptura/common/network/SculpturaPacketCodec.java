package dev.sculptura.common.network;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.math.BlockPos3i;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Version-agnostic binary packet codec for SCULPTURA client-server networking (Section 7).
 * Enforces strict packet size validation (MAX_PACKET_BYTES = 262,144) before decoding.
 */
public final class SculpturaPacketCodec {
    public static final int MAX_PACKET_BYTES = 262_144; // 256 KB hard limit
    public static final int PROTOCOL_VERSION = 1;

    public enum PacketType {
        OPERATION_COMMIT_REQUEST(1),
        UNDO_REDO_REQUEST(2),
        OPERATION_PROGRESS_SYNC(3),
        OPERATION_CANCEL_REQUEST(4);

        private final int id;

        PacketType(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static PacketType fromId(int id) {
            for (PacketType t : values()) {
                if (t.id == id) return t;
            }
            throw new IllegalArgumentException("Unknown SCULPTURA packet type id: " + id);
        }
    }

    public record OperationCommitPacket(
            String requestId,
            String playerUuid,
            String operationId,
            BoundingBox3i bounds,
            BlockStateRef primaryMaterial,
            int parameterRadius,
            long seed
    ) {}

    public record UndoRedoPacket(
            String requestId,
            String playerUuid,
            boolean isRedo,
            boolean forceOverwriteConflicts
    ) {}

    public record ProgressSyncPacket(
            String requestId,
            int processedBlocks,
            int totalBlocks,
            boolean completed,
            String statusMessage
    ) {}

    private SculpturaPacketCodec() {}

    public static byte[] encodeOperationCommit(OperationCommitPacket pkt) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(PROTOCOL_VERSION);
            out.writeByte(PacketType.OPERATION_COMMIT_REQUEST.id());
            out.writeUTF(pkt.requestId());
            out.writeUTF(pkt.playerUuid());
            out.writeUTF(pkt.operationId());
            BoundingBox3i b = pkt.bounds();
            out.writeInt(b.minX());
            out.writeInt(b.minY());
            out.writeInt(b.minZ());
            out.writeInt(b.maxX());
            out.writeInt(b.maxY());
            out.writeInt(b.maxZ());
            out.writeUTF(pkt.primaryMaterial().canonicalString());
            out.writeInt(pkt.parameterRadius());
            out.writeLong(pkt.seed());
            out.flush();
            byte[] bytes = baos.toByteArray();
            validatePacketSize(bytes);
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode OperationCommitPacket", e);
        }
    }

    public static OperationCommitPacket decodeOperationCommit(byte[] payload) {
        validatePacketSize(payload);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported SCULPTURA protocol version: " + version);
            }
            PacketType type = PacketType.fromId(in.readByte());
            if (type != PacketType.OPERATION_COMMIT_REQUEST) {
                throw new IllegalArgumentException("Expected OPERATION_COMMIT_REQUEST but got " + type);
            }
            String reqId = in.readUTF();
            String playerUuid = in.readUTF();
            String opId = in.readUTF();
            BoundingBox3i bounds = new BoundingBox3i(
                    in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt()
            );
            BlockStateRef mat = BlockStateRef.parse(in.readUTF());
            int radius = in.readInt();
            long seed = in.readLong();
            return new OperationCommitPacket(reqId, playerUuid, opId, bounds, mat, radius, seed);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed OperationCommitPacket payload", e);
        }
    }

    public static byte[] encodeUndoRedo(UndoRedoPacket pkt) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(PROTOCOL_VERSION);
            out.writeByte(PacketType.UNDO_REDO_REQUEST.id());
            out.writeUTF(pkt.requestId());
            out.writeUTF(pkt.playerUuid());
            out.writeBoolean(pkt.isRedo());
            out.writeBoolean(pkt.forceOverwriteConflicts());
            out.flush();
            byte[] bytes = baos.toByteArray();
            validatePacketSize(bytes);
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode UndoRedoPacket", e);
        }
    }

    public static UndoRedoPacket decodeUndoRedo(byte[] payload) {
        validatePacketSize(payload);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported protocol version: " + version);
            }
            PacketType type = PacketType.fromId(in.readByte());
            if (type != PacketType.UNDO_REDO_REQUEST) {
                throw new IllegalArgumentException("Expected UNDO_REDO_REQUEST but got " + type);
            }
            return new UndoRedoPacket(
                    in.readUTF(),
                    in.readUTF(),
                    in.readBoolean(),
                    in.readBoolean()
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed UndoRedoPacket payload", e);
        }
    }

    public static void validatePacketSize(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Packet payload cannot be null or empty");
        }
        if (payload.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException(
                    "Packet size " + payload.length + " bytes exceeds maximum allowed " + MAX_PACKET_BYTES + " bytes"
            );
        }
    }
}
