package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "organizations",
        uniqueConstraints = @UniqueConstraint(name = "uk_organizations_ruc", columnNames = "ruc"))
@Getter
@Setter
@NoArgsConstructor
public class OrganizationPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 11)
    private String ruc;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private OrganizationType type;

    @Column(nullable = false)
    private boolean active;
}
