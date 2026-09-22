package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "memberships",
        uniqueConstraints = @UniqueConstraint(name = "uk_memberships_organization_user", columnNames = {"organization_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MembershipPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private MembershipRole role;

    @Column(nullable = false)
    private boolean active;
}
