public class Packet {

    private String srcMac;
    private String dstMac;
    private final String srcIP;
    private final String dstIP;
    private final String message;

    public Packet(String srcMac, String dstMac,
                  String srcIP, String dstIP,
                  String message) {

        if (srcMac == null || srcMac.isBlank())
            throw new IllegalArgumentException("srcMac cannot be blank");

        if (dstMac == null || dstMac.isBlank())
            throw new IllegalArgumentException("dstMac cannot be blank");

        if (srcIP == null || srcIP.isBlank())
            throw new IllegalArgumentException("srcIP cannot be blank");

        if (dstIP == null || dstIP.isBlank())
            throw new IllegalArgumentException("dstIP cannot be blank");

        this.srcMac = srcMac.trim();
        this.dstMac = dstMac.trim();
        this.srcIP = srcIP.trim();
        this.dstIP = dstIP.trim();
        this.message = (message == null) ? "" : message;
    }

    // Format:
    // SRC_MAC:DST_MAC:SRC_IP:DST_IP:MESSAGE
    public static Packet decode(String raw) {
        if (raw == null)
            throw new IllegalArgumentException("raw frame is null");

        String[] parts = raw.trim().split(":", 5);

        if (parts.length < 4)
            throw new IllegalArgumentException("Bad frame format");

        String msg = (parts.length == 5) ? parts[4] : "";

        return new Packet(parts[0], parts[1], parts[2], parts[3], msg);
    }

    public String encode() {
        return srcMac + ":" + dstMac + ":" + srcIP + ":" + dstIP + ":" + message;
    }

    // let routers rewrite MAC
    public void setSrcMac(String srcMac) {
        this.srcMac = srcMac;
    }

    public void setDstMac(String dstMac) {
        this.dstMac = dstMac;
    }

    // getters
    public String getSrcMac() { return srcMac; }
    public String getDstMac() { return dstMac; }
    public String getSrcIP() { return srcIP; }
    public String getDstIP() { return dstIP; }
    public String getMessage() { return message; }
}