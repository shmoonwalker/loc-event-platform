package nl.loc.backend.user.service;

import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public AccountUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userAccountRepository.findByEmail(UserAccount.normalizeEmail(username))
                // a null hash would make the password encoder throw (500), so treat it like an unknown account
                .filter(account -> account.getPasswordHash() != null)
                .map(AccountUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account with a password for this email"));
    }
}
