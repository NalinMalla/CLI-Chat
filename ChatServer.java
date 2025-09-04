package Socket_Programming.CLI_Chat;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.SecureRandom;

public class ChatServer extends Thread {
    final static String DELIMITER = ";";
    static ServerSocket serverSocket;
    static Map<String, Socket> connectedClients = new ConcurrentHashMap<>();     // We aren't using normal HashMap as like StringBuilder it is not built for multi-threaded operations.
    static Map<String, String> clientCredentials = null;        // Like connectedClients key = clientName but value = clientPassword
    static Map<Integer, String> sessions = new ConcurrentHashMap<>();
    static AtomicInteger numberOfActiveConnections = new AtomicInteger(0);
    Socket clientSocket;
    String[] request;
    String response = "";

    static {
        try {
            serverSocket = new ServerSocket(1234);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    ChatServer(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }

    synchronized String getClientNames() {
        StringBuilder clientInfo = new StringBuilder();
        for (String address : clientCredentials.keySet()) {
            if (connectedClients.containsKey(address)) {
                clientInfo.append(address).append("&bOnline").append(DELIMITER);
            } else {
                clientInfo.append(address).append("&bOffLine").append(DELIMITER);
            }
        }

        return clientInfo.toString();
    }

    File getFile(String fileName) {
        File dir = new File("Chat_Files");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File f = new File(dir, fileName);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not create " + fileName + " file.");
            }
        }

        return f;
    }

    synchronized void saveClientLocally(String userName, String userPassword) {
        File f = getFile("ClientList.txt");
        try (FileWriter fWrite = new FileWriter(f, true)) {
            fWrite.write(userName + DELIMITER + userPassword + System.lineSeparator());
        } catch (IOException e) {
            response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not update client list file.");
        }
    }

    synchronized void loadClients() {
        File f = getFile("ClientList.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            clientCredentials = new ConcurrentHashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] clientInfo = line.split(DELIMITER);
                if (clientInfo.length == 2) {
                    clientCredentials.put(clientInfo[0], clientInfo[1]);
                }
            }
        } catch (IOException e) {
            response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not load the client list file.");
        }
    }

    synchronized void saveSession(Integer sessionID, String userName) {
        File f = getFile("Sessions.txt");
        try (FileWriter fWrite = new FileWriter(f, true)) {
            fWrite.write(sessionID.toString() + DELIMITER + userName + System.lineSeparator());
        } catch (IOException e) {
            response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not update sessions file.");
        }
    }

    synchronized void loadSessions() {
        File f = getFile("Sessions.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            sessions = new ConcurrentHashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] sessionInfo = line.split(DELIMITER);
                if (sessionInfo.length == 2) {
                    sessions.put(Integer.parseInt(sessionInfo[0]), sessionInfo[1]);
                }
            }
        } catch (IOException e) {
            response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not load the sessions file.");
        }
    }

    synchronized void removeTerminatedSessionsFromFile() {
        File f = getFile("Sessions.txt");
        try (FileWriter fWrite = new FileWriter(f, false)) {
            for (Integer sessionID : sessions.keySet()) {
                fWrite.write(sessionID.toString() + DELIMITER + sessions.get(sessionID) + System.lineSeparator());
            }
        } catch (IOException e) {
            response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not rewrite sessions file to remove terminated session.");
        }
    }

    File getChatFile(String clientName, String chatPartner) {
        File dir = new File("Chat_Files", clientName);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File f = new File(dir, (chatPartner + ".txt"));
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not create the chat file.");
            }
        }

        return f;
    }

    synchronized void saveChatLocally(String clientName, String chatPartner, String inboundMsg) {
        File f = getChatFile(clientName, chatPartner);
        if (!inboundMsg.isEmpty()) {
            try (FileWriter fWrite = new FileWriter(f, true)) {
                fWrite.write(inboundMsg + System.lineSeparator());
            } catch (IOException e) {
                response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not update chat history in file.");
            }
        }
    }

    synchronized String readChatFileToString(String clientName, String chatPartner) {
        File f = getChatFile(clientName, chatPartner);
        StringBuilder fileContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(String.join("&b", line.split(DELIMITER))).append("&n");
            }
        } catch (IOException e) {
            response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Could not create read chat file to string.");
        }

        return fileContent.toString();
    }

    int generateSessionID() {
        SecureRandom secureRandom = new SecureRandom();
        int sessionID = secureRandom.nextInt(1000);  // Here, secureRandom was used instead of rand because its output is unpredictable even if an attacker knows part of the system as it pulls entropy from secure system sources.
        return sessionID;
    }

    synchronized String parseSessionIDToUserName() {
        String userName = null;

        if (!request[1].isEmpty()) {
            int sessionID = Integer.parseInt(request[1]);
            userName = sessions.get(sessionID);
        }

        if (userName == null) {
            response = ("401" + DELIMITER + request[0] + DELIMITER + "Server: Unauthorized to send message due to invalid Session ID.");
        }
        return userName;
    }

    synchronized void userSignup() {
        String userName = request[1];
        String userPassword = request[2];
        if (clientCredentials.containsKey(userName)) {
            response = ("406" + DELIMITER + "/signup" + DELIMITER + "Server: An account with username " + userName + " already exists.");
        }

        if (!clientCredentials.containsKey(userName)) {
            response = ("201" + DELIMITER + "/signup" + DELIMITER + "Server: New client with username " + userName + " created.");
            saveClientLocally(userName, userPassword);
            clientCredentials.put(userName, userPassword);
            connectedClients.put(userName, clientSocket);
        }
    }

    synchronized void userLogin() {
        String userName = request[1];
        String userPassword = request[2];
        if (!clientCredentials.containsKey(userName)) {
            response = ("404" + DELIMITER + "/login" + DELIMITER + "Server: Account with username " + userName + " doesn't exists.");
        }

        if (clientCredentials.containsKey(userName) && clientCredentials.get(userName).equals(userPassword)) {
            int sessionID = generateSessionID();
            saveSession(sessionID, userName);
            sessions.put(sessionID, userName);      // cannot replace sessionID as it will render obsolete the ones being used in other devices without notification.
            response = ("200" + DELIMITER + "/login" + DELIMITER + sessionID);
            if (connectedClients.containsKey(userName)) {
                connectedClients.replace(userName, clientSocket);
            } else {
                connectedClients.put(userName, clientSocket);
            }
        }

        if (clientCredentials.containsKey(userName) && !clientCredentials.get(userName).equals(userPassword)) {
            response = ("401" + DELIMITER + "/login" + DELIMITER + "Server: Incorrect password for " + userName);
        }
    }

    synchronized void syncChatHistory(String sendersName) {
        String chatHistory = readChatFileToString(sendersName, request[2]);
        response = ("200" + DELIMITER + request[0] + DELIMITER + chatHistory);
    }

    synchronized void rerouteInboundMessage(String sendersName) {
        String receiversName = request[2];
        String timestamp = request[3];
        String msg = request[4];
        Socket receiversSocket = connectedClients.get(receiversName);
        String outboundMsg = sendersName + DELIMITER + timestamp + DELIMITER + msg;

        saveChatLocally(sendersName, receiversName, outboundMsg);
        saveChatLocally(receiversName, sendersName, outboundMsg);

        if (receiversSocket != null) {  // Chat partner is online
            try {
                PrintWriter outToReceiver = new PrintWriter(receiversSocket.getOutputStream(), true);
                outToReceiver.println("200" + DELIMITER + request[0] + DELIMITER + outboundMsg);
            } catch (IOException e) {
                response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Unknown Server Side Error");
                throw new RuntimeException(e);
            }
        }

        response = ("200" + DELIMITER + request[0]);
    }

    synchronized void broadcast(String sendersName) {
        for (Map.Entry<String, Socket> entry : connectedClients.entrySet()) {
            String receiversName = entry.getKey();
            Socket receiversSocket = entry.getValue();
            PrintWriter receiversOut;

            // Here, we aren't using try block to close the PrintWriter after task completion because that will lead to premature closure of all sockets.
            try {
                receiversOut = new PrintWriter(receiversSocket.getOutputStream(), true);
                String outboundMsg = "";
                if (request[0].equals("/broadcast")) {
                    String timestamp = request[2];
                    String msg = request[3];
                    outboundMsg = (request[0] + DELIMITER + sendersName + DELIMITER + timestamp + DELIMITER + msg);
                    saveChatLocally(sendersName, receiversName, outboundMsg);
                    saveChatLocally(receiversName, sendersName, outboundMsg);
                }

                if (request[0].equals("/broadcastUserList")) {
                    outboundMsg = (request[0] + DELIMITER + getClientNames());
                }

                if (!outboundMsg.isEmpty()) {
                    receiversOut.println("200" + DELIMITER + outboundMsg);
                }
            } catch (IOException e) {
                response = ("500" + DELIMITER + request[0] + DELIMITER + "Server: Unknown Server Side Error");
            }
        }
    }

    @Override
    synchronized public void run() {
        System.out.println("New connection established.");
        numberOfActiveConnections.incrementAndGet();

        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            loadClients();
            loadSessions();

            while (true) {
                String inboundMsg = in.readLine();
                if (inboundMsg == null) {
                    continue;
                }
                System.out.println("Request: " + inboundMsg);

                request = inboundMsg.split(DELIMITER);
                if (request[0].equals("/logout")) {
                    sessions.remove(Integer.parseInt(request[1]));
                    response = ("200" + DELIMITER + request[0] + DELIMITER + "Server: User successfully logged out.");
                    removeTerminatedSessionsFromFile();
                    break;
                }

                if (request[0].equals("/users")) {
                    response = ("200" + DELIMITER + request[0] + DELIMITER + getClientNames());
                }

                if (request[0].equals("/signup")) {
                    userSignup();
                }

                if (request[0].equals("/login")) {
                    userLogin();
                }

                if (request[0].equals("/syncChatHistory")) {
                    String userName = parseSessionIDToUserName();
                    if (userName != null) {
                        syncChatHistory(userName);
                    }
                }

                if (request[0].equals("/message")) {
                    String userName = parseSessionIDToUserName();
                    if (userName != null) {
                        rerouteInboundMessage(userName);
                    }
                }

                if (request[0].equals("/broadcast") || request[0].equals("/broadcastUserList")) {
                    String userName = parseSessionIDToUserName();
                    if (userName != null) {
                        broadcast(userName);
                    }
                }

                if (!response.isEmpty()) {
                    out.println(response);
                    System.out.println("Response: " + response);
                    response = "";
                }

                System.out.println("Iteration Ended.");
            }
        } catch (IOException e) {
            System.out.println("ERROR: Connection with client unexpectedly broken.");
            throw new RuntimeException(e);
        } finally {
            numberOfActiveConnections.decrementAndGet();
            System.out.println("Terminating connection.");
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.print("\033[H\033[2J");      // Clears output terminal
        while (ChatServer.numberOfActiveConnections.getPlain() < 100) {
            System.out.println("Waiting for a new client to connect with server.");
            Socket clientSocket = serverSocket.accept();
            ChatServer server = new ChatServer(clientSocket);
            server.start();
//            server.join();
//            System.out.println("A client connection has been terminated.");
        }
    }
}

