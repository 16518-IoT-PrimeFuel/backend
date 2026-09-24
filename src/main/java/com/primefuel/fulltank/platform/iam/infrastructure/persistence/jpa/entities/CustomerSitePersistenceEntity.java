package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_sites")
@Getter
@Setter
@NoArgsConstructor
public class CustomerSitePersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "customer_account_id", nullable = false)
    private Long customerAccountId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 100)
    private String sector;

    @Column(nullable = false, length = 20)
    private String status;
}
