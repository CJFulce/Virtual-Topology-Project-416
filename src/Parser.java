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
                if(rawConfig.contains(deviceId)){
                    String[] lineElements = rawConfig.split(" ");
                        int parsedPortNumber = Integer.parseInt(lineElements[3]);
                        System.out.println("Gotcha");
                        return parsedPortNumber;
                }else{
                    System.out.println("Nothing here");
                }
            
            }
        } catch (FileNotFoundException error) {
            System.err.println(error);
            error.printStackTrace();
        }
        return 0;
     
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
    }

}


