package dev.azreyzaako.hopskiprtp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RtpCodecTest {

    @Test
    void roundTripsRequestAndResponse() {
        UUID requestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        RtpRequest request = new RtpRequest(requestId, playerId, "Ariqq123", 7, 0.35);
        byte[] encodedRequest = RtpCodec.encodeRequest("secret", request);

        assertTrue(RtpCodec.decodeRequest("secret", encodedRequest).isPresent());
        assertEquals(request, RtpCodec.decodeRequest("secret", encodedRequest).orElseThrow());

        RtpResponse response = new RtpResponse(requestId, playerId, true, "ok");
        byte[] encodedResponse = RtpCodec.encodeResponse("secret", response);

        assertTrue(RtpCodec.decodeResponse("secret", encodedResponse).isPresent());
        assertEquals(response, RtpCodec.decodeResponse("secret", encodedResponse).orElseThrow());
    }

    @Test
    void rejectsWrongSecretOrVersion() {
        RtpRequest request = new RtpRequest(UUID.randomUUID(), UUID.randomUUID(), "player", 1, 0.2);
        byte[] encoded = RtpCodec.encodeRequest("secret", request);
        byte[] corrupted = encoded.clone();
        ByteBuffer.wrap(corrupted).putInt(0, RtpProtocol.PROTOCOL_VERSION + 1);

        assertFalse(RtpCodec.decodeRequest("wrong", encoded).isPresent());
        assertFalse(RtpCodec.decodeRequest("secret", corrupted).isPresent());
    }
}
