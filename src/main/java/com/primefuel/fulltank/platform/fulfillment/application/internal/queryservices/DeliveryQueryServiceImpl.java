package com.primefuel.fulltank.platform.fulfillment.application.internal.queryservices;

import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetAllDeliveriesQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveriesByProviderQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class DeliveryQueryServiceImpl implements DeliveryQueryService {

    private final DeliveryRepository deliveryRepository;

    public DeliveryQueryServiceImpl(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Override
    public List<Delivery> handle(GetDeliveriesByProviderQuery query) {
        return deliveryRepository.findByProviderId(query.providerId()).stream()
                .filter(d -> query.providerId().equals(d.getProviderId()))
                .filter(
                        d ->
                                query.date() == null
                                        || query.date().toString().equals(d.getScheduledDate()))
                .sorted(Comparator.comparing(Delivery::getId))
                .toList();
    }

    @Override
    public Optional<Delivery> handle(GetDeliveryByIdQuery query) {
        return deliveryRepository.findById(query.deliveryId());
    }

    @Override
    public List<Delivery> handle(GetAllDeliveriesQuery query) {
        return deliveryRepository.findAll();
    }
}
