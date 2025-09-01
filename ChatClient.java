package Socket_Programming.CLI_Chat;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatClient extends Thread {
    final static int RECONNECT_THRESHOLD = 256;
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
    int sleepDuration = 1;

    enum CHAT_STATE {ACTIVE, CONNECTED, DISCONNECTED, TERMINATED}

    ;
    CHAT_STATE currentChatState;

    enum MODE {LOGIN, SIGNUP, DASHBOARD, CHAT}

    ;
    MODE currentMode;

    ChatClient() {
        sc = new Scanner(System.in);
        this.newlyDetectedClients = new AtomicInteger();
        this.currentChatState = CHAT_STATE.ACTIVE;
//        this.serverMsg = new String[]{"","",""};     // Required to copy inboundMsg to serverMsg for the first time
    }

    boolean isCurrentModeInitialized() {
        String loginMode = "c";
        while (loginMode.equals("c")) {
            clearDisplay();
            System.out.print("Do you wish to: \na) Login to you exiting account.\nb) Create a new account. \nInput either a or b to continue and '&exit' to exit. \n>:");
            loginMode = sc.nextLine();
            switch (loginMode) {
                case "a":
                    this.currentMode = MODE.LOGIN;
                    displayUserAuthentication();
                    break;

                case "b":
                    this.currentMode = MODE.SIGNUP;
                    displayUserAuthentication();
                    break;

                case "&exit":
                    this.currentChatState = CHAT_STATE.TERMINATED;
                    return false;

                default:
                    loginMode = "c";
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
        ArrayList<String> parsedChatHistory = new ArrayList<>();

        if (content != null && !content.isEmpty()) {
            String[] lines = content.split("&n");
            for (String line : lines) {
                String parsedLine = String.join(DELIMITER, line.split("&b"));
                parsedChatHistory.add(parsedLine);
            }
        }

        return parsedChatHistory;
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

    synchronized void clearDisplay() {
        System.out.print("\033[H\033[2J");
    }

    synchronized void displayUserAuthentication() {
        clearDisplay();
        System.out.println(currentMode == MODE.LOGIN ? "LOGIN" : "SIGNUP");
        System.out.println("------");
        if (inboundMsg != null) {
            System.out.println(inboundMsg[2]);
        }
    }

    synchronized void displayDashboard() {
        clearDisplay();
        System.out.println(currentChatState);
        System.out.println("DASHBOARD");
        System.out.println("---------");
        if (clientList == null) {
            System.out.println("Trying to get client list from Server.");
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
        clearDisplay();
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

        synchronized void authenticateUser(PrintWriter out) {
            if (inboundMsg[0].charAt(0) == '2') {        // Authentication Success
                if (inboundMsg[1].equals("/signup")) {
                    userName = "";
                    userPassword = "";
                    currentMode = MODE.LOGIN;
                    clearDisplay();
                    System.out.println(inboundMsg[2]);
                    System.out.println("Try logging into this existing account.");
                    System.out.println("Press enter to return to the main menu.");
                    sc.nextLine();
                    displayUserAuthentication();
                }

                if (inboundMsg[1].equals("/login")) {
                    System.out.println("Setting sessionID");
                    sessionID = inboundMsg[2];
                    out.println("/broadcastUserList" + DELIMITER + sessionID);
                    currentMode = MODE.DASHBOARD;
                }
            }

            if (inboundMsg[0].charAt(0) != '2') {        // Authentication Failed
                userName = "";
                userPassword = "";
                clearDisplay();
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

        synchronized void sendUserCredentials(PrintWriter out) {
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
                authenticateUser(out);
            }
            sendUserCredentials(out);
        }

        synchronized void handleDashboardOutput(PrintWriter out) {
            sendTo = sc.nextLine();

            clearDisplay();
            if (sendTo.isEmpty()) {
                System.out.println("No input for client address was given.");
            }

            if (sendTo.equals("&exit")) {
                currentChatState = CHAT_STATE.TERMINATED;
                sendTo = "";
            }

            if (!sendTo.isEmpty()) {
                if (clientInList()) {
                    out.println("/syncChatHistory" + DELIMITER + sessionID + DELIMITER + sendTo);

//                    while (!inboundMsg[1].equals("/syncChatHistory")) {
//                    }
                    if (inboundMsg[0].charAt(0) == '2') {
                        currentMode = MODE.CHAT;
                        System.out.println("You are about enter into a chat room with " + sendTo + ".");
                    }
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
            if (outboundMsg.equals("&exit")) {
                currentChatState = CHAT_STATE.TERMINATED;
            }

            if (!outboundMsg.equals("&exit") && outboundMsg.equals("&switch")) {
                sendTo = "";
                chatHistory = null;
                currentMode = MODE.DASHBOARD;
            }

            if (!outboundMsg.equals("&exit") && !outboundMsg.equals("&switch") && chatHistory != null) {
                outboundMsg = getCurrentDateTime() + DELIMITER + outboundMsg;
                out.println("/message" + DELIMITER + sessionID + DELIMITER + sendTo + DELIMITER + outboundMsg);
                chatHistory.add(userName + DELIMITER + outboundMsg);
                outboundMsg = "";
            }
        }

        @Override
        public synchronized void run() {
            try (
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            ) {
                while (currentChatState != CHAT_STATE.TERMINATED && currentChatState != CHAT_STATE.DISCONNECTED) {
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
                            handleDashboardOutput(out);
                        }
                    }

                    if (currentMode == MODE.CHAT) {
                        if (!sendTo.isEmpty() && chatHistory != null) {
                            displayChat();
                            handleChatOutput(out);
                        }

                        if (sendTo.isEmpty() || chatHistory == null) {
                            clearDisplay();
                            System.out.println("ERROR: Chat history with " + sendTo + " couldn't be loaded.");

                            sendTo = "";
                            chatHistory = null;
                            currentMode = MODE.DASHBOARD;

                            System.out.println("Press Enter to continue.");
                            sc.nextLine();
                        }
                    }
                    inboundMsg = null;
                }
                System.out.println("Exiting sendOutbound");
                if (!sessionID.isEmpty()) {       // outboundMsg == "&exit"
                    out.println("/logout" + DELIMITER + sessionID);
                }
            } catch (IOException e) {
                currentChatState = CHAT_STATE.DISCONNECTED;
                System.out.println("Client disconnected from server.\n" + e);
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
                while (currentChatState != CHAT_STATE.TERMINATED && currentChatState != CHAT_STATE.DISCONNECTED) {
                    try {
                        String response = in.readLine();
                        if (response == null || response.isEmpty()) {
                            continue;
                        }
                        inboundMsg = response.split(DELIMITER);
                    } catch (IOException e) {
                        currentChatState = CHAT_STATE.DISCONNECTED;
                        System.out.println("Client disconnected from server.\n" + e);
                        break;
                    }

                    if (inboundMsg[0].equals("200") && inboundMsg[1].equals("/broadcastUserList")) {
                        clientList = new String[inboundMsg.length - 2];
                        System.arraycopy(inboundMsg, 2, clientList, 0, inboundMsg.length - 2);
                        if (currentMode == MODE.DASHBOARD) {
                            displayDashboard();
                        }
                        continue;
                    }

                    if (inboundMsg[0].equals("200") && inboundMsg[1].equals("/syncChatHistory")) {
                        chatHistory = new ArrayList<String>();
                        if (inboundMsg.length > 2) {
                            chatHistory = parseChatHistory(inboundMsg[2]);
                        }
                        continue;
                    }

                    if (inboundMsg[0].equals("200") && inboundMsg[1].equals("/message") && chatHistory != null && inboundMsg[2].equals(sendTo)) {
                        chatHistory.add(inboundMsg[2] + DELIMITER + inboundMsg[3] + DELIMITER + inboundMsg[4]);
                        displayChat();
                        continue;
                    }

//                    System.arraycopy(inboundMsg, 0, serverMsg, 0, inboundMsg.length);
                }
                System.out.println("Exiting receiveInbound");
            } catch (IOException e) {
                currentChatState = CHAT_STATE.DISCONNECTED;
                System.out.println("Client disconnected from server.\n" + e);
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
                currentChatState = CHAT_STATE.CONNECTED;
                sleepDuration = 1;

                inbound = new ReceiveInboundMsg(clientSocket);
                outbound = new SendOutboundMsg(clientSocket);

                inbound.start();
                outbound.start();

                inbound.join();
                outbound.join();
            } catch (IOException | InterruptedException e) {
                currentChatState = CHAT_STATE.DISCONNECTED;
                System.out.println("Client disconnected from server.\n" + e);
            }
        } else {
            currentChatState = CHAT_STATE.TERMINATED;
        }
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        while (client.currentChatState != CHAT_STATE.TERMINATED) {
            if (client.sleepDuration >= 256) {
                break;
            }

            client.activateChat();

            System.out.println(client.currentChatState);

            while (client.currentChatState == CHAT_STATE.CONNECTED) {
            }


            if (client.currentChatState == CHAT_STATE.TERMINATED) {
                break;
            }

            try {
                for (int i = client.sleepDuration; i > 0; i--) {
                    client.clearDisplay();
                    if (i > client.sleepDuration - 2) {
                        System.out.println("ERROR: Couldn't connect with Server.");
                    }
                    System.out.println("Waiting for " + i + " seconds before reconnecting.");
                    Thread.sleep(1000);
                }
                client.sleepDuration *= 2;
            } catch (InterruptedException e) {
                System.out.println(e);
            }
        }

        System.out.println("Exiting Program.");
    }
}
