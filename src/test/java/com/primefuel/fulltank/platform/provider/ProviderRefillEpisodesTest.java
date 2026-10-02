package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillEpisodeRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

class ProviderRefillEpisodesTest extends ProviderReadTestSupport {
    @Autowired RefillEpisodeRepository episodes;

    @Test
    void returnsExistingEpisodeContractOnlyForLinkedProviderAndKeepsBuyerRouteProtected()
            throws Exception {
        var f = fixture(false, true);
        var e =
                episodes.save(
                        new RefillEpisode(
                                "provider-episode-" + f.tank(),
                                f.tank(),
                                f.org(),
                                1,
                                15,
                                150,
                                1000,
                                "LITRE",
                                Instant.parse("2026-10-01T10:00:00Z")));
        mvc.perform(
                        get("/api/provider/tanks/{id}/refill-episodes", f.tank())
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(e.getId()))
                .andExpect(jsonPath("$[0].requestedVolume").value(850))
                .andExpect(jsonPath("$[0].openedAt").value("2026-10-01T10:00:00Z"));
        mvc.perform(
                        get("/api/provider/tanks/{id}/refill-episodes", f.tank())
                                .with(provider(f.provider() + 50000)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tanks/{id}/refill-episodes", f.tank()).with(provider(f.provider())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/provider/tanks/{id}/refill-episodes", 0).with(provider(f.provider())))
                .andExpect(status().isBadRequest());
        var empty = fixture(true, false);
        mvc.perform(
                        get("/api/provider/tanks/{id}/refill-episodes", empty.tank())
                                .with(provider(empty.provider())))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
