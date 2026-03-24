import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class Router {

    private static final int LINK_COST = 1;
    private static final int INFINITY = 1_000_000;

    private final String routerId;
    private final Map<String, Port> ports = new HashMap<>();
    private final Map<Integer, Port> portByNumber = new HashMap<>();
    private final Map<String, DatagramSocket> sendSockets = new HashMap<>();
    private final Map<String, DatagramSocket> receiveSockets = new HashMap<>();

    private final Map<String, Integer> dist = new ConcurrentHashMap<>();
    private final Map<String, String> tieVia = new ConcurrentHashMap<>();
    private final Map<String, Port> forwardPort = new ConcurrentHashMap<>();
    private final Set<String> directSubnets = ConcurrentHashMap.newKeySet();
    private final Set<Port> routerNeighborPorts = new HashSet<>();

    public Router(String logicalRouterId) throws FileNotFoundException {
        this.routerId = logicalRouterId;
        initializePorts();
        seedDirectRoutes();
        for (Port p : ports.values()) {
            if (isRouterInterface(p.neighborId)) {
                routerNeighborPorts.add(p);
            }
        }
    }

    private static boolean isRouterInterface(String deviceId) {
        return deviceId != null && deviceId.matches("^R\\d+[A-Z]+$");
    }

    static String logicalRouterIdFromInterface(String iface) {
        if (!isRouterInterface(iface)) {
            return null;
        }
        return iface.replaceAll("[A-Z]+$", "");
    }

    private void initializePorts() throws FileNotFoundException {
        try (Scanner s = new Scanner(new File("config.txt"))) {
            while (s.hasNextLine()) {
                String line = s.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("device " + routerId)) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 5) {
                        String deviceId = p[1];
                        String virtualIP = p[2];
                        String portName = deviceId.substring(routerId.length());
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
        }
    }

    private void seedDirectRoutes() {
        for (Port p : ports.values()) {
            String sub = subnet(p.virtualIP);
            directSubnets.add(sub);
            dist.put(sub, 0);
            tieVia.put(sub, routerId);
            forwardPort.put(sub, p);
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

    private String serializeVector() {
        List<String> nets = new ArrayList<>(dist.keySet());
        Collections.sort(nets);
        StringBuilder sb = new StringBuilder();
        sb.append(routerId).append('|');
        for (int i = 0; i < nets.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(nets.get(i)).append('=').append(dist.get(nets.get(i)));
        }
        return sb.toString();
    }

    private static Map<String, Integer> parseAdvertisedCosts(String body) {
        Map<String, Integer> m = new HashMap<>();
        if (body == null || body.isBlank()) {
            return m;
        }
        for (String pair : body.split(",")) {
            pair = pair.trim();
            if (pair.isEmpty()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                m.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            }
        }
        return m;
    }

    private synchronized boolean integrateAdvertisement(String senderLogicalId, Map<String, Integer> adv,
            Port ingress) {
        if (senderLogicalId == null || senderLogicalId.equals(routerId)) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, Integer> e : adv.entrySet()) {
            String net = e.getKey();
            if (directSubnets.contains(net)) {
                continue;
            }
            int remote = e.getValue();
            if (remote >= INFINITY / 2) {
                continue;
            }
            int candidate = remote + LINK_COST;
            int cur = dist.getOrDefault(net, INFINITY);
            String curVia = tieVia.get(net);
            boolean better = candidate < cur;
            boolean tieWin = candidate == cur && curVia != null && senderLogicalId.compareTo(curVia) < 0;
            if (better || tieWin) {
                dist.put(net, candidate);
                tieVia.put(net, senderLogicalId);
                forwardPort.put(net, ingress);
                changed = true;
            }
        }
        return changed;
    }

    private void sendDistanceVectors() {
        String payload = serializeVector();
        for (Port p : routerNeighborPorts) {
            String neighborVip = Parser.getVirtualIPStatic(p.neighborId);
            if (neighborVip == null) {
                continue;
            }
            String nMac = mac(neighborVip);
            String sMac = mac(p.virtualIP);
            if (nMac == null || sMac == null) {
                continue;
            }
            Packet dv = new Packet(1, sMac, nMac, p.virtualIP, neighborVip, payload);
            byte[] data = dv.encode().getBytes(StandardCharsets.UTF_8);
            try {
                DatagramSocket sock = getSendSocket(p.portName);
                if (sock != null) {
                    sock.send(new DatagramPacket(data, data.length, InetAddress.getByName(p.ip), p.udpPort));
                }
            } catch (IOException e) {
                System.err.println("[" + routerId + "] DV send error on " + p.portName + ": " + e.getMessage());
            }
        }
    }

    void handleRoutingPacket(Packet packet, Port ingress) {
        String msg = packet.getMessage();
        int sep = msg.indexOf('|');
        if (sep < 0) {
            return;
        }
        String senderLogical = msg.substring(0, sep);
        String body = msg.substring(sep + 1);
        Map<String, Integer> adv = parseAdvertisedCosts(body);
        if (integrateAdvertisement(senderLogical, adv, ingress)) {
            sendDistanceVectors();
        }
    }

    public void handleIncomingPacket(Packet packet, Port sourcePort) throws IOException {
        if (sourcePort == null) {
            return;
        }
        String destIP = packet.getDstIP();
        String destSubnet = subnet(destIP);
        String srcSubnet = subnet(packet.getSrcIP());

        if (destSubnet != null && destSubnet.equals(srcSubnet)) {
            System.out.println("[" + routerId + "] Packet ignored (same subnet)");
            return;
        }

        if (packet.getType() != 0) {
            return;
        }

        Port out = forwardPort.get(destSubnet);
        if (out == null) {
            System.out.println("[" + routerId + "] No route. Dropping packet.");
            return;
        }

        boolean onLan = destSubnet.equals(subnet(out.virtualIP));
        String newDstMac;
        if (onLan) {
            newDstMac = mac(destIP);
        } else {
            String nhVip = Parser.getVirtualIPStatic(out.neighborId);
            newDstMac = mac(nhVip);
        }
        if (newDstMac == null || newDstMac.isEmpty()) {
            System.out.println("[" + routerId + "] ERROR: cannot resolve next-hop MAC for " + destSubnet);
            return;
        }
        String newSrcMac = mac(out.virtualIP);
        if (newSrcMac == null || newSrcMac.isEmpty()) {
            System.out.println("[" + routerId + "] ERROR: bad source MAC on port " + out.portName);
            return;
        }
        Packet fwd = new Packet(0, newSrcMac, newDstMac, packet.getSrcIP(), destIP, packet.getMessage());
        byte[] data = fwd.encode().getBytes(StandardCharsets.UTF_8);
        DatagramSocket sendSocket = getSendSocket(out.portName);
        if (sendSocket == null) {
            System.out.println("[" + routerId + "] ERROR: no send socket for " + out.portName);
            return;
        }
        sendSocket.send(new DatagramPacket(data, data.length, InetAddress.getByName(out.ip), out.udpPort));
        printFrame("TX", fwd);
    }

    private DatagramSocket getSendSocket(String portName) {
        if (sendSockets.containsKey(portName)) {
            return sendSockets.get(portName);
        }

        String deviceId = routerId + portName;
        int sourcePortNum = Parser.getPort(deviceId);

        if (receiveSockets.containsKey(portName)) {
            DatagramSocket recvSocket = receiveSockets.get(portName);
            sendSockets.put(portName, recvSocket);
            return recvSocket;
        }

        try {
            DatagramSocket sock = new DatagramSocket(sourcePortNum);
            sendSockets.put(portName, sock);
            return sock;
        } catch (Exception e) {
            System.err.println("[" + routerId + "] ERROR creating socket for " + portName + " on port " + sourcePortNum
                    + ": " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Router <R1|R2|…>");
            return;
        }

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

        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
        sched.scheduleAtFixedRate(() -> {
            try {
                r.sendDistanceVectors();
            } catch (Exception e) {
                System.err.println("[" + r.routerId + "] periodic DV: " + e.getMessage());
            }
        }, 50, 400, TimeUnit.MILLISECONDS);

        System.out.println("[" + args[0] + "] Router up (DV). Ports: " + r.receiveSockets.keySet());
        System.out.println("[" + args[0] + "] Router peers: " + r.routerNeighborPorts.stream()
                .map(p -> p.neighborId).toList());

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
            byte[] buf = new byte[8192];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);

            while (true) {
                try {
                    socket.receive(dp);
                    Packet pkt = Packet.decode(
                            new String(dp.getData(), dp.getOffset(), dp.getLength(), StandardCharsets.UTF_8).trim());

                    Port inPort = router.portByNumber.get(portNumber);
                    if (inPort == null) {
                        System.out.println("[" + router.routerId + "] WARNING: unknown ingress UDP " + portNumber);
                        continue;
                    }

                    boolean isForMe = router.ports.values().stream()
                            .anyMatch(
                                    p -> router.mac(p.virtualIP) != null
                                            && pkt.getDstMac().equals(router.mac(p.virtualIP)));

                    if (pkt.getType() == 1) {
                        if (isForMe) {
                            router.handleRoutingPacket(pkt, inPort);
                        }
                        continue;
                    }

                    router.printFrame("RX", pkt);
                    if (isForMe) {
                        router.handleIncomingPacket(pkt, inPort);
                    }
                } catch (Exception e) {
                    System.err.println(
                            "[" + router.routerId + "] ERROR receiving on port " + portName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}
