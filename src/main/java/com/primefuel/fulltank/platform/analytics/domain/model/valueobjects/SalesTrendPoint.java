package com.primefuel.fulltank.platform.analytics.domain.model.valueobjects;

import java.time.LocalDate;

/** Daily UTC date and litres sold, suitable for daily/weekly/monthly grouping in the client. */
public record SalesTrendPoint(LocalDate date, double litres) {}
