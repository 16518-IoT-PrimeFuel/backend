package com.primefuel.fulltank.platform.iam.application.internal.commandservices;

import com.primefuel.fulltank.platform.iam.application.commandservices.InvitationCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.MembershipCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.domain.model.commands.AcceptInvitationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.InviteMemberCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.RevokeInvitationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.InvitationStatus;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationInvitationRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class InvitationCommandServiceImpl implements InvitationCommandService {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final OrganizationInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipCommandService membershipCommandService;

    public InvitationCommandServiceImpl(OrganizationInvitationRepository invitationRepository,
                                        OrganizationRepository organizationRepository,
                                        MembershipCommandService membershipCommandService) {
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
        this.membershipCommandService = membershipCommandService;
    }

    @Override
    @Transactional
    public Result<OrganizationInvitation, ApplicationError> handle(InviteMemberCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            return Result.failure(ApplicationError.notFound("Organization", command.organizationId().toString()));
        }
        if (invitationRepository.existsByOrganizationIdAndEmailAndStatus(
                command.organizationId(), command.email(), InvitationStatus.PENDING)) {
            return Result.failure(ApplicationError.conflict(
                    "Invitation", "A pending invitation already exists for this email"));
        }
        var token = UUID.randomUUID().toString();
        var invitation = new OrganizationInvitation(command, token, Instant.now().plus(INVITATION_TTL));
        return Result.success(invitationRepository.save(invitation));
    }

    @Override
    @Transactional
    public Result<OrganizationInvitation, ApplicationError> handle(AcceptInvitationCommand command) {
        var existing = invitationRepository.findByToken(command.token());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Invitation", command.token()));
        }
        var invitation = existing.get();
        if (!invitation.isUsable(Instant.now())) {
            return Result.failure(ApplicationError.businessRuleViolation(
                    "invitation", "The invitation is expired, already accepted or revoked"));
        }
        return membershipCommandService.handle(new GrantMembershipCommand(
                        invitation.getOrganizationId(), command.userId(), invitation.getRole()))
                .flatMap(membership -> {
                    invitation.accept();
                    return Result.success(invitationRepository.save(invitation));
                });
    }

    @Override
    @Transactional
    public Result<OrganizationInvitation, ApplicationError> handle(RevokeInvitationCommand command) {
        var existing = invitationRepository.findById(command.invitationId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Invitation", command.invitationId().toString()));
        }
        var invitation = existing.get();
        if (!invitation.isPending()) {
            return Result.failure(ApplicationError.conflict(
                    "Invitation", "Only pending invitations can be revoked"));
        }
        invitation.revoke();
        return Result.success(invitationRepository.save(invitation));
    }
}
