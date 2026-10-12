package com.primefuel.fulltank.platform.iam.api;

/** Public read seam: whether an IAM user is linked to a provider (distribuidor) tenant. */
public interface UserProviderDirectory {

    boolean belongsToProvider(Long userId, Long providerId);
}
