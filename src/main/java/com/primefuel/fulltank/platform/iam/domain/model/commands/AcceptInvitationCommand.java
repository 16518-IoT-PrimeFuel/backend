package com.primefuel.fulltank.platform.iam.domain.model.commands;

public record AcceptInvitationCommand(
        String token,
        Long userId) {
}
