package com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.User;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode
public class UserDetailsImpl implements UserDetails {

    private final String username;
    private final Long userId;
    private final Long companyId;
    private final Long providerId;
    @JsonIgnore
    private final String password;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    @JsonIgnore
    private final Instant tokenValidAfter;

    public UserDetailsImpl(Long userId, String username, String password,
                           Long companyId, Long providerId,
                           Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, password, companyId, providerId, authorities, null);
    }

    public UserDetailsImpl(Long userId, String username, String password,
                           Long companyId, Long providerId,
                           Collection<? extends GrantedAuthority> authorities,
                           Instant tokenValidAfter) {
        this.tokenValidAfter = tokenValidAfter;
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.companyId = companyId;
        this.providerId = providerId;
        this.authorities = authorities;
        this.accountNonExpired = true;
        this.accountNonLocked = true;
        this.credentialsNonExpired = true;
        this.enabled = true;
    }

    public static UserDetailsImpl build(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> role.getName().name())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new UserDetailsImpl(user.getId(), user.getUsername(), user.getPassword(),
                user.getCompanyId(), user.getProviderId(), authorities, user.getTokenValidAfter());
    }
}
