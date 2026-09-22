package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "customer_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_accounts_legacy_company", columnNames = "legacy_company_id"))
@Getter
@Setter
@NoArgsConstructor
public class CustomerAccountPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 11)
    private String ruc;

    private String address;

    @Column(name = "contact_email")
    private String contactEmail;

    private String phone;

    @Column(name = "legacy_company_id")
    private Long legacyCompanyId;

    @Column(nullable = false)
    private boolean active;
}
