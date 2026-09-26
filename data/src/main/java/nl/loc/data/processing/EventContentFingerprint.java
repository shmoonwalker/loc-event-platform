package nl.loc.data.processing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import nl.loc.data.event.NormalizedEvent;
import org.springframework.stereotype.Component;

/** Compares mapped content, excluding snapshot provenance and source update times. */
@Component
public class EventContentFingerprint {
    private final ObjectMapper objectMapper;

    public EventContentFingerprint() {
        SimpleModule dates = new SimpleModule();
        dates.addSerializer(Instant.class, ToStringSerializer.instance);
        dates.addSerializer(LocalDate.class, ToStringSerializer.instance);
        dates.addSerializer(LocalTime.class, ToStringSerializer.instance);
        objectMapper = new ObjectMapper().registerModule(dates);
    }

    public String of(NormalizedEvent event) {
        ObjectNode content = objectMapper.valueToTree(event);
        content.remove(List.of("rawObjectKey", "sourceCreatedAt", "sourceUpdatedAt"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(content));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint normalized event content", exception);
        }
    }
}
