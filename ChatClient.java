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
    final static String DELIMITER = ";";
    String userName = "";
    String userPassword = "";
    String sessionID = "";
    String[] inboundMsg;
    String[] serverMsg;      //This is the inboundMsg sent by server which is not to be overridden by ReceiveInboundMsg.
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
        this.newlyDetectedClients = new AtomicInteger();
//        this.serverMsg = new String[]{"","",""};     // Required to copy inboundMsg to serverMsg for the first time
    }

    boolean isCurrentModeInitialized() {
        char loginMode = 'c';
        while (loginMode == 'c') {
            System.out.print("\033[H\033[2J");      // Clears output terminal
            System.out.print("Do you wish to: \na) Login to you exiting account.\nb) Create a new account. \nInput either a or b to continue and x to exit. \n>:");
            loginMode = sc.nextLine().charAt(0);
            switch (loginMode) {
                case 'a':
                    this.currentMode = MODE.LOGIN;
                    break;

                case 'b':
                    this.currentMode = MODE.SIGNUP;
                    break;

                case 'x':
                    return false;

                default:
                    loginMode = 'c';
            }
        }

        return true;
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
        if (userName.trim().isEmpty() || userPassword.trim().isEmpty()) {
            userName = "";
            userPassword = "";
            System.out.println("Invalid Input: Username and Password cannot be empty.");
            System.out.println("Press enter to continue");
            sc.nextLine();
        }

        if (!userPassword.isEmpty() && currentMode == MODE.SIGNUP) {
            System.out.print("Confirm Password: ");
            String confirmPassword = sc.nextLine();

            if (!userPassword.equals(confirmPassword)) {
                userName = "";
                userPassword = "";
                System.out.println("Error: The passwords you have inputted don't match.");
                System.out.println("Press enter to continue");
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

    synchronized void displayUserAuthentication() {
        System.out.print("\033[H\033[2J");      // Clears output terminal
        System.out.println(currentMode == MODE.LOGIN ? "LOGIN" : "SIGNUP");
        System.out.println("------");
        if (inboundMsg != null) {
            System.out.println(inboundMsg[2]);
        }
    }

    synchronized void displayDashboard() {
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
                    if (client[0].equals(userName)) {
                        System.out.println("" + (i + 1) + ") " + client[0] + " (You)");
                    } else {
                        System.out.println("" + (i + 1) + ") " + client[0] + " (" + client[1] + ")");
                    }
                }
                System.out.print("\nWhich client will you like to chat with? \n>:");
            }
        }
    }

    synchronized void displayChat() {
        System.out.print("\033[H\033[2J");      // Clears output terminal
        System.out.println("CHAT ROOM (" + userName + "->" + sendTo + ")");
        System.out.println("-------------------------------------------");
        System.out.println("To exit this session input '&exit' into the message box and to chat with another client input '&switch'.");
        if (chatHistory != null) {
            for (String line : chatHistory) {
                String[] msg = line.split(DELIMITER);
                if (msg.length == 3) {
                    System.out.println(((msg[0].equals(userName)) ? "You" : msg[0]) + " (" + msg[1] + ") : " + msg[2]);
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

        synchronized void authenticateUser() {
            if (inboundMsg[0].charAt(0) == '2') {        // Authentication Success
                if (inboundMsg[1].equals("/signup")) {
                    userName = "";
                    userPassword = "";
                }

                if (inboundMsg[1].equals("/login")) {
                    sessionID = inboundMsg[2];
                    currentMode = MODE.DASHBOARD;
                }
            }


            if (inboundMsg[0].charAt(0) != '2') {        // Authentication Failed
                userName = "";
                userPassword = "";
                System.out.print("\033[H\033[2J");      // Clears output terminal
                System.out.println(inboundMsg[2]);
                if (inboundMsg[0].equals("406")) {
                    System.out.println("Try logging into this existing account.");
                }

                if (inboundMsg[0].equals("404")) {
                    System.out.println("You may not have an account yet. Try creating new account using this username.");
                }
                System.out.println("Press enter to return to the main menu.");
                sc.nextLine();
                if (!isCurrentModeInitialized()) {
                    outboundMsg = "&exit";
                }
            }
        }

        synchronized void getUserCredentials(PrintWriter out) {
            if (userName.isEmpty()) {
                System.out.print("Username: ");
                userName = sc.nextLine();
                System.out.print("Password: ");
                userPassword = sc.nextLine();

                credentialValidation();

                if (!userName.isEmpty() && !userPassword.isEmpty()) {
                    if (currentMode == MODE.LOGIN) {
                        out.println("/login" + DELIMITER + userName + DELIMITER + userPassword);
                    }
                    if (currentMode == MODE.SIGNUP) {
                        out.println("/signup" + DELIMITER + userName + DELIMITER + userPassword);
                    }
                }
            }
        }

        synchronized void handleUserAuthentication(PrintWriter out) {
            if (inboundMsg != null && (inboundMsg[1].equals("/login") || inboundMsg[1].equals("/signup"))) {
                authenticateUser();
            }
            getUserCredentials(out);
        }

        synchronized void handleDashboardOutput() {
            sendTo = sc.nextLine();

            System.out.print("\033[H\033[2J");
            if (sendTo.isEmpty()) {
                System.out.println("No input for client address was given.");
            }

            if (sendTo.equals("&exit")) {
                outboundMsg = "&exit";
                sendTo = "";
            }

            if (!sendTo.isEmpty()) {
                if (clientInList()) {
                    System.out.println("You are about enter into a chat room with " + sendTo + ".");
                    currentMode = MODE.CHAT;
                } else {
                    System.out.println("Client not found in list.");
                    sendTo = "";
                }
            }
            System.out.println("Press Enter to continue.");
            sc.nextLine();
        }

        synchronized void handleChatOutput(PrintWriter out) {
            outboundMsg = sc.nextLine();
            if (!outboundMsg.equals("&exit") && outboundMsg.equals("&switch")) {
                out.println(outboundMsg);
                sendTo = "";
                chatHistory = null;
                currentMode = MODE.DASHBOARD;
            }

            if (!outboundMsg.equals("&exit") && !outboundMsg.equals("&switch") && chatHistory != null) {
                outboundMsg = sendTo + DELIMITER + getCurrentDateTime() + DELIMITER + outboundMsg;
                out.println("/message" + DELIMITER + sessionID + DELIMITER + outboundMsg);
                chatHistory.add(outboundMsg);
                outboundMsg = "";
            }
        }

        @Override
        public synchronized void run() {
            try (
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            ) {
                while (!outboundMsg.equals("&exit")) {
                    if (currentMode == MODE.SIGNUP || currentMode == MODE.LOGIN) {
                        if (sessionID.isEmpty()) {
                            displayUserAuthentication();
                            handleUserAuthentication(out);
                        }
                        if (!sessionID.isEmpty()) {      // For auto login
                            currentMode = MODE.DASHBOARD;
                        }
                    }

                    if (currentMode == MODE.DASHBOARD) {
                        if (sessionID.isEmpty()) {
                            currentMode = MODE.LOGIN;
                        }
                        if (!sessionID.isEmpty()) {
                            displayDashboard();
                            handleDashboardOutput();
                        }
                    }

                    if (currentMode == MODE.CHAT) {
                        if (!sendTo.isEmpty() && chatHistory != null) {
                            displayChat();
                            handleChatOutput(out);
                        }

                        if (sendTo.isEmpty() || chatHistory == null) {
                            currentMode = MODE.DASHBOARD;
                        }
                    }
//                    inboundMsg = null;
                }

                if (!sessionID.isEmpty()) {       // outboundMsg == "&exit"
                    out.println("/logout" + DELIMITER + sessionID);
                }
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

        synchronized void readInboundMsg(BufferedReader in) {
            try {
                inboundMsg = in.readLine().split(DELIMITER);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            ) {
                while (!outboundMsg.equals("&exit")) {
                    readInboundMsg(in);

                    if (inboundMsg[0].equals("200") && inboundMsg[1].equals("/broadcastUserList")) {
                        System.arraycopy(inboundMsg, 2, clientList, 0, inboundMsg.length - 2);
                        if (currentMode == MODE.DASHBOARD) {
                            displayDashboard();
                        }
                        continue;
                    }

                    if (inboundMsg[0].equals("200") && inboundMsg[1].equals("/syncChatHistory")) {
                        chatHistory = parseChatHistory(inboundMsg[2]);
                        continue;
                    }

                    if (inboundMsg[0].equals("200") && inboundMsg[1].equals("/message") && chatHistory != null && inboundMsg[2].equals(sendTo)) {
                        chatHistory.add(inboundMsg[2]);
                        displayChat();
                        continue;
                    }

//                    System.arraycopy(inboundMsg, 0, serverMsg, 0, inboundMsg.length);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public void activateChat() {
        if (isCurrentModeInitialized()) {
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
                System.out.println("Deactivating client " + userName + "\n");
            }
        } else {
            System.out.println("Exiting Program.");
        }
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.activateChat();
    }
}
