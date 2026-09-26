package nl.loc.data.collection;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionCommand implements CommandLineRunner {

    private final IngestionRun ingestionRun;

    @Override
    public void run(String... args) {
        if (args == null || args.length == 0) {
            log.info("Processing pending stored events for all sources");
            ingestionRun.processPendingAll();
            return;
        }

        String command = args[0];
        switch (command) {
            case "process" -> {
                requireArgCount(args, 2, "process <source>");
                ingestionRun.processPending(args[1]);
            }
            case "run-all" -> {
                requireArgCount(args, 1, "run-all");
                ingestionRun.runAll();
            }
            case "run" -> {
                requireArgCount(args, 2, "run <source>");
                ingestionRun.run(args[1]);
            }
            case "reprocess" -> {
                if (args.length < 3) {
                    throw new IllegalArgumentException("reprocess requires a source and at least one raw object key");
                }
                ingestionRun.reprocess(args[1], List.copyOf(Arrays.asList(args).subList(2, args.length)));
            }
            default -> throw new IllegalArgumentException("Unknown ingestion command " + command);
        }
    }

    private static void requireArgCount(String[] args, int expected, String usage) {
        if (args.length != expected) {
            throw new IllegalArgumentException(usage);
        }
    }
}
