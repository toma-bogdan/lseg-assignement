import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class Main {
    private static final Duration WARNING_TIME = Duration.ofMinutes(5);
    private static final Duration ERROR_TIME = Duration.ofMinutes(10);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        String fileName = "logs.log";
        String outputFileName = "out.log";

        Map<Integer, Job> mappedJobs = processLogs(fileName);
        generateReport(mappedJobs,outputFileName);
    }

    /**
     * Process file lines concurrently using parallel stream
     * Maps the jobs by their pid
     */
    static Map<Integer, Job> processLogs(String filename) {
        Map<Integer, Job> mappedJobs = new ConcurrentHashMap<>();
        Path logPath = Paths.get(filename);

        try (Stream<String> lines = Files.lines(logPath)) {
            lines.parallel().forEach(line -> {
                String[] parts = line.split(",");
                if (parts.length < 4) return; // skips the lines with malformed jobs

                try {

                    String timeStr = parts[0].trim();
                    String description = parts[1].trim();
                    String event = parts[2].trim().toUpperCase();
                    int pid = Integer.parseInt(parts[3].trim());
                    LocalTime timestamp = LocalTime.parse(timeStr, TIME_FORMATTER);

                    mappedJobs.compute(pid, (key, existingJob) -> {
                        if (existingJob == null) {
                            existingJob = new Job(pid, description);
                        }
                        if (event.equals("START")) {
                            existingJob.setStart(timestamp);
                        } else if (event.equals("END")) {
                            existingJob.setEnd(timestamp);
                        }
                        return existingJob;
                    });

                } catch (Exception e) {
                    System.err.println("Error parsing line: " + line + " : " + e.getMessage());
                }

            });
        } catch (Exception e) {
            System.err.println("Error reading the log file: " + e.getMessage());
        }
        return mappedJobs;
    }

    /**
     *
     * Iterate through all the jobs and computes each job duration using parallel stream
     */
    static void generateReport(Map<Integer, Job> mappedJobs, String outputFileName) {
        List<String> reportLines = mappedJobs.entrySet().parallelStream()
                .map(entry -> {
                    int pid = entry.getKey();
                    Job job = entry.getValue();

                    if (job.getStart() == null || job.getEnd() == null) {
                        return "PID " + pid + " (" + job.getDescription() + ") is missing a START or END event.";
                    } else {
                        Duration duration = Duration.between(job.getStart(), job.getEnd());

                        return getString(duration, pid, job);
                    }
                })
                .toList();

        try {
            Files.write(Paths.get(outputFileName), reportLines);
            System.out.println("Report successfully written to " + outputFileName);
        } catch (IOException e) {
            System.err.println("Error writing report file: " + e.getMessage());
        }
    }

    private static String getString(Duration duration, int pid, Job job) {
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).getSeconds();
        String message = String.format("PID %s (%s): Duration: %d min %d sec.", pid, job.getDescription(), minutes, seconds);
        if (duration.compareTo(ERROR_TIME) > 0) {
            message += " ERROR: Job took longer than 10 minutes.";
        } else if (duration.compareTo(WARNING_TIME) > 0) {
            message += " WARNING: Job took longer than 5 minutes.";
        }
        return message;
    }

    public static DateTimeFormatter getTimeFormatter() {
        return TIME_FORMATTER;
    }
}