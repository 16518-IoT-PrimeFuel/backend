package com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "geofence_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_geofence_policies_delivery_version",
                columnNames = {"delivery_id", "policy_version"}))
@Getter
@Setter
@NoArgsConstructor
public class GeofencePolicyPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "center_latitude", nullable = false)
    private double centerLatitude;

    @Column(name = "center_longitude", nullable = false)
    private double centerLongitude;

    @Column(name = "radius_meters", nullable = false)
    private double radiusMeters;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;
}
