package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_sites")
@Getter
@Setter
@NoArgsConstructor
public class CustomerSitePersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "customer_account_id", nullable = false)
    private Long customerAccountId;

    @Column(nullable = false, length = 150)
    private String name;

    private String address;

    @Column(nullable = false)
    private boolean active;
}
