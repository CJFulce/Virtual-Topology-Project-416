import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Host {

    //Two threads needed, one dedicated to waiting to receive any incoming packets
    //and the other dedicated to sending out packets

    public static void main(String[] args) throws Exception {

        //Make instance of Parser
        Parser parser = new Parser();

        //Get info
        String myMac = args[0];
        String myIP = parser.getVirtualIPAddr(myMac);
        List<String> neighbors = parser.getNeighborAddr(myMac);
        String neighbor = neighbors.getFirst();

        //Open socket
        DatagramSocket socket = new DatagramSocket(parser.getDevicePort(myMac));

        //Make two threads
        ExecutorService es = Executors.newFixedThreadPool(2);

        //Start receiving thread
        es.submit(new receiveTask(socket, myMac));

        //Start sending thread loop
        es.submit(new sendingTask(socket, myMac, myIP, neighbor, parser));

    }

    static class receiveTask implements Runnable {

        private final DatagramSocket socket;
        private final String myMac;

        public receiveTask(DatagramSocket socket, String myMac) {

            this.socket = socket;
            this.myMac = myMac;

        }

        public void run() {

            //make buffer
            byte[] buffer = new byte[1024];
            DatagramPacket frame = new DatagramPacket(buffer, buffer.length);

            while (true) {

                try {

                    socket.receive(frame);

                }

                catch (IOException e) {

                    throw new RuntimeException(e);


                }

                String message = new String(frame.getData(), frame.getOffset(), frame.getLength(), StandardCharsets.UTF_8);
                Packet packet = Packet.decode(message);


                if (packet.getDstMac().equals(myMac)) {

                    System.out.println("Message From " + packet.getSrcMac() + ": " + packet.getMessage());

                }

                else {

                    System.out.println("Frame For Other Host. Destination : " + packet.getDstMac() + "Source : " + packet.getSrcMac());

                }


            }

        }

    }

     static class sendingTask implements Runnable {

        private final DatagramSocket socket;
        private final String myMac;
        private final String myIP;
        private final String neighbor;
        private final Parser parser;

        public sendingTask(DatagramSocket socket, String myMac, String myIP, String neighbor, Parser parser) {

            this.socket = socket;
            this.myMac = myMac;
            this.myIP = myIP;
            this.neighbor = neighbor;
            this.parser = parser;

        }

         public void run() {

             //Create Scanner
             Scanner scanner = new Scanner(System.in);

             while (true) {

                 //Prompt user for destination MAC, destination IP, & message
                 System.out.println("Enter Destination MAC");
                 String destMAC = scanner.nextLine();

                 System.out.println("Enter Destination IP");
                 String destIP = scanner.nextLine();

                 System.out.println("Enter Message To Send");
                 String message = scanner.nextLine();

                 //Create virtual frame string (Packet Class)
                 Packet packet = new Packet(myMac, destMAC, myIP, destIP, message);
                 byte[] data = packet.encode().getBytes(StandardCharsets.UTF_8);

                 InetAddress switchIP;
                 try {

                     switchIP = InetAddress.getByName(parser.getDeviceIP(neighbor));

                 }

                 catch (UnknownHostException e) {

                     throw new RuntimeException(e);

                 }

                 int switchPort = parser.getDevicePort(neighbor);

                 //Send UDP packet to designated switch
                 DatagramPacket frame = new DatagramPacket(data, data.length, switchIP, switchPort);

                 try {

                     socket.send(frame);

                 } catch (IOException e) {

                     throw new RuntimeException(e);

                 }

                 System.out.println("Frame Sent To Switch");

             }

         }

     }

}
