package com.primefuel.fulltank.platform.iam.domain.model.commands;

import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;

public record GrantMembershipCommand(
        Long organizationId,
        Long userId,
        MembershipRole role) {
}
