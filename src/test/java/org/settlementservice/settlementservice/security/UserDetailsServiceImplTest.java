package org.settlementservice.settlementservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.settlementservice.settlementservice.enums.Role;
import org.settlementservice.settlementservice.models.User;
import org.settlementservice.settlementservice.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void loadUserByUsername_existingUser_returnsUserDetailsWithRolePrefixedAuthority() {
        // Regression test: the authority must be "ROLE_ADMIN", not "ADMIN" — Spring Security's
        // hasRole('ADMIN') checks for the literal string "ROLE_ADMIN" in the granted authorities.
        User user = new User();
        user.setUsername("admin");
        user.setPasswordHash("hashed-password");
        user.setRole(Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("admin");

        assertThat(userDetails.getUsername()).isEqualTo("admin");
        assertThat(userDetails.getPassword()).isEqualTo("hashed-password");
        assertThat(userDetails.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_reconOfficer_returnsRolePrefixedAuthority() {
        User user = new User();
        user.setUsername("officer1");
        user.setPasswordHash("hashed-password");
        user.setRole(Role.RECON_OFFICER);
        when(userRepository.findByUsername("officer1")).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("officer1");

        assertThat(userDetails.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_RECON_OFFICER");
    }

    @Test
    void loadUserByUsername_unknownUsername_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
