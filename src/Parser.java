import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;


public class Parser {
    {
        File configFile = new File("config.txt");

        try (Scanner configScanner = new Scanner(configFile)){
            while (configScanner.hasNextLine()){
                String rawConfig = configScanner.nextLine();
                System.out.println(rawConfig);
            }

        } catch (FileNotFoundException error){
            System.out.println(error);
            error.printStackTrace();
        }
    }
public static void main(String[] args){
    Parser testParser = new Parser();
    }
}

