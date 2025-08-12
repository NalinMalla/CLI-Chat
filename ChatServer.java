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
    static Map<String, String> clientDetails = null;
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
        for (String address : clientDetails.keySet()) {
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
//        Path currentDirPath = Paths.get("src", "main", "java", "Socket_Programming", "CLI_Chat", "Chat_Files");
//        File dir = new File(currentDirPath.toString());
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
            clientDetails = new ConcurrentHashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] clientInfo = line.split("&nbsp");
                if (clientInfo.length == 2) {
                    clientDetails.put(clientInfo[0], clientInfo[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    File getChatFile(String clientName, String chatPartner) throws IOException {
//        Path currentDirPath = Paths.get("src", "main", "java", "Socket_Programming", "CLI_Chat", "Chat_Files");
//        File dir = new File(currentDirPath.toString(), clientName);
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


    @Override
    public void run() {
        Socket receiversSocket = null;
        String receiversName = "";
        numberOfActiveConnections.incrementAndGet();

        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            loadClients();
            while (clientName.isEmpty()) {
                out.println("Server: Client Authentication.");
                clientName = in.readLine();
                String loginMode = in.readLine();
                clientPassword = in.readLine();

                System.out.println(loginMode + " " + clientName + " " + clientPassword);

                if (clientDetails.containsKey(clientName)) {
                    System.out.println(clientName + " found.");
                    if (loginMode.equals("SIGNUP")) {
                        out.println("Server: An account with username " + clientName + " already exists.");
                        clientName = "";
                        clientPassword = "";
                    } else if (clientDetails.get(clientName).equals(clientPassword)) {
                        out.println("Server: Welcome back " + clientName);
                        System.out.println("Reconnecting with " + clientName);
                        if (connectedClients.containsKey(clientName)) {
                            connectedClients.replace(clientName, clientSocket);
                        } else {
                            connectedClients.put(clientName, clientSocket);
                        }
                    } else {
                        out.println("Server: Incorrect password for " + clientName);
                        clientName = "";
                        clientPassword = "";
                    }
                } else {
                    System.out.println(clientName + " not found.");
                    if (loginMode.equals("LOGIN")) {
                        out.println("Server: Account with username " + clientName + " doesn't exists.");
                        System.out.println("Server: Account with username " + clientName + " doesn't exists.");
                        clientName = "";
                        clientPassword = "";
                    } else {
                        System.out.println("Adding " + clientName + " to Client List.");
                        out.println("Server: New client with username " + clientName + " created.");
                        saveClientLocally(clientName, clientPassword);
                        clientDetails.put(clientName, clientPassword);
                        connectedClients.put(clientName, clientSocket);
                    }
                }
            }

            broadcastClientList();

            while (!inboundMsg.equals("&exit")) {
                if (receiversSocket == null) {
                    System.out.println("Requesting chat partners name from " + clientName);
                }
                try {
                    inboundMsg = in.readLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                if (inboundMsg.equals("&switch")) {
                    receiversSocket = null;
                    receiversName = "";
                    continue;
                }

                String[] msg = inboundMsg.split("&nbsp");
                if (msg.length == 1) {
                    receiversSocket = connectedClients.get(msg[0]);
                    receiversName = msg[0];
                    try {
                        loadChat(clientName, msg[0]);
                        String chatHistory = readChatFileToString(clientName, msg[0]);
                        System.out.println("Syncing Chat History with " + clientName);
                        out.println("Server: Syncing Chat History.");
                        out.println(chatHistory);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if ((msg.length == 3)) {
                    try {
                        saveChatLocally(clientName, receiversName, inboundMsg);
                        saveChatLocally(receiversName, clientName, inboundMsg);
                        if (receiversSocket != null) {
                            PrintWriter outToReceiver = new PrintWriter(receiversSocket.getOutputStream(), true);
                            outToReceiver.println(inboundMsg);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                } else {
                    out.println("Server: Error! Invalid message.");
                }
            }
            out.println("Server: Terminating Connection");
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
        while (ChatServer.numberOfActiveConnections.getPlain() < 100) {
            System.out.println("Waiting for a new client.");
            Socket clientSocket = serverSocket.accept();
            ChatServer server = new ChatServer(clientSocket);
            System.out.println("New connection established");
            server.start();
//            server.join();
//            System.out.println("A client connection has been terminated.");
        }
    }
}

