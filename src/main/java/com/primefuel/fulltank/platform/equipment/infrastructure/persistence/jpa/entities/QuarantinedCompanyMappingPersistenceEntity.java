package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "customer_mapping_quarantine",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_mapping_quarantine_legacy_company", columnNames = "legacy_company_id"))
@Getter
@Setter
@NoArgsConstructor
public class QuarantinedCompanyMappingPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "legacy_company_id", nullable = false)
    private Long legacyCompanyId;

    @Column(length = 11)
    private String ruc;

    @Column(nullable = false)
    private String reason;
}
