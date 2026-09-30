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
        String[] commandArgs = ingestionArgs(args);
        if (commandArgs.length == 0) {
            log.info("Processing pending stored events for all sources");
            ingestionRun.processPendingAll();
            return;
        }

        String command = commandArgs[0];
        switch (command) {
            case "publish" -> {
                requireArgCount(commandArgs, 1, "publish");
                // PublicationScan evaluates the catalogue after ApplicationReadyEvent.
                log.info("Starting final publication from the existing catalogue; no source collection or raw replay");
            }
            case "process" -> {
                requireArgCount(commandArgs, 2, "process <source>");
                ingestionRun.processPending(commandArgs[1]);
            }
            case "process-all" -> {
                requireArgCount(commandArgs, 1, "process-all");
                ingestionRun.processPendingAll();
            }
            case "collect" -> {
                requireArgCount(commandArgs, 2, "collect <source>");
                ingestionRun.collect(commandArgs[1]);
            }
            case "run-all" -> {
                requireArgCount(commandArgs, 1, "run-all");
                ingestionRun.runAll();
            }
            case "run" -> {
                requireArgCount(commandArgs, 2, "run <source>");
                ingestionRun.run(commandArgs[1]);
            }
            case "reprocess" -> {
                if (commandArgs.length < 3) {
                    throw new IllegalArgumentException("reprocess requires a source and at least one raw object key");
                }
                ingestionRun.reprocess(commandArgs[1], List.copyOf(Arrays.asList(commandArgs).subList(2, commandArgs.length)));
            }
            default -> throw new IllegalArgumentException("Unknown ingestion command " + command);
        }
    }

    /** Spring Boot passes --property=value as application arguments; those are not ingestion commands. */
    private static String[] ingestionArgs(String... args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }
        return Arrays.stream(args)
                .filter(arg -> arg != null && !arg.startsWith("--"))
                .toArray(String[]::new);
    }

    private static void requireArgCount(String[] args, int expected, String usage) {
        if (args.length != expected) {
            throw new IllegalArgumentException(usage);
        }
    }
}
