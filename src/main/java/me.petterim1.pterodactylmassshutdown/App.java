package me.petterim1.pterodactylmassshutdown;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;


import java.io.*;
import java.util.*;

public class App {

    public static void main(String[] args) {
        System.out.println("Server power actions tool for Pterodactyl Panel");
        System.out.println("Made by PetteriM1");
        System.out.println("---------------------");
        Scanner scanner = new Scanner(System.in);
        String host = null;
        String token = null;
        System.out.println("Loading host from config...");
        try {
            BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.dir") + File.separatorChar + "host.txt"));
            host = in.readLine();
            in.close();
            System.out.println("Target host: " + host);
        } catch (Exception e) {
        }
        boolean needsSaving = false;
        if (host == null || host.isEmpty()) {
            System.out.println("No saved host found");
            needsSaving = true;
        }
        while (host == null || host.isEmpty()) {
            System.out.println("Please input the host address");
            host = scanner.nextLine();
        }
        while (host == null || !host.toLowerCase().startsWith("http")) {
            System.out.println("Please input a valid http address");
            host = scanner.nextLine();
        }
        if (host.charAt(host.length() - 1) == '/') {
            host = host.substring(0, host.length() - 1);
        }
        if (needsSaving) {
            System.out.println("Saving the host address...");
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(System.getProperty("user.dir") + File.separatorChar + "host.txt"));
                out.write(host);
                out.close();
            } catch (Exception e) {
                System.out.println("Failed to save the host address");
                e.printStackTrace();
            }
        }
        System.out.println("Loading saved API token...");
        try {
            BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.dir") + File.separatorChar + "token.txt"));
            token = in.readLine();
            in.close();
        } catch (Exception e) {
        }
        needsSaving = false;
        if (token == null || token.isEmpty()) {
            System.out.println("No saved API token found");
            needsSaving = true;
        }
        while (token == null || token.isEmpty()) {
            System.out.println("Please input your API token");
            token = scanner.nextLine();
        }
        if (needsSaving) {
            System.out.println("Saving the API token...");
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(System.getProperty("user.dir") + File.separatorChar + "token.txt"));
                out.write(token);
                out.close();
            } catch (Exception e) {
                System.out.println("Failed to save the API token");
                e.printStackTrace();
            }
        }
        Map<String, String> servers = new HashMap<>();
        System.out.println("Fetching the server list...");
        HttpClient client = HttpClientBuilder.create().build();
        try {
            String requestUrl = host + "/api/application/servers";
            System.out.println("Connecting to " + requestUrl);
            HttpGet requestGet = new HttpGet(requestUrl);
            requestGet.setHeader("Accept", "application/json");
            requestGet.setHeader("Content-Type", "application/json");
            requestGet.setHeader("Authorization", "Bearer " + token);
            HttpResponse response = client.execute(requestGet);
            if (response.getStatusLine().getStatusCode() != 200) {
                System.out.println("Failed: " + EntityUtils.toString(response.getEntity()));
                System.out.println(response);
                return;
            }
            Map<String, Object> serverList = new Gson().fromJson(EntityUtils.toString(response.getEntity()), new MapTypeToken().getType());
            List<Map<?, ?>> serverData = (List<Map<?, ?>>) serverList.get("data");
            for (Map<?, ?> map : serverData) {
                Map<?, ?> attributes = ((Map<?, ?>) map.get("attributes"));
                servers.put((String) attributes.get("identifier"), (String) attributes.get("name"));
            }
            try {
                Map<?, Map<?, ?>> meta = (Map<?, Map<?, ?>>) serverList.get("meta");
                Map<?, ?> pagination = meta.get("pagination");
                double totalPages = (double) pagination.get("total_pages");
                int pages = (int) totalPages;
                if (pages > 1) {
                    for (int page = 2; page <= pages; page++) {
                        requestUrl = host + "/api/application/servers?page=" + page;
                        System.out.println("Connecting to " + requestUrl);
                        requestGet = new HttpGet(requestUrl);
                        requestGet.setHeader("Accept", "application/json");
                        requestGet.setHeader("Content-Type", "application/json");
                        requestGet.setHeader("Authorization", "Bearer " + token);
                        response = client.execute(requestGet);
                        if (response.getStatusLine().getStatusCode() != 200) {
                            System.out.println("Failed: " + EntityUtils.toString(response.getEntity()));
                            System.out.println(response);
                            return;
                        }
                        serverList = new Gson().fromJson(EntityUtils.toString(response.getEntity()), new MapTypeToken().getType());
                        serverData = (List<Map<?, ?>>) serverList.get("data");
                        for (Map<?, ?> map : serverData) {
                            Map<?, ?> attributes = ((Map<?, ?>) map.get("attributes"));
                            servers.put((String) attributes.get("identifier"), (String) attributes.get("name"));
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (servers.isEmpty()) {
            System.out.println("No servers found!");
            return;
        }
        System.out.println("Found " + servers.size() + " servers: " + servers.values());
        String action = null;
        while (action == null || (!action.equalsIgnoreCase("start") && !action.equalsIgnoreCase("stop") && !action.equalsIgnoreCase("restart") && !action.equalsIgnoreCase("kill"))) {
            System.out.println("Which power action you want to run? (start, stop, restart, kill)");
            action = scanner.nextLine();
        }
        action = action.toLowerCase();
        boolean ignoreOffline = false;
        if (action.equals("start") || action.equals("restart")) {
            System.out.println("Do you want to ignore servers with (offline) in their name? (yes/no)");
            if ("yes".equalsIgnoreCase(scanner.nextLine())) {
                ignoreOffline = true;
                System.out.println("Ignoring servers that are marked to stay offline");
            }
        }
        String message = null;
        boolean sendMessage = false;
        int repeatMessage = 1;
        if (!action.equals("start")) {
            System.out.println("Do you want to broadcast a message using 'say' command on servers supporting it? Enter a message or press enter to skip (You will be asked if you want to repeat the message x times next)");
            message = scanner.nextLine();
            sendMessage = message != null && !message.trim().isEmpty();
            if (!sendMessage) {
                System.out.println("No message");
            } else {
                while (true) {
                    System.out.println("How many times you want the message to be sent with a 10 seconds delay? Enter a number or press enter to use the default (1)");
                    String number = scanner.nextLine();
                    if (number == null || number.trim().isEmpty()) {
                        break;
                    }
                    try {
                        repeatMessage = Integer.parseInt(number);
                        if (repeatMessage > 0) {
                            break;
                        }
                    } catch (NumberFormatException ignore) {
                    }
                    System.out.println("The number must be a positive integer");
                }
            }
        }
        if (sendMessage) {
            System.out.println("Message '" + message + "' will be broadcasted " + repeatMessage + " times");
        }
        System.out.println("Do you want to " + action + " the servers now? (yes/no)");
        if (!"yes".equalsIgnoreCase(scanner.nextLine())) {
            System.out.println("Aborting operation");
            return;
        }
        if (sendMessage) {
            for (int messageCount = 1; messageCount <= repeatMessage; messageCount++) {
                System.out.println("Broadcasting the message (" + messageCount + "/" + repeatMessage + ")...");
                for (String server : servers.keySet()) {
                    if (ignoreOffline) {
                        String srv = servers.get(server);
                        if (srv != null && srv.toLowerCase().startsWith("(offline)")) {
                            System.out.println("Skipping a server that was marked to stay offline: " + srv);
                            continue;
                        }
                    }
                    try {
                        String requestUrl = host + "/api/client/servers/" + server + "/command";
                        System.out.println("Connecting to " + requestUrl);
                        HttpPost requestPost = new HttpPost(requestUrl);
                        requestPost.setHeader("Accept", "application/json");
                        requestPost.setHeader("Content-Type", "application/json");
                        requestPost.setHeader("Authorization", "Bearer " + token);
                        requestPost.setEntity(new StringEntity("{\n  \"command\": \"say " + message + "\"\n}"));
                        HttpResponse response = client.execute(requestPost);
                        if (response.getStatusLine().getStatusCode() == 204) {
                            System.out.println("Done! Message broadcasted successfully on " + servers.get(server));
                        } else if (response.getStatusLine().getStatusCode() == 502) {
                            System.out.println("Status 502: " + servers.get(server) + " is probably offline");
                        } else {
                            System.out.println("Failed: " + EntityUtils.toString(response.getEntity()));
                            System.out.println(response);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                try {
                    System.out.println("Waiting 10 seconds...");
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        int count = 0;
        System.out.println("Running the power action...");
        for (String server : servers.keySet()) {
            if (ignoreOffline) {
                String srv = servers.get(server);
                if (srv != null && srv.toLowerCase().startsWith("(offline)")) {
                    System.out.println("Skipping a server that was marked to stay offline: " + srv);
                    continue;
                }
            }
            try {
                String requestUrl = host + "/api/client/servers/" + server + "/power";
                System.out.println("Connecting to " + requestUrl);
                HttpPost requestPost = new HttpPost(requestUrl);
                requestPost.setHeader("Accept", "application/json");
                requestPost.setHeader("Content-Type", "application/json");
                requestPost.setHeader("Authorization", "Bearer " + token);
                requestPost.setEntity(new StringEntity("{\n  \"signal\": \"" + action + "\"\n}"));
                HttpResponse response = client.execute(requestPost);
                if (response.getStatusLine().getStatusCode() == 204) {
                    System.out.println("Done! Power action '" + action + "' successful for " + servers.get(server));
                    count++;
                } else {
                    System.out.println("Failed: " + EntityUtils.toString(response.getEntity()));
                    System.out.println(response);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        System.out.println("All operations done! Ran power action '" + action + "' on " + count + "/" + servers.size() + " servers at " + host);
    }

    private static class MapTypeToken extends TypeToken<Map<String, Object>> {
    }
}
