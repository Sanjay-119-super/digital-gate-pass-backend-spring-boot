package com.college.gatepass.security;

import com.college.gatepass.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Wraps our {@code User} entity so Spring Security can use it during authentication.
 *
 * <p>Spring Security does not know about our {@code User} class directly.
 * This adapter class translates it into the {@code UserDetails} interface that
 * Spring Security requires.
 *
 * <p>Each role (e.g. STUDENT) becomes a {@code GrantedAuthority} with the
 * prefix "ROLE_" (e.g. "ROLE_STUDENT"), which is what Spring Security expects
 * when you use {@code @PreAuthorize("hasRole('STUDENT')")}.
 */
@RequiredArgsConstructor
public class AppUserDetails implements UserDetails {

    /** The underlying user entity this object wraps. */
    private final User user;

    /**
     * Returns the numeric database ID of the user (not the username string).
     * Used in controllers to get the logged-in user's ID from the security context.
     *
     * @return the user's database ID
     */
    public Long getId() {
        return user.getId();
    }

    /**
     * Returns the underlying {@code User} entity so the service layer can access
     * full user details when needed.
     *
     * @return the wrapped user entity
     */
    public User getUser() {
        return user;
    }

    /**
     * Converts the user's roles to Spring Security's {@code GrantedAuthority} list.
     * Each role is prefixed with "ROLE_" as Spring Security convention requires.
     *
     * @return a collection of granted authorities (e.g. ROLE_STUDENT, ROLE_WARDEN)
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the BCrypt-hashed password stored in the database.
     * Spring Security uses this to verify the password during login.
     *
     * @return the hashed password string
     */
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /**
     * Returns the user's email address, which is used as the login username.
     *
     * @return the user's email
     */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    /** Account never expires in this system — always returns true. */
    @Override
    public boolean isAccountNonExpired() { return true; }

    /** Account locking is handled by the {@code active} flag, not this method. */
    @Override
    public boolean isAccountNonLocked() {
        return user.isActive();
    }

    /** Credentials never expire — always returns true. */
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    /**
     * Returns whether this account is active.
     * An inactive account ({@code active = false}) cannot log in.
     *
     * @return true if the account is active, false if disabled
     */
    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}