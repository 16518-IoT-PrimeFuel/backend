package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.domain.repositories.EquipmentRepository;
import com.primefuel.fulltank.platform.fulfillment.application.ports.DeliveryEquipmentPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeliveryEquipmentPortAdapter implements DeliveryEquipmentPort {
    private final EquipmentRepository equipment;

    public DeliveryEquipmentPortAdapter(EquipmentRepository equipment) {
        this.equipment = equipment;
    }

    @Override
    @Transactional
    public boolean receiveFuel(Long equipmentId, Double quantity) {
        var existing = equipment.findById(equipmentId);
        if (existing.isEmpty()) return false;
        existing.get().receiveFuel(quantity);
        equipment.save(existing.get());
        return true;
    }
}
