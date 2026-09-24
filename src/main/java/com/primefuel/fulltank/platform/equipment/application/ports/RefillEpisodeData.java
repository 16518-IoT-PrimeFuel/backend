package com.primefuel.fulltank.platform.equipment.application.ports;

import java.time.Instant;

public record RefillEpisodeData(String episodeKey, Long tankId, Instant startedAt, Instant closedAt,
                                String status, Long requestId) {
}
