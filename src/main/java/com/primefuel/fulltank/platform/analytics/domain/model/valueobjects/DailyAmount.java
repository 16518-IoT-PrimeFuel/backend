package com.primefuel.fulltank.platform.analytics.domain.model.valueobjects;

import java.time.LocalDate;

/** Daily UTC date and amount, suitable for daily/weekly/monthly grouping in the client. */
public record DailyAmount(LocalDate date, double amount) {}
