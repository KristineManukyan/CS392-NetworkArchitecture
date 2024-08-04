import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;

public class GroupChatClient implements Runnable {
    private static final int SERVER_PORT = 7777;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String clientName;
    private Thread listenerThread;

    // GUI components
    private JFrame frame;
    private JTextArea messageArea;
    private JTextField inputField;
    private JTextField fileField;
    private JButton sendMessageButton;
    private JButton fileButton;
    private JButton addGroupButton;
    private JButton joinGroupButton;
    private JButton leaveGroupButton;
    private JButton removeGroupButton;
    private JButton quitButton;

    public GroupChatClient(String serverAddress) throws IOException {
        socket = new Socket(serverAddress, SERVER_PORT);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());

        clientName = getClientName();
        out.writeUTF(clientName);

        // Initialize GUI
        initializeGUI();

        listenerThread = new Thread(this);
        listenerThread.start();
    }

    private String getClientName() {
        return JOptionPane.showInputDialog(frame, "Enter your nickname:", "Nickname", JOptionPane.PLAIN_MESSAGE);
    }

    private void initializeGUI() {
        frame = new JFrame("Group Chat Client - " + clientName);
        messageArea = new JTextArea(20, 50);
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);

        inputField = new JTextField(40);
        fileField = new JTextField(20);
        sendMessageButton = new JButton("Send Message");
        fileButton = new JButton("Send File");
        addGroupButton = new JButton("Add Group");
        joinGroupButton = new JButton("Join Group");
        leaveGroupButton = new JButton("Leave Group");
        removeGroupButton = new JButton("Remove Group");
        quitButton = new JButton("Quit");

        JPanel panel = new JPanel();
        panel.add(new JLabel("Message:"));
        panel.add(inputField);
        panel.add(sendMessageButton);
        panel.add(new JLabel("File:"));
        panel.add(fileField);
        panel.add(fileButton);
        panel.add(addGroupButton);
        panel.add(joinGroupButton);
        panel.add(leaveGroupButton);
        panel.add(removeGroupButton);
        panel.add(quitButton);

        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.getContentPane().add(panel, BorderLayout.SOUTH);

        sendMessageButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String groupName = JOptionPane.showInputDialog(frame, "Enter group name to send message:", "Send Message", JOptionPane.PLAIN_MESSAGE);
                if (groupName != null && !groupName.trim().isEmpty()) {
                    sendMessage(groupName, inputField.getText());
                    inputField.setText("");
                }
            }
        });

        fileButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String filePath = fileField.getText();
                String groupName = JOptionPane.showInputDialog(frame, "Enter group name to send file:", "Send File", JOptionPane.PLAIN_MESSAGE);
                if (groupName != null && !groupName.trim().isEmpty()) {
                    if (filePath != null && !filePath.trim().isEmpty()) {
                        sendFile(groupName, filePath);
                        fileField.setText("");
                    } else {
                        messageArea.append("Error: File path is not provided.\n");
                    }
                }
            }
        });

        addGroupButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String groupName = JOptionPane.showInputDialog(frame, "Enter group name to add:", "Add Group", JOptionPane.PLAIN_MESSAGE);
                if (groupName != null && !groupName.trim().isEmpty()) {
                    sendCommand("AddGroup " + groupName);
                }
            }
        });

        joinGroupButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String groupName = JOptionPane.showInputDialog(frame, "Enter group name to join:", "Join Group", JOptionPane.PLAIN_MESSAGE);
                if (groupName != null && !groupName.trim().isEmpty()) {
                    sendCommand("JoinGroup " + groupName);
                }
            }
        });

        leaveGroupButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String groupName = JOptionPane.showInputDialog(frame, "Enter group name to leave:", "Leave Group", JOptionPane.PLAIN_MESSAGE);
                if (groupName != null && !groupName.trim().isEmpty()) {
                    sendCommand("LeaveGroup " + groupName);
                }
            }
        });

        removeGroupButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String groupName = JOptionPane.showInputDialog(frame, "Enter group name to remove:", "Remove Group", JOptionPane.PLAIN_MESSAGE);
                if (groupName != null && !groupName.trim().isEmpty()) {
                    sendCommand("RemoveGroup " + groupName);
                }
            }
        });

        quitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendCommand("quit");
                frame.dispose(); // Close the GUI window
                try {
                    socket.close(); // Close the connection to the server
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                System.exit(0); // Exit the program
            }
        });

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    public void run() {
        try {
            String message;
            while ((message = in.readUTF()) != null) {
                if (message.startsWith("ReceiveFile")) {
                    handleFileReception(message);
                } else {
                    messageArea.append(message + "\n");
                }
            }
        } catch (IOException e) {
            messageArea.append("Connection lost: " + e.getMessage() + "\n");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleFileReception(String message) {
        try {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String fileName = parts[1];
                File file = new File("received_" + fileName);
                try (DataOutputStream fileOut = new DataOutputStream(new FileOutputStream(file))) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long remaining = file.length(); // file.length() will be zero initially
                    while (remaining > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                    fileOut.flush();
                }
                messageArea.append("File " + fileName + " received.\n");
            }
        } catch (IOException e) {
            messageArea.append("Error receiving file: " + e.getMessage() + "\n");
        }
    }

    public void sendMessage(String groupName, String message) {
        sendCommand("SendMessage " + groupName + " " + message);
    }

    public void sendFile(String groupName, String filePath) {
        File file = new File(filePath);
        if (file.exists() && file.isFile()) {
            try {
                // Notify server about the file to be sent
                out.writeUTF("SendFile");
                out.writeUTF(groupName);
                out.writeUTF(file.getName());
                out.writeLong(file.length());

                // Send the file data in chunks
                try (FileInputStream fileIn = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long remaining = file.length();
                    while (remaining > 0 && (bytesRead = fileIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                        out.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                    out.flush(); // Ensure all data is sent
                }
                messageArea.append("File " + filePath + " sent to group " + groupName + "\n");
            } catch (IOException e) {
                messageArea.append("Error sending file: " + e.getMessage() + "\n");
            }
        } else {
            messageArea.append("File " + filePath + " does not exist.\n");
        }
    }

    private void sendCommand(String command) {
        try {
            out.writeUTF(command);
        } catch (IOException e) {
            messageArea.append("Error sending command: " + e.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        String serverAddress = JOptionPane.showInputDialog(
                null, "Enter server address:", "Server Address", JOptionPane.PLAIN_MESSAGE);
        if (serverAddress != null && !serverAddress.trim().isEmpty()) {
            try {
                new GroupChatClient(serverAddress);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error connecting to server: " + e.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(null, "Server address cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
