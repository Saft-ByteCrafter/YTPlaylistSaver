import com.google.gson.*;

import java.io.*;
import java.net.URISyntaxException;

public class Main {

    public static void main(String[] args) throws IOException {
        try {

            /* Filecreation*/
            File playlistFile = new File(getClasspath() + /*"\\YTPlaylistSaver*/"\\playlists.txt"); //TODO add the outcommented stuff back in final version, messes with IDE
            File apiFile = new File(getClasspath() + /*"\\YTPlaylistSaver*/"\\APIKey.txt"); //TODO same here
            File dataFile = new File(getClasspath() + /*"\\YTPlaylistSaver*/"\\data.json");
            if(playlistFile.createNewFile()){
                System.out.println("New file created " + playlistFile.getName());
            }
            if(apiFile.createNewFile()){
                System.out.println("New file created " + apiFile.getName());
            }
            if(dataFile.createNewFile()){
                System.out.println("New file created " + dataFile.getName());
            }

            Gson jsonBuilder = new Gson();
            Gson jsonToString = new GsonBuilder().setPrettyPrinting().create();
            JsonArray localSaves;

            /*get the local playlists*/
            try (BufferedReader dataFileReader = new BufferedReader((new FileReader(dataFile.getPath())))){
                String line;
                StringBuilder content = new StringBuilder();
                while((line = dataFileReader.readLine()) != null){
                    content.append(line);
                }
                localSaves = jsonBuilder.fromJson(content.toString(), JsonArray.class);
            }

            /*get or initialize the API-Key*/
            try (BufferedReader apiReader = new BufferedReader(new FileReader(apiFile.getPath()))){
                String key = apiReader.readLine();
                if(key == null || key.equals("")){
                    System.out.println("No API-Key found, enter it here (You can get one as described here: https://developers.google.com/youtube/v3/getting-started):");
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

                /*go through all the saved playlists*/
                try (BufferedReader fileReader = new BufferedReader(new FileReader(playlistFile.getPath()))){
                    String playlistString = fileReader.readLine();
                    here:
                    while(playlistString != null){
                        String playlistName = playlistString.split(": ")[0];
                        String playlistID = playlistString.split(": ")[1];
                        JsonArray playlist = new JsonArray();
                        JsonObject response = ApiGetter.getApiInfo("https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet%2CcontentDetails&maxResults=25&playlistId=" + playlistID + "&key=" + key);
                        if (response.has("error")) {
                            System.out.println("There was an error trying to fetch the " + playlistName + " playlist from the YouTube API. Error code: " + response.get("error").getAsJsonObject().get("code").getAsInt() + " " + response.get("error").getAsJsonObject().get("message").getAsString());
                            playlistString = fileReader.readLine();
                            continue;
                        }
                        /*all the vids that are in the playlist rn are put into our list*/
                        for(JsonElement video : response.get("items").getAsJsonArray()){
                            playlist.add(video);
                        }

                        /*in case there are more than 50 vids*/
                        while(response.has("nextPageToken")) {
                            String nextPageToken = response.get("nextPageToken").getAsString();
                            response = ApiGetter.getApiInfo("https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet%2CcontentDetails&maxResults=25&pageToken=" + nextPageToken + "&playlistId=" + playlistID + "&key=" + key);
                            if (response.has("error")) {
                                System.out.println("There was an error trying to fetch the " + playlistName + " playlist from the YouTube API. Error code: " + response.get("error").getAsJsonObject().get("code").getAsInt() + " " + response.get("error").getAsJsonObject().get("message").getAsString());
                                playlistString = fileReader.readLine();
                                continue here;
                            }
                            for(JsonElement video : response.get("items").getAsJsonArray()){
                                playlist.add(video);
                            }
                        }

                        /*compares the two playlists (or adds a new one if the list wasn't there before*/
                        if(localSaves == null) {
                            localSaves = new JsonArray();
                            addPlaylistToSave(localSaves, playlistID, playlist);
                        }
                        if(!comparePlaylist(localSaves, playlistID)){ //returns false if the playlist has not yet been initialized in the datafile
                            addPlaylistToSave(localSaves, playlistID, playlist);
                        }
                        //TODO put the comparation-code here

                        else{
                            //TODO if there is nothing in the file, it should not be possible to not have any playlists AND still have stuff in the file, make it so that that is the case
                        }
                        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(dataFile.getPath()))) {
                            bufferedWriter.write(jsonToString.toJson(localSaves));
                        }
                        //TODO code lol (add everything that is comparing it and make it possible to add playlists obv
                        playlistString = fileReader.readLine();
                    }
                } //TODO maybe add catch or more chatches tho that there is no exception in the main method signature
            }
        }

        catch (URISyntaxException e) {
            System.out.println("Location of the program could not be fetched. Reason: " + e);
        }

    }

    private static void addPlaylistToSave(JsonArray localSaves, String playlistID, JsonArray playlist) {
        JsonObject newPlaylist = new JsonObject();
        newPlaylist.addProperty("id", playlistID);
        JsonArray videos = new JsonArray();
        for (JsonElement video : playlist) {
            JsonObject info = new JsonObject();
            info.addProperty("id", video.getAsJsonObject().get("id").getAsString());
            info.addProperty("title", video.getAsJsonObject().get("snippet").getAsJsonObject().get("title").getAsString());
            info.addProperty("placement", video.getAsJsonObject().get("snippet").getAsJsonObject().get("position").getAsInt());
            videos.add(info);
        }
        newPlaylist.add("videos", videos);
        localSaves.add(newPlaylist);
    }

    public static boolean comparePlaylist(JsonArray localSaves, String playlistID){
        for(JsonElement playlist : localSaves){
            if(playlist.getAsJsonObject().get("id").getAsString().equals(playlistID)) {
                return true;
            }
        }
        return false;
    }

    public static String getClasspath() throws URISyntaxException {
        File file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String fileName = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getName();
        return file.getPath()/*.replace(fileName, "")*/; //TODO add this back when compiling, it doesn't work in the IDE but is needed when executed normally
    }
}
