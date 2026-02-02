import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Host {

    //Two threads needed, one dedicated to waiting to receive any incoming packets --
    //and the other dedicated to sending out packets

    public static void main(String[] args) throws Exception {

        //Get info
        String myMac = args[1];
        String neighbor = Parser.getNeighborAddr(myMac);

        //Open socket
        DatagramSocket socket = new DatagramSocket(Parser.getDevicePort(myMac));

        //Make two threads
        ExecutorService es = Executors.newFixedThreadPool(2);

        //Start receiving thread
        es.submit(new receiveTask(socket, myMac));

        //Start sending thread loop
        es.submit(new sendingTask(socket, myMac, neighbor));

    }

    static class receiveTask implements Runnable {

        private DatagramSocket socket;
        private String myMac;

        public receiveTask(DatagramSocket socket, String myMac) {

            this.socket = socket;
            this.myMac = myMac;

        }

        public void run() {

            //make buffer
            byte[] buffer = new byte[1024];
            DatagramPacket frame = new DatagramPacket(buffer, buffer.length);

            while (true) {


                socket.receive(frame);

                //parse the frame
                String message = new String(frame.getData(), frame.getOffset(), frame.getLength(), StandardCharsets.UTF_8);
                Packet packet = Packet.decode(message);


                if (packet.getDstMac().equals(myMac)) {

                    System.out.println("Message From " + packet.getSrcMac() + ": " + packet.getMessage);

                }

                else {

                    System.out.println("Frame For Other Host. Destination : " + packet.getDstMac + "Source : " + packet.getSrcMac);

                }


            }

        }

    }

     static class sendingTask implements Runnable {

        private DatagramSocket socket;
        private String myMac;
        private String neighbor;

        public sendingTask(DatagramSocket socket, String myMac, String neighbor) {

            this.socket = socket;
            this.myMac = myMac;
            this.neighbor = neighbor;

        }

         public void run() {

             //Create Scanner
             Scanner scanner = new Scanner(System.in);

             while (true) {

                 //Prompt user for destination MAC then message
                 System.out.println("Enter Destination");
                 String destMAC = scanner.nextLine();

                 System.out.println("Enter Message To Send");
                 String message = scanner.nextLine();

                 //Create virtual frame string (Packet Class)
                 Packet packet = new Packet(myMac, destMAC, message);
                 byte[] data = packet.encode().getBytes(StandardCharsets.UTF_8);

                 InetAddress switchIP = Parser.getDeviceIP(neighbor);
                 int switchPort = Parser.getDevicePort(neighbor);

                 //Send UDP packet to designated switch
                 DatagramPacket frame = new DatagramPacket(data, data.length, switchIP, switchPort);

                 socket.send(frame);
                 System.out.println("Frame Sent To Switch");

             }

         }

     }

}
