import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Router {
    private Map<String, String> ipRoutingTable = new HashMap<>();
    private Map<String, Port> ports = new HashMap<>();
    private Map<Integer, Port> portByNumber = new HashMap<>();
    private Map<String, DatagramSocket> sendSockets = new HashMap<>();
    private Map<String, DatagramSocket> receiveSockets = new HashMap<>();
    private String routerId;

    public Router(String deviceMAC) throws FileNotFoundException {
        this.routerId = deviceMAC;
        parseRoutingTable();
        initializePorts();
    }

    private void parseRoutingTable() throws FileNotFoundException {
        Scanner s = new Scanner(new File("config.txt"));
        boolean inSection = false;
        while (s.hasNextLine()) {
            String line = s.nextLine().trim();
            if (line.startsWith("routing " + routerId)) {
                inSection = true;
                continue;
            }
            if (inSection) {
                if (line.isEmpty() || line.startsWith("routing"))
                    break;
                String[] p = line.split("\\s+");
                if (p.length >= 2) {
                    String subnet = p[0];
                    if (p.length == 2) {
                        String secondToken = p[1];
                        if (secondToken.matches("R\\d+[LR]")) {
                            ipRoutingTable.put(subnet, secondToken);
                        } else if (secondToken.matches("R\\d+")) {
                            String nextHopRouter = secondToken;
                            String nextHopVirtualIP = findNextHopVirtualIPForSubnet(nextHopRouter, subnet);
                            if (nextHopVirtualIP != null) {
                                ipRoutingTable.put(subnet, nextHopVirtualIP);
                            }
                        } else {
                            ipRoutingTable.put(subnet, secondToken);
                        }
                    } else if (p.length >= 3) {
                        String nextHopRouter = p[1];
                        String nextHopPort = p[2];
                        String deviceId = nextHopRouter + nextHopPort.substring(nextHopRouter.length());
                        Scanner s2 = new Scanner(new File("config.txt"));
                        while (s2.hasNextLine()) {
                            String l = s2.nextLine().trim();
                            if (l.startsWith("device " + deviceId)) {
                                String[] parts = l.split("\\s+");
                                if (parts.length >= 3) {
                                    ipRoutingTable.put(subnet, parts[2]);
                                    break;
                                }
                            }
                        }
                        s2.close();
                    }
                }
            }
        }
        s.close();
    }

    private String findNextHopVirtualIPForSubnet(String routerId, String subnet) throws FileNotFoundException {
        Scanner scanner = new Scanner(new File("config.txt"));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.startsWith("device " + routerId)) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    String virtualIP = parts[2];
                    String portSubnet = virtualIP.substring(0, virtualIP.indexOf('.'));
                    if (portSubnet.equals(subnet)) {
                        scanner.close();
                        return virtualIP;
                    }
                }
            }
        }
        scanner.close();
        return null;
    }

    private void initializePorts() {
        try {
            Scanner s = new Scanner(new File("config.txt"));
            while (s.hasNextLine()) {
                String line = s.nextLine().trim();
                if (line.startsWith("device " + routerId)) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 5) {
                        String deviceId = p[1], virtualIP = p[2], portName = deviceId.substring(routerId.length());
                        List<Parser.NeighborAddr> n = Parser.getNeighborAddrs(deviceId);
                        if (!n.isEmpty()) {
                            Parser.NeighborAddr neighbor = n.get(0);
                            int ownPort = Parser.getPort(deviceId);
                            Port port = new Port(portName, neighbor.id, neighbor.ip, neighbor.port, virtualIP);
                            ports.put(portName, port);
                            portByNumber.put(ownPort, port);
                        }
                    }
                }
            }
            s.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void printFrame(String dir, Packet pkt) {
        System.out.println("[" + routerId + "] " + dir + " frame:");
        System.out.println("  srcMac=" + pkt.getSrcMac() + " dstMac=" + pkt.getDstMac());
        try {
            System.out.println("  srcIP=" + pkt.getSrcIP() + " dstIP=" + pkt.getDstIP());
        } catch (Exception e) {
            System.out.println("  srcIP=<not available> dstIP=<not available>");
        }
        System.out.println("  message=" + pkt.getMessage());
    }

    private String subnet(String ip) {
        return ip != null ? ip.split("\\.")[0] : null;
    }

    private String mac(String ip) {
        return ip != null && ip.contains(".") ? ip.split("\\.")[1] : null;
    }

    public void handleIncomingPacket(Packet packet, Port sourcePort) throws IOException {
        if (sourcePort == null)
            return;

        String destIP = packet.getDstIP(), destSubnet = subnet(destIP);
        String srcSubnet = subnet(packet.getSrcIP());

        if (destSubnet != null && destSubnet.equals(srcSubnet)) {
            System.out.println("[" + routerId + "] Packet ignored (same subnet)");
            return;
        }

        String nextHop = ipRoutingTable.get(destSubnet);
        if (nextHop == null) {
            System.out.println("[" + routerId + "] No route. Dropping packet.");
            return;
        }

        Port destPort;
        boolean isDirectlyConnected = false;
        String portName = null;
        if (nextHop.matches("R\\d+[LR]")) {
            portName = nextHop.substring(routerId.length());
            if (ports.containsKey(portName)) {
                destPort = ports.get(portName);
                isDirectlyConnected = true;
            } else {
                System.out.println(
                        "[" + routerId + "] ERROR: Port name " + portName + " not found for deviceId " + nextHop);
                return;
            }
        } else if (ports.containsKey(nextHop)) {
            destPort = ports.get(nextHop);
            isDirectlyConnected = true;
        } else if (nextHop.contains(".")) {
            String nextHopRouterId = mac(nextHop);
            if (nextHopRouterId == null || nextHopRouterId.isEmpty()) {
                System.out.println("[" + routerId + "] ERROR: Cannot extract router ID from nextHop=" + nextHop);
                return;
            }
            destPort = ports.values().stream()
                    .filter(p -> p != null && p.neighborId != null && p.neighborId.startsWith(nextHopRouterId))
                    .findFirst().orElse(null);
            isDirectlyConnected = false;
        } else {
            System.out.println("[" + routerId + "] ERROR: Invalid nextHop format: " + nextHop);
            return;
        }

        if (destPort == null) {
            System.out.println("[" + routerId + "] Cannot find port for nextHop=" + nextHop + ". Dropping packet.");
            return;
        }

        String newDstMac;
        if (isDirectlyConnected) {
            newDstMac = mac(destIP);
            if (newDstMac == null || newDstMac.isEmpty()) {
                System.out.println("[" + routerId + "] ERROR: Cannot extract MAC from destIP=" + destIP);
                return;
            }
        } else {
            newDstMac = mac(nextHop);
            if (newDstMac == null || newDstMac.isEmpty()) {
                System.out.println("[" + routerId + "] ERROR: Cannot extract MAC from nextHop=" + nextHop);
                return;
            }
        }
        String newSrcMac = mac(destPort.virtualIP);
        if (newSrcMac == null || newSrcMac.isEmpty()) {
            System.out
                    .println("[" + routerId + "] ERROR: Cannot extract MAC from port virtualIP=" + destPort.virtualIP);
            return;
        }
        Packet fwd = new Packet(newSrcMac, newDstMac, packet.getSrcIP(), destIP, packet.getMessage());

        byte[] data = fwd.encode().getBytes(StandardCharsets.UTF_8);

        DatagramSocket sendSocket = getSendSocket(destPort.portName);
        if (sendSocket == null) {
            System.out.println("[" + routerId + "] ERROR: Cannot create send socket for port " + destPort.portName);
            return;
        }

        sendSocket.send(new DatagramPacket(data, data.length,
                InetAddress.getByName(destPort.ip), destPort.udpPort));

        printFrame("TX", fwd);
    }

    private DatagramSocket getSendSocket(String portName) {
        if (sendSockets.containsKey(portName)) {
            return sendSockets.get(portName);
        }

        String deviceId = routerId + portName;
        int sourcePort = Parser.getPort(deviceId);

        if (receiveSockets.containsKey(portName)) {
            DatagramSocket recvSocket = receiveSockets.get(portName);
            sendSockets.put(portName, recvSocket);
            return recvSocket;
        }

        try {
            DatagramSocket sock = new DatagramSocket(sourcePort);
            sendSockets.put(portName, sock);
            return sock;
        } catch (Exception e) {
            System.err.println("[" + routerId + "] ERROR creating socket for " + portName + " on port " + sourcePort
                    + ": " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        Router r = new Router(args[0]);
        ExecutorService es = Executors.newCachedThreadPool();
        for (Map.Entry<String, Port> entry : r.ports.entrySet()) {
            String portName = entry.getKey();
            String deviceId = r.routerId + portName;
            int portNumber = Parser.getPort(deviceId);

            try {
                DatagramSocket recvSocket = new DatagramSocket(portNumber);
                r.receiveSockets.put(portName, recvSocket);

                es.submit(new ReceiveTask(r, portName, portNumber, recvSocket));
            } catch (Exception e) {
                System.err.println(
                        "[" + args[0] + "] ERROR creating receive socket for " + portName + ": " + e.getMessage());
            }
        }

        System.out.println("[" + args[0] + "] Router up. Table: " + r.ipRoutingTable);
        System.out.println("[" + args[0] + "] Listening on ports: " + r.receiveSockets.keySet());

        Thread.sleep(Long.MAX_VALUE);
    }

    static class ReceiveTask implements Runnable {
        private final Router router;
        private final String portName;
        private final int portNumber;
        private final DatagramSocket socket;

        ReceiveTask(Router router, String portName, int portNumber, DatagramSocket socket) {
            this.router = router;
            this.portName = portName;
            this.portNumber = portNumber;
            this.socket = socket;
        }

        @Override
        public void run() {
            byte[] buf = new byte[2048];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);

            while (true) {
                try {
                    socket.receive(dp);
                    Packet pkt = Packet.decode(
                            new String(dp.getData(), dp.getOffset(), dp.getLength(), StandardCharsets.UTF_8).trim());

                    Port inPort = router.portByNumber.get(portNumber);

                    if (inPort == null) {
                        System.out.println("[" + router.routerId + "] WARNING: Cannot identify incoming port for port "
                                + portNumber);
                        continue;
                    }

                    router.printFrame("RX", pkt);

                    boolean isForMe = router.ports.values().stream()
                            .anyMatch(p -> router.mac(p.virtualIP) != null
                                    && pkt.getDstMac().equals(router.mac(p.virtualIP)));

                    if (isForMe)
                        router.handleIncomingPacket(pkt, inPort);
                } catch (Exception e) {
                    System.err.println(
                            "[" + router.routerId + "] ERROR receiving on port " + portName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}