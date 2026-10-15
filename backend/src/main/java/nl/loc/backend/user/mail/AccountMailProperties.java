package nl.loc.backend.user.mail;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mail")
public record AccountMailProperties(String from, String frontendUrl) {
}
