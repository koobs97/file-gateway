package com.file.gateway.auth;

import com.file.gateway.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record UserPrincipal(
        Long id,
        String username,
        String password,
        String role,
        boolean active,
        boolean passwordChanged,
        List<GrantedAuthority> authorities
) implements UserDetails {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getRole(),
                user.isActive(),
                user.isPasswordChanged(),
                List.of(new SimpleGrantedAuthority(user.getRole()))
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
