import java.io.*;
import java.net.*;
import java.util.*;

public class GroupChatServer {
    private static final int PORT = 7777;
    private static final Map<String, Set<ClientHandler>> groups = new HashMap<>(); // Stores groups and their members
    private static final Set<ClientHandler> clients = new HashSet<>(); // Stores all connected clients

    public static void main(String[] args) {
        System.out.println("Server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept(); // Accept incoming client connections
                ClientHandler clientHandler = new ClientHandler(socket);
                synchronized (clients) {
                    clients.add(clientHandler); // Add new client to the list of clients
                }
                clientHandler.start(); // Start handling the client in a new thread
            }
        } catch (IOException e) {
            e.printStackTrace(); // Handle I/O errors
        }
    }

    // Inner class to handle each client connection
    private static class ClientHandler extends Thread {
        private Socket socket; // Client socket for communication
        private DataOutputStream out; // Output stream to send data to the client
        private DataInputStream in; // Input stream to receive data from the client
        private String clientName; // Client's nickname
        private Set<String> joinedGroups = new HashSet<>(); // Groups that the client has joined

        public ClientHandler(Socket socket) {
            this.socket = socket; // Initialize the client handler with the client socket
        }

        public void run() {
            try {
                // Initialize input and output streams
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                // Read the client's nickname
                clientName = in.readUTF();
                System.out.println(clientName + " connected.");

                String message;
                while ((message = in.readUTF()) != null) {
                    // Handle different types of messages from the client
                    if (message.equalsIgnoreCase("quit")) {
                        break;
                    } else if (message.startsWith("AddGroup")) {
                        handleAddGroup(message); // Handle group creation
                    } else if (message.startsWith("JoinGroup")) {
                        handleJoinGroup(message); // Handle joining a group
                    } else if (message.startsWith("SendMessage")) {
                        handleSendMessage(message); // Handle sending a message to a group
                    } else if (message.startsWith("LeaveGroup")) {
                        handleLeaveGroup(message); // Handle leaving a group
                    } else if (message.startsWith("RemoveGroup")) {
                        handleRemoveGroup(message); // Handle removing a group
                    } else if (message.startsWith("SendFile")) {
                        handleSendFile(); // Handle sending a file to a group
                    } else {
                        out.writeUTF("Unknown command."); // Handle unknown commands
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection error with " + clientName); // Handle connection errors
            } finally {
                cleanup(); // Clean up resources when done
            }
        }

        // Handle group creation requests
        private void handleAddGroup(String message) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String groupName = parts[1];
                synchronized (groups) {
                    if (!groups.containsKey(groupName)) {
                        groups.put(groupName, new HashSet<>());
                        try {
                            out.writeUTF("Group " + groupName + " created.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            out.writeUTF("Error: Group " + groupName + " already exists.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                try {
                    out.writeUTF("Usage: AddGroup <group name>");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Handle group joining requests
        private void handleJoinGroup(String message) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String groupName = parts[1];
                synchronized (groups) {
                    Set<ClientHandler> group = groups.get(groupName);
                    if (group != null) {
                        if (!joinedGroups.contains(groupName)) {
                            group.add(this);
                            joinedGroups.add(groupName);
                            // Notify all group members that a new member has joined
                            for (ClientHandler client : group) {
                                if (client != this) {
                                    try {
                                        client.out.writeUTF(clientName + " has joined the group " + groupName);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            try {
                                out.writeUTF("Joined group " + groupName);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            try {
                                out.writeUTF("Error: You are already a member of " + groupName);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        try {
                            out.writeUTF("Error: Group " + groupName + " does not exist.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                try {
                    out.writeUTF("Usage: JoinGroup <group name>");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Handle message sending requests
        private void handleSendMessage(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length == 3) {
                String groupName = parts[1];
                String msg = parts[2];
                synchronized (groups) {
                    Set<ClientHandler> group = groups.get(groupName);
                    if (group != null) {
                        if (joinedGroups.contains(groupName)) {
                            for (ClientHandler client : group) {
                                try {
                                    client.out.writeUTF(clientName + " (" + groupName + "): " + msg);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            try {
                                out.writeUTF("Error: You are not a member of " + groupName);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        try {
                            out.writeUTF("Error: Group " + groupName + " does not exist.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                try {
                    out.writeUTF("Usage: SendMessage <group name> <message>");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Handle group leaving requests
        private void handleLeaveGroup(String message) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String groupName = parts[1];
                synchronized (groups) {
                    Set<ClientHandler> group = groups.get(groupName);
                    if (group != null && group.remove(this)) {
                        joinedGroups.remove(groupName);
                        // Notify all remaining members that a member has left
                        for (ClientHandler client : group) {
                            try {
                                client.out.writeUTF(clientName + " has left the group " + groupName);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        try {
                            out.writeUTF("Left group " + groupName);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            out.writeUTF("Error: Group " + groupName + " does not exist or you are not a member.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                try {
                    out.writeUTF("Usage: LeaveGroup <group name>");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Handle group removal requests
        private void handleRemoveGroup(String message) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String groupName = parts[1];
                synchronized (groups) {
                    Set<ClientHandler> group = groups.remove(groupName);
                    if (group != null) {
                        // Notify all members of the group that the group is removed
                        for (ClientHandler client : group) {
                            try {
                                client.out.writeUTF("Group " + groupName + " has been removed.");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        try {
                            out.writeUTF("Group " + groupName + " removed.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            out.writeUTF("Error: Group " + groupName + " does not exist.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                try {
                    out.writeUTF("Usage: RemoveGroup <group name>");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Handle file sending requests
        private void handleSendFile() {
            try {
                // Read the group name, file name, and file size from the client
                String groupName = in.readUTF();
                String fileName = in.readUTF();
                long fileSize = in.readLong();

                File file = new File("received_" + fileName);
                try (DataOutputStream fileOut = new DataOutputStream(new FileOutputStream(file))) {
                    byte[] buffer = new byte[4096];
                    long remaining = fileSize;
                    int bytesRead;
                    while (remaining > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                    fileOut.flush();
                }

                synchronized (groups) {
                    Set<ClientHandler> group = groups.get(groupName);
                    if (group != null) {
                        for (ClientHandler client : group) {
                            if (client != this) {
                                try {
                                    client.out.writeUTF("ReceiveFile " + fileName);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        try {
                            out.writeUTF("File " + fileName + " sent to group " + groupName);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            out.writeUTF("Error: Group " + groupName + " does not exist.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                try {
                    out.writeUTF("Error sending file: " + e.getMessage());
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }

        // Clean up resources when the client disconnects
        private void cleanup() {
            try {
                if (clientName != null) {
                    System.out.println(clientName + " disconnected.");
                }

                synchronized (groups) {
                    for (String group : joinedGroups) {
                        Set<ClientHandler> groupMembers = groups.get(group);
                        if (groupMembers != null) {
                            groupMembers.remove(this); // Remove client from all groups
                            for (ClientHandler client : groupMembers) {
                                try {
                                    client.out.writeUTF(clientName + " has left the group " + group);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }

                synchronized (clients) {
                    clients.remove(this); // Remove client from the global client list
                }

                in.close();
                out.close();
                socket.close(); // Close the client socket and streams
            } catch (IOException e) {
                e.printStackTrace(); // Handle errors during cleanup
            }
        }
    }
}
