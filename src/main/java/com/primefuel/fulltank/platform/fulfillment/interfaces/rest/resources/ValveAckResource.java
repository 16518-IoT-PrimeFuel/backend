package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record ValveAckResource(@NotBlank String ackId) {
}
