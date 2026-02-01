import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;


public class Parser {
    File configFile;
    private Map<String, Integer> storedValues;

    public Parser(){   
        this.configFile = new File("Virtual-Topology-Project-416/config.txt");
        this.storedValues = new HashMap<>();
    }

    private List<String> initConfig(){
        List<String> configByLines = new ArrayList<>();
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                configByLines.add(rawConfig);
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return configByLines;
    }

    private int getDevicePort(String deviceId){
    
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if(rawConfig.contains(deviceId) && rawConfig.startsWith("device")){
                    String[] lineElements = rawConfig.split(" ");
                        int parsedPortNumber = Integer.parseInt(lineElements[3]);
                        System.out.println("Gotcha");
                        return parsedPortNumber;
                }
                // else{
                //     System.out.println("Nothing here for port");
                // }
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return 0;
    }
    private String getDeviceIP(String deviceId){
    
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if(rawConfig.contains(deviceId) && rawConfig.startsWith("device")){
                    String[] lineElements = rawConfig.split(" ");
                        String parsedIPAddress = lineElements[2];
                        System.out.println("Got IP");
                        return parsedIPAddress;
                }
                //     else{
                // //     System.out.println("Nothing here for IP");
                // }
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return "0.0.0.0";
    }

    private List<String> getNeighborAddr(String deviceId){
        List<String> neighbors = new ArrayList<>();
        try (Scanner configScanner = new Scanner(this.configFile)) {
            while (configScanner.hasNextLine()) {
                String rawConfig = configScanner.nextLine();
                if(rawConfig.startsWith("link")){
                    String[] lineElements = rawConfig.split(" ");
                    // use equals to compare strings
                    if(lineElements[1].equals(deviceId)){
                        // add the neighbor id or ip (adjust index per your file format)
                        neighbors.add(lineElements[2]);
                    }if(lineElements[2].equals(deviceId)){
                        neighbors.add(lineElements[1]);
                    }
                }

            }
            return neighbors;

        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return neighbors; //will return empty if not seen in config file
    }
public static void main(String[] args){

    Parser testParser = new Parser();

    List<String> scannedConfig = testParser.initConfig();
        for (int i = 0; i < scannedConfig.size(); i++) { //test print
            System.out.println(i + ": " + scannedConfig.get(i));
        }
    int portNumber = testParser.getDevicePort("S2");

    if(portNumber == 0){
        System.out.println("Error loading port number");
    }else{
        System.out.printf("Port Number: %d\n", portNumber);
    }

    String ipAddress = testParser.getDeviceIP("S2");
    
    if(ipAddress == "0.0.0.0\n"){
        System.out.println("Error loading IP Address");
    }else{
        System.out.printf("IP Address: %s\n", ipAddress);
    }

    List<String> neighborList = testParser.getNeighborAddr("S2");
    
    if(neighborList.isEmpty()){
        System.out.println("No neighbors found");
    }else{
        for(String neighbor : neighborList){
            System.out.printf("Neighbor: %s\n", neighbor);
        }
        
    }
    }

}


