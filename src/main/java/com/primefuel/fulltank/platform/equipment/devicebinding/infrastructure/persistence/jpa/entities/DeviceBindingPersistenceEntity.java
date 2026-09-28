package com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects.BindingStatus;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "device_bindings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_device_bindings_open_channel",
                columnNames = {"device_id", "channel", "active_slot"}))
@Getter
@Setter
@NoArgsConstructor
public class DeviceBindingPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(nullable = false, length = 60)
    private String channel;

    @Column(name = "tank_id", nullable = false)
    private Long tankId;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private BindingStatus status;

    /** 1 while open, null once closed, so the unique index only constrains open bindings. */
    @Column(name = "active_slot")
    private Integer activeSlot;
}
