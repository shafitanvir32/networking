package Threading;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class FileServer {
    public static HashSet<String> onlineUsers = new HashSet<>();
    public static HashSet<String> allUsers = new HashSet<>();
    public static int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB
    public static int MIN_CHUNK_SIZE = 1024;              // 1 KB
    public static int MAX_CHUNK_SIZE = 100 * 1024;        // 100 KB
    public static int currentBufferUsage = 0;
    
    public static Map<String, String[]> fileRequests = new HashMap<>();//requestID -> {description, requester, recipient}
    public static int requestCounter = 0;
    public static Map<String, List<String>> unreadMessages = new HashMap<>();
    public static Map<String, Object[]> ongoingUploads = new HashMap<>();//fileID -> {filename, username, expectedSize, currentSize}
    public static int fileIdCounter = 0;
    
    public static String STORAGE_DIR = "server_files";
    public static String STATE_FILE = "server_files/server_state.dat";

    public static void main(String[] args) throws IOException {
        new File(STORAGE_DIR).mkdirs();
        loadState(); // Load previous state
        
        ServerSocket welcomeSocket = new ServerSocket(6666);
        System.out.println("File Server started on port 6666");

        while (true) {
            System.out.println("Waiting for connection");
            Socket socket = welcomeSocket.accept();
            Thread worker = new FileWorker(socket);
            worker.start();
        }
    }
    
    public static synchronized void addMessage(String username, String message) {
        if (!unreadMessages.containsKey(username)) {
            unreadMessages.put(username, new ArrayList<>());
        }
        unreadMessages.get(username).add(message);
        saveState();
    }
    
    public static synchronized List<String> getAndClearMessages(String username) {
        List<String> msgs = unreadMessages.getOrDefault(username, new ArrayList<>());
        unreadMessages.put(username, new ArrayList<>());
        saveState();
        return msgs;
    }
    
    public static synchronized String generateFileId() {
        String id = "FILE" + (++fileIdCounter);
        saveState();
        return id;
    }
    
    public static synchronized String generateRequestId() {
        String id = "REQ" + (++requestCounter);
        saveState();
        return id;
    }
    
    public static synchronized void saveState() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(STATE_FILE));
            oos.writeObject(allUsers);
            oos.writeObject(fileRequests);
            oos.writeObject(unreadMessages);
            oos.writeInt(requestCounter);
            oos.writeInt(fileIdCounter);
            oos.close();
        } catch (IOException e) {}
    }
    
    @SuppressWarnings("unchecked")
    public static synchronized void loadState() {
        File file = new File(STATE_FILE);
        if (!file.exists()) return;
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
            allUsers = (HashSet<String>) ois.readObject();
            fileRequests = (Map<String, String[]>) ois.readObject();
            unreadMessages = (Map<String, List<String>>) ois.readObject();
            requestCounter = ois.readInt();
            fileIdCounter = ois.readInt();
            ois.close();
            System.out.println("Loaded state: " + allUsers.size() + " users");
        } catch (Exception e) {}
    }
}
