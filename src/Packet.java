public class Packet {
    private final String srcMac;
    private final String dstMac;
    private final String message;

    public Packet(String srcMac, String dstMac, String message){
        if(srcMac == null || srcMac.isBlank()){
            throw new IllegalArgumentException("srcMac cannot be blank");
        }
        if (dstMac == null || dstMac.isBlank()) {
            throw new IllegalArgumentException("dstMac cannot be blank");
        }
        if (message == null) {
            message = "";
        }
        this.srcMac = srcMac.trim();
        this.dstMac = dstMac.trim();
        this.message = message;
    }

    // parses SRC:DST:MESSAGE into a packet
    public static Packet decode(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw frame is null");
        }
        String s = raw.trim();

        // splits it into 3 parts
        String[] parts = s.split(":", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Bad frame (need SRC:DST:MSG): " + raw);
        }

        String src = parts[0].trim();
        String dst = parts[1].trim();
        String msg = (parts.length == 3) ? parts[2] : ""; // allows an empty message

        if (src.isEmpty() || dst.isEmpty()) {
            throw new IllegalArgumentException("Bad frame (empty SRC or DST): " + raw);
        }

        return new Packet(src, dst, msg);
    }
    // converts it to the wire format SRC:DST:MESSAGE
    public String encode() {
        return srcMac + ":" + dstMac + ":" + message;
    }

    // getters
    public String getSrcMac() { return srcMac; }
    public String getDstMac() { return dstMac; }
    public String getMessage() { return message; }

}
