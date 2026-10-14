package nl.loc.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SecureTokensTest {

    @Test
    void generateReturnsUrlSafeTokenOf43Chars() {
        String token = SecureTokens.generate();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void generateReturnsDifferentTokensEachTime() {
        String token1 = SecureTokens.generate();
        String token2 = SecureTokens.generate();
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void hashMatchesKnownSha256Value() {
        byte[] expected = HexFormat.of().parseHex(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
        byte[] actual = SecureTokens.hash("abc");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void sameTokenAlwaysGivesSameHash() {
        String token = "exampleToken";
        byte[] hash1 = SecureTokens.hash(token);
        byte[] hash2 = SecureTokens.hash(token);
        assertThat(hash1).isEqualTo(hash2);
    }
}
