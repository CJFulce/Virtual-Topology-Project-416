public class Packet {

    private int type; // 0 = hosts, 1 = routers
    private String srcMac;
    private String dstMac;
    private final String srcIP;
    private final String dstIP;
    private final String message;

    public Packet(int type, String srcMac, String dstMac,
            String srcIP, String dstIP,
            String message) {

        if (type != 0 && type != 1)
            throw new IllegalArgumentException("type cannot be blank, must be 0 or 1");

        if (srcMac == null || srcMac.isBlank())
            throw new IllegalArgumentException("srcMac cannot be blank");

        if (dstMac == null || dstMac.isBlank())
            throw new IllegalArgumentException("dstMac cannot be blank");

        if (srcIP == null || srcIP.isBlank())
            throw new IllegalArgumentException("srcIP cannot be blank");

        if (dstIP == null || dstIP.isBlank())
            throw new IllegalArgumentException("dstIP cannot be blank");

        this.type = type;
        this.srcMac = srcMac.trim();
        this.dstMac = dstMac.trim();
        this.srcIP = srcIP.trim();
        this.dstIP = dstIP.trim();
        this.message = (message == null) ? "" : message;
    }

    // Format:
    // TYPE:SRC_MAC:DST_MAC:SRC_IP:DST_IP:MESSAGE
    public static Packet decode(String raw) {
        if (raw == null)
            throw new IllegalArgumentException("raw frame is null");

        String[] parts = raw.trim().split(":", 6);

        if (parts.length < 5)
            throw new IllegalArgumentException("Bad frame format");

        int type = Integer.parseInt(parts[0]);
        String dstIP = parts[4];
        String msg = (parts.length >= 6) ? (parts[5] == null ? "" : parts[5]) : "";

        return new Packet(type, parts[1], parts[2], parts[3], dstIP, msg);
    }

    public String encode() {
        return type + ":" + srcMac + ":" + dstMac + ":" + srcIP + ":" + dstIP + ":" + message;
    }

    // let routers rewrite MAC
    public void setSrcMac(String srcMac) {
        this.srcMac = srcMac;
    }

    public void setDstMac(String dstMac) {
        this.dstMac = dstMac;
    }

    // getters
    public int getType() {
        return type;
    }

    public String getSrcMac() {
        return srcMac;
    }

    public String getDstMac() {
        return dstMac;
    }

    public String getSrcIP() {
        return srcIP;
    }

    public String getDstIP() {
        return dstIP;
    }

    public String getMessage() {
        return message;
    }
}