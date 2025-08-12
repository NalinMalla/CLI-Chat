package Socket_Programming.CLI_Chat;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;


public class ChatClient {
    String clientName = "";
    String clientPassword = "";
    String inboundMsg = "";
    String serverMsg = "";      //This is the inboundMsg sent by server which is not to be overridden by ReceiveInboundMsg.
    String outboundMsg = "";
    String sendTo = "";
    String[] clientList;     // We aren't using normal HashMap as like StringBuilder it is not built for multi-threaded operations.
    ArrayList<String> chatHistory = null;
    AtomicInteger newlyDetectedClients;
    Scanner sc;

    enum MODE {LOGIN, SIGNUP, DASHBOARD, CHAT}

    ;
    MODE currentMode;

    ChatClient() {
        sc = new Scanner(System.in);
        char loginMode = 'x';
        while (loginMode == 'x') {
            System.out.print("Do you wish to: \na) Login to you exiting account.\nb) Create a new account. \nInput either a or b. \n>:");
            loginMode = sc.nextLine().charAt(0);
            switch (loginMode) {
                case 'a':
                    this.currentMode = MODE.LOGIN;
                    break;

                case 'b':
                    this.currentMode = MODE.SIGNUP;
                    break;

                default:
                    System.out.println("Invalid Input.");
                    loginMode = 'x';
            }
        }

        this.newlyDetectedClients = new AtomicInteger();
    }

    String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }

    public ArrayList<String> parseChatHistory(String content) {
        if (content == null || content.isEmpty()) return new ArrayList<>();

        // Split using line separator regex to handle different OS formats
        String[] lines = content.split("\\R"); // \R matches any line break (Unix, Windows, Mac)
        return new ArrayList<>(Arrays.asList(lines));
    }

    void credentialValidation() {
        if (clientName.trim().isEmpty() || clientPassword.trim().isEmpty()) {
            System.out.println("Invalid Input: Username and Password cannot be empty.");
            System.out.println("Press enter to continue");
            clientName = "";
            clientPassword = "";
            sc.nextLine();
        }
        if (!clientPassword.isEmpty() && currentMode == MODE.SIGNUP) {
            System.out.print("Confirm Password: ");
            String confirmPassword = sc.nextLine();
            if (!clientPassword.equals(confirmPassword)) {
                System.out.println("Error: The passwords you have inputted don't match.");
                System.out.println("Press enter to continue");
                clientName = "";
                clientPassword = "";
                sc.nextLine();
            }
        }
    }

    boolean clientInList() {
        for (String clientAddress : clientList) {
            String[] client = clientAddress.split("&b");
            if (client[0].equals(sendTo)) {
                return true;
            }
        }
        return false;
    }

    void displayLogin() {
        System.out.print("\033[H\033[2J");      // Clears output terminal
        System.out.println(currentMode == MODE.LOGIN ? "LOGIN" : "SIGNUP");
        System.out.println("------");
        if (!serverMsg.isEmpty()) {
            System.out.println(serverMsg);
        }
    }

    void displayDashboard() {
        System.out.print("\033[H\033[2J");      // Clears output terminal
        System.out.println("DASHBOARD");
        System.out.println("---------");
        if (clientList == null) {
            System.out.println("Connecting with server.");
        } else {
            if (outboundMsg.equals("&switch") || sendTo.isEmpty()) {
                System.out.println("Client List:");
                for (int i = 0; i < clientList.length; i++) {
                    String[] client = clientList[i].split("&b");
                    if (client[0].equals(clientName)) {
                        System.out.println("" + (i + 1) + ") " + client[0] + " (You)");
                    } else {
                        System.out.println("" + (i + 1) + ") " + client[0] + " (" + client[1] + ")");
                    }
                }
                System.out.print("\nWhich client will you like to chat with? \n>:");
            }
        }
    }

    void displayChat() {
        System.out.print("\033[H\033[2J");      // Clears output terminal
        System.out.println("CHAT ROOM (" + clientName + "->" + sendTo + ")");
        System.out.println("-------------------------------------------");
        System.out.println("To exit this session input '&exit' into the message box and to chat with another client input '&switch'.");
        if (chatHistory != null) {
            for (String line : chatHistory) {
                String[] msg = line.split("&nbsp");
                if (msg.length == 3) {
                    System.out.println(((msg[0].equals(clientName)) ? "You" : msg[0]) + " (" + msg[1] + ") : " + msg[2]);
                }
            }
        } else {
            System.out.println("ERROR: Chat history could not be loaded.");
        }

        System.out.print(">:");
    }

    class SendOutboundMsg extends Thread {
        Socket clientSocket;

        SendOutboundMsg(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
        }

        @Override
        public synchronized void run() {
            try (
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            ) {
                while (!outboundMsg.equals("&exit")) {
                    if (currentMode == MODE.LOGIN || currentMode == MODE.SIGNUP) {
                        displayLogin();
                        if (serverMsg.equals("Server: Welcome back " + clientName) || serverMsg.equals("Server: New client with username " + clientName + " created.")) {
                            currentMode = MODE.DASHBOARD;
                            serverMsg = "";
                        } else if (inboundMsg.equals("Server: Client Authentication.")) {
                            System.out.print("Username: ");
                            clientName = sc.nextLine();
                            System.out.print("Password: ");
                            clientPassword = sc.nextLine();

                            credentialValidation();

                            if (!clientName.isEmpty() && !clientPassword.isEmpty()) {
                                out.println(clientName);
                                if (currentMode == MODE.LOGIN) {
                                    out.println("LOGIN");
                                } else {
                                    out.println("SIGNUP");
                                }
                                out.println(clientPassword);
                            }
                        }
                    } else if (sendTo.isEmpty() && currentMode == MODE.DASHBOARD) {
                        displayDashboard();
                        sendTo = sc.nextLine();

                        System.out.print("\033[H\033[2J");
                        if (sendTo.isEmpty()) {
                            System.out.println("No input for client address was given.");
                        } else if (sendTo.equals("&exit")) {
                            outboundMsg = "&exit";
                            break;
                        } else {
                            if (clientInList()) {
                                System.out.println("You are about enter into a chat room with " + sendTo + ".");
                                out.println(sendTo);
                                currentMode = MODE.CHAT;
                            } else {
                                System.out.println("Client not found in list.");
                                sendTo = "";
                            }
                        }
                        System.out.println("Press Enter to continue.");
                        sc.nextLine();
                    } else if (currentMode == MODE.CHAT) {
                        if (!sendTo.isEmpty()) {
                            displayChat();
                            if (chatHistory != null) {
                                outboundMsg = sc.nextLine();
                                if (!outboundMsg.equals("&exit")) {
                                    if (outboundMsg.equals("&switch")) {
                                        out.println(outboundMsg);
                                        sendTo = "";
                                        chatHistory = null;
                                        currentMode = MODE.DASHBOARD;
                                    } else if (chatHistory != null) {
                                        outboundMsg = clientName + "&nbsp" + getCurrentDateTime() + "&nbsp" + outboundMsg;
                                        out.println(outboundMsg);
                                        chatHistory.add(outboundMsg);
                                        outboundMsg = "";
                                    }
                                }
                            }

                        } else {
                            currentMode = MODE.DASHBOARD;
                        }
                    }
                }
                out.println("&exit");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    class ReceiveInboundMsg extends Thread {
        Socket clientSocket;

        ReceiveInboundMsg(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
        }

        @Override
        public synchronized void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            ) {
                while (!outboundMsg.equals("&exit")) {
                    try {
                        inboundMsg = in.readLine();
                        if (inboundMsg == null) {
                            inboundMsg = "";
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (inboundMsg.equals("Server: Client Authentication.")) {
                        System.out.println(inboundMsg);
                    } else if (inboundMsg.equals("Server: New Client Found.")) {
                        try {
                            inboundMsg = in.readLine();
                            clientList = inboundMsg.split("&nbsp");
                            if (currentMode == MODE.DASHBOARD) {
                                displayDashboard();
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (inboundMsg.equals("Server: Syncing Chat History.") && currentMode == MODE.CHAT) {
                        try {
                            inboundMsg = in.readLine();
                            chatHistory = parseChatHistory(inboundMsg);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (!inboundMsg.isEmpty() && chatHistory != null) {
                        chatHistory.add(inboundMsg);
                        displayChat();
                    } else {
                        serverMsg = inboundMsg;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public void activateChat() {
        ReceiveInboundMsg inbound = null;
        SendOutboundMsg outbound = null;
        try (
                Socket clientSocket = new Socket("127.0.0.1", 1234);
        ) {
            inbound = new ReceiveInboundMsg(clientSocket);
            outbound = new SendOutboundMsg(clientSocket);

            inbound.start();
            outbound.start();

            inbound.join();
            outbound.join();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("Deactivating client " + clientName + "\n");
        }
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.activateChat();
    }
}
