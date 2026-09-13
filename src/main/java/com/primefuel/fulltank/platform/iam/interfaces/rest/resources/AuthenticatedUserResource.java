package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import java.util.List;

public record AuthenticatedUserResource(Long id, String username, String token,
                                        List<String> roles, Long companyId, Long providerId) {
}
