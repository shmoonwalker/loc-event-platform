package nl.loc.backend.user.service;

import java.util.Collection;
import java.util.List;
import nl.loc.backend.user.model.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// isEnabled() stays true: DaoAuthenticationProvider checks it before the password, so it would reveal unverified accounts
public record AccountUserDetails(UserAccount account) implements UserDetails {

    @Override
    public String getUsername() {
        return account.getEmail();
    }

    @Override
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));
    }
}
