package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.ports.TankStore;
import com.primefuel.fulltank.platform.iam.application.internal.commandservices.CustomerSiteCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TankCommandService {
    private final CustomerSiteCommandService sites;
    private final TankStore tanks;

    public TankCommandService(CustomerSiteCommandService sites, TankStore tanks) {
        this.sites = sites;
        this.tanks = tanks;
    }

    @Transactional
    public Long create(Long companyId, Long siteId, String name, String fuelType,
                       Double capacity, String unit, Double currentLevel) {
        if (!sites.siteBelongsToCompany(siteId, companyId)) {
            throw new IllegalArgumentException("Customer site does not belong to buyer company");
        }
        if (capacity <= 0 || currentLevel < 0 || currentLevel > capacity) {
            throw new IllegalArgumentException("Tank level must be within capacity");
        }
        return tanks.create(siteId, name, fuelType, capacity, unit, currentLevel);
    }
}
