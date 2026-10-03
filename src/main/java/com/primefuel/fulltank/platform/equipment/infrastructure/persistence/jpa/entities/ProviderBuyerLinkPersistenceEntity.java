package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "provider_buyer_links",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_provider_buyer_link",
                        columnNames = {"provider_id", "buyer_company_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProviderBuyerLinkPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "buyer_company_id", nullable = false)
    private Long buyerCompanyId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
}
