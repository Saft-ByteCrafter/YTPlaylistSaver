import com.google.gson.*;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

public class Main {

    public static void main(String[] args) {

        try {

            String ossplit = getOSDirSplitString();

            /* File creation*/
            File directory = new File(getClasspath() + ossplit + "YTPlaylistSaver");
            if (directory.mkdir()) {
                System.out.println("Created new directory");
            }
            File playlistFile = new File(directory + ossplit + "playlists.txt");
            File apiFile = new File(directory + ossplit + "APIKey.txt");
            File dataFile = new File(directory + ossplit + "data.json");

            if (playlistFile.createNewFile()) {
                System.out.println("New file created " + playlistFile.getName());
            }
            if (apiFile.createNewFile()) {
                System.out.println("New file created " + apiFile.getName());
            }
            if (dataFile.createNewFile()) {
                System.out.println("New file created " + dataFile.getName());
            }

            Gson jsonBuilder = new Gson();
            Gson jsonToString = new GsonBuilder().setPrettyPrinting().create();
            JsonArray localSaves;

            /*get the local playlists*/
            try (BufferedReader dataFileReader = new BufferedReader((new FileReader(dataFile.getPath())))) {
                String line;
                StringBuilder content = new StringBuilder();
                while ((line = dataFileReader.readLine()) != null) {
                    content.append(line);
                }
                localSaves = jsonBuilder.fromJson(content.toString(), JsonArray.class);
            }

            String key;
            /*get or initialize the API-Key*/
            try (BufferedReader apiReader = new BufferedReader(new FileReader(apiFile.getPath()))) {
                key = apiReader.readLine();
                if (key == null || key.isBlank()) {
                    System.out.println("No API-Key found, enter it here (You can get one as described here: https://developers.google.com/youtube/v3/getting-started):");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    while (true) {
                        String newKey = reader.readLine();
                        if (!newKey.isBlank()) {
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
                try (BufferedReader fileReader = new BufferedReader(new FileReader(playlistFile.getPath()))) {
                    String playlistString = fileReader.readLine();
                    Map<String, String> playlists = new LinkedHashMap<>();

                    List<String> differentVideos = new ArrayList<>();

                    while (playlistString != null) {
                        playlists.put(playlistString.split(": ")[0], playlistString.split(": ")[1]);
                        playlistString = fileReader.readLine();
                    }

                    here:
                    for (String playlistName : playlists.keySet()) {
                        String playlistID = playlists.get(playlistName);
                        JsonArray playlist = new JsonArray();
                        JsonObject response = ApiGetter.getApiInfo("https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet%2CcontentDetails&maxResults=25&playlistId=" + playlistID + "&key=" + key);
                        if (response.equals(new JsonObject())) {
                            continue;
                        }
                        /*all the videos that are in the playlist rn are put into our list*/
                        for (JsonElement video : response.get("items").getAsJsonArray()) {
                            playlist.add(video);
                        }

                        /*in case there are more than 50 videos*/
                        while (response.has("nextPageToken")) {
                            String nextPageToken = response.get("nextPageToken").getAsString();
                            response = ApiGetter.getApiInfo("https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet%2CcontentDetails&maxResults=25&pageToken=" + nextPageToken + "&playlistId=" + playlistID + "&key=" + key);
                            if (response.has("error")) {
                                System.out.println("There was an error trying to fetch the " + playlistName + " playlist from the YouTube API. Error code: " + response.get("error").getAsJsonObject().get("code").getAsInt() + " " + response.get("error").getAsJsonObject().get("message").getAsString());
                                continue here;
                            }
                            for (JsonElement video : response.get("items").getAsJsonArray()) {
                                playlist.add(video);
                            }
                        }

                        /*compares the two playlists (or adds a new one if the list wasn't there before*/
                        if (localSaves == null) {
                            localSaves = new JsonArray();
                            addPlaylistToSave(localSaves, playlistID, playlist);
                        } else {

                            if (!comparePlaylist(localSaves, playlistID)) { //returns false if the playlist has not yet been initialized in the datafile
                                addPlaylistToSave(localSaves, playlistID, playlist);
                            }

                            /*all the videos that are in the saved playlist put into a list*/
                            JsonArray savedPlaylist = new JsonArray();
                            for (JsonElement pl : localSaves) {
                                if (pl.getAsJsonObject().get("id").getAsString().equals(playlistID)) {
                                    savedPlaylist = pl.getAsJsonObject().get("videos").getAsJsonArray();
                                    break;
                                }
                            }

                            int removedVideos = 0;
                            List<JsonElement> toBeRemoved = new ArrayList<>();
                            matchingTitle:
                            for (JsonElement savedVideo : savedPlaylist) {
                                for (JsonElement fetchedVideo : playlist) {
                                    if (savedVideo.getAsJsonObject().get("id").getAsString().equals(fetchedVideo.getAsJsonObject().get("contentDetails").getAsJsonObject().get("videoId").getAsString())) {
                                        if (!(savedVideo.getAsJsonObject().get("position").getAsInt() == fetchedVideo.getAsJsonObject().get("snippet").getAsJsonObject().get("position").getAsInt())) {
                                            savedVideo.getAsJsonObject().addProperty("position", fetchedVideo.getAsJsonObject().get("snippet").getAsJsonObject().get("position").getAsInt());
                                        }
                                        if (!savedVideo.getAsJsonObject().get("title").getAsString().equals(fetchedVideo.getAsJsonObject().get("snippet").getAsJsonObject().get("title").getAsString())) {
                                            differentVideos.add("\"" + savedVideo.getAsJsonObject().get("title").getAsString() + "\" changed to \"" +
                                                    fetchedVideo.getAsJsonObject().get("snippet").getAsJsonObject().get("title").getAsString() + "\" at position " +
                                                    (savedVideo.getAsJsonObject().get("position").getAsInt() + 1) + " in playlist " + playlistName + ".");
                                        }
                                        continue matchingTitle;
                                    }
                                }
                                toBeRemoved.add(savedVideo); //if it has not been found in the new playlist
                                removedVideos++;
                            }
                            for (JsonElement video : toBeRemoved) {
                                savedPlaylist.remove(video);
                            }

                            if (removedVideos > 0) {
                                if (removedVideos == 1)
                                    System.out.println(removedVideos + " video has been removed from the \"" + playlistName + "\" playlist.");
                                else
                                    System.out.println(removedVideos + " video have been removed from the \"" + playlistName + "\" playlist.");
                            }

                            /*add newly added videos*/
                            int addedVideos = 0;
                            videoSaved:
                            for (JsonElement fetchedVideo : playlist) {
                                for (JsonElement savedVideo : savedPlaylist) {
                                    if (fetchedVideo.getAsJsonObject().get("snippet").getAsJsonObject().get("title").getAsString().equals(savedVideo.getAsJsonObject().get("title").getAsString())) {
                                        continue videoSaved;
                                    }
                                }
                                addVideoToPlaylist(savedPlaylist, fetchedVideo);
                                addedVideos++;
                            }
                            if (addedVideos > 0) {
                                if (addedVideos == 1)
                                    System.out.println(addedVideos + " video has been added to the \"" + playlistName + "\" playlist.");
                                else
                                    System.out.println(addedVideos + " video have been added to the \"" + playlistName + "\" playlist.");
                            }
                        }
                        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(dataFile.getPath()))) {
                            bufferedWriter.write(jsonToString.toJson(localSaves));
                        }
                    } //end of while(playlistString != null)

                    System.out.println();
                    if (!differentVideos.isEmpty()) {
                        for (String out : differentVideos) {
                            System.out.println(out);
                        }
                    } else {
                        System.out.println("Nothing has been changed in your playlist(s).");
                    }
                    System.out.println();
                    System.out.println("Sadly, there is currently no way of verifying whether a video is unlisted at the moment, you used to be able to watch those videos if they were in a playlist before, that doesn't seem to apply to at least some videos though ;-;");

                    System.out.println("\n");
                    System.out.println("These are your playlists that are being checked at the moment:");
                    if (playlists.keySet().isEmpty()) {
                        System.out.println("none");
                    } else {
                        for (String playlistName : playlists.keySet()) {
                            System.out.println(playlistName + " (ID: " + playlists.get(playlistName) + ")");
                        }
                    }
                    System.out.println("\n");

                    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
                        printCommands();
                        System.out.println();

                        actionPerformed:
                        while (true) {
                            String input = in.readLine();

                            if (input.contains(":")) {
                                System.out.println("Please don't include colons (:) in your Name (there are no colons in your ID and you know it).");
                                continue;
                            }
                            if (input.toLowerCase().startsWith("add")) {
                                String[] parts = input.split(" ");
                                if (parts.length != 3) {
                                    System.out.println("Please format your request like this: \"add [playlistName] [playlistID]\" (without the brackets ([]) and quotation marks (\"\") and with the right amount of spaces (two in this case)).");
                                    System.out.println();
                                    continue;
                                }
                                for (String playlistName : playlists.keySet()) {
                                    if (playlistName.equals(parts[1])) {
                                        System.out.println("A playlist with this name already exists, please choose a different one.");
                                        System.out.println();
                                        continue actionPerformed;
                                    }
                                }
                                playlists.put(parts[1], parts[2]);
                                System.out.println("The Playlist \"" + parts[1] + "\" has been added.");
                            } else if (input.toLowerCase().startsWith("edit")) {
                                String[] parts = input.split(" ");
                                if (parts.length != 3) {
                                    System.out.println("Please format your request like this: \"edit [playlistName] [newPlaylistID]\" (without the brackets ([]) and quotation marks (\"\") and with the right amount of spaces (two in this case)).");
                                    System.out.println();
                                    continue;
                                }
                                for (String playlistName : playlists.keySet()) {
                                    if (playlistName.equals(parts[1])) {
                                        System.out.println("Are you sure you want to change the ID of \"" + playlistName + "\" from \"" + playlists.get(playlistName) + "\" to \"" + parts[2] + "\"? Y/N");
                                        String answer = in.readLine();
                                        if (answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("yes")) {
                                            playlists.put(playlistName, parts[2]);
                                            System.out.println("ID for \"" + playlistName + "\" has been changed to \"" + playlists.get(playlistName) + "\".");
                                        } else System.out.println("Action cancelled");
                                        System.out.println();
                                        continue actionPerformed;
                                    }
                                }
                                System.out.println("Your requested playlist is not being tracked, make sure you spelled it correctly (also don't actually enter the brackets ([]) or quotation marks (\"\").");
                            } else if (input.toLowerCase().startsWith("remove")) {
                                String[] parts = input.split(" ");
                                if (parts.length != 2) {
                                    System.out.println("Please format your request like this: \"remove [playlistName]\" (without the brackets ([]) and quotation marks (\"\") and with the right amount of spaces (one in this case)).");
                                    System.out.println();
                                    continue;
                                }
                                for (String playlistName : playlists.keySet()) {
                                    if (playlistName.equals(parts[1])) {
                                        System.out.println("Are you sure you want to remove \"" + playlistName + "\" from the checked playlists? Y/N");
                                        String answer = in.readLine();
                                        if (answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("yes")) {
                                            playlists.remove(playlistName);
                                            System.out.println("\"" + playlistName + "\" has been removed and is not being tracked anymore.");
                                        } else System.out.println("Action cancelled");
                                        System.out.println();
                                        continue actionPerformed;
                                    }
                                }
                                System.out.println("Your requested playlist is not being tracked, make sure you spelled it correctly (also don't actually enter the brackets ([]) or quotation marks (\"\").");
                            } else if (input.equalsIgnoreCase("list")) {
                                System.out.println("These are your playlists that are being checked at the moment:");
                                for (String playlistName : playlists.keySet()) {
                                    System.out.println(playlistName + " (ID: " + playlists.get(playlistName) + ")");
                                }
                            } else if (input.equalsIgnoreCase("help")) {
                                printCommands();
                            } else if (input.toLowerCase().startsWith("changeapi")) {
                                String[] parts = input.split(" ");
                                if (parts.length != 2) {
                                    System.out.println("Please format your request like this: \"changeApi [newAPI-key]\" (without the brackets ([]) and quotation marks (\"\") and with the right amount of spaces (one in this case)).");
                                    System.out.println();
                                    continue;
                                }
                                System.out.println("Are you sure you want to change your API-key from \"" + key + "\" to \"" + parts[1] + "\"? Y/N");
                                String answer = in.readLine();
                                if (answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("yes")) {
                                    key = parts[1];
                                    System.out.println("Your API-key has been changed to \"" + key + "\".");
                                } else System.out.println("Action cancelled");
                            } else if (input.equalsIgnoreCase("stop")) break;
                            System.out.println();
                        }

                        try (BufferedWriter bw = new BufferedWriter(new FileWriter(playlistFile.getPath()))) {
                            for (String playlistName : playlists.keySet()) {
                                bw.write(playlistName + ": " + playlists.get(playlistName) + "\n");
                            }
                        }
                        try (BufferedWriter bw = new BufferedWriter(new FileWriter(apiFile.getPath()))) {
                            bw.write(key);
                        }
                        System.out.println("Bye!");
                    }

                }
            }
        } catch (URISyntaxException e) {
            System.out.println("Important files could be fetched: " + e);
        } catch (IOException e) {
            System.out.println("Relevant save-files could not be created or read: " + e);
        }

    }

    private static String getOSDirSplitString() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "\\";
        } else {
            return "/";
        }
    }

    private static void printCommands() {
        System.out.println("To add another playlist, enter \"add [playlistName] [playlistID]\".");
        System.out.println("(You find a playlist's ID after the \"?list=\" in it's respective URL)");
        System.out.println("To edit a playlist's ID enter \"edit [playlistName] [newPlaylistID]\".");
        System.out.println("To remove a playlist, enter \"remove [playlistName]\".");
        System.out.println("To list all active playlists, enter \"list\".");
        System.out.println("To change your API-key, enter \"changeAPI [newAPI-key]\".");
        System.out.println("To exit the program, simply enter \"stop\".");
    }

    private static void addVideoToPlaylist(JsonArray savedPlaylist, JsonElement fetchedVideo) {
        JsonObject newVideo = new JsonObject();
        newVideo.addProperty("id", fetchedVideo.getAsJsonObject().get("contentDetails").getAsJsonObject().get("videoId").getAsString());
        newVideo.addProperty("title", fetchedVideo.getAsJsonObject().get("snippet").getAsJsonObject().get("title").getAsString());
        newVideo.addProperty("position", fetchedVideo.getAsJsonObject().get("snippet").getAsJsonObject().get("position").getAsInt());
        savedPlaylist.add(newVideo);
    }

    private static void addPlaylistToSave(JsonArray localSaves, String playlistID, JsonArray playlist) {
        JsonObject newPlaylist = new JsonObject();
        newPlaylist.addProperty("id", playlistID);
        JsonArray videos = new JsonArray();
        for (JsonElement video : playlist) {
            addVideoToPlaylist(videos, video);
        }
        newPlaylist.add("videos", videos);
        localSaves.add(newPlaylist);
    }

    public static boolean comparePlaylist(JsonArray localSaves, String playlistID) {
        for (JsonElement playlist : localSaves) {
            if (playlist.getAsJsonObject().get("id").getAsString().equals(playlistID)) {
                return true;
            }
        }
        return false;
    }

    public static String getClasspath() throws URISyntaxException {
        File file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String fileName = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getName();
        return file.getPath().substring(0, file.getPath().length() - (fileName.length()));
    }
}
