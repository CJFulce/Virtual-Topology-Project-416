import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Router {
    private Map<String, String> ipRoutingTable = new HashMap<>();
    private Map<String, Port> ports = new HashMap<>();
    private String routerId;
    private DatagramSocket socket;

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
                    if (p.length == 2 && !p[1].contains("R")) {
                        ipRoutingTable.put(p[0], p[1]); // Direct: "net1 G0/0"
                    } else if (p.length >= 3) {
                        // Next-hop: find virtual IP of "R2G0/0"
                        Scanner s2 = new Scanner(new File("config.txt"));
                        while (s2.hasNextLine()) {
                            String l = s2.nextLine().trim();
                            if (l.startsWith("device " + (p[1] + p[2]))) {
                                ipRoutingTable.put(p[0], l.split("\\s+")[1]);
                                break;
                            }
                        }
                        s2.close();
                    }
                }
            }
        }
        s.close();
    }

    private void initializePorts() {
        try {
            Scanner s = new Scanner(new File("config.txt"));
            while (s.hasNextLine()) {
                String line = s.nextLine().trim();
                if (line.startsWith("device " + routerId)) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 4) {
                        String deviceId = p[1], virtualIP = p[2], portName = deviceId.substring(routerId.length());
                        List<Parser.NeighborAddr> n = Parser.getNeighborAddrs(deviceId);
                        if (!n.isEmpty()) {
                            Parser.NeighborAddr neighbor = n.get(0);
                            ports.put(portName, new Port(portName, neighbor.id, neighbor.ip, neighbor.port, virtualIP));
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

        Port destPort = ports.containsKey(nextHop) ? ports.get(nextHop)
                : ports.values().stream()
                        .filter(p -> p.virtualIP != null && subnet(p.virtualIP).equals(subnet(nextHop)))
                        .findFirst().orElse(null);

        if (destPort == null) {
            System.out.println("[" + routerId + "] Cannot find port. Dropping packet.");
            return;
        }

        String newDstMac = ports.containsKey(nextHop) ? mac(destIP) : mac(nextHop);
        String newSrcMac = mac(destPort.virtualIP);
        Packet fwd = new Packet(newSrcMac, newDstMac, packet.getSrcIP(), destIP, packet.getMessage());

        byte[] data = fwd.encode().getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(data, data.length,
                InetAddress.getByName(destPort.ip), destPort.udpPort));

        printFrame("TX", fwd);
    }

    public static void main(String[] args) throws Exception {
        Router r = new Router(args[0]);

        String firstPortId = null;
        Scanner s = new Scanner(new File("config.txt"));
        while (s.hasNextLine()) {
            String line = s.nextLine().trim();
            if (line.startsWith("device " + args[0])) {
                firstPortId = line.split("\\s+")[1];
                break;
            }
        }
        s.close();

        r.socket = new DatagramSocket(Parser.getPort(firstPortId));
        System.out.println("[" + args[0] + "] Router up. Table: " + r.ipRoutingTable);

        byte[] buf = new byte[2048];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        while (true) {
            r.socket.receive(dp);
            Packet pkt = Packet
                    .decode(new String(dp.getData(), dp.getOffset(), dp.getLength(), StandardCharsets.UTF_8).trim());

            Port inPort = r.ports.values().stream()
                    .filter(p -> p.ip.equals(dp.getAddress().getHostAddress()) && p.udpPort == dp.getPort())
                    .findFirst().orElse(null);

            if (inPort == null)
                continue;

            r.printFrame("RX", pkt);

            boolean isForMe = r.ports.values().stream()
                    .anyMatch(p -> r.mac(p.virtualIP) != null && pkt.getDstMac().equals(r.mac(p.virtualIP)));

            if (isForMe)
                r.handleIncomingPacket(pkt, inPort);
        }
    }
}