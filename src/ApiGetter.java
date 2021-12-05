import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiGetter {

    public static JsonObject getApiInfo(String urlString) {

        try {

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String input;
                StringBuilder response = new StringBuilder();

                while ((input = in.readLine()) != null) {
                    response.append(input);
                }
                in.close();

                Gson gson = new Gson();
                return gson.fromJson(response.toString(), JsonObject.class);

            }
            else{
                try(BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream()))){
                    String input;
                    StringBuilder response = new StringBuilder();

                    while ((input = in.readLine()) != null){
                        response.append(input);
                    }

                    Gson gson = new Gson();
                    JsonObject responseAsJson = gson.fromJson(response.toString(), JsonObject.class);
                    if(responseAsJson.has("error")){
                        JsonObject error = responseAsJson.getAsJsonObject().get("error").getAsJsonObject();
                        int errorCode = error.get("code").getAsInt();
                        switch (errorCode){
                            case 400:
                                System.out.println("Your API-key is not valid. Please change it to a valid one. (Error code 400)");
                                break;
                            case 404:
                                System.out.println("PlaylistID " + urlString.split("&playlistId=")[1].split("&key=")[0] + " does not exist. Make sure it is spelt correctly and that the playlist is set to public. (Error code 404)");
                                break;
                            default:
                                System.out.println("An error occurred while trying to fetch data from the YouTube API: " + errorCode + ": " + error.get("message").getAsString());
                        }

                    }
                }
            }
        } catch (IOException e) {
            System.out.println("An error has occurred: " + e);
        }

        return new JsonObject();
    }

}
