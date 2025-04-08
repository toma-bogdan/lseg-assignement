## How It Works

1. **Log Parsing:**  
   The application reads the log file and, for each line, extracts the timestamp, job description, event type, and PID.

2. **Job Mapping:**  
   Jobs are stored in a map (keyed by PID) where their START and END times are recorded. If a job is incomplete (missing an event), this is noted.

3. **Report Generation:**  
   The application calculates each job’s duration and generates a report line with the computed duration, appending a warning or an error message if it exceeds 5 or 10 minutes, respectively.

4. **Output:**  
   The final report is output to the console (or can be written to a file).