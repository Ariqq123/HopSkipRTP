package dev.azreyzaako.hopskiprtp.common;

import java.util.UUID;

public record RtpResponse(
    UUID requestId,
    UUID playerId,
    boolean success,
    String message
) {
}
