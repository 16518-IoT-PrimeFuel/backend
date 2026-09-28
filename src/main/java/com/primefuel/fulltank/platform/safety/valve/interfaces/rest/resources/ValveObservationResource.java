package com.primefuel.fulltank.platform.safety.valve.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ValveObservationResource(@NotBlank String state, @NotNull Instant observedAt, String commandId) { }
