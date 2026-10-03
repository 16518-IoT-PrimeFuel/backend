package com.primefuel.fulltank.platform.fulfillment.application.internal.queryservices;

import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryValveObservationsQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryValveObservation;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryBusinessJournalRepository;
import com.primefuel.fulltank.platform.shared.application.result.*;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeliveryValveObservationQueryService {
    private final DeliveryRepository deliveries;
    private final DeliveryBusinessJournalRepository journal;

    public DeliveryValveObservationQueryService(
            DeliveryRepository deliveries, DeliveryBusinessJournalRepository journal) {
        this.deliveries = deliveries;
        this.journal = journal;
    }

    public Result<List<DeliveryValveObservation>, ApplicationError> handle(
            GetDeliveryValveObservationsQuery q) {
        if (deliveries
                .findById(q.deliveryId())
                .filter(d -> q.providerId().equals(d.getProviderId()))
                .isEmpty())
            return Result.failure(
                    ApplicationError.notFound("Delivery", String.valueOf(q.deliveryId())));
        return Result.success(
                journal.findByDeliveryIdOrderByOccurredAtAsc(q.deliveryId()).stream()
                        .filter(e -> q.providerId().equals(e.getProviderId()))
                        .filter(
                                e ->
                                        "VALVE_OBSERVATION".equals(e.getType())
                                                || "SAFETY_INCIDENT".equals(e.getType())
                                                        && "SPONTANEOUS_OPEN"
                                                                .equals(e.getSummary()))
                        .map(
                                e ->
                                        new DeliveryValveObservation(
                                                e.getId(),
                                                "VALVE_OBSERVATION".equals(e.getType())
                                                        ? e.getSummary()
                                                        : "OPEN",
                                                "SAFETY_INCIDENT".equals(e.getType()),
                                                e.getRefId().isBlank() ? null : e.getRefId(),
                                                e.getOccurredAt()))
                        .toList());
    }
}
