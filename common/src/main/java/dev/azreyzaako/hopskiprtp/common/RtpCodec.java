package dev.azreyzaako.hopskiprtp.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class RtpCodec {

    private static final String ACTION_REQUEST = "request";
    private static final String ACTION_RESPONSE = "response";

    private RtpCodec() {
    }

    public static byte[] encodeRequest(String sharedSecret, RtpRequest request) {
        return encode(sharedSecret, ACTION_REQUEST, out -> {
            out.writeLong(request.requestId().getMostSignificantBits());
            out.writeLong(request.requestId().getLeastSignificantBits());
            out.writeLong(request.playerId().getMostSignificantBits());
            out.writeLong(request.playerId().getLeastSignificantBits());
            out.writeUTF(request.playerName());
            out.writeInt(request.warmupSeconds());
            out.writeDouble(request.moveCancelDistanceBlocks());
        });
    }

    public static Optional<RtpRequest> decodeRequest(String sharedSecret, byte[] payload) {
        return decode(sharedSecret, ACTION_REQUEST, payload, input -> new RtpRequest(
            new UUID(input.readLong(), input.readLong()),
            new UUID(input.readLong(), input.readLong()),
            input.readUTF(),
            input.readInt(),
            input.readDouble()
        ));
    }

    public static byte[] encodeResponse(String sharedSecret, RtpResponse response) {
        return encode(sharedSecret, ACTION_RESPONSE, out -> {
            out.writeLong(response.requestId().getMostSignificantBits());
            out.writeLong(response.requestId().getLeastSignificantBits());
            out.writeLong(response.playerId().getMostSignificantBits());
            out.writeLong(response.playerId().getLeastSignificantBits());
            out.writeBoolean(response.success());
            out.writeUTF(response.message());
        });
    }

    public static Optional<RtpResponse> decodeResponse(String sharedSecret, byte[] payload) {
        return decode(sharedSecret, ACTION_RESPONSE, payload, input -> new RtpResponse(
            new UUID(input.readLong(), input.readLong()),
            new UUID(input.readLong(), input.readLong()),
            input.readBoolean(),
            input.readUTF()
        ));
    }

    private static byte[] encode(String sharedSecret, String action, IoWriter writer) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream dataOutput = new DataOutputStream(output)) {
            dataOutput.writeInt(RtpProtocol.PROTOCOL_VERSION);
            dataOutput.writeUTF(sharedSecret);
            dataOutput.writeUTF(action);
            writer.write(dataOutput);
            dataOutput.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode RTP message.", exception);
        }
    }

    private static <T> Optional<T> decode(String sharedSecret, String action, byte[] payload, IoReader<T> reader) {
        try (DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (dataInput.readInt() != RtpProtocol.PROTOCOL_VERSION) {
                return Optional.empty();
            }
            if (!sharedSecret.equals(dataInput.readUTF())) {
                return Optional.empty();
            }
            if (!action.equals(dataInput.readUTF())) {
                return Optional.empty();
            }
            return Optional.of(reader.read(dataInput));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream output) throws IOException;
    }

    @FunctionalInterface
    private interface IoReader<T> {
        T read(DataInputStream input) throws IOException;
    }
}
