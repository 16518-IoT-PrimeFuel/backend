package com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl;

import com.primefuel.fulltank.platform.fulfillment.interfaces.acl.FulfillmentContextFacade;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ACL de analytics hacia fulfillment.
 */
@Service
public class ExternalFulfillmentService {

    private final FulfillmentContextFacade fulfillmentContextFacade;

    public ExternalFulfillmentService(FulfillmentContextFacade fulfillmentContextFacade) {
        this.fulfillmentContextFacade = fulfillmentContextFacade;
    }

    public List<String> fetchAllDeliveryStatuses() {
        return fulfillmentContextFacade.fetchAllDeliveryStatuses();
    }
}
