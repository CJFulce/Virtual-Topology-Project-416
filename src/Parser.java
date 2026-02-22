
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class Parser {
    File configFile;
    private Map<String, Integer> storedValues;

    public Parser() {
        this.configFile = new File("config.txt");
        this.storedValues = new HashMap<>();
    }

    public static int getPort(String deviceId) {
        Parser p = new Parser();
        return p.getDevicePort(deviceId);
    }

    public static String getIp(String deviceId) {
        Parser p = new Parser();
        return p.getDeviceIP(deviceId);
    }

    public static List<NeighborAddr> getNeighborAddrs(String deviceId) {
        Parser p = new Parser();
        List<String> neighborIds = p.getNeighborAddr(deviceId);
        List<NeighborAddr> result = new ArrayList<>();

        for (String neighborId : neighborIds) {
            String ip = p.getDeviceIP(neighborId);
            int port = p.getDevicePort(neighborId);
            result.add(new NeighborAddr(neighborId, ip, port));
        }
        return result;
    }

    public static class NeighborAddr {
        public final String id;
        public final String ip;
        public final int port;

        public NeighborAddr(String id, String ip, int port) {
            this.id = id;
            this.ip = ip;
            this.port = port;
        }
    }

    private List<String> initConfig() {
        List<String> configByLines = new ArrayList<>();
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                configByLines.add(rawConfig);
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return configByLines;
    }

    String getVirtualIPAddr(String deviceId) {

        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if (rawConfig.startsWith("device")) {
                    String[] lineElements = rawConfig.split(" ");
                    if (lineElements.length >= 3 && lineElements[1].equals(deviceId)) {
                        String virtualIP = lineElements[2];
                        System.out.printf("Virtual IP Found: %s\n", virtualIP);
                        return virtualIP;
                    }
                }
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return null;
    }

    int getDevicePort(String deviceId) {
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if (rawConfig.startsWith("device")) {
                    String[] lineElements = rawConfig.split(" ");
                    if (lineElements.length >= 2 && lineElements[1].equals(deviceId)) {
                        if (lineElements.length == 5 && lineElements[1].startsWith("R")) {
                            int parsedPortNumber = Integer.parseInt(lineElements[4]);
                            System.out.printf("Port Number Found: %s\n", parsedPortNumber);
                            return parsedPortNumber;
                        } else if (lineElements.length >= 5) {
                            int parsedPortNumber = Integer.parseInt(lineElements[4]);
                            System.out.printf("Port Number Found: %s\n", parsedPortNumber);
                            return parsedPortNumber;
                        }
                    }
                }
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return -1;
    }

    String getDeviceIP(String deviceId) {
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if (rawConfig.startsWith("device")) {
                    String[] lineElements = rawConfig.split(" ");
                    if (lineElements.length >= 2 && lineElements[1].equals(deviceId)) {
                        if (lineElements.length == 5 && lineElements[1].startsWith("R")) {
                            // Router port format: device R1L net1.R1 127.0.0.1 7000 (IP at index 3)
                            String parsedIPAddress = lineElements[3];
                            System.out.printf("IP Addr Found: %s\n", parsedIPAddress);
                            return parsedIPAddress;
                        } else if (lineElements.length >= 5) {
                            // Host or switch format: device A net1.A 127.0.0.1 5001 ... (IP at index 3)
                            String parsedIPAddress = lineElements[3];
                            System.out.printf("IP Addr Found: %s\n", parsedIPAddress);
                            return parsedIPAddress;
                        }
                    }
                }
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return null;
    }

    List<String> getNeighborAddr(String deviceId) {
        List<String> neighbors = new ArrayList<>();
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if (rawConfig.startsWith("link")) {
                    String[] lineElements = rawConfig.split(" ");
                    // use equals to compare strings
                    if (lineElements[1].equals(deviceId)) {
                        // add the neighbor id or ip (adjust index per your file format)
                        neighbors.add(lineElements[2]);
                    }
                    if (lineElements[2].equals(deviceId)) {
                        neighbors.add(lineElements[1]);
                    }
                }

            }
            return neighbors;

        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return neighbors; // will return empty if not seen in config file
    }

    String getDefaultGateway(String deviceId) {

        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if (rawConfig.startsWith("device")) {
                    String[] lineElements = rawConfig.split(" ");
                    if (lineElements.length >= 2 && lineElements[1].equals(deviceId)) {
                        if (lineElements.length >= 6) {
                            String gateway = lineElements[5];
                            System.out.printf("Gateway found: %s\n", gateway);
                            return gateway;
                        }
                    }
                }
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return null;
    }

    String getGatewayVirtualIP(String deviceId) {
        String gatewayDeviceId = getDefaultGateway(deviceId);
        if (gatewayDeviceId != null) {
            return getVirtualIPAddr(gatewayDeviceId);
        }
        return null;
    }

    List<String> getRoutingTables(String deviceId) {
        List<String> routingTable = new ArrayList<>();
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if (rawConfig.contains(deviceId) && rawConfig.startsWith("routing")) {
                    for (int i = 0; i <= 3; i++) {
                        String routingTableEntry = configScanner.nextLine();
                        routingTable.add(routingTableEntry);
                    }
                    return routingTable;
                }
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {

        Parser testParser = new Parser();

        List<String> scannedConfig = testParser.initConfig();
        for (int i = 0; i < scannedConfig.size(); i++) { // test print
            System.out.println(i + ": " + scannedConfig.get(i));
        }
        int portNumber = testParser.getDevicePort("S2");

        if (portNumber == 0) {
            System.out.println("Error loading port number");
        } else {
            System.out.printf("Port Number: %d\n", portNumber);
        }

        String ipAddress = testParser.getDeviceIP("S2");

        if (ipAddress == "0.0.0.0\n") {
            System.out.println("Error loading IP Address");
        } else {
            System.out.printf("IP Address: %s\n", ipAddress);
        }

        String virtualIP = testParser.getVirtualIPAddr("A");

        if (virtualIP == null) {
            System.out.println("Error loading virtual IP");
        } else {
            System.out.printf("Virtual IP: %s\n", virtualIP);
        }

        String defaultGateway = testParser.getDefaultGateway("D");
        if (defaultGateway == null) {
            System.out.println("Error loading gateway");
        } else {
            System.out.printf("Host %s's gateway: %s\n", "D", defaultGateway);
        }
        List<String> neighborList = testParser.getNeighborAddr("S2");

        if (neighborList.isEmpty()) {
            System.out.println("No neighbors found");
        } else {
            for (String neighbor : neighborList) {
                System.out.printf("Neighbor: %s\n", neighbor);
            }
        }
        List<String> routingTable = testParser.getRoutingTables("R1");
        System.out.println("R1 routing table");
        for (String entry : routingTable) {
            System.out.println(entry);
        }

    }

}
