package com.posgateway.aml.service.auth;

import com.posgateway.aml.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// @RequiredArgsConstructor removed
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads user details by username or email.
     *
     * <p>Result is cached in the "users" Caffeine cache (5-min TTL) keyed by the
     * supplied username/email string.  Spring Security calls this on every
     * authenticated request; caching it eliminates a DB round-trip on the hot
     * authentication path.
     *
     * <p>Cache entries are evicted by {@code UserService} whenever a user's
     * credentials or status are mutated.
     */
    @Override
    @Cacheable(cacheNames = "users", key = "#username")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Try to find by username first, then by email
        return userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username or email: " + username));
    }
}
