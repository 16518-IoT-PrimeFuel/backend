package com.primefuel.fulltank.platform.payment.infrastructure.persistence.jpa.repositories;

import java.time.LocalDateTime;
import java.util.Date;

public interface ProviderPaymentProjection {
    Long getId();

    Long getOrderId();

    Long getBuyerCompanyId();

    String getBuyerName();

    Double getAmount();

    String getStatus();

    String getPaymentMethod();

    Date getCreatedAt();

    Date getUpdatedAt();

    LocalDateTime getPaidAt();
}
