package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.ports.JournalQueryPort;
import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v2/deliveries/{deliveryId}/timeline", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeliveryTimelineController {
    private final DeliveryQueryService deliveries;
    private final TenantAccess access;
    private final JournalQueryPort journal;

    public DeliveryTimelineController(DeliveryQueryService deliveries, TenantAccess access, JournalQueryPort journal) {
        this.deliveries = deliveries;
        this.access = access;
        this.journal = journal;
    }

    @GetMapping
    public ResponseEntity<?> timeline(@PathVariable Long deliveryId) {
        var delivery = deliveries.handle(new GetDeliveryByIdQuery(deliveryId));
        if (delivery.isEmpty() || !access.ownsProvider(delivery.get().getProviderId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(journal.findByDeliveryId(deliveryId));
    }
}
