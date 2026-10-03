package com.primefuel.fulltank.platform.analytics.application.queryservices;

import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetBuyerAnalyticsQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetPlatformSummaryQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetProviderAnalyticsQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.BuyerAnalytics;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.PlatformSummary;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.ProviderAnalytics;

public interface AnalyticsQueryService {
    ProviderAnalytics handle(GetProviderAnalyticsQuery query);
    BuyerAnalytics handle(GetBuyerAnalyticsQuery query);
    PlatformSummary handle(GetPlatformSummaryQuery query);
}
