package com.primefuel.fulltank.platform.tracking.infrastructure.metrics;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;

@Component
public class ApiRouteMetricsInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ApiRouteMetricsInterceptor.class);
    private final ApiRouteMetricsRecorder recorder;
    private final MembershipAccess membershipAccess;

    public ApiRouteMetricsInterceptor(ApiRouteMetricsRecorder recorder, MembershipAccess membershipAccess) {
        this.recorder = recorder;
        this.membershipAccess = membershipAccess;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                               Exception exception) {
        String path = request.getRequestURI();
        if (!(path.startsWith("/api/v1/") || path.startsWith("/api/v2/"))
                || !(handler instanceof HandlerMethod method)) return;
        Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (matchedPattern == null) return;

        String pattern = matchedPattern.toString();
        String version = pattern.startsWith("/api/v1/") ? "v1" : "v2";
        String routeKey = request.getMethod() + " " + pattern;
        String handlerName = method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
        try {
            // ponytail: una escritura por request; mover a buffer en memoria + flush periódico si el throughput lo exige
            recorder.record(routeKey, version, handlerName, callerKey(), Instant.now());
        } catch (Exception failure) {
            log.warn("Could not record API route metrics for {}", routeKey, failure);
        }
    }

    private String callerKey() {
        var organization = membershipAccess.currentOrganizationId();
        if (organization.isPresent()) return "organization:" + organization.get();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetailsImpl principal) {
            if (principal.getCompanyId() != null) return "company:" + principal.getCompanyId();
            if (principal.getProviderId() != null) return "provider:" + principal.getProviderId();
        }
        return "anonymous";
    }
}
