import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    void testProcessLogs(@TempDir Path tempDir) throws IOException {
        // Prepare sample log lines.
        List<String> logLines = Arrays.asList(
                "11:35:23, job one, START, 1",
                "11:35:56, job one, END, 1",
                "11:36:11, job two, START, 2",
                "11:36:18, job two, END, 2",
                "11:37:00, job three, START, 3"
        );

        Path tempLogFile = tempDir.resolve("testLogs.log");
        Files.write(tempLogFile, logLines);

        // Call processLogs using the temporary file.
        Map<Integer, Job> jobs = Main.processLogs(tempLogFile.toString());

        // We expect three jobs.
        assertEquals(3, jobs.size(), "Expected 3 jobs to be processed.");

        // Verify job one (PID 1).
        Job job1 = jobs.get(1);
        assertNotNull(job1, "Job for PID 1 should exist.");
        assertEquals("job one", job1.getDescription());
        assertEquals(LocalTime.parse("11:35:23", Main.getTimeFormatter()), job1.getStart());
        assertEquals(LocalTime.parse("11:35:56", Main.getTimeFormatter()), job1.getEnd());

        // Verify job two (PID 2).
        Job job2 = jobs.get(2);
        assertNotNull(job2, "Job for PID 2 should exist.");
        assertEquals("job two", job2.getDescription());
        assertEquals(LocalTime.parse("11:36:11", Main.getTimeFormatter()), job2.getStart());
        assertEquals(LocalTime.parse("11:36:18", Main.getTimeFormatter()), job2.getEnd());

        // Verify job three (PID 3) is incomplete (missing END event).
        Job job3 = jobs.get(3);
        assertNotNull(job3, "Job for PID 3 should exist.");
        assertEquals("job three", job3.getDescription());
        assertNotNull(job3.getStart(), "Job three should have a START time.");
        assertNull(job3.getEnd(), "Job three should be missing an END time.");
    }

    @Test
    void testGenerateReport(@TempDir Path tempDir) throws IOException {
        // Create test jobs.
        Map<Integer, Job> jobs = new ConcurrentHashMap<>();

        // Job 1: Duration 3 minutes (no warning or error)
        Job job1 = new Job(1, "job one");
        job1.setStart(LocalTime.parse("10:00:00", Main.getTimeFormatter()));
        job1.setEnd(LocalTime.parse("10:03:00", Main.getTimeFormatter()));
        jobs.put(1, job1);

        // Job 2: Duration 7 minutes (should trigger a warning).
        Job job2 = new Job(2, "job two");
        job2.setStart(LocalTime.parse("10:00:00", Main.getTimeFormatter()));
        job2.setEnd(LocalTime.parse("10:07:00", Main.getTimeFormatter()));
        jobs.put(2, job2);

        // Job 3: Duration 12 minutes (should trigger an error).
        Job job3 = new Job(3, "job three");
        job3.setStart(LocalTime.parse("10:00:00", Main.getTimeFormatter()));
        job3.setEnd(LocalTime.parse("10:12:00", Main.getTimeFormatter()));
        jobs.put(3, job3);

        // Job 4: Incomplete (missing END event).
        Job job4 = new Job(4, "job four");
        job4.setStart(LocalTime.parse("10:00:00", Main.getTimeFormatter()));
        jobs.put(4, job4);

        // Create a temporary output file.
        Path tempOutputFile = tempDir.resolve("report.out");
        Main.generateReport(jobs, tempOutputFile.toString());

        // Read the generated report.
        List<String> reportLines = Files.readAllLines(tempOutputFile);
        assertEquals(4, reportLines.size(), "Expected 4 report lines.");

        // Verify report for Job 1.
        String reportJob1 = reportLines.stream()
                .filter(s -> s.contains("job one"))
                .findFirst().orElse("");
        assertTrue(reportJob1.contains("Duration: 3 min 0 sec."), "Job one should have a duration of 3 min 0 sec.");
        assertFalse(reportJob1.contains("WARNING"), "Job one should not trigger a warning.");
        assertFalse(reportJob1.contains("ERROR"), "Job one should not trigger an error.");

        // Verify report for Job 2.
        String reportJob2 = reportLines.stream()
                .filter(s -> s.contains("job two"))
                .findFirst().orElse("");
        assertTrue(reportJob2.contains("Duration: 7 min 0 sec."), "Job two should have a duration of 7 min 0 sec.");
        assertTrue(reportJob2.contains("WARNING"), "Job two should trigger a warning.");
        assertFalse(reportJob2.contains("ERROR"), "Job two should not trigger an error.");

        // Verify report for Job 3.
        String reportJob3 = reportLines.stream()
                .filter(s -> s.contains("job three"))
                .findFirst().orElse("");
        assertTrue(reportJob3.contains("Duration: 12 min 0 sec."), "Job three should have a duration of 12 min 0 sec.");
        assertTrue(reportJob3.contains("ERROR"), "Job three should trigger an error.");

        // Verify report for Job 4 (incomplete job).
        String reportJob4 = reportLines.stream()
                .filter(s -> s.contains("job four"))
                .findFirst().orElse("");
        assertTrue(reportJob4.contains("missing a START or END event"), "Job four should be reported as incomplete.");
    }
}
