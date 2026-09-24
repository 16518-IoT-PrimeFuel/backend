package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.application.ports.ValveCommandStore;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.ValveCommandPersistenceEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.ValveCommandJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ValveCommandStoreAdapter implements ValveCommandStore {
    private final ValveCommandJpaRepository repository;

    public ValveCommandStoreAdapter(ValveCommandJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String commandId) {
        return repository.existsByCommandId(commandId);
    }

    @Override
    public void create(String commandId, Long deliveryId, String desiredState, Instant issuedAt) {
        var command = new ValveCommandPersistenceEntity();
        command.setCommandId(commandId);
        command.setDeliveryId(deliveryId);
        command.setDesiredState(desiredState);
        command.setStatus("PENDING");
        command.setIssuedAt(issuedAt);
        repository.save(command);
    }

    @Override
    public boolean acknowledge(String commandId, String ackId, Instant acknowledgedAt) {
        var command = repository.findByCommandId(commandId).orElse(null);
        if (command == null) return false;
        if ("ACKED".equals(command.getStatus())) return true;
        command.setAckId(ackId);
        command.setAcknowledgedAt(acknowledgedAt);
        command.setStatus("ACKED");
        repository.save(command);
        return true;
    }
}
