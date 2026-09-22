package com.primefuel.fulltank.platform.iam.application.internal.queryservices;

import com.primefuel.fulltank.platform.iam.application.queryservices.InvitationQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetInvitationByIdQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetInvitationByTokenQuery;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationInvitationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InvitationQueryServiceImpl implements InvitationQueryService {

    private final OrganizationInvitationRepository invitationRepository;

    public InvitationQueryServiceImpl(OrganizationInvitationRepository invitationRepository) {
        this.invitationRepository = invitationRepository;
    }

    @Override
    public Optional<OrganizationInvitation> handle(GetInvitationByTokenQuery query) {
        return invitationRepository.findByToken(query.token());
    }

    @Override
    public Optional<OrganizationInvitation> handle(GetInvitationByIdQuery query) {
        return invitationRepository.findById(query.invitationId());
    }
}
