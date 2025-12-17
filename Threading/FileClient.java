package Threading;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class FileClient {
    static Socket socket;
    static DataInputStream in;
    static DataOutputStream out;
    static Scanner scanner = new Scanner(System.in);
    static String username;

    public static void main(String[] args) throws IOException {
        System.out.print("Enter your username: ");
        username = scanner.nextLine();

        socket = new Socket("localhost", 6666);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());

        out.writeUTF(username);

        String response = in.readUTF();
        if (response.equals("DENIED")) {
            System.out.println("Username already connected");
            socket.close();
            return;
        }
        System.out.println("Successfully logged in");

        // Main menu loop
        while (true) {
            System.out.println("\nMENU");
            System.out.println("1.List all users");
            System.out.println("2.List my files");
            System.out.println("3.List public files of others");
            System.out.println("4.Upload a file");
            System.out.println("5.Download a file");
            System.out.println("6.Make a file request");
            System.out.println("7.View unread messages");
            System.out.println("8.View history");
            System.out.println("9.View requested files");
            System.out.println("10.Logout");
            System.out.print("Choice: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1": listUsers(); break;
                case "2": listMyFiles(); break;
                case "3": listPublicFiles(); break;
                case "4": uploadFile(); break;
                case "5": downloadFile(); break;
                case "6": makeFileRequest(); break;
                case "7": viewMessages(); break;
                case "8": viewHistory(); break;
                case "9": viewRequests(); break;
                case "10":
                    out.writeUTF("LOGOUT");
                    socket.close();
                    System.out.println("Logged out.");
                    return;
                default:
                    System.out.println("Invalid choice");
            }
        }
    }

    static void listUsers() throws IOException {
        out.writeUTF("LIST_USERS");
        System.out.println("\n--- Users ---");
        System.out.println(in.readUTF());
    }

    static void listMyFiles() throws IOException {
        out.writeUTF("LIST_MY_FILES");
        System.out.println("\n--- My Files ---");
        System.out.println(in.readUTF());
    }

    static void listPublicFiles() throws IOException {
        out.writeUTF("LIST_PUBLIC_FILES");
        System.out.println("\n---Public Files---");
        System.out.println(in.readUTF());
    }

    static void uploadFile() throws IOException {
        System.out.print("Enter file path: ");
        String filePath = scanner.nextLine();

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found");
            return;
        }

        System.out.print("Access type(public/private):");
        String accessType = scanner.nextLine();

        System.out.print("Request ID(or NONE): ");
        String requestId = scanner.nextLine();
        if (requestId.isEmpty()) requestId = "NONE";

        out.writeUTF("UPLOAD");
        out.writeUTF(file.getName());
        out.writeLong(file.length());
        out.writeUTF(accessType);
        out.writeUTF(requestId);

        String response = in.readUTF();
        if (response.equals("REJECTED")) {
            System.out.println("Upload rejected: Server buffer full");
            return;
        }

        int chunkSize = in.readInt();
        String fileId = in.readUTF();
        System.out.println("Upload approved.FileID: "+ fileId +", Chunk size:" + chunkSize);

        // Send file in chunks
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[chunkSize];
        int bytesRead;
        int chunkNum = 0;

        while ((bytesRead = fis.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            String ack = in.readUTF(); // Wait for ACK
            chunkNum++;
            System.out.println("Chunk " + chunkNum + " sent,Ack received from server");
        }
        fis.close();

        out.writeUTF("COMPLETE");
        String result = in.readUTF();
        System.out.println("Upload result: " + result);
    }

    static void downloadFile() throws IOException {
        System.out.print("Enter file path (filename or user/filename): ");
        String filePath = scanner.nextLine();

        out.writeUTF("DOWNLOAD");
        out.writeUTF(filePath);

        String response = in.readUTF();
        if (response.equals("ERROR")) {
            System.out.println("File not found or access denied");
            return;
        }

        String fileName = in.readUTF();
        long fileSize = in.readLong();
        System.out.println("Downloading: " + fileName + " (" + fileSize + " bytes)");

        String savePath = "downloaded_" + fileName;
        FileOutputStream fos = new FileOutputStream(savePath);

        byte[] buffer = new byte[4096];
        long totalRead = 0;
        int bytesRead;

        while (totalRead < fileSize) {
            bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead));
            if (bytesRead == -1) break;
            fos.write(buffer, 0, bytesRead);
            totalRead += bytesRead;
        }
        fos.close();

        String complete = in.readUTF();
        System.out.println("Download complete: " + savePath);
    }

    static void makeFileRequest() throws IOException {
        System.out.print("File description: ");
        String description = scanner.nextLine();

        System.out.print("Recipient (username or ALL): ");
        String recipient = scanner.nextLine();

        out.writeUTF("FILE_REQUEST");
        out.writeUTF(description);
        out.writeUTF(recipient);

        System.out.println(in.readUTF());
    }

    static void viewMessages() throws IOException {
        out.writeUTF("VIEW_MESSAGES");
        System.out.println("\n--Messages--");
        System.out.println(in.readUTF());
    }

    static void viewHistory() throws IOException {
        out.writeUTF("VIEW_HISTORY");
        System.out.println("\n--History--");
        System.out.println(in.readUTF());
    }

    static void viewRequests() throws IOException {
        out.writeUTF("VIEW_REQUESTS");
        System.out.println("\n--File Requests--");
        System.out.println(in.readUTF());
    }
}
