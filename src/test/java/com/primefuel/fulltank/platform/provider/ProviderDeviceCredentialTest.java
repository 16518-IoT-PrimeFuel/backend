package com.primefuel.fulltank.platform.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.equipment.devicebinding.application.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.BindDeviceCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

class ProviderDeviceCredentialTest extends ProviderReadTestSupport {
    @Autowired DeviceBindingCommandService bindingService;
    @Autowired ObjectMapper json;

    @Test
    void ownerManagesTheDeviceTokenAndForeignProvidersAndBuyersCannot() throws Exception {
        var f = fixture(true, false); // an order links the provider to the buyer organization
        String device = "dev-cred-" + f.provider();
        assertThat(bindingService.handle(new BindDeviceCommand(
                f.org(), device, "level", f.tank(), Instant.now().minusSeconds(60))).isSuccess()).isTrue();
        String base = "/api/provider/tanks/" + f.tank() + "/device-credential";

        mvc.perform(post(base).with(auth(f.provider(), "ROLE_BUYER"))).andExpect(status().isForbidden());
        mvc.perform(post(base).with(provider(f.provider() + 50000))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/rotate").with(provider(f.provider() + 50000))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/revoke").with(provider(f.provider() + 50000))).andExpect(status().isNotFound());

        // Emitir: el token en claro aparece una única vez en la respuesta.
        var issued = mvc.perform(post(base).with(provider(f.provider())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tankId").value(f.tank()))
                .andExpect(jsonPath("$.revokedAt").doesNotExist())
                .andReturn();
        String first = json.readTree(issued.getResponse().getContentAsString()).path("token").asString();
        assertThat(first).isNotBlank();
        mvc.perform(post(base).with(provider(f.provider()))).andExpect(status().isConflict());

        // El token emitido autentica la ingesta.
        assertThat(ingest(device, 1L, first)).isEqualTo("ACCEPTED");

        // Rotar: el token anterior deja de autenticar de inmediato; el nuevo funciona.
        var rotated = mvc.perform(post(base + "/rotate").with(provider(f.provider())))
                .andExpect(status().isOk())
                .andReturn();
        String second = json.readTree(rotated.getResponse().getContentAsString()).path("token").asString();
        assertThat(second).isNotBlank().isNotEqualTo(first);
        assertThat(ingest(device, 2L, first)).isEqualTo("QUARANTINED");
        assertThat(ingest(device, 3L, second)).isEqualTo("ACCEPTED");

        // Revocar: el token deja de autenticar; las lecturas quedan en cuarentena (202), no se pierden.
        mvc.perform(post(base + "/revoke").with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.revokedAt").exists());
        assertThat(ingest(device, 4L, second)).isEqualTo("QUARANTINED");
        mvc.perform(post(base + "/revoke").with(provider(f.provider()))).andExpect(status().isNotFound());
    }

    @Test
    void aProviderWithoutAnyLinkToTheTankCannotIssueRotateOrRevoke() throws Exception {
        // No order, request or buyer link ties this provider to the tank's organization.
        var f = fixture(false, false);
        assertThat(bindingService.handle(new BindDeviceCommand(
                f.org(), "dev-nolink-" + f.provider(), "level", f.tank(), Instant.now().minusSeconds(60))).isSuccess())
                .isTrue();
        String base = "/api/provider/tanks/" + f.tank() + "/device-credential";

        mvc.perform(post(base).with(provider(f.provider()))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/rotate").with(provider(f.provider()))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/revoke").with(provider(f.provider()))).andExpect(status().isNotFound());
    }

    private String ingest(String device, long sequence, String token) throws Exception {
        var body = """
                {"schemaVersion":1,"deviceId":"%s","channel":"level","sequence":%d,"capturedAt":"%s","level":10,"unit":"LITRE"}
                """.formatted(device, sequence, Instant.now().plusSeconds(60));
        var response = mvc.perform(post("/api/telemetry/readings")
                        .header("X-Device-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        return json.readTree(response.getResponse().getContentAsString()).path("quality").asString();
    }
}
