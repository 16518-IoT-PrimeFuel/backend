package com.primefuel.fulltank.platform.equipment.application.internal.queryservices;

import com.primefuel.fulltank.platform.equipment.application.queryservices.TankQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetTankByIdQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetTanksByOrganizationQuery;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TankQueryServiceImpl implements TankQueryService {

    private final TankRepository tankRepository;

    public TankQueryServiceImpl(TankRepository tankRepository) {
        this.tankRepository = tankRepository;
    }

    @Override
    public Optional<Tank> handle(GetTankByIdQuery query) {
        return tankRepository.findById(query.tankId());
    }

    @Override
    public List<Tank> handle(GetTanksByOrganizationQuery query) {
        return tankRepository.findByOrganizationId(query.organizationId());
    }
}
