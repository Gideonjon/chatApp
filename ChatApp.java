/*
 * ChatApp.java
 *
 * A single-file Java Swing chat application using SQLite for persistence.
 *
 * Features:
 * - Sign Up / Login
 * - See list of registered users
 * - One-to-one chat with other registered users
 * - Messages stored in SQLite (messages table)
 * - Auto-refresh (polling) to load new messages
 * - About tab with an auto-generated author image
 *
 * Security note: This is a demo. Passwords are hashed with SHA-256 (no salt).
 * For production use proper password hashing (bcrypt/argon2) and secure transport (TLS).
 *
 * Build & Run (Windows PowerShell):
 * 1. Place ChatApp.java and sqlite-jdbc-3.46.0.0.jar in same folder.
 * 2. Compile:
 *    javac -cp ".;sqlite-jdbc-3.46.0.0.jar" ChatApp.java
 * 3. Run:
 *    java -cp ".;sqlite-jdbc-3.46.0.0.jar" ChatApp
 */

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.imageio.ImageIO;

public class ChatApp extends JFrame {
    private static final String DB_URL = "jdbc:sqlite:chat.db";
    private static final String IMAGE_DIR = "images";

    // UI cards
    private CardLayout cards = new CardLayout();
    private JPanel main = new JPanel(cards);

    // Auth fields
    private JTextField loginUser;
    private JPasswordField loginPass;
    private JTextField signupUser;
    private JPasswordField signupPass;

    // Chat UI
    private JLabel onlineUserLabel;
    private JList<String> usersList; // items "id: username"
    private DefaultListModel<String> usersListModel;
    private JTextPane chatPane;
    private JTextField msgField;
    private JButton sendBtn;

    // session
    private Integer currentUserId = null;
    private String currentUsername = null;
    private Integer selectedChatUserId = null;

    private javax.swing.Timer pollTimer;
    private final SimpleDateFormat tsFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            
            ensureDb();
            ensureImageDir();
            seedIfEmpty();
            new ChatApp().setVisible(true);
        });
    }

    private static void ensureImageDir() {
        File dir = new File("images");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public ChatApp() {
        super("Simple Chat App");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 620);
        setLocationRelativeTo(null);

        main.add(buildAuthPanel(), "auth");
        main.add(buildChatPanel(), "chat");
        main.add(buildAboutPanel(), "about");

        add(main);
        cards.show(main, "auth");

        // poll for new messages every 2 seconds when logged in
        pollTimer = new javax.swing.Timer(2000, e -> {
            if (currentUserId != null && selectedChatUserId != null) loadMessages(selectedChatUserId);
        });
    }

    // ----------------- AUTH -----------------
    private JPanel buildAuthPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(12,12,12,12));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8,8,8,8);
        gc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Welcome to Simple Chat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        gc.gridx=0; gc.gridy=0; gc.gridwidth=2; p.add(title, gc);

        // Login
        JPanel loginBox = new JPanel(new GridBagLayout());
        loginBox.setBorder(BorderFactory.createTitledBorder("Login"));
        GridBagConstraints l = new GridBagConstraints(); l.insets=new Insets(6,6,6,6); l.fill=GridBagConstraints.HORIZONTAL;
        l.gridx=0; l.gridy=0; loginBox.add(new JLabel("Username"), l);
        l.gridx=1; loginUser = new JTextField(16); loginBox.add(loginUser, l);
        l.gridx=0; l.gridy++; loginBox.add(new JLabel("Password"), l);
        l.gridx=1; loginPass = new JPasswordField(16); loginBox.add(loginPass, l);
        l.gridx=0; l.gridy++; l.gridwidth=2; JButton loginBtn = new JButton("Login"); loginBtn.addActionListener(e->doLogin()); loginBox.add(loginBtn, l);

        gc.gridx=0; gc.gridy=1; gc.gridwidth=1; p.add(loginBox, gc);

        // Signup
        JPanel signBox = new JPanel(new GridBagLayout());
        signBox.setBorder(BorderFactory.createTitledBorder("Sign Up"));
        GridBagConstraints s = new GridBagConstraints(); s.insets=new Insets(6,6,6,6); s.fill=GridBagConstraints.HORIZONTAL;
        s.gridx=0; s.gridy=0; signBox.add(new JLabel("Username"), s);
        s.gridx=1; signupUser = new JTextField(16); signBox.add(signupUser, s);
        s.gridx=0; s.gridy++; signBox.add(new JLabel("Password"), s);
        s.gridx=1; signupPass = new JPasswordField(16); signBox.add(signupPass, s);
        s.gridx=0; s.gridy++; s.gridwidth=2; JButton signupBtn = new JButton("Create Account"); signupBtn.addActionListener(e->doSignup()); signBox.add(signupBtn, s);

        gc.gridx=1; gc.gridy=1; p.add(signBox, gc);

        // footer
        JButton about = new JButton("About"); about.addActionListener(e->cards.show(main, "about"));
        gc.gridx=0; gc.gridy=2; gc.gridwidth=2; p.add(about, gc);

        return p;
    }

    private void doLogin() {
        String user = loginUser.getText().trim();
        String pass = new String(loginPass.getPassword());
        if (user.isEmpty() || pass.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter username & password."); return; }
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, password_hash FROM users WHERE username = ?")) {
                ps.setString(1, user);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int uid = rs.getInt(1);
                        String hash = rs.getString(2);
                        if (hash.equals(hashPassword(pass))) {
                            currentUserId = uid; currentUsername = user;
                            loginUser.setText(""); loginPass.setText("");
                            cards.show(main, "chat");
                            loadUsers();
                            pollTimer.start();
                            return;
                        }
                    }
                }
            }
        } catch (SQLException ex) { showError(ex); }
        JOptionPane.showMessageDialog(this, "Invalid credentials.");
    }

    private void doSignup() {
        String user = signupUser.getText().trim();
        String pass = new String(signupPass.getPassword());
        if (user.isEmpty() || pass.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter username & password."); return; }
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password_hash) VALUES(?,?)")) {
                ps.setString(1, user);
                ps.setString(2, hashPassword(pass));
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Account created. You can now login.");
                signupUser.setText(""); signupPass.setText("");
            }
        } catch (SQLException ex) {
            if (ex.getMessage().contains("UNIQUE")) JOptionPane.showMessageDialog(this, "Username already exists."); else showError(ex);
        }
    }

    // ----------------- CHAT UI -----------------
    private JPanel buildChatPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(8,8,8,8));

        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Chat"); title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        top.add(title, BorderLayout.WEST);
        JButton logout = new JButton("Logout"); logout.addActionListener(e->doLogout());
        JButton about = new JButton("About"); about.addActionListener(e->cards.show(main, "about"));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT)); right.add(about); right.add(logout);
        top.add(right, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        // center split: users list left, chat area right
        JSplitPane split = new JSplitPane(); split.setResizeWeight(0.25);

        // users list
        usersListModel = new DefaultListModel<>();
        usersList = new JList<>(usersListModel);
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        usersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = usersList.getSelectedValue();
                if (sel != null) {
                    selectedChatUserId = Integer.parseInt(sel.split(":")[0]);
                    loadMessages(selectedChatUserId);
                }
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel("Registered Users"), BorderLayout.NORTH);
        left.add(new JScrollPane(usersList), BorderLayout.CENTER);
        JButton refreshUsers = new JButton("Refresh"); refreshUsers.addActionListener(e->loadUsers());
        left.add(refreshUsers, BorderLayout.SOUTH);

        // chat area
        JPanel rightPanel = new JPanel(new BorderLayout());
        chatPane = new JTextPane(); chatPane.setEditable(false);
        chatPane.setContentType("text/plain");

        JPanel sendBar = new JPanel(new BorderLayout());
        msgField = new JTextField();
        msgField.addActionListener(e->sendMessage());
        sendBtn = new JButton("Send"); sendBtn.addActionListener(e->sendMessage());
        sendBar.add(msgField, BorderLayout.CENTER); sendBar.add(sendBtn, BorderLayout.EAST);

        rightPanel.add(new JScrollPane(chatPane), BorderLayout.CENTER);
        rightPanel.add(sendBar, BorderLayout.SOUTH);

        split.setLeftComponent(left); split.setRightComponent(rightPanel);
        p.add(split, BorderLayout.CENTER);

        return p;
    }

    private void doLogout() {
        currentUserId = null; currentUsername = null; selectedChatUserId = null;
        pollTimer.stop();
        cards.show(main, "auth");
    }

    private void loadUsers() {
        usersListModel.clear();
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, username FROM users WHERE id != ? ORDER BY username")) {
                ps.setInt(1, currentUserId==null? -1 : currentUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) usersListModel.addElement(rs.getInt(1) + ": " + rs.getString(2));
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void loadMessages(int otherUserId) {
        // load messages between currentUserId and otherUserId
        if (currentUserId == null) return;
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT from_user, to_user, content, created_at FROM messages " +
                            "WHERE (from_user = ? AND to_user = ?) OR (from_user = ? AND to_user = ?) ORDER BY id ASC")) {
                ps.setInt(1, currentUserId); ps.setInt(2, otherUserId); ps.setInt(3, otherUserId); ps.setInt(4, currentUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    StringBuilder sb = new StringBuilder();
                    while (rs.next()) {
                        Integer f = rs.getObject(1) == null ? null : rs.getInt(1);
                        Integer t = rs.getObject(2) == null ? null : rs.getInt(2);
                        String content = rs.getString(3);
                        String created = rs.getString(4);
                        String who = (f.equals(currentUserId)) ? "You" : getUsernameById(f);
                        sb.append(String.format("[%s] %s: %s\n", created, who, content));
                    }
                    chatPane.setText(sb.toString());
                    // scroll to bottom
                    chatPane.setCaretPosition(chatPane.getDocument().getLength());
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void sendMessage() {
        if (currentUserId == null) { JOptionPane.showMessageDialog(this, "Login first."); return; }
        if (selectedChatUserId == null) { JOptionPane.showMessageDialog(this, "Select a user to chat with."); return; }
        String text = msgField.getText().trim(); if (text.isEmpty()) return;
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO messages(from_user,to_user,content,created_at) VALUES(?,?,?,datetime('now'))")) {
                ps.setInt(1, currentUserId); ps.setInt(2, selectedChatUserId); ps.setString(3, text); ps.executeUpdate();
            }
        } catch (SQLException ex) { showError(ex); }
        msgField.setText("");
        loadMessages(selectedChatUserId);
    }

    private String getUsernameById(int id) {
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement ps = c.prepareStatement("SELECT username FROM users WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
            }
        } catch (SQLException ex) { showError(ex); }
        return "user:" + id;
    }

    // ----------------- ABOUT -----------------
    private JPanel buildAboutPanel() {
        JPanel p = new JPanel(new BorderLayout()); p.setBorder(new EmptyBorder(12,12,12,12));
        JPanel top = new JPanel(new BorderLayout());
        JLabel photo = new JLabel(); photo.setPreferredSize(new Dimension(160,160));
        String generated = IMAGE_DIR + File.separator + "chat_author.png";
        try { if (!Files.exists(Paths.get(generated))) createAuthorImage(generated, "You"); } catch (IOException ignored) {}
        photo.setIcon(new ImageIcon(new ImageIcon(generated).getImage().getScaledInstance(160,160, Image.SCALE_SMOOTH)));
        JTextArea ta = new JTextArea(); ta.setEditable(false); ta.setLineWrap(true); ta.setWrapStyleWord(true);
        ta.setText("Simple Chat App\n\nPrivate one-to-one chat between registered users. Messages persist in SQLite.\n\nAuthor: You ✨");
        JButton back = new JButton("Back"); back.addActionListener(e -> cards.show(main, currentUserId==null?"auth":"chat"));
        top.add(photo, BorderLayout.WEST); top.add(new JScrollPane(ta), BorderLayout.CENTER);
        p.add(top, BorderLayout.CENTER); p.add(back, BorderLayout.SOUTH);
        return p;
    }

    private void createAuthorImage(String path, String name) throws IOException {
        Files.createDirectories(Paths.get(IMAGE_DIR));
        BufferedImage bi = new BufferedImage(640,640,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(new Color(90,170,140)); g.fillRect(0,0,640,640);
        g.setColor(new Color(0,0,0,120)); g.fillRoundRect(24,24,592,120,24,24);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 48)); g.drawString(name, 40, 88);
        g.dispose();
        ImageIO.write(bi, "png", new File(path));
    }

    // ----------------- DB -----------------
    private static void ensureDb() {
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, from_user INTEGER NOT NULL, to_user INTEGER NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL)");
            }
        } catch (SQLException ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(null, "DB init failed: "+ex.getMessage()); System.exit(1); }
    }

    private static void seedIfEmpty() {
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password_hash) VALUES(?,?)")) {
                        ps.setString(1, "alice"); ps.setString(2, hashPassword("alicepass")); ps.executeUpdate();
                        ps.setString(1, "bob"); ps.setString(2, hashPassword("bobpass")); ps.executeUpdate();
                        ps.setString(1, "carol"); ps.setString(2, hashPassword("carolpass")); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO messages(from_user,to_user,content,created_at) VALUES(?,?,?,datetime('now'))")) {
                        ps.setInt(1, 1); ps.setInt(2, 2); ps.setString(3, "Hi Bob! This is Alice."); ps.executeUpdate();
                        ps.setInt(1, 2); ps.setInt(2, 1); ps.setString(3, "Hey Alice — nice to meet you."); ps.executeUpdate();
                    }
                }
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
    }

    // ----------------- Utils -----------------
    private static String hashPassword(String pw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(pw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x: b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void showError(Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
    private static void setSystemLAF() { try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {} }
}
