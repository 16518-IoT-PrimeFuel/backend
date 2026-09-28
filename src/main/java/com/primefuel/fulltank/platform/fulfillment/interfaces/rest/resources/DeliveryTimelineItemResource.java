package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import java.time.Instant;

public record DeliveryTimelineItemResource(Instant occurredAt, String type, String summary, String refId) { }
