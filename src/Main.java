import java.io.*;
import java.net.URISyntaxException;

public class Main {

    public static void main(String[] args) throws IOException {
        try {
            File playlistFile = new File(getClasspath() + /*"\\YTPlaylistSaver*/"\\playlists.txt"); //TODO add the outcommented stuff back in final version, messes with IDE
            File apiFile = new File(getClasspath() + /*"\\YTPlaylistSaver*/"\\APIKey.txt"); //TODO same here
            if(playlistFile.createNewFile()){
                System.out.println("New file created " + playlistFile.getName());
            }
            if(apiFile.createNewFile()){
                System.out.println("New file created " + apiFile.getName());
            }
            try (BufferedReader fileReader = new BufferedReader(new FileReader(apiFile.getPath()))){
                String key = fileReader.readLine();
                if(key.equals("")){
                    System.out.println("No API-Key found, enter it here (You can get one under :"); //TODO put in the youtube api getting thingy
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    while(true) {
                        String newKey = reader.readLine();
                        if (!newKey.equals("")) {
                            key = newKey;
                            try (FileWriter writer = new FileWriter(apiFile.getPath())) {
                                writer.write(newKey);
                            } catch (Exception e) {
                                System.out.println("Something went wrong trying to save your key: " + e + "\n Try to paste it yourself into " + apiFile.getPath() + ".");
                            }
                            break;
                        } else {
                            System.out.println("Enter your API-Key please.");
                        }
                    }
                }
                //TODO code lol
            }
        }

        catch (URISyntaxException e) {
            System.out.println("Location of the program could not be fetched. Reason: " + e);
        }

    }

    public static String getClasspath() throws URISyntaxException {
        File file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String fileName = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getName();
        return file.getPath()/*.replace(fileName, "")*/; //TODO add this back when compiling, it doesen't work in the IDE but is needed when executed normally
    }
}
