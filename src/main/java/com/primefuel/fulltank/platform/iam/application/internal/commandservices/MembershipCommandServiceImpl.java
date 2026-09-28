package com.primefuel.fulltank.platform.iam.application.internal.commandservices;

import com.primefuel.fulltank.platform.iam.application.commandservices.MembershipCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.RevokeMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class MembershipCommandServiceImpl implements MembershipCommandService {

    private final MembershipRepository membershipRepository;

    public MembershipCommandServiceImpl(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Override
    public Result<Membership, ApplicationError> handle(GrantMembershipCommand command) {
        var existing = membershipRepository.findByOrganizationIdAndUserId(command.organizationId(), command.userId());
        if (existing.isPresent()) {
            var membership = existing.get();
            if (membership.isActive()) {
                return Result.failure(ApplicationError.conflict("Membership", "The user is already a member of this organization"));
            }
            membership.reactivate(command.role());
            try {
                return Result.success(membershipRepository.save(membership));
            } catch (DataIntegrityViolationException exception) {
                return Result.failure(ApplicationError.conflict("Membership", "Could not grant membership"));
            }
        }
        var membership = new Membership(command);
        try {
            return Result.success(membershipRepository.save(membership));
        } catch (DataIntegrityViolationException exception) {
            return Result.failure(ApplicationError.conflict("Membership", "The user is already a member of this organization"));
        }
    }

    @Override
    public Result<Membership, ApplicationError> handle(RevokeMembershipCommand command) {
        var existing = membershipRepository.findById(command.membershipId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Membership", command.membershipId().toString()));
        }
        var membership = existing.get();
        membership.revoke();
        return Result.success(membershipRepository.save(membership));
    }
}
