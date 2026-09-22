package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerBackfillReport;
import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerBackfillService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.QuarantinedCompanyMapping;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerAccountRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.QuarantinedCompanyMappingRepository;
import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent backfill of legacy buyer companies into CustomerAccount rows. Companies whose RUC does
 * not resolve to exactly one organization are never inferred: they are quarantined for manual review.
 */
@Service
public class CustomerBackfillServiceImpl implements CustomerBackfillService {

    private static final String NO_ORGANIZATION = "NO_ORGANIZATION_FOR_RUC";
    private static final String REGISTRATION_CONFLICT = "REGISTRATION_CONFLICT";

    private final LegacyCompanyDirectory legacyCompanyDirectory;
    private final CustomerAccountRepository customerAccountRepository;
    private final QuarantinedCompanyMappingRepository quarantineRepository;
    private final CustomerCommandService customerCommandService;

    public CustomerBackfillServiceImpl(LegacyCompanyDirectory legacyCompanyDirectory,
                                       CustomerAccountRepository customerAccountRepository,
                                       QuarantinedCompanyMappingRepository quarantineRepository,
                                       CustomerCommandService customerCommandService) {
        this.legacyCompanyDirectory = legacyCompanyDirectory;
        this.customerAccountRepository = customerAccountRepository;
        this.quarantineRepository = quarantineRepository;
        this.customerCommandService = customerCommandService;
    }

    @Override
    @Transactional
    public CustomerBackfillReport run() {
        var companies = legacyCompanyDirectory.findAllBuyerCompanies();
        long newlyMapped = 0;
        long alreadyMapped = 0;
        long newlyQuarantined = 0;
        long alreadyQuarantined = 0;

        for (var company : companies) {
            if (customerAccountRepository.findByLegacyCompanyId(company.id()).isPresent()) {
                alreadyMapped++;
                continue;
            }
            if (quarantineRepository.findByLegacyCompanyId(company.id()).isPresent()) {
                alreadyQuarantined++;
                continue;
            }
            var organizationId = legacyCompanyDirectory.organizationIdForRuc(company.ruc());
            if (organizationId.isEmpty()) {
                quarantine(company.id(), company.ruc(), NO_ORGANIZATION);
                newlyQuarantined++;
                continue;
            }
            var result = customerCommandService.handle(new RegisterCustomerCommand(
                    organizationId.get(), company.name(), company.ruc(), company.address(),
                    company.contactEmail(), company.phone(), company.id()));
            if (result.isSuccess()) {
                newlyMapped++;
            } else {
                quarantine(company.id(), company.ruc(), REGISTRATION_CONFLICT);
                newlyQuarantined++;
            }
        }
        return new CustomerBackfillReport(
                companies.size(), newlyMapped, alreadyMapped, newlyQuarantined, alreadyQuarantined);
    }

    private void quarantine(Long legacyCompanyId, String ruc, String reason) {
        quarantineRepository.save(new QuarantinedCompanyMapping(legacyCompanyId, ruc, reason));
    }
}
