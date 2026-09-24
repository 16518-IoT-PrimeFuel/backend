package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_accounts")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAccountPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "legacy_buyer_company_id", unique = true)
    private Long legacyBuyerCompanyId;

    @Column(nullable = false, length = 20)
    private String status;
}
