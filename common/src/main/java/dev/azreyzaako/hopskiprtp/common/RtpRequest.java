package dev.azreyzaako.hopskiprtp.common;

import java.util.UUID;

public record RtpRequest(
    UUID requestId,
    UUID playerId,
    String playerName,
    int warmupSeconds,
    double moveCancelDistanceBlocks
) {
}
