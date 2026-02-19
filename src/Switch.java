import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Switch {

    private final Map<String, Port> macTable = new HashMap<>();
    private final Map<String, Port> ports = new HashMap<>();

    private static String key(String ip, int port) {
        return ip + ":" + port;
    }

    private void addPort(String neighborId, String ip, int udpPort) {
        Port p = new Port(neighborId, ip, udpPort);
        ports.put(p.key(), p);
    }

    private void learn(String srcMac, Port incoming) {
        Port prev = macTable.get(srcMac);
        if (prev == null || !prev.equals(incoming)) {
            macTable.put(srcMac, incoming);
            System.out.println("[SW] LEARN " + srcMac + " -> " + incoming);
            printTable();
        }
    }

    private void forward(Packet pkt, Port incoming, DatagramSocket sock) throws IOException {
        String dst = pkt.getDstMac();
        Port out = macTable.get(dst);

        if (out != null && !out.equals(incoming)) {
            // unicast
            send(pkt, out, sock);
        } else {
            // flood
            for (Port p : ports.values()) {
                if (!p.equals(incoming)) {
                    send(pkt, p, sock);
                }
            }
        }
    }

    private void send(Packet pkt, Port out, DatagramSocket sock) throws IOException {
        byte[] data = pkt.encode().getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(
                data, data.length,
                InetAddress.getByName(out.ip),
                out.udpPort);
        sock.send(dp);
    }

    private void printTable() {
        System.out.println("---- SWITCH TABLE ----");
        if (macTable.isEmpty()) {
            System.out.println("(empty)");
        } else {
            List<String> macs = new ArrayList<>(macTable.keySet());
            Collections.sort(macs);
            for (String mac : macs) {
                System.out.println(mac + " -> " + macTable.get(mac));
            }
        }
        System.out.println("----------------------");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Switch <SWITCH_ID>");
            return;
        }

        String myId = args[0];
        int myPort = Parser.getPort(myId);
        String myIp = Parser.getIp(myId);
        if (myPort < 0 || myIp == null) {
            throw new IllegalArgumentException("Unknown device in config: " + myId);
        }

        Switch sw = new Switch();

        // Build neighbor ports
        for (Parser.NeighborAddr n : Parser.getNeighborAddrs(myId)) {
            sw.addPort(n.id, n.ip, n.port);
        }

        DatagramSocket sock = new DatagramSocket(myPort);
        System.out.println("[" + myId + "] Switch up on " + myIp + ":" + myPort);
        System.out.println("[" + myId + "] Ports:");
        for (Port p : sw.ports.values())
            System.out.println("  - " + p);

        byte[] buf = new byte[2048];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        while (true) {
            sock.receive(dp);

            String incomingKey = key(dp.getAddress().getHostAddress(), dp.getPort());
            Port incomingPort = sw.ports.get(incomingKey);
            if (incomingPort == null) {
                System.out.println("[" + myId + "] WARNING: received from unknown neighbor " + incomingKey);
                continue;
            }

            String payload = new String(dp.getData(), dp.getOffset(), dp.getLength(), StandardCharsets.UTF_8).trim();
            Packet pkt;
            try {
                pkt = Packet.decode(payload);
            } catch (Exception e) {
                System.out.println("[" + myId + "] Bad frame: " + payload);
                continue;
            }

            System.out.println("[" + myId + "] RX " + incomingKey + " frame=" + pkt.encode());
            sw.learn(pkt.getSrcMac(), incomingPort);
            sw.forward(pkt, incomingPort, sock);
        }
    }

}
