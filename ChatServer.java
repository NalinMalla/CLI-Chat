package Socket_Programming.CLI_Chat;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatServer extends Thread {
    static ServerSocket serverSocket;
    static Map<String, Socket> connectedClients = new ConcurrentHashMap<>();     // We aren't using normal HashMap as like StringBuilder it is not built for multi-threaded operations.
    static Map<String, String> clientCredentials = null;        // Like connectedClients key = clientName but value = clientPassword
    static AtomicInteger numberOfActiveConnections = new AtomicInteger(0);
    Socket clientSocket = null;
    ArrayList<String> chatHistory = new ArrayList<>();
    String clientName = "";
    String clientPassword = "";
    String inboundMsg = "";

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

    void broadcastClientList() throws IOException {
        System.out.println("Broadcasting Client List with " + clientName + " to all clients.");
        StringBuilder clientInfo = new StringBuilder();
        for (String address : clientCredentials.keySet()) {
            if (connectedClients.containsKey(address)) {
                clientInfo.append(address).append("&bOnline").append("&nbsp");
            } else {
                clientInfo.append(address).append("&bOffLine").append("&nbsp");
            }
        }

        for (Socket socket : connectedClients.values()) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("Server: New Client Found.");
            out.println(clientInfo);
        }
    }

    File getClientFile() throws IOException {
        File dir = new File("Chat_Files");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File f = new File(dir, "ClientList.txt");
        if (!f.exists()) {
            f.createNewFile();
        }

        return f;
    }

    void saveClientLocally(String userName, String userPassword) throws IOException {
        File f = getClientFile();
        try (FileWriter fWrite = new FileWriter(f, true)) {
            fWrite.write(userName + "&nbsp" + userPassword + System.lineSeparator());
        }
    }

    void loadClients() throws IOException {
        File f = getClientFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            clientCredentials = new ConcurrentHashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] clientInfo = line.split("&nbsp");
                if (clientInfo.length == 2) {
                    clientCredentials.put(clientInfo[0], clientInfo[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    File getChatFile(String clientName, String chatPartner) throws IOException {
        File dir = new File("Chat_Files", clientName);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File f = new File(dir, (chatPartner + ".txt"));
        if (!f.exists()) {
            f.createNewFile();
        }

        return f;
    }

    void saveChatLocally(String clientName, String chatPartner, String inboundMsg) throws IOException {
        File f = getChatFile(clientName, chatPartner);
        if (!inboundMsg.isEmpty()) {
            try (FileWriter fWrite = new FileWriter(f, true)) {
                fWrite.write(inboundMsg + System.lineSeparator());
            }
        }
    }

    void loadChat(String clientName, String chatPartner) throws IOException {
        File f = getChatFile(clientName, chatPartner);
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            chatHistory = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                chatHistory.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String readChatFileToString(String clientName, String chatPartner) throws IOException {
        File f = getChatFile(clientName, chatPartner);
        StringBuilder fileContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fileContent.toString();
    }

    void authenticateUser(BufferedReader in, PrintWriter out) throws IOException {
        while (clientName.isEmpty()) {
            out.println("Server: Client Authentication.");
            clientName = in.readLine();
            String loginMode = in.readLine();
            clientPassword = in.readLine();

            if (loginMode.equals("SIGNUP")) {
                if (clientCredentials.containsKey(clientName)) {
                    out.println("Server: An account with username " + clientName + " already exists.");
                    clientName = "";
                    clientPassword = "";
                }

                if (!clientCredentials.containsKey(clientName)) {
                    System.out.println("Adding " + clientName + " to Client List.");
                    out.println("Server: New client with username " + clientName + " created.");
                    saveClientLocally(clientName, clientPassword);
                    clientCredentials.put(clientName, clientPassword);
                    connectedClients.put(clientName, clientSocket);
                }
            }

            if (loginMode.equals("LOGIN")) {
                if (!clientCredentials.containsKey(clientName)) {
                    out.println("Server: Account with username " + clientName + " doesn't exists.");
                    System.out.println("Server: Account with username " + clientName + " doesn't exists.");
                    clientName = "";
                    clientPassword = "";
                }

                if (clientCredentials.containsKey(clientName) && clientCredentials.get(clientName).equals(clientPassword)) {
                    out.println("Server: Welcome back " + clientName);
                    System.out.println("Reconnecting with " + clientName);
                    if (connectedClients.containsKey(clientName)) {
                        connectedClients.replace(clientName, clientSocket);
                    } else {
                        connectedClients.put(clientName, clientSocket);
                    }
                }

                if (clientCredentials.containsKey(clientName) && !clientCredentials.get(clientName).equals(clientPassword)) {
                    out.println("Server: Incorrect password for " + clientName);
                    clientName = "";
                    clientPassword = "";
                }
            }
        }
    }

    void rerouteInboundMessage(BufferedReader in, PrintWriter out) throws IOException {
        Socket receiversSocket = null;
        String receiversName = "";

        while (!inboundMsg.equals("&exit")) {
            if (receiversName.isEmpty()) {
                System.out.println("Requesting chat partners name from " + clientName);
            }
            try {
                inboundMsg = in.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (inboundMsg.equals("&switch")) {     // Case where client choosing another chat partner
                receiversSocket = null;
                receiversName = "";
                continue;
            }

            System.out.println(inboundMsg);
            String[] msg = inboundMsg.split("&nbsp");
            if (msg.length == 1) {      // Case where client is sending only chat partners name.
                receiversSocket = connectedClients.get(msg[0]);
                receiversName = msg[0];
                try {
                    loadChat(clientName, receiversName);
                    String chatHistory = readChatFileToString(clientName, receiversName);
                    System.out.println("Syncing Chat History with " + clientName);
                    out.println("Server: Syncing Chat History.");
                    out.println(chatHistory);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if ((msg.length == 3) && !receiversName.isEmpty()) {      // Case where client is message to chat partner.
                try {
                    saveChatLocally(clientName, receiversName, inboundMsg);
                    saveChatLocally(receiversName, clientName, inboundMsg);
                    if (receiversSocket != null) {  // Chat partner is online
                        PrintWriter outToReceiver = new PrintWriter(receiversSocket.getOutputStream(), true);
                        outToReceiver.println(inboundMsg);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    @Override
    public void run() {
        System.out.println("New connection established.");
        numberOfActiveConnections.incrementAndGet();

        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            loadClients();

            authenticateUser(in, out);

            broadcastClientList();

            rerouteInboundMessage(in, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            clientSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            numberOfActiveConnections.decrementAndGet();
            System.out.println("Terminating connection with " + clientName);
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
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

