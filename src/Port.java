import java.util.Objects;

public class Port {
    final String neighborId;
    final String ip;
    final int udpPort;

    final String portName;
    final String virtualIP;

    // Constructor for Switch
    public Port(String neighborId, String ip, int udpPort) {
        this.neighborId = neighborId;
        this.ip = ip;
        this.udpPort = udpPort;
        this.portName = null;
        this.virtualIP = null;
    }

    // Constructor for Router
    public Port(String portName, String neighborId, String ip, int udpPort, String virtualIP) {
        this.portName = portName;
        this.neighborId = neighborId;
        this.ip = ip;
        this.udpPort = udpPort;
        this.virtualIP = virtualIP;
    }

    String key() {
        return ip + ":" + udpPort;
    }

    @Override
    public String toString() {
        if (portName != null) {
            return portName + " -> " + neighborId + "(" + ip + ":" + udpPort + ")";
        }
        return neighborId + "(" + ip + ":" + udpPort + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Port))
            return false;
        Port other = (Port) o;
        return udpPort == other.udpPort && Objects.equals(ip, other.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, udpPort);
    }
}