package Threading;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

public class FileWorker extends Thread {
    Socket socket;
    String username;
    DataInputStream in;
    DataOutputStream out;

    public FileWorker(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            
            username = in.readUTF();
            
            synchronized (FileServer.onlineUsers) {
                if (FileServer.onlineUsers.contains(username)) {
                    out.writeUTF("DENIED");
                    socket.close();
                    return;
                }
                FileServer.onlineUsers.add(username);
                FileServer.allUsers.add(username);
                FileServer.saveState();
            }
            out.writeUTF("OK");
            
            File userDir = new File(FileServer.STORAGE_DIR + "/" + username);
            if (!userDir.exists()) {
                userDir.mkdirs();
            }
            System.out.println("User connected: " + username);

            while (true) {
                String command = in.readUTF();
                switch (command) {
                    case "LOGOUT": return;
                    case "LIST_USERS":listUsers();break;
                    case "LIST_MY_FILES":listMyFiles();break;
                    case "LIST_PUBLIC_FILES": listPublicFiles(); break;
                    case "UPLOAD": handleUpload(); break;
                    case "DOWNLOAD":handleDownload(); break;
                    case "FILE_REQUEST":handleFileRequest();break;
                    case "VIEW_MESSAGES": viewMessages(); break;
                    case "VIEW_HISTORY":viewHistory(); break;
                    case "VIEW_REQUESTS": viewRequests();break;
                }
            }
            
        } catch (IOException e) {
        } finally {
            removeUser();
            try { socket.close(); } catch (IOException e) {}
        }
    }

    private void listUsers() throws IOException {
        StringBuilder sb = new StringBuilder();
        synchronized (FileServer.allUsers) {
            for (String user : FileServer.allUsers) {
                sb.append(user);
                if (FileServer.onlineUsers.contains(user)) {
                    sb.append(" (online)");
                } 
                else 
                {
                    sb.append(" (offline)");
                }
                sb.append("\n");
            }
        }
        out.writeUTF(sb.toString().isEmpty() ? "No users" : sb.toString());
    }

    private void listMyFiles() throws IOException {
        File userDir = new File(FileServer.STORAGE_DIR + "/" + username);
        StringBuilder sb = new StringBuilder();
        
        File[] files = userDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().equals("history.log")) {
                    String access = f.getName().startsWith("public_") ? "(public)" : "(private)";
                    sb.append(f.getName() + " " + access + "\n");
                }
            }
        }
        out.writeUTF(sb.toString().isEmpty() ? "No files" : sb.toString());
    }

    private void listPublicFiles() throws IOException {
        StringBuilder sb = new StringBuilder();
        File storageDir = new File(FileServer.STORAGE_DIR);
        
        for (File userDir : storageDir.listFiles()) {
            if (userDir.isDirectory() && !userDir.getName().equals(username)) 
            {
                File[] files = userDir.listFiles();
                if (files != null) {
                    for (File f : files) 
                       {
                        if (f.isFile() && f.getName().startsWith("public_")) 
                        {
                            sb.append(userDir.getName() + "/" + f.getName() + "\n");
                        }
                    }
                }
            }
        }
        out.writeUTF(sb.toString().isEmpty() ? "No public files" : sb.toString());
    }

    private void handleUpload() throws IOException {
        String fileName = in.readUTF();
        long fileSize = in.readLong();
        String accessType = in.readUTF();
        String requestId = in.readUTF();

        if (!requestId.equals("NONE")) 
        {
            accessType = "public";
        }

        synchronized (FileServer.class) 
        {
            if (FileServer.currentBufferUsage+fileSize>FileServer.MAX_BUFFER_SIZE) {
                out.writeUTF("REJECTED");
                logHistory("upload", fileName, "failed-buffer full");
                return;
            }
            FileServer.currentBufferUsage += fileSize;
        }
        
        Random rand = new Random();
        int chunkSize = rand.nextInt(FileServer.MIN_CHUNK_SIZE, FileServer.MAX_CHUNK_SIZE + 1);
         String fileId = FileServer.generateFileId();
        out.writeUTF("OK");
        out.writeInt(chunkSize);
        out.writeUTF(fileId);
        String prefix = accessType.equals("public") ? "public_" : "private_";
        String savePath = FileServer.STORAGE_DIR + "/" + username + "/" + prefix + fileName;
        FileOutputStream fos = new FileOutputStream(savePath);
        
        long totalReceived = 0;
        byte[] buffer = new byte[chunkSize];
        
        while (totalReceived < fileSize) {
            int toRead = (int) Math.min(chunkSize, fileSize - totalReceived);
            int bytesRead = 0;
            while (bytesRead < toRead) 
            {
                int r=in.read(buffer, bytesRead,toRead-bytesRead);
                if (r==-1) break;
                bytesRead+= r;
            }
            fos.write(buffer, 0, bytesRead);
            totalReceived += bytesRead;
            out.writeUTF("ACK");//ack-acknowledgment
        }
        String complete = in.readUTF();
        fos.close();
        
        synchronized (FileServer.class) {
            FileServer.currentBufferUsage -= fileSize;
        }
        
        if (complete.equals("COMPLETE") && totalReceived == fileSize) 
        {
            out.writeUTF("SUCCESS");
            logHistory("upload", fileName, "successful");
            if (!requestId.equals("NONE") && FileServer.fileRequests.containsKey(requestId)) 
            {
                String[] reqInfo = FileServer.fileRequests.get(requestId);
                String requester = reqInfo[1];
                FileServer.addMessage(requester, "File uploaded for your request " + requestId + " by " + username);
                FileServer.fileRequests.remove(requestId);
                FileServer.saveState();
            }
        } 
        else {
            out.writeUTF("FAILED");
            new File(savePath).delete();
            logHistory("upload", fileName, "failed");
        }
    }

    private void handleDownload() throws IOException {
        String filePath = in.readUTF();
        
        File file;
        if (filePath.contains("/")) {
            // Downloading from another user (public files only)
            file = new File(FileServer.STORAGE_DIR + "/" + filePath);
            if (!file.exists() || !file.getName().startsWith("public_")) {
                out.writeUTF("ERROR");
                return;
            }
        } else {
            // Downloading own file
            file = new File(FileServer.STORAGE_DIR + "/" + username + "/" + filePath);
            if (!file.exists()) {
                out.writeUTF("ERROR");
                return;
            }
        }
        
        out.writeUTF("OK");
        out.writeUTF(file.getName());
        out.writeLong(file.length());
        
        // Send file in MAX_CHUNK_SIZE chunks (no ack required)
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[FileServer.MAX_CHUNK_SIZE];
        int bytesRead;
        
        while ((bytesRead = fis.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        fis.close();
        
        out.writeUTF("DOWNLOAD_COMPLETE");
        logHistory("download", file.getName(), "successful");
    }

    private void handleFileRequest() throws IOException {
        String description = in.readUTF();
        String recipient = in.readUTF(); // username or "ALL"
        
        String requestId = FileServer.generateRequestId();
        FileServer.fileRequests.put(requestId, new String[]{description, username, recipient});
        FileServer.saveState();
        
        String message = "File request " + requestId + " from " + username + ": " + description;
        
        if (recipient.equals("ALL")) {
            synchronized (FileServer.allUsers) {
                for (String user : FileServer.allUsers) {
                    if (!user.equals(username)) {
                        FileServer.addMessage(user, message);
                    }
                }
            }
        } else {
            // Unicast to specific user
            FileServer.addMessage(recipient, message);
        }
        
        out.writeUTF("Request created: " + requestId);
    }

    private void viewMessages() throws IOException {
        List<String> messages = FileServer.getAndClearMessages(username);
        if (messages.isEmpty()) {
            out.writeUTF("No unread messages");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String msg : messages) {
                sb.append(msg + "\n");
            }
            out.writeUTF(sb.toString());
        }
    }

    private void viewHistory() throws IOException {
        File logFile = new File(FileServer.STORAGE_DIR + "/" + username + "/history.log");
        if (!logFile.exists()) {
            out.writeUTF("No history");
            return;
        }
        
        BufferedReader br = new BufferedReader(new FileReader(logFile));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
        }
        br.close();
        out.writeUTF(sb.toString().isEmpty() ? "No history" : sb.toString());
    }

    private void viewRequests() throws IOException {
        StringBuilder sb = new StringBuilder();
        synchronized (FileServer.fileRequests) {
            for (Map.Entry<String, String[]> entry : FileServer.fileRequests.entrySet()) {
                String reqId = entry.getKey();
                String[] info = entry.getValue(); // {description, requester, recipient}
                String recipient = info[2];
                // Show if recipient is ALL or this user
                if (recipient.equals("ALL") || recipient.equals(username)) {
                    sb.append(reqId + " | From: " + info[1] + " | Description: " + info[0] + "\n");
                }
            }
        }
        out.writeUTF(sb.toString().isEmpty() ? "No file requests for you" : sb.toString());
    }

    private void logHistory(String action, String filename, String status) {
        try {
            String logPath = FileServer.STORAGE_DIR+"/"+username+"/history.log";
            FileWriter fw = new FileWriter(logPath, true);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            fw.write(timestamp + " | " + action + " | " + filename + " | " + status + "\n");
            fw.close();
        } 
        catch (IOException e) {
        }
    }

    private void removeUser() {
        if (username != null) {
            synchronized (FileServer.onlineUsers) {
                FileServer.onlineUsers.remove(username);
            }
            System.out.println("User disconnected: " + username);
        }
    }
}
