package nl.loc.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.source.rvo.RvoCollectionService;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionRunner implements CommandLineRunner {

    private final RvoCollectionService rvoCollectionService;

    @Override
    public void run(String... args) {
        log.info("Starting collection job");
        rvoCollectionService.collect();
    }
}
