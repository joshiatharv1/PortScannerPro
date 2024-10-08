import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {

    private static final int SCAN_INTERVAL_MS = 5000; // Interval for scanning in milliseconds
    private static Set<String> knownPorts = new HashSet<>();
    private static Logger logger = Logger.getLogger("PortScannerLog");

    public static void main(String[] args) {
        // Setup logging
        setupLogging();

        Scanner scanner = new Scanner(System.in);

        System.out.println("Port Scanner");
        System.out.print("Enter protocol (TCP/UDP or leave empty for all): ");
        String protocol = scanner.nextLine().trim().toUpperCase();

        System.out.print("Enter specific port number (or leave empty for all): ");
        String specificPort = scanner.nextLine().trim();

        System.out.println("Enter port numbers to monitor (comma-separated, leave empty for none): ");
        String[] monitorPorts = scanner.nextLine().split(",");

        for (int i = 0; i < monitorPorts.length; i++) {
            monitorPorts[i] = monitorPorts[i].trim(); // Trim spaces from port numbers
        }

        logger.info("Starting real-time port monitoring...");

        while (true) {
            List<String> results = getPortUsage(protocol, specificPort);
            logPortUsage(results);
            checkForAlerts(results, monitorPorts);

            // Sleep for the specified interval before the next scan
            try {
                Thread.sleep(SCAN_INTERVAL_MS);
            } catch (InterruptedException e) {
                logger.warning("Monitoring interrupted: " + e.getMessage());
                break;
            }
        }

        scanner.close();
    }

    private static void setupLogging() {
        try {
            FileHandler fileHandler = new FileHandler("port_scanner.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setUseParentHandlers(false); // Disable console logging to avoid duplication
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    public static List<String> getPortUsage(String protocolFilter, String portFilter) {
        List<String> results = new ArrayList<>();
        String command = getCommandForOS();

        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            // Add header for output
            results.add(String.format("%-15s %-10s %-10s %-15s %-10s", "Protocol", "Local Address", "Foreign Address", "State", "PID"));
            results.add("---------------------------------------------------------------");

            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("TCP") || line.trim().startsWith("UDP")) {
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length >= 5) {
                        String protocol = tokens[0];
                        String localAddress = tokens[1];
                        String foreignAddress = tokens[2];
                        String state = tokens[3];
                        String pid = tokens[4];

                        // Filter by protocol and port
                        if ((protocolFilter.isEmpty() || protocol.equals(protocolFilter)) &&
                                (portFilter.isEmpty() || localAddress.contains(":" + portFilter))) {
                            results.add(String.format("%-15s %-10s %-10s %-15s %-10s", protocol, localAddress, foreignAddress, state, pid));
                        }

                        // Track known ports
                        knownPorts.add(localAddress.split(":")[1]); // Extract port number
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            logger.severe("Error retrieving port usage: " + e.getMessage());
        }

        return results;
    }

    private static void checkForAlerts(List<String> results, String[] monitorPorts) {
        for (String port : monitorPorts) {
            if (!port.isEmpty()) {
                boolean isPortOpen = results.stream()
                        .anyMatch(result -> result.contains(":" + port.trim()));

                if (isPortOpen) {
                    String alertMessage = "Alert: Port " + port.trim() + " is currently open!";
                    System.out.println(alertMessage);
                    logger.info(alertMessage);
                } else if (knownPorts.contains(port.trim())) {
                    String alertMessage = "Alert: Port " + port.trim() + " is currently closed!";
                    System.out.println(alertMessage);
                    logger.info(alertMessage);
                }
            }
        }
    }

    private static String getCommandForOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "netstat -ano";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            return "lsof -i -n -P";
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + os);
        }
    }

    public static void logPortUsage(List<String> results) {
        String logFile = "port_usage_log.txt";

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write("Port Usage Log:\n");
            for (String result : results) {
                writer.write(result + "\n");
            }
            writer.write("\n");
        } catch (IOException e) {
            logger.severe("Error logging port usage: " + e.getMessage());
        }
    }
}
