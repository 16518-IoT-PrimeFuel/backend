package com.primefuel.fulltank.platform.analytics.domain.model.queries;

import java.time.LocalDate;

public record GetProviderAnalyticsQuery(Long providerId, LocalDate from, LocalDate to) {
    public GetProviderAnalyticsQuery(Long providerId) {
        this(providerId, null, null);
    }
}
