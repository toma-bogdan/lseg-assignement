import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final Duration WARNING_TIME = Duration.ofMinutes(5);
    private static final Duration ERROR_TIME = Duration.ofMinutes(10);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        String fileName = "logs.log";

        Map<Integer, Job> mappedJobs = processLogs(fileName);
        generateReport(mappedJobs);
    }

    /**
     * Reads line by line from file and maps each job start and end time by pid
     */
    private static Map<Integer, Job> processLogs(String filename) {
        Map<Integer, Job> mappedJobs = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] jobParts = line.split(",");
                if (jobParts.length < 4) {
                    // skips the lines with malformed jobs
                    continue;
                }

                String time = jobParts[0].trim();
                String jobDescription = jobParts[1].trim();
                String event = jobParts[2].trim().toUpperCase();
                int pid = Integer.parseInt(jobParts[3].trim());
                LocalTime timestamp = LocalTime.parse(time, TIME_FORMATTER);

                // Retrieve or create the job based on PID
                Job job = mappedJobs.getOrDefault(pid, new Job(pid, jobDescription));

                if (event.equals("START")) {
                    job.setStart(timestamp);
                } else if (event.equals("END")) {
                    job.setEnd(timestamp);
                }

                mappedJobs.put(pid, job);
            }

        } catch (Exception e) {
            System.err.println("Error reading the log file: " + e.getMessage());
        }
        return mappedJobs;
    }

    private static void generateReport(Map<Integer, Job> mappedJobs) {
        for (Map.Entry<Integer, Job> entry : mappedJobs.entrySet()) {
            int pid = entry.getKey();
            Job job = entry.getValue();

            if (job.getStart() == null || job.getEnd() == null) {
                System.out.println("PID " + pid + " (" + job.getDescription() + ") is missing starting or ending time");
            } else {
                Duration duration = Duration.between(job.getStart(), job.getEnd());

                String message = getLogOutput(duration, pid, job);
                System.out.println(message);
            }
        }
    }

    private static String getLogOutput(Duration duration, int pid, Job job) {
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
}