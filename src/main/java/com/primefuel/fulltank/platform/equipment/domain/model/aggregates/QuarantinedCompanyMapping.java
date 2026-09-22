package com.primefuel.fulltank.platform.equipment.domain.model.aggregates;

import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QuarantinedCompanyMapping extends AbstractDomainAggregateRoot<QuarantinedCompanyMapping> {

    private Long id;
    private Long legacyCompanyId;
    private String ruc;
    private String reason;

    public QuarantinedCompanyMapping(Long legacyCompanyId, String ruc, String reason) {
        this.legacyCompanyId = legacyCompanyId;
        this.ruc = ruc;
        this.reason = reason;
    }
}
