package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "legacy_buyer_company_id", unique = true)
    private Long legacyBuyerCompanyId;

    @Column(name = "legacy_provider_company_id", unique = true)
    private Long legacyProviderCompanyId;

    @Column(nullable = false, length = 20)
    private String status;
}
