package com.college.gatepass.security;

import com.college.gatepass.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Tells Spring Security how to load a user from the database by their email.
 *
 * <p>Spring Security calls {@code loadUserByUsername} during the
 * {@code UsernamePasswordAuthenticationToken} authentication process
 * (i.e. during {@code authManager.authenticate()} in {@code AuthService.login()}).
 *
 * <p>The "username" in Spring Security terminology is our user's email address.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    /**
     * Loads a user by their email address and wraps them in {@code AppUserDetails}.
     *
     * @param email the email address typed in by the user at login
     * @return an {@code AppUserDetails} object Spring Security can work with
     * @throws UsernameNotFoundException if no user with that email exists
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(email)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));
    }
}