import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import model.Account;
import model.Email;
import protocol.Pop3Client;
import protocol.SmtpClient;
import storage.FileManager;

public class MailClientGUI extends JFrame {

    // Theme
    public static final int WINDOW_WIDTH = 1320;
    public static final int WINDOW_HEIGHT = 820;

    public static final Color COL_BG_MAIN = new Color(244, 246, 250);
    public static final Color COL_SURFACE = Color.WHITE;
    public static final Color COL_SIDEBAR = new Color(248, 250, 252);
    public static final Color COL_BORDER = new Color(220, 226, 235);
    public static final Color COL_PRIMARY = new Color(11, 87, 208);
    public static final Color COL_PRIMARY_HOVER = new Color(8, 66, 160);
    public static final Color COL_COMPOSE_FAB = new Color(194, 231, 255);
    public static final Color COL_COMPOSE_FAB_HOVER = new Color(172, 219, 250);
    public static final Color COL_COMPOSE_FAB_FG = new Color(0, 29, 53);
    public static final Color COL_NAV_ACTIVE_BG = new Color(211, 227, 253);
    public static final Color COL_NAV_ACTIVE_FG = new Color(4, 30, 73);
    public static final Color COL_SUCCESS = new Color(22, 163, 74);
    public static final Color COL_SUCCESS_HOVER = new Color(21, 128, 61);
    public static final Color COL_DANGER = new Color(220, 38, 38);
    public static final Color COL_DANGER_HOVER = new Color(185, 28, 28);
    public static final Color COL_SECONDARY_BG = new Color(241, 245, 249);
    public static final Color COL_SECONDARY_FG = new Color(30, 41, 59);
    public static final Color COL_TEXT_MAIN = new Color(15, 23, 42);
    public static final Color COL_TEXT_MUTED = new Color(100, 116, 139);
    public static final Color COL_CHAT_ME = new Color(219, 234, 254);
    public static final Color COL_CHAT_PEER = new Color(241, 245, 249);

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_UI = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_UI_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);

    private static final String CARD_MAILBOX = "MAILBOX";
    private static final String CARD_COMPOSE = "COMPOSE";
    private static final String CARD_CHAT = "CHAT";
    private static final String CARD_SETTINGS = "SETTINGS";

    // State
    private final FileManager fileManager;
    private Account currentUser;
    private final SmtpClient smtpClient;
    private final Pop3Client pop3Client;
    private Timer autoSyncTimer;
    private long lastKnownFileModified = 0L;

    private boolean viewingInbox = true;
    private String activeReceiveProtocol = "IMAP";
    private String tcpServerHost = "127.0.0.1";
    private int tcpSmtpPort = 2525;
    private int tcpPop3Port = 1110;
    private int tcpImapPort = 1143;

    private String savedGmailAddress = "";
    private String savedGmailAppPass = "";
    private boolean gmailVerified = false;

    private final List<Email> displayedMailboxEmails = new ArrayList<>();
    private final Map<String, List<Email>> chatThreadsMap = new LinkedHashMap<>();
    private String selectedChatPeer = null;

    // UI
    private CardLayout workspaceCardLayout;
    private JPanel workspaceContainer;

    private JButton navBtnInbox;
    private JButton navBtnSent;
    private JButton navBtnCompose;
    private JButton navBtnChat;
    private JButton navBtnSettings;
    private JLabel lblSidebarProtocolBadge;
    private JLabel lblSidebarGmailBadge;

    // Mailbox (POP3 / IMAP)
    private JLabel lblMailboxTitle;
    private JTextField txtSearchMail;
    private JButton btnReplyMail;
    private DefaultTableModel modelMailTable;
    private JTable tblMail;
    private JLabel lblReaderSubject;
    private JLabel lblReaderMeta;
    private JTextArea txtReaderBody;
    private JPanel pnlReaderAttachment;

    // Compose & SendMail
    private JComboBox<String> cbComposeChannel;
    private JLabel lblComposeFrom;
    private JTextField txtComposeTo;
    private JTextField txtComposeSubject;
    private JTextField txtComposeAttachment;
    private JTextArea txtComposeBody;
    private JButton btnSendMail;

    // Chat
    private DefaultListModel<String> modelChatContacts;
    private JList<String> listChatContacts;
    private CardLayout chatRightCardLayout;
    private JPanel chatRightContainer;
    private JLabel lblChatPeerHeader;
    private JPanel pnlChatMessagesList;
    private JScrollPane scrollChatMessages;
    private JTextField txtChatInput;
    private JButton btnChatSend;

    // Settings
    private JTextField txtSettingGmailUser;
    private JPasswordField txtSettingGmailPass;
    private JLabel lblGmailVerifyStatus;
    private JButton btnVerifyGmail;
    private JComboBox<String> cbSettingReceiveProtocol;
    private JTextField txtSettingServerHost;
    private JTextField txtSettingSmtpPort;
    private JPasswordField txtSettingNewAccountPass;

    public MailClientGUI(FileManager fileManager, Account loggedInUser) {
        this.fileManager = fileManager;
        this.currentUser = loggedInUser;
        this.smtpClient = new SmtpClient();
        this.pop3Client = new Pop3Client();

        setTitle("Mail Workspace - " + currentUser.getUsername());
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setMinimumSize(new Dimension(1080, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        loadUserSettings();
        initLayout();
        syncMailboxData(true);
        startAutoSyncTimer();
    }

    // UI Helpers
    public static JButton createFlatButton(String text, Color bgColor, Color fgColor, Color hoverColor, boolean bold, int radius) {
        JButton btn = new JButton(text) {
            private boolean hovered = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(203, 213, 225));
                } else if (hovered && hoverColor != null) {
                    g2.setColor(hoverColor);
                } else {
                    g2.setColor(getBackground());
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                if (getBackground().equals(COL_SECONDARY_BG) || getBackground().equals(Color.WHITE)) {
                    g2.setColor(COL_BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(bold ? FONT_UI_BOLD : FONT_UI);
        btn.setBackground(bgColor);
        btn.setForeground(fgColor);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(8, 16, 8, 16));
        return btn;
    }

    public static void styleTextField(JTextField tf) {
        tf.setFont(FONT_UI);
        tf.setForeground(COL_TEXT_MAIN);
        tf.setBackground(Color.WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COL_BORDER, 1, true),
                new EmptyBorder(7, 10, 7, 10)
        ));
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_UI);
        table.setRowHeight(34);
        table.setGridColor(new Color(235, 240, 245));
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(COL_TEXT_MAIN);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_UI_BOLD);
        header.setBackground(new Color(248, 250, 252));
        header.setForeground(COL_TEXT_MUTED);
        header.setPreferredSize(new Dimension(header.getWidth(), 36));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
    }

    // Layout
    private void initLayout() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(COL_BG_MAIN);

        root.add(buildSidebar(), BorderLayout.WEST);

        workspaceCardLayout = new CardLayout();
        workspaceContainer = new JPanel(workspaceCardLayout);
        workspaceContainer.setBackground(COL_BG_MAIN);
        workspaceContainer.setBorder(new EmptyBorder(12, 12, 12, 12));

        workspaceContainer.add(buildMailboxView(), CARD_MAILBOX);
        workspaceContainer.add(buildComposeView(), CARD_COMPOSE);
        workspaceContainer.add(buildChatView(), CARD_CHAT);
        workspaceContainer.add(buildSettingsView(), CARD_SETTINGS);

        root.add(workspaceContainer, BorderLayout.CENTER);
        setContentPane(root);
        switchNavigation(0);
    }

    // Sidebar
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 0));
        sidebar.setBackground(COL_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(245, 100));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, COL_BORDER));

        JPanel topBox = new JPanel();
        topBox.setLayout(new BoxLayout(topBox, BoxLayout.Y_AXIS));
        topBox.setOpaque(false);
        topBox.setBorder(new EmptyBorder(18, 14, 14, 14));

        JLabel lblBrand = new JLabel("Mail Workspace");
        lblBrand.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblBrand.setForeground(COL_PRIMARY);
        lblBrand.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSubBrand = new JLabel("TCP/IP • SMTP • POP3 • IMAP");
        lblSubBrand.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSubBrand.setForeground(COL_TEXT_MUTED);
        lblSubBrand.setAlignmentX(Component.LEFT_ALIGNMENT);

        topBox.add(lblBrand);
        topBox.add(Box.createVerticalStrut(2));
        topBox.add(lblSubBrand);
        topBox.add(Box.createVerticalStrut(18));

        JButton btnFabCompose = createFlatButton(
                "+  Soạn thư mới",
                COL_COMPOSE_FAB, COL_COMPOSE_FAB_FG, COL_COMPOSE_FAB_HOVER, true, 18
        );
        btnFabCompose.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnFabCompose.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btnFabCompose.addActionListener(e -> switchNavigation(2));
        topBox.add(btnFabCompose);
        topBox.add(Box.createVerticalStrut(18));

        navBtnInbox = createNavButton("Hộp thư đến (0)");
        navBtnInbox.addActionListener(e -> {
            viewingInbox = true;
            switchNavigation(0);
        });

        navBtnSent = createNavButton("Thư đã gửi (0)");
        navBtnSent.addActionListener(e -> {
            viewingInbox = false;
            switchNavigation(1);
        });

        navBtnCompose = createNavButton("Soạn thư");
        navBtnCompose.addActionListener(e -> switchNavigation(2));

        navBtnChat = createNavButton("Trò chuyện (0)");
        navBtnChat.addActionListener(e -> switchNavigation(3));

        navBtnSettings = createNavButton("Cài đặt (Settings)");
        navBtnSettings.addActionListener(e -> switchNavigation(4));

        topBox.add(navBtnInbox);
        topBox.add(Box.createVerticalStrut(4));
        topBox.add(navBtnSent);
        topBox.add(Box.createVerticalStrut(4));
        topBox.add(navBtnCompose);
        topBox.add(Box.createVerticalStrut(4));
        topBox.add(navBtnChat);
        topBox.add(Box.createVerticalStrut(4));
        topBox.add(navBtnSettings);

        sidebar.add(topBox, BorderLayout.NORTH);

        JPanel bottomProfile = new JPanel();
        bottomProfile.setLayout(new BoxLayout(bottomProfile, BoxLayout.Y_AXIS));
        bottomProfile.setBackground(new Color(241, 245, 249));
        bottomProfile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COL_BORDER),
                new EmptyBorder(12, 14, 14, 14)
        ));

        JLabel lblCurrentTitle = new JLabel("Tài khoản đang đăng nhập:");
        lblCurrentTitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblCurrentTitle.setForeground(COL_TEXT_MUTED);
        lblCurrentTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblCurrentEmail = new JLabel(currentUser.getUsername());
        lblCurrentEmail.setFont(FONT_UI_BOLD);
        lblCurrentEmail.setForeground(COL_TEXT_MAIN);
        lblCurrentEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblSidebarProtocolBadge = new JLabel("Giao thức: " + activeReceiveProtocol + " / SMTP");
        lblSidebarProtocolBadge.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSidebarProtocolBadge.setForeground(COL_PRIMARY);
        lblSidebarProtocolBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblSidebarGmailBadge = new JLabel();
        lblSidebarGmailBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblSidebarGmailBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateSidebarBadges();

        JButton btnLogout = createFlatButton(
                "Đăng xuất", COL_SECONDARY_BG, COL_DANGER, new Color(254, 226, 226), true, 8
        );
        btnLogout.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLogout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btnLogout.addActionListener(e -> handleLogout());

        bottomProfile.add(lblCurrentTitle);
        bottomProfile.add(Box.createVerticalStrut(2));
        bottomProfile.add(lblCurrentEmail);
        bottomProfile.add(Box.createVerticalStrut(6));
        bottomProfile.add(lblSidebarProtocolBadge);
        bottomProfile.add(Box.createVerticalStrut(2));
        bottomProfile.add(lblSidebarGmailBadge);
        bottomProfile.add(Box.createVerticalStrut(10));
        bottomProfile.add(btnLogout);

        sidebar.add(bottomProfile, BorderLayout.SOUTH);
        return sidebar;
    }

    private JButton createNavButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_UI);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(true);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        styleNavButton(btn, false);
        return btn;
    }

    private void styleNavButton(JButton btn, boolean active) {
        if (active) {
            btn.setFont(FONT_UI_BOLD);
            btn.setBackground(COL_NAV_ACTIVE_BG);
            btn.setForeground(COL_NAV_ACTIVE_FG);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, COL_PRIMARY),
                    new EmptyBorder(8, 12, 8, 12)
            ));
        } else {
            btn.setFont(FONT_UI);
            btn.setBackground(COL_SIDEBAR);
            btn.setForeground(COL_TEXT_MAIN);
            btn.setBorder(new EmptyBorder(8, 16, 8, 12));
        }
    }

    private void switchNavigation(int index) {
        styleNavButton(navBtnInbox, index == 0);
        styleNavButton(navBtnSent, index == 1);
        styleNavButton(navBtnCompose, index == 2);
        styleNavButton(navBtnChat, index == 3);
        styleNavButton(navBtnSettings, index == 4);

        if (index == 0 || index == 1) {
            workspaceCardLayout.show(workspaceContainer, CARD_MAILBOX);
            syncMailboxData(false);
        } else if (index == 2) {
            refreshComposeChannelOptions();
            workspaceCardLayout.show(workspaceContainer, CARD_COMPOSE);
            txtComposeTo.requestFocusInWindow();
        } else if (index == 3) {
            rebuildChatThreadsFromEmails();
            workspaceCardLayout.show(workspaceContainer, CARD_CHAT);
        } else if (index == 4) {
            workspaceCardLayout.show(workspaceContainer, CARD_SETTINGS);
        }
    }

    private void updateSidebarBadges() {
        if (lblSidebarProtocolBadge != null) {
            lblSidebarProtocolBadge.setText("Giao thức: " + activeReceiveProtocol + " & SMTP (TCP/IP)");
        }
        if (lblSidebarGmailBadge != null) {
            if (gmailVerified && !savedGmailAddress.isEmpty()) {
                lblSidebarGmailBadge.setText("● Gmail thật: " + savedGmailAddress);
                lblSidebarGmailBadge.setForeground(COL_SUCCESS);
            } else {
                lblSidebarGmailBadge.setText("○ Gmail thật: Chưa liên kết");
                lblSidebarGmailBadge.setForeground(COL_TEXT_MUTED);
            }
        }
    }

    // Mailbox (POP3 / IMAP)
    private JPanel buildMailboxView() {
        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);

        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setBackground(COL_SURFACE);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COL_BORDER, 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));

        lblMailboxTitle = new JLabel("Hộp thư đến");
        lblMailboxTitle.setFont(FONT_TITLE);
        lblMailboxTitle.setForeground(COL_TEXT_MAIN);
        topBar.add(lblMailboxTitle, BorderLayout.WEST);

        txtSearchMail = new JTextField();
        styleTextField(txtSearchMail);
        txtSearchMail.setToolTipText("Tìm kiếm trong thư theo địa chỉ email, chủ đề hoặc nội dung...");
        txtSearchMail.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                populateMailboxTable();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                populateMailboxTable();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                populateMailboxTable();
            }
        });

        JPanel searchWrap = new JPanel(new BorderLayout(6, 0));
        searchWrap.setOpaque(false);
        JLabel lblSearch = new JLabel("Tìm kiếm:");
        lblSearch.setFont(FONT_UI_BOLD);
        lblSearch.setForeground(COL_TEXT_MUTED);
        searchWrap.add(lblSearch, BorderLayout.WEST);
        searchWrap.add(txtSearchMail, BorderLayout.CENTER);
        topBar.add(searchWrap, BorderLayout.CENTER);

        JPanel actionsBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionsBar.setOpaque(false);

        JButton btnSync = createFlatButton("Đồng bộ", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_ACTIVE_BG, false, 8);
        btnSync.addActionListener(e -> syncMailboxData(true));

        btnReplyMail = createFlatButton("Trả lời", COL_SECONDARY_BG, COL_PRIMARY, COL_NAV_ACTIVE_BG, true, 8);
        btnReplyMail.addActionListener(e -> replyToSelectedEmail());

        JButton btnAttachment = createFlatButton("Tệp đính kèm", COL_SECONDARY_BG, COL_SUCCESS, COL_NAV_ACTIVE_BG, true, 8);
        btnAttachment.addActionListener(e -> openOrSaveSelectedAttachment());

        JButton btnDelete = createFlatButton("Xóa", COL_SECONDARY_BG, COL_DANGER, new Color(254, 226, 226), false, 8);
        btnDelete.addActionListener(e -> deleteSelectedEmail());

        actionsBar.add(btnSync);
        actionsBar.add(btnReplyMail);
        actionsBar.add(btnAttachment);
        actionsBar.add(btnDelete);
        topBar.add(actionsBar, BorderLayout.EAST);

        container.add(topBar, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.46);
        splitPane.setBorder(null);

        modelMailTable = new DefaultTableModel(
                new String[]{"Người gửi / Nhận", "Chủ đề", "Thời gian", "Đính kèm"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblMail = new JTable(modelMailTable);
        styleTable(tblMail);
        tblMail.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblMail.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedEmailDetails();
            }
        });

        JScrollPane scrollTable = new JScrollPane(tblMail);
        scrollTable.setBorder(new LineBorder(COL_BORDER, 1, true));
        splitPane.setLeftComponent(scrollTable);

        JPanel readerCard = new JPanel(new BorderLayout(0, 0));
        readerCard.setBackground(COL_SURFACE);
        readerCard.setBorder(new LineBorder(COL_BORDER, 1, true));

        JPanel readerHeader = new JPanel();
        readerHeader.setLayout(new BoxLayout(readerHeader, BoxLayout.Y_AXIS));
        readerHeader.setBackground(new Color(248, 250, 252));
        readerHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER),
                new EmptyBorder(14, 18, 14, 18)
        ));

        lblReaderSubject = new JLabel("Chọn một thư để xem nội dung");
        lblReaderSubject.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblReaderSubject.setForeground(COL_TEXT_MAIN);
        lblReaderSubject.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblReaderMeta = new JLabel(" ");
        lblReaderMeta.setFont(FONT_SMALL);
        lblReaderMeta.setForeground(COL_TEXT_MUTED);
        lblReaderMeta.setAlignmentX(Component.LEFT_ALIGNMENT);

        readerHeader.add(lblReaderSubject);
        readerHeader.add(Box.createVerticalStrut(6));
        readerHeader.add(lblReaderMeta);
        readerCard.add(readerHeader, BorderLayout.NORTH);

        JPanel readerBodyWrap = new JPanel(new BorderLayout(0, 10));
        readerBodyWrap.setBackground(COL_SURFACE);
        readerBodyWrap.setBorder(new EmptyBorder(14, 18, 14, 18));

        txtReaderBody = new JTextArea();
        txtReaderBody.setEditable(false);
        txtReaderBody.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtReaderBody.setForeground(COL_TEXT_MAIN);
        txtReaderBody.setLineWrap(true);
        txtReaderBody.setWrapStyleWord(true);
        txtReaderBody.setBorder(null);

        pnlReaderAttachment = new JPanel(new BorderLayout(0, 8));
        pnlReaderAttachment.setBackground(COL_SURFACE);
        pnlReaderAttachment.setVisible(false);

        readerBodyWrap.add(txtReaderBody, BorderLayout.CENTER);
        readerBodyWrap.add(pnlReaderAttachment, BorderLayout.SOUTH);

        JScrollPane scrollReader = new JScrollPane(readerBodyWrap);
        scrollReader.setBorder(null);
        readerCard.add(scrollReader, BorderLayout.CENTER);

        splitPane.setRightComponent(readerCard);
        container.add(splitPane, BorderLayout.CENTER);
        return container;
    }

    private void populateMailboxTable() {
        List<Email> sourceList = viewingInbox ? currentUser.getInbox() : currentUser.getSent();
        String query = txtSearchMail == null ? "" : txtSearchMail.getText().trim().toLowerCase();

        displayedMailboxEmails.clear();
        modelMailTable.setRowCount(0);

        for (int i = sourceList.size() - 1; i >= 0; i--) {
            Email e = sourceList.get(i);
            if (!query.isEmpty()) {
                boolean match = e.getSender().toLowerCase().contains(query)
                        || e.getReceiver().toLowerCase().contains(query)
                        || e.getSubject().toLowerCase().contains(query)
                        || e.getBody().toLowerCase().contains(query);
                if (!match) {
                    continue;
                }
            }
            displayedMailboxEmails.add(e);
            String peer = viewingInbox ? e.getSender() : ("Đến: " + e.getReceiver());
            String att = ("None".equalsIgnoreCase(e.getAttachedFile()) || e.getAttachedFile().isEmpty())
                    ? ""
                    : e.getAttachedFile();
            modelMailTable.addRow(new Object[]{peer, e.getSubject(), e.getTime(), att});
        }

        if (displayedMailboxEmails.isEmpty()) {
            lblReaderSubject.setText(viewingInbox ? "Hộp thư đến đang trống" : "Chưa có thư đã gửi");
            lblReaderMeta.setText("Giao thức đồng bộ: " + activeReceiveProtocol);
            txtReaderBody.setText("");
            pnlReaderAttachment.removeAll();
            pnlReaderAttachment.setVisible(false);
        } else {
            tblMail.setRowSelectionInterval(0, 0);
            displaySelectedEmailDetails();
        }
    }

    private void displaySelectedEmailDetails() {
        int row = tblMail.getSelectedRow();
        if (row < 0 || row >= displayedMailboxEmails.size()) {
            return;
        }
        Email e = displayedMailboxEmails.get(row);
        lblReaderSubject.setText(e.getSubject());
        lblReaderMeta.setText(String.format(
                "Từ: %s   |   Đến: %s   |   Thời gian: %s   |   Giao thức: %s",
                e.getSender(), e.getReceiver(), e.getTime(), activeReceiveProtocol
        ));
        txtReaderBody.setText(e.getBody());
        txtReaderBody.setCaretPosition(0);

        pnlReaderAttachment.removeAll();
        String att = e.getAttachedFile();
        if (att != null && !att.trim().isEmpty() && !"None".equalsIgnoreCase(att.trim())) {
            pnlReaderAttachment.setVisible(true);
            pnlReaderAttachment.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, COL_BORDER),
                    new EmptyBorder(10, 0, 0, 0)
            ));

            JPanel attBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            attBar.setOpaque(false);
            JLabel lblAttName = new JLabel("Đính kèm: " + att);
            lblAttName.setFont(FONT_UI_BOLD);
            lblAttName.setForeground(COL_PRIMARY);

            JButton btnOpenAtt = createFlatButton("Mở / Tải xuống", COL_SECONDARY_BG, COL_SUCCESS, COL_NAV_ACTIVE_BG, true, 6);
            btnOpenAtt.addActionListener(ev -> openOrSaveSelectedAttachment());

            attBar.add(lblAttName);
            attBar.add(btnOpenAtt);
            pnlReaderAttachment.add(attBar, BorderLayout.NORTH);

            File realFile = resolveLocalAttachment(att);
            if (realFile != null && realFile.exists()) {
                String lower = realFile.getName().toLowerCase();
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
                    ImageIcon icon = new ImageIcon(realFile.getAbsolutePath());
                    if (icon.getIconWidth() > 0) {
                        int maxW = 460;
                        int maxH = 260;
                        int w = icon.getIconWidth();
                        int h = icon.getIconHeight();
                        if (w > maxW || h > maxH) {
                            double scale = Math.min((double) maxW / w, (double) maxH / h);
                            w = (int) (w * scale);
                            h = (int) (h * scale);
                        }
                        Image scaled = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
                        JLabel imgLabel = new JLabel(new ImageIcon(scaled));
                        imgLabel.setBorder(new LineBorder(COL_BORDER, 1, true));
                        pnlReaderAttachment.add(imgLabel, BorderLayout.CENTER);
                    }
                }
            }
        } else {
            pnlReaderAttachment.setVisible(false);
        }
        pnlReaderAttachment.revalidate();
        pnlReaderAttachment.repaint();
    }

    // Compose & SendMail
    private JPanel buildComposeView() {
        JPanel card = new JPanel(new BorderLayout(0, 0));
        card.setBackground(COL_SURFACE);
        card.setBorder(new LineBorder(COL_BORDER, 1, true));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(248, 250, 252));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER),
                new EmptyBorder(14, 20, 14, 20)
        ));
        JLabel lblTitle = new JLabel("Soạn thư mới (SMTP)");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(COL_TEXT_MAIN);
        header.add(lblTitle, BorderLayout.WEST);
        card.add(header, BorderLayout.NORTH);

        JPanel bodyWrap = new JPanel(new BorderLayout(0, 10));
        bodyWrap.setBackground(COL_SURFACE);
        bodyWrap.setBorder(new EmptyBorder(16, 24, 16, 24));

        JPanel formGrid = new JPanel(new GridBagLayout());
        formGrid.setBackground(COL_SURFACE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        cbComposeChannel = new JComboBox<>();
        cbComposeChannel.setFont(FONT_UI);
        refreshComposeChannelOptions();

        lblComposeFrom = new JLabel(currentUser.getUsername());
        lblComposeFrom.setFont(FONT_UI_BOLD);
        lblComposeFrom.setForeground(COL_PRIMARY);

        txtComposeTo = new JTextField("");
        txtComposeSubject = new JTextField("");
        txtComposeAttachment = new JTextField("");
        txtComposeAttachment.setEditable(false);
        styleTextField(txtComposeTo);
        styleTextField(txtComposeSubject);
        styleTextField(txtComposeAttachment);

        JButton btnPickFile = createFlatButton("Chọn tệp...", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_ACTIVE_BG, false, 8);
        btnPickFile.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Chọn tệp đính kèm");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                txtComposeAttachment.setText(f.getName() + " (" + f.length() + " bytes - " + f.getAbsolutePath() + ")");
            }
        });

        JButton btnClearFile = createFlatButton("Bỏ tệp", COL_SECONDARY_BG, COL_DANGER, new Color(254, 226, 226), false, 8);
        btnClearFile.addActionListener(e -> txtComposeAttachment.setText(""));

        JButton btnOpenSettingQuick = createFlatButton("Cấu hình Gmail thật", COL_SECONDARY_BG, COL_PRIMARY, COL_NAV_ACTIVE_BG, false, 8);
        btnOpenSettingQuick.addActionListener(e -> switchNavigation(4));

        addComposeRow(formGrid, gbc, 0, "Phương thức gửi:", cbComposeChannel, btnOpenSettingQuick);
        addComposeRow(formGrid, gbc, 1, "Từ (From):", lblComposeFrom, null);
        addComposeRow(formGrid, gbc, 2, "Đến (Email nhận):", txtComposeTo, null);
        addComposeRow(formGrid, gbc, 3, "Chủ đề (Subject):", txtComposeSubject, null);

        JPanel fileActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        fileActionPanel.setOpaque(false);
        fileActionPanel.add(btnPickFile);
        fileActionPanel.add(btnClearFile);
        addComposeRow(formGrid, gbc, 4, "Tệp đính kèm:", txtComposeAttachment, fileActionPanel);

        bodyWrap.add(formGrid, BorderLayout.NORTH);

        txtComposeBody = new JTextArea();
        txtComposeBody.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtComposeBody.setLineWrap(true);
        txtComposeBody.setWrapStyleWord(true);
        txtComposeBody.setMargin(new Insets(12, 12, 12, 12));

        JScrollPane scrollBody = new JScrollPane(txtComposeBody);
        scrollBody.setBorder(new LineBorder(COL_BORDER, 1, true));
        bodyWrap.add(scrollBody, BorderLayout.CENTER);

        card.add(bodyWrap, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setBackground(new Color(248, 250, 252));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COL_BORDER),
                new EmptyBorder(12, 24, 12, 24)
        ));

        JButton btnResetCompose = createFlatButton("Xóa trắng", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_ACTIVE_BG, false, 8);
        btnResetCompose.addActionListener(e -> {
            txtComposeTo.setText("");
            txtComposeSubject.setText("");
            txtComposeAttachment.setText("");
            txtComposeBody.setText("");
        });

        btnSendMail = createFlatButton("Gửi thư (SMTP)", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true, 18);
        btnSendMail.addActionListener(e -> executeSendMailFromCompose());

        footer.add(btnResetCompose);
        footer.add(btnSendMail);
        card.add(footer, BorderLayout.SOUTH);

        return card;
    }

    private void addComposeRow(JPanel grid, GridBagConstraints gbc, int row, String label, Component field, Component rightAction) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_UI_BOLD);
        lbl.setForeground(COL_TEXT_MUTED);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        lbl.setPreferredSize(new Dimension(140, 32));
        grid.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1.0;
        gbc.gridwidth = (rightAction == null) ? 2 : 1;
        grid.add(field, gbc);

        if (rightAction != null) {
            gbc.gridx = 2;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.gridwidth = 1;
            grid.add(rightAction, gbc);
        }
    }

    private void refreshComposeChannelOptions() {
        if (cbComposeChannel == null) {
            return;
        }
        int prev = cbComposeChannel.getSelectedIndex();
        cbComposeChannel.removeAllItems();
        cbComposeChannel.addItem("1. TCP/IP Mail Server (" + tcpServerHost + ":" + tcpSmtpPort + " / Lưu Database)");
        if (!savedGmailAddress.isEmpty() && !savedGmailAppPass.isEmpty()) {
            String status = gmailVerified ? "Đã xác nhận" : "Đã lưu";
            cbComposeChannel.addItem("2. Gmail Thực tế (" + savedGmailAddress + " - " + status + ")");
        } else {
            cbComposeChannel.addItem("2. Gmail Thực tế (Chưa cấu hình - Bấm 'Cấu hình Gmail thật')");
        }
        if (prev >= 0 && prev < cbComposeChannel.getItemCount()) {
            cbComposeChannel.setSelectedIndex(prev);
        } else if (gmailVerified) {
            cbComposeChannel.setSelectedIndex(1);
        }
    }

    private void executeSendMailFromCompose() {
        final String toEmail = FileManager.normalizeEmail(txtComposeTo.getText());
        String rawSub = txtComposeSubject.getText().trim();
        final String subject = rawSub.isEmpty() ? "(Không có chủ đề)" : rawSub;
        final String body = txtComposeBody.getText().trim();
        final String attachment = txtComposeAttachment.getText().trim();

        if (!FileManager.isValidEmailSyntax(toEmail)) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập đúng định dạng địa chỉ Email người nhận (ví dụ: user@gmail.com)!",
                    "Email không hợp lệ", JOptionPane.WARNING_MESSAGE);
            txtComposeTo.requestFocusInWindow();
            return;
        }

        if (body.isEmpty() && attachment.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập nội dung thư hoặc chọn tệp đính kèm!",
                    "Thiếu nội dung", JOptionPane.WARNING_MESSAGE);
            txtComposeBody.requestFocusInWindow();
            return;
        }

        final int channelIdx = cbComposeChannel.getSelectedIndex();
        if (channelIdx == 1 && (savedGmailAddress.isEmpty() || savedGmailAppPass.isEmpty())) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn chưa nhập Email & App Password của Gmail thực tế trong phần Cài đặt.\n"
                            + "Chuyển sang tab Cài đặt (Settings) ngay bây giờ?",
                    "Chưa cấu hình Gmail thực tế", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                switchNavigation(4);
            }
            return;
        }

        btnSendMail.setEnabled(false);
        btnSendMail.setText("Đang gửi...");

        new Thread(() -> {
            final boolean ok;
            if (channelIdx == 1) {
                ok = smtpClient.sendEmailViaExternalServer(
                        currentUser, "smtp.gmail.com", 587,
                        savedGmailAddress, savedGmailAppPass,
                        toEmail, subject, body, attachment
                );
            } else {
                boolean sentViaTcp = smtpClient.sendEmailViaExternalServer(
                        currentUser, tcpServerHost, tcpSmtpPort,
                        "", "", toEmail, subject, body, attachment
                );
                if (!sentViaTcp) {
                    if (!fileManager.userExists(toEmail)) {
                        try {
                            fileManager.createAccount(toEmail, "12345678");
                        } catch (Exception ignored) {
                        }
                    }
                    ok = smtpClient.sendEmail(currentUser, toEmail, subject, body, attachment);
                } else {
                    ok = true;
                }
            }

            SwingUtilities.invokeLater(() -> {
                btnSendMail.setEnabled(true);
                btnSendMail.setText("Gửi thư (SMTP)");
                syncMailboxData(false);

                if (ok) {
                    txtComposeTo.setText("");
                    txtComposeSubject.setText("");
                    txtComposeAttachment.setText("");
                    txtComposeBody.setText("");
                    JOptionPane.showMessageDialog(this,
                            smtpClient.getLastStatusMessage(),
                            "Gửi thư thành công", JOptionPane.INFORMATION_MESSAGE);
                    viewingInbox = false;
                    switchNavigation(1);
                } else {
                    JOptionPane.showMessageDialog(this,
                            smtpClient.getLastStatusMessage(),
                            "Gửi thư thất bại", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    // Chat
    private JPanel buildChatView() {
        JPanel chatRoot = new JPanel(new BorderLayout(0, 0));
        chatRoot.setBackground(COL_SURFACE);
        chatRoot.setBorder(new LineBorder(COL_BORDER, 1, true));

        JSplitPane chatSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        chatSplit.setResizeWeight(0.28);
        chatSplit.setBorder(null);

        JPanel leftContactsPanel = new JPanel(new BorderLayout(0, 0));
        leftContactsPanel.setBackground(COL_SIDEBAR);
        leftContactsPanel.setPreferredSize(new Dimension(280, 100));

        JPanel leftHeader = new JPanel(new BorderLayout(6, 0));
        leftHeader.setBackground(new Color(241, 245, 249));
        leftHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel lblContactTitle = new JLabel("Đã liên lạc");
        lblContactTitle.setFont(FONT_SECTION);
        lblContactTitle.setForeground(COL_TEXT_MAIN);

        JButton btnAddChatBubbleSmall = createFlatButton("+ Mới", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true, 14);
        btnAddChatBubbleSmall.setMargin(new Insets(4, 10, 4, 10));
        btnAddChatBubbleSmall.addActionListener(e -> promptStartNewChatConversation());

        leftHeader.add(lblContactTitle, BorderLayout.WEST);
        leftHeader.add(btnAddChatBubbleSmall, BorderLayout.EAST);
        leftContactsPanel.add(leftHeader, BorderLayout.NORTH);

        modelChatContacts = new DefaultListModel<>();
        listChatContacts = new JList<>(modelChatContacts);
        listChatContacts.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listChatContacts.setBackground(COL_SIDEBAR);
        listChatContacts.setFixedCellHeight(54);
        listChatContacts.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel item = new JPanel(new BorderLayout(8, 2));
                item.setBorder(new EmptyBorder(8, 12, 8, 12));
                item.setBackground(isSelected ? COL_NAV_ACTIVE_BG : COL_SIDEBAR);

                String email = String.valueOf(value);
                List<Email> history = chatThreadsMap.get(email);
                int count = history == null ? 0 : history.size();
                String lastPreview = (history != null && !history.isEmpty())
                        ? history.get(history.size() - 1).getBody().replace("\n", " ")
                        : "";
                if (lastPreview.length() > 30) {
                    lastPreview = lastPreview.substring(0, 30) + "...";
                }

                JLabel lblEmail = new JLabel(email + " (" + count + ")");
                lblEmail.setFont(FONT_UI_BOLD);
                lblEmail.setForeground(isSelected ? COL_NAV_ACTIVE_FG : COL_TEXT_MAIN);

                JLabel lblSub = new JLabel(lastPreview.isEmpty() ? "(Đính kèm / Không có chữ)" : lastPreview);
                lblSub.setFont(FONT_SMALL);
                lblSub.setForeground(COL_TEXT_MUTED);

                item.add(lblEmail, BorderLayout.NORTH);
                item.add(lblSub, BorderLayout.SOUTH);
                return item;
            }
        });
        listChatContacts.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = listChatContacts.getSelectedValue();
                if (sel != null) {
                    selectedChatPeer = sel;
                    renderSelectedChatThread();
                }
            }
        });

        JScrollPane scrollContacts = new JScrollPane(listChatContacts);
        scrollContacts.setBorder(null);
        leftContactsPanel.add(scrollContacts, BorderLayout.CENTER);

        chatRightCardLayout = new CardLayout();
        chatRightContainer = new JPanel(chatRightCardLayout);
        chatRightContainer.setBackground(COL_SURFACE);

        chatRightContainer.add(buildEmptyChatBubblePanel(), "EMPTY_CHAT");
        chatRightContainer.add(buildActiveChatThreadPanel(), "ACTIVE_CHAT");

        chatSplit.setLeftComponent(leftContactsPanel);
        chatSplit.setRightComponent(chatRightContainer);
        chatRoot.add(chatSplit, BorderLayout.CENTER);
        return chatRoot;
    }

    private JPanel buildEmptyChatBubblePanel() {
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setBackground(COL_SURFACE);

        JPanel centerBox = new JPanel();
        centerBox.setLayout(new BoxLayout(centerBox, BoxLayout.Y_AXIS));
        centerBox.setOpaque(false);

        JButton plusBubbleButton = new JButton() {
            private boolean hovered = false;
            {
                setPreferredSize(new Dimension(88, 88));
                setMaximumSize(new Dimension(88, 88));
                setMinimumSize(new Dimension(88, 88));
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setAlignmentX(Component.CENTER_ALIGNMENT);
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hovered ? COL_PRIMARY : COL_COMPOSE_FAB);
                g2.fillOval(4, 4, getWidth() - 8, getHeight() - 8);
                g2.setColor(hovered ? Color.WHITE : COL_PRIMARY);
                g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.drawLine(cx - 14, cy, cx + 14, cy);
                g2.drawLine(cx, cy - 14, cx, cy + 14);
                g2.dispose();
            }
        };
        plusBubbleButton.addActionListener(e -> promptStartNewChatConversation());

        JLabel lblEmptyTitle = new JLabel("Bắt đầu cuộc trò chuyện với Email đã liên lạc");
        lblEmptyTitle.setFont(FONT_TITLE);
        lblEmptyTitle.setForeground(COL_TEXT_MAIN);
        lblEmptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblEmptySub = new JLabel("Chỉ những địa chỉ Email đã từng gửi/nhận thư với bạn mới hiển thị ở danh sách bên trái.");
        lblEmptySub.setFont(FONT_UI);
        lblEmptySub.setForeground(COL_TEXT_MUTED);
        lblEmptySub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblEmptyAction = new JLabel("Nhấn vào bong bóng dấu (+) phía trên để gửi tin nhắn mở đầu cho một Email mới");
        lblEmptyAction.setFont(FONT_UI_BOLD);
        lblEmptyAction.setForeground(COL_PRIMARY);
        lblEmptyAction.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerBox.add(plusBubbleButton);
        centerBox.add(Box.createVerticalStrut(18));
        centerBox.add(lblEmptyTitle);
        centerBox.add(Box.createVerticalStrut(6));
        centerBox.add(lblEmptySub);
        centerBox.add(Box.createVerticalStrut(8));
        centerBox.add(lblEmptyAction);

        emptyPanel.add(centerBox);
        return emptyPanel;
    }

    private JPanel buildActiveChatThreadPanel() {
        JPanel activePanel = new JPanel(new BorderLayout(0, 0));
        activePanel.setBackground(COL_SURFACE);

        JPanel topPeerBar = new JPanel(new BorderLayout(10, 0));
        topPeerBar.setBackground(new Color(248, 250, 252));
        topPeerBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER),
                new EmptyBorder(12, 18, 12, 18)
        ));

        lblChatPeerHeader = new JLabel("Cuộc trò chuyện");
        lblChatPeerHeader.setFont(FONT_SECTION);
        lblChatPeerHeader.setForeground(COL_PRIMARY);

        JButton btnBackToPlusBubble = createFlatButton("+ Tạo hội thoại mới", COL_SECONDARY_BG, COL_PRIMARY, COL_NAV_ACTIVE_BG, true, 12);
        btnBackToPlusBubble.addActionListener(e -> {
            listChatContacts.clearSelection();
            selectedChatPeer = null;
            chatRightCardLayout.show(chatRightContainer, "EMPTY_CHAT");
        });

        topPeerBar.add(lblChatPeerHeader, BorderLayout.WEST);
        topPeerBar.add(btnBackToPlusBubble, BorderLayout.EAST);
        activePanel.add(topPeerBar, BorderLayout.NORTH);

        pnlChatMessagesList = new JPanel();
        pnlChatMessagesList.setLayout(new BoxLayout(pnlChatMessagesList, BoxLayout.Y_AXIS));
        pnlChatMessagesList.setBackground(COL_SURFACE);
        pnlChatMessagesList.setBorder(new EmptyBorder(14, 18, 14, 18));

        scrollChatMessages = new JScrollPane(pnlChatMessagesList);
        scrollChatMessages.setBorder(null);
        scrollChatMessages.getVerticalScrollBar().setUnitIncrement(16);
        activePanel.add(scrollChatMessages, BorderLayout.CENTER);

        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(new Color(248, 250, 252));
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COL_BORDER),
                new EmptyBorder(10, 16, 10, 16)
        ));

        txtChatInput = new JTextField();
        styleTextField(txtChatInput);
        txtChatInput.addActionListener(e -> sendQuickChatMessage());

        btnChatSend = createFlatButton("Gửi tin nhắn", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true, 16);
        btnChatSend.addActionListener(e -> sendQuickChatMessage());

        inputBar.add(txtChatInput, BorderLayout.CENTER);
        inputBar.add(btnChatSend, BorderLayout.EAST);
        activePanel.add(inputBar, BorderLayout.SOUTH);

        return activePanel;
    }

    private void rebuildChatThreadsFromEmails() {
        chatThreadsMap.clear();
        String me = FileManager.normalizeEmail(currentUser.getUsername());

        List<Email> merged = new ArrayList<>();
        merged.addAll(currentUser.getInbox());
        merged.addAll(currentUser.getSent());
        merged.sort((a, b) -> a.getTime().compareTo(b.getTime()));

        for (Email e : merged) {
            String s = FileManager.normalizeEmail(e.getSender());
            String r = FileManager.normalizeEmail(e.getReceiver());
            String peer = s.equalsIgnoreCase(me) ? r : s;
            if (peer.isEmpty() || peer.equalsIgnoreCase(me)) {
                continue;
            }
            chatThreadsMap.computeIfAbsent(peer, k -> new ArrayList<>());
            List<Email> list = chatThreadsMap.get(peer);
            boolean duplicate = false;
            for (Email existing : list) {
                if (existing.getTime().equals(e.getTime())
                        && existing.getSubject().equals(e.getSubject())
                        && existing.getBody().equals(e.getBody())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                list.add(e);
            }
        }

        if (navBtnChat != null) {
            navBtnChat.setText("Trò chuyện (" + chatThreadsMap.size() + ")");
        }

        if (modelChatContacts == null) {
            return;
        }

        String prevPeer = selectedChatPeer;
        modelChatContacts.clear();
        int idxToSelect = -1;
        int idx = 0;
        for (String peer : chatThreadsMap.keySet()) {
            modelChatContacts.addElement(peer);
            if (peer.equalsIgnoreCase(prevPeer)) {
                idxToSelect = idx;
            }
            idx++;
        }

        if (modelChatContacts.isEmpty() || idxToSelect == -1) {
            selectedChatPeer = null;
            chatRightCardLayout.show(chatRightContainer, "EMPTY_CHAT");
        } else {
            listChatContacts.setSelectedIndex(idxToSelect);
            renderSelectedChatThread();
        }
    }

    private void renderSelectedChatThread() {
        if (selectedChatPeer == null || !chatThreadsMap.containsKey(selectedChatPeer)) {
            chatRightCardLayout.show(chatRightContainer, "EMPTY_CHAT");
            return;
        }
        chatRightCardLayout.show(chatRightContainer, "ACTIVE_CHAT");
        lblChatPeerHeader.setText("Trò chuyện qua Mail với: " + selectedChatPeer);

        pnlChatMessagesList.removeAll();
        String me = FileManager.normalizeEmail(currentUser.getUsername());
        List<Email> messages = chatThreadsMap.get(selectedChatPeer);

        for (Email e : messages) {
            boolean fromMe = FileManager.normalizeEmail(e.getSender()).equalsIgnoreCase(me);
            JPanel row = new JPanel(new FlowLayout(fromMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 4));
            row.setOpaque(false);

            JPanel bubble = new JPanel();
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
            bubble.setBackground(fromMe ? COL_CHAT_ME : COL_CHAT_PEER);
            bubble.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(fromMe ? new Color(147, 197, 253) : COL_BORDER, 1, true),
                    new EmptyBorder(8, 12, 8, 12)
            ));

            JLabel lblMeta = new JLabel((fromMe ? "Bạn" : e.getSender()) + " • " + e.getTime() + " • [" + e.getSubject() + "]");
            lblMeta.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lblMeta.setForeground(fromMe ? COL_PRIMARY : COL_TEXT_MUTED);
            lblMeta.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextArea txtMsg = new JTextArea(e.getBody());
            txtMsg.setEditable(false);
            txtMsg.setOpaque(false);
            txtMsg.setFont(FONT_UI);
            txtMsg.setForeground(COL_TEXT_MAIN);
            txtMsg.setLineWrap(true);
            txtMsg.setWrapStyleWord(true);
            txtMsg.setColumns(34);
            txtMsg.setAlignmentX(Component.LEFT_ALIGNMENT);

            bubble.add(lblMeta);
            bubble.add(Box.createVerticalStrut(4));
            bubble.add(txtMsg);

            if (e.getAttachedFile() != null && !"None".equalsIgnoreCase(e.getAttachedFile()) && !e.getAttachedFile().isEmpty()) {
                JLabel lblAtt = new JLabel("Đính kèm: " + e.getAttachedFile());
                lblAtt.setFont(FONT_UI_BOLD);
                lblAtt.setForeground(COL_SUCCESS);
                lblAtt.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubble.add(Box.createVerticalStrut(4));
                bubble.add(lblAtt);
            }

            row.add(bubble);
            pnlChatMessagesList.add(row);
        }

        pnlChatMessagesList.revalidate();
        pnlChatMessagesList.repaint();
        SwingUtilities.invokeLater(() -> {
            if (scrollChatMessages != null) {
                scrollChatMessages.getVerticalScrollBar().setValue(
                        scrollChatMessages.getVerticalScrollBar().getMaximum()
                );
            }
        });
    }

    private void promptStartNewChatConversation() {
        JTextField txtPeerEmail = new JTextField("", 20);
        JTextField txtFirstMessage = new JTextField("", 20);
        styleTextField(txtPeerEmail);
        styleTextField(txtFirstMessage);

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Email người nhận:"));
        form.add(txtPeerEmail);
        form.add(new JLabel("Tin nhắn mở đầu:"));
        form.add(txtFirstMessage);

        if (JOptionPane.showConfirmDialog(this, form, "Bắt đầu trò chuyện mới",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            String peer = FileManager.normalizeEmail(txtPeerEmail.getText());
            String msg = txtFirstMessage.getText().trim();
            if (!FileManager.isValidEmailSyntax(peer)) {
                JOptionPane.showMessageDialog(this, "Địa chỉ Email không hợp lệ!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (msg.isEmpty()) {
                msg = "Xin chào!";
            }
            selectedChatPeer = peer;
            dispatchChatMessage(peer, msg);
        }
    }

    private void sendQuickChatMessage() {
        if (selectedChatPeer == null || selectedChatPeer.isEmpty()) {
            return;
        }
        String text = txtChatInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        txtChatInput.setText("");
        dispatchChatMessage(selectedChatPeer, text);
    }

    private void dispatchChatMessage(String targetPeer, String content) {
        btnChatSend.setEnabled(false);
        new Thread(() -> {
            if (!fileManager.userExists(targetPeer)) {
                try {
                    fileManager.createAccount(targetPeer, "12345678");
                } catch (Exception ignored) {
                }
            }
            boolean ok;
            if (gmailVerified && !savedGmailAddress.isEmpty() && !savedGmailAppPass.isEmpty()) {
                ok = smtpClient.sendEmailViaExternalServer(
                        currentUser, "smtp.gmail.com", 587,
                        savedGmailAddress, savedGmailAppPass,
                        targetPeer, "Chat: " + currentUser.getUsername(), content, "None"
                );
            } else {
                ok = smtpClient.sendEmail(
                        currentUser, targetPeer, "Chat: " + currentUser.getUsername(), content, "None"
                );
            }
            SwingUtilities.invokeLater(() -> {
                btnChatSend.setEnabled(true);
                if (ok) {
                    selectedChatPeer = targetPeer;
                    syncMailboxData(false);
                    rebuildChatThreadsFromEmails();
                } else {
                    JOptionPane.showMessageDialog(this, smtpClient.getLastStatusMessage(),
                            "Lỗi gửi tin nhắn", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    // Settings
    private JPanel buildSettingsView() {
        JPanel outer = new JPanel(new BorderLayout(0, 14));
        outer.setOpaque(false);

        JPanel gmailCard = new JPanel(new BorderLayout(0, 0));
        gmailCard.setBackground(COL_SURFACE);
        gmailCard.setBorder(new LineBorder(COL_BORDER, 1, true));

        JPanel gmailHeader = new JPanel(new BorderLayout());
        gmailHeader.setBackground(new Color(248, 250, 252));
        gmailHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER),
                new EmptyBorder(12, 18, 12, 18)
        ));
        JLabel lblGmailTitle = new JLabel("1. Cấu hình & Xác nhận Gmail Thực tế (Gửi Mail Thật qua SMTP TLS 587)");
        lblGmailTitle.setFont(FONT_SECTION);
        lblGmailTitle.setForeground(COL_PRIMARY);
        gmailHeader.add(lblGmailTitle, BorderLayout.WEST);
        gmailCard.add(gmailHeader, BorderLayout.NORTH);

        JPanel gmailForm = new JPanel(new GridBagLayout());
        gmailForm.setBackground(COL_SURFACE);
        gmailForm.setBorder(new EmptyBorder(16, 20, 16, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        txtSettingGmailUser = new JTextField(savedGmailAddress.isEmpty() ? currentUser.getUsername() : savedGmailAddress);
        txtSettingGmailPass = new JPasswordField(savedGmailAppPass);
        styleTextField(txtSettingGmailUser);
        styleTextField(txtSettingGmailPass);

        final char defaultEcho = txtSettingGmailPass.getEchoChar();
        JCheckBox chkShowPass = new JCheckBox("Hiện App Password");
        chkShowPass.setOpaque(false);
        chkShowPass.setFont(FONT_SMALL);
        chkShowPass.addActionListener(e -> txtSettingGmailPass.setEchoChar(chkShowPass.isSelected() ? (char) 0 : defaultEcho));

        lblGmailVerifyStatus = new JLabel(
                gmailVerified
                        ? ("● Đã xác nhận kết nối Gmail thật: " + savedGmailAddress)
                        : "○ Chưa xác nhận (Nhập Gmail và Mật khẩu ứng dụng 16 ký tự của Google rồi bấm Xác nhận)"
        );
        lblGmailVerifyStatus.setFont(FONT_UI_BOLD);
        lblGmailVerifyStatus.setForeground(gmailVerified ? COL_SUCCESS : COL_TEXT_MUTED);

        addComposeRow(gmailForm, gc, 0, "Địa chỉ Gmail thật:", txtSettingGmailUser, null);
        addComposeRow(gmailForm, gc, 1, "App Password (16 ký tự):", txtSettingGmailPass, chkShowPass);

        gc.gridx = 1;
        gc.gridy = 2;
        gc.gridwidth = 2;
        gmailForm.add(lblGmailVerifyStatus, gc);

        JPanel gmailBtnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        gmailBtnBar.setOpaque(false);

        btnVerifyGmail = createFlatButton(
                "Xác nhận & Lưu tài khoản Gmail thực tế",
                COL_SUCCESS, Color.WHITE, COL_SUCCESS_HOVER, true, 10
        );
        btnVerifyGmail.addActionListener(e -> verifyAndSaveGmailCredentials());

        JButton btnClearGmail = createFlatButton(
                "Xóa cấu hình Gmail",
                COL_SECONDARY_BG, COL_DANGER, new Color(254, 226, 226), false, 10
        );
        btnClearGmail.addActionListener(e -> {
            savedGmailAddress = "";
            savedGmailAppPass = "";
            gmailVerified = false;
            txtSettingGmailPass.setText("");
            lblGmailVerifyStatus.setText("○ Đã xóa liên kết Gmail thực tế.");
            lblGmailVerifyStatus.setForeground(COL_TEXT_MUTED);
            saveUserSettings();
            updateSidebarBadges();
            refreshComposeChannelOptions();
        });

        gmailBtnBar.add(btnVerifyGmail);
        gmailBtnBar.add(btnClearGmail);

        gc.gridx = 1;
        gc.gridy = 3;
        gc.gridwidth = 2;
        gmailForm.add(gmailBtnBar, gc);
        gmailCard.add(gmailForm, BorderLayout.CENTER);

        JPanel protoCard = new JPanel(new BorderLayout(0, 0));
        protoCard.setBackground(COL_SURFACE);
        protoCard.setBorder(new LineBorder(COL_BORDER, 1, true));

        JPanel protoHeader = new JPanel(new BorderLayout());
        protoHeader.setBackground(new Color(248, 250, 252));
        protoHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER),
                new EmptyBorder(12, 18, 12, 18)
        ));
        JLabel lblProtoTitle = new JLabel("2. Cấu hình Giao thức TCP/IP (POP3 • IMAP • SMTP) & Bảo mật Tài khoản");
        lblProtoTitle.setFont(FONT_SECTION);
        lblProtoTitle.setForeground(COL_TEXT_MAIN);
        protoHeader.add(lblProtoTitle, BorderLayout.WEST);
        protoCard.add(protoHeader, BorderLayout.NORTH);

        JPanel protoForm = new JPanel(new GridBagLayout());
        protoForm.setBackground(COL_SURFACE);
        protoForm.setBorder(new EmptyBorder(16, 20, 16, 20));
        GridBagConstraints pc = new GridBagConstraints();
        pc.insets = new Insets(6, 6, 6, 6);
        pc.fill = GridBagConstraints.HORIZONTAL;

        cbSettingReceiveProtocol = new JComboBox<>(new String[]{
                "IMAP (Đồng bộ trực tiếp trên Mail Server - Port " + tcpImapPort + ")",
                "POP3 (Tải thư về máy trạm - Port " + tcpPop3Port + ")"
        });
        cbSettingReceiveProtocol.setFont(FONT_UI);
        cbSettingReceiveProtocol.setSelectedIndex("IMAP".equalsIgnoreCase(activeReceiveProtocol) ? 0 : 1);

        txtSettingServerHost = new JTextField(tcpServerHost);
        txtSettingSmtpPort = new JTextField(String.valueOf(tcpSmtpPort));
        txtSettingNewAccountPass = new JPasswordField("");
        styleTextField(txtSettingServerHost);
        styleTextField(txtSettingSmtpPort);
        styleTextField(txtSettingNewAccountPass);

        JButton btnSaveProtocol = createFlatButton("Lưu cấu hình TCP/IP", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true, 10);
        btnSaveProtocol.addActionListener(e -> {
            activeReceiveProtocol = (cbSettingReceiveProtocol.getSelectedIndex() == 0) ? "IMAP" : "POP3";
            tcpServerHost = txtSettingServerHost.getText().trim().isEmpty() ? "127.0.0.1" : txtSettingServerHost.getText().trim();
            try {
                tcpSmtpPort = Integer.parseInt(txtSettingSmtpPort.getText().trim());
            } catch (NumberFormatException ignored) {
                tcpSmtpPort = 2525;
            }
            saveUserSettings();
            updateSidebarBadges();
            refreshComposeChannelOptions();
            JOptionPane.showMessageDialog(this, "Đã lưu cấu hình giao thức " + activeReceiveProtocol + " / SMTP (" + tcpServerHost + ":" + tcpSmtpPort + ")!");
        });

        JButton btnUpdateAccountPass = createFlatButton("Đổi mật khẩu", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_ACTIVE_BG, true, 10);
        btnUpdateAccountPass.addActionListener(e -> updateAccountPasswordFromSettings());

        addComposeRow(protoForm, pc, 0, "Giao thức nhận thư:", cbSettingReceiveProtocol, null);
        addComposeRow(protoForm, pc, 1, "TCP/IP Server Host:", txtSettingServerHost, null);
        addComposeRow(protoForm, pc, 2, "Cổng SMTP Server:", txtSettingSmtpPort, btnSaveProtocol);
        addComposeRow(protoForm, pc, 3, "Đổi mật khẩu (>=8 ký tự):", txtSettingNewAccountPass, btnUpdateAccountPass);

        protoCard.add(protoForm, BorderLayout.CENTER);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        stack.add(gmailCard);
        stack.add(Box.createVerticalStrut(14));
        stack.add(protoCard);

        outer.add(stack, BorderLayout.NORTH);
        return outer;
    }

    private void verifyAndSaveGmailCredentials() {
        final String gmailUser = FileManager.normalizeEmail(txtSettingGmailUser.getText());
        final String rawPass = new String(txtSettingGmailPass.getPassword()).replace(" ", "").trim();

        if (!FileManager.isValidEmailSyntax(gmailUser)) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ Email hợp lệ!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (rawPass.length() < 8) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập Mật khẩu ứng dụng (App Password 16 ký tự) của Google!",
                    "Thiếu App Password", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnVerifyGmail.setEnabled(false);
        btnVerifyGmail.setText("Đang kiểm tra kết nối TLS với smtp.gmail.com:587...");
        lblGmailVerifyStatus.setText("Đang xác thực STARTTLS + AUTH LOGIN với máy chủ Google...");
        lblGmailVerifyStatus.setForeground(COL_PRIMARY);

        new Thread(() -> {
            boolean success = false;
            String detailMsg = "";
            try (Socket rawSocket = new Socket()) {
                rawSocket.connect(new InetSocketAddress("smtp.gmail.com", 587), 8000);
                rawSocket.setSoTimeout(10000);

                BufferedReader r = new BufferedReader(new InputStreamReader(rawSocket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter w = new PrintWriter(new OutputStreamWriter(rawSocket.getOutputStream(), StandardCharsets.UTF_8), true);

                readSmtpBlock(r);
                w.print("EHLO localhost\r\n");
                w.flush();
                readSmtpBlock(r);

                w.print("STARTTLS\r\n");
                w.flush();
                String tlsResp = readSmtpBlock(r);
                if (tlsResp != null && tlsResp.startsWith("220")) {
                    SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    SSLSocket ssl = (SSLSocket) factory.createSocket(rawSocket, "smtp.gmail.com", 587, true);
                    ssl.setSoTimeout(10000);
                    ssl.startHandshake();

                    r = new BufferedReader(new InputStreamReader(ssl.getInputStream(), StandardCharsets.UTF_8));
                    w = new PrintWriter(new OutputStreamWriter(ssl.getOutputStream(), StandardCharsets.UTF_8), true);

                    w.print("EHLO localhost\r\n");
                    w.flush();
                    readSmtpBlock(r);

                    w.print("AUTH LOGIN\r\n");
                    w.flush();
                    readSmtpBlock(r);

                    w.print(Base64.getEncoder().encodeToString(gmailUser.getBytes(StandardCharsets.UTF_8)) + "\r\n");
                    w.flush();
                    readSmtpBlock(r);

                    w.print(Base64.getEncoder().encodeToString(rawPass.getBytes(StandardCharsets.UTF_8)) + "\r\n");
                    w.flush();
                    String authResp = readSmtpBlock(r);

                    w.print("QUIT\r\n");
                    w.flush();

                    if (authResp != null && (authResp.startsWith("235") || authResp.startsWith("250"))) {
                        success = true;
                        detailMsg = "Xác thực thành công với máy chủ Gmail (" + gmailUser + ")!";
                    } else {
                        detailMsg = "Máy chủ Gmail từ chối mật khẩu: " + authResp;
                    }
                } else {
                    detailMsg = "Không khởi tạo được STARTTLS: " + tlsResp;
                }
            } catch (Exception ex) {
                detailMsg = "Không thể kết nối tới smtp.gmail.com:587 (" + ex.getMessage() + ")";
            }

            final boolean finalSuccess = success;
            final String finalMsg = detailMsg;
            SwingUtilities.invokeLater(() -> {
                btnVerifyGmail.setEnabled(true);
                btnVerifyGmail.setText("Xác nhận & Lưu tài khoản Gmail thực tế");

                savedGmailAddress = gmailUser;
                savedGmailAppPass = rawPass;
                gmailVerified = finalSuccess;
                saveUserSettings();
                updateSidebarBadges();
                refreshComposeChannelOptions();

                if (finalSuccess) {
                    lblGmailVerifyStatus.setText("● Đã xác nhận thành công: " + gmailUser + " (Sẵn sàng gửi thư ở tab Soạn thư)");
                    lblGmailVerifyStatus.setForeground(COL_SUCCESS);
                    JOptionPane.showMessageDialog(this,
                            finalMsg + "\nBạn có thể chuyển sang tab 'Soạn thư' để gửi mail thật ngay bây giờ!",
                            "Xác nhận Gmail Thành công", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    lblGmailVerifyStatus.setText("● Đã lưu tạm cấu hình (" + gmailUser + ") nhưng chưa xác thực được: " + finalMsg);
                    lblGmailVerifyStatus.setForeground(COL_DANGER);
                    JOptionPane.showMessageDialog(this,
                            finalMsg + "\n(Hãy kiểm tra lại mã App Password 16 ký tự của Google hoặc kết nối mạng)",
                            "Cảnh báo xác thực Gmail", JOptionPane.WARNING_MESSAGE);
                }
            });
        }).start();
    }

    private String readSmtpBlock(BufferedReader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);
            if (line.length() < 4 || line.charAt(3) != '-') {
                break;
            }
        }
        return sb.toString();
    }

    private void updateAccountPasswordFromSettings() {
        String newPass = new String(txtSettingNewAccountPass.getPassword()).trim();
        if (!FileManager.isValidNewPassword(newPass)) {
            JOptionPane.showMessageDialog(this, "Mật khẩu mới phải có ít nhất 8 ký tự!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            synchronized (FileManager.class) {
                Account fresh = fileManager.loadAccount(currentUser.getUsername());
                if (fresh != null) {
                    currentUser = fresh;
                }
                currentUser.setPassword(newPass);
                fileManager.saveAccount(currentUser);
            }
            txtSettingNewAccountPass.setText("");
            JOptionPane.showMessageDialog(this, "Đã cập nhật mật khẩu tài khoản thành công!");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi cập nhật mật khẩu: " + ex.getMessage());
        }
    }

    private File getSettingsFile() {
        File dir = new File("data");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "settings_" + FileManager.normalizeEmail(currentUser.getUsername()) + ".properties");
    }

    private void loadUserSettings() {
        File f = getSettingsFile();
        if (!f.exists()) {
            return;
        }
        Properties props = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            props.load(reader);
            savedGmailAddress = props.getProperty("gmail.address", "");
            savedGmailAppPass = props.getProperty("gmail.app_pass", "");
            gmailVerified = Boolean.parseBoolean(props.getProperty("gmail.verified", "false"));
            activeReceiveProtocol = props.getProperty("protocol.receive", "IMAP");
            tcpServerHost = props.getProperty("tcp.host", "127.0.0.1");
            tcpSmtpPort = Integer.parseInt(props.getProperty("tcp.smtp_port", "2525"));
        } catch (Exception ignored) {
        }
    }

    private void saveUserSettings() {
        Properties props = new Properties();
        props.setProperty("gmail.address", savedGmailAddress);
        props.setProperty("gmail.app_pass", savedGmailAppPass);
        props.setProperty("gmail.verified", String.valueOf(gmailVerified));
        props.setProperty("protocol.receive", activeReceiveProtocol);
        props.setProperty("tcp.host", tcpServerHost);
        props.setProperty("tcp.smtp_port", String.valueOf(tcpSmtpPort));
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(getSettingsFile()), StandardCharsets.UTF_8)) {
            props.store(writer, "Mail Client Settings");
        } catch (Exception ignored) {
        }
    }

    // Attachment
    private File resolveLocalAttachment(String att) {
        File f = SmtpClient.resolveAttachmentFile(att);
        if (f != null && f.exists()) {
            return f;
        }
        String cleanName = new File(att).getName();
        File fallback = new File("attachments", cleanName);
        return fallback.exists() ? fallback : null;
    }

    private void openOrSaveSelectedAttachment() {
        int row = tblMail.getSelectedRow();
        if (row < 0 || row >= displayedMailboxEmails.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thư trong danh sách!");
            return;
        }
        Email email = displayedMailboxEmails.get(row);
        String att = email.getAttachedFile();
        if (att == null || att.trim().isEmpty() || "None".equalsIgnoreCase(att.trim())) {
            JOptionPane.showMessageDialog(this, "Thư này không có tệp đính kèm!");
            return;
        }

        File targetFile = resolveLocalAttachment(att);
        if (targetFile == null || !targetFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy tệp đính kèm trên ổ cứng ('" + att + "')!",
                    "Không tìm thấy tệp", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object[] options = {"Mở tệp trực tiếp", "Lưu về máy (Save As...)", "Đóng"};
        int choice = JOptionPane.showOptionDialog(this,
                "Tệp đính kèm: " + targetFile.getName() + " (" + targetFile.length() + " bytes)",
                "Quản lý Tệp đính kèm",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);

        try {
            if (choice == 0) {
                java.awt.Desktop.getDesktop().open(targetFile);
            } else if (choice == 1) {
                JFileChooser saver = new JFileChooser();
                saver.setSelectedFile(new File(targetFile.getName()));
                if (saver.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File dest = saver.getSelectedFile();
                    Files.copy(targetFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    JOptionPane.showMessageDialog(this, "Đã lưu tệp tại:\n" + dest.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi thao tác tệp: " + ex.getMessage());
        }
    }

    private void replyToSelectedEmail() {
        int row = tblMail.getSelectedRow();
        if (row < 0 || row >= displayedMailboxEmails.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thư để trả lời!");
            return;
        }
        Email e = displayedMailboxEmails.get(row);
        txtComposeTo.setText(viewingInbox ? e.getSender() : e.getReceiver());
        String sub = e.getSubject();
        txtComposeSubject.setText(sub.startsWith("Re:") ? sub : ("Re: " + sub));
        txtComposeBody.setText("\n\n--- Thư gốc từ " + e.getSender() + " lúc " + e.getTime() + " ---\n" + e.getBody());
        switchNavigation(2);
        txtComposeBody.requestFocusInWindow();
        txtComposeBody.setCaretPosition(0);
    }

    private void deleteSelectedEmail() {
        int row = tblMail.getSelectedRow();
        if (row < 0 || row >= displayedMailboxEmails.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thư để xóa!");
            return;
        }
        Email target = displayedMailboxEmails.get(row);
        try {
            synchronized (FileManager.class) {
                Account fresh = fileManager.loadAccount(currentUser.getUsername());
                if (fresh != null) {
                    List<Email> box = viewingInbox ? fresh.getInbox() : fresh.getSent();
                    box.removeIf(item -> item.getTime().equals(target.getTime())
                            && item.getSender().equals(target.getSender())
                            && item.getSubject().equals(target.getSubject()));
                    fileManager.saveAccount(fresh);
                    currentUser = fresh;
                }
            }
            syncMailboxData(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi xóa thư: " + ex.getMessage());
        }
    }

    // Sync
    private void startAutoSyncTimer() {
        File userFile = fileManager.getAccountFile(currentUser.getUsername());
        if (userFile.exists()) {
            lastKnownFileModified = userFile.lastModified();
        }
        autoSyncTimer = new Timer(2000, e -> {
            File f = fileManager.getAccountFile(currentUser.getUsername());
            if (f.exists() && f.lastModified() != lastKnownFileModified) {
                lastKnownFileModified = f.lastModified();
                syncMailboxData(false);
            }
        });
        autoSyncTimer.start();
    }

    private void syncMailboxData(boolean fetchFromProtocol) {
        try {
            if (fetchFromProtocol) {
                currentUser = pop3Client.fetchEmails(currentUser);
            } else {
                Account latest = fileManager.loadAccount(currentUser.getUsername());
                if (latest != null) {
                    currentUser = latest;
                }
            }
        } catch (Exception ignored) {
        }

        int inboxCount = currentUser.getInbox().size();
        int sentCount = currentUser.getSent().size();

        if (navBtnInbox != null) {
            navBtnInbox.setText("Hộp thư đến (" + inboxCount + ")");
        }
        if (navBtnSent != null) {
            navBtnSent.setText("Thư đã gửi (" + sentCount + ")");
        }
        if (lblMailboxTitle != null) {
            lblMailboxTitle.setText(viewingInbox
                    ? ("Hộp thư đến (" + inboxCount + ")")
                    : ("Thư đã gửi (" + sentCount + ")"));
        }
        if (btnReplyMail != null) {
            btnReplyMail.setVisible(viewingInbox);
        }

        populateMailboxTable();
        rebuildChatThreadsFromEmails();
    }

    private void handleLogout() {
        if (autoSyncTimer != null) {
            autoSyncTimer.stop();
        }
        dispose();
        launch(fileManager);
    }

    // Auth
    public static class AuthDialog extends JDialog {
        private final FileManager fm;
        private Account authenticatedAccount;
        private CardLayout authCardLayout;
        private JPanel formContainer;
        private JButton btnSwitchLogin;
        private JButton btnSwitchRegister;

        public AuthDialog(FileManager fm) {
            super((JFrame) null, "Đăng nhập Mail Workspace", true);
            this.fm = fm;
            setSize(480, 440);
            setResizable(false);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            buildUi();
        }

        private void buildUi() {
            JPanel root = new JPanel(new BorderLayout(0, 0));
            root.setBackground(COL_BG_MAIN);
            root.setBorder(new EmptyBorder(24, 28, 24, 28));

            JPanel card = new JPanel(new BorderLayout(0, 0));
            card.setBackground(COL_SURFACE);
            card.setBorder(new LineBorder(COL_BORDER, 1, true));

            JPanel topHeader = new JPanel(new BorderLayout(0, 4));
            topHeader.setBackground(COL_SURFACE);
            topHeader.setBorder(new EmptyBorder(20, 24, 12, 24));

            JLabel lblTitle = new JLabel("Mail Workspace", SwingConstants.CENTER);
            lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
            lblTitle.setForeground(COL_PRIMARY);

            JLabel lblSub = new JLabel("Hệ thống Email TCP/IP • SMTP • POP3 • IMAP", SwingConstants.CENTER);
            lblSub.setFont(FONT_SMALL);
            lblSub.setForeground(COL_TEXT_MUTED);

            topHeader.add(lblTitle, BorderLayout.NORTH);
            topHeader.add(lblSub, BorderLayout.SOUTH);
            card.add(topHeader, BorderLayout.NORTH);

            JPanel centerWrap = new JPanel(new BorderLayout(0, 0));
            centerWrap.setBackground(COL_SURFACE);

            JPanel tabStrip = new JPanel(new GridLayout(1, 2, 0, 0));
            tabStrip.setBackground(COL_SECONDARY_BG);
            tabStrip.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, COL_BORDER));

            btnSwitchLogin = new JButton("Đăng nhập");
            btnSwitchRegister = new JButton("Tạo tài khoản");
            setupTabButton(btnSwitchLogin);
            setupTabButton(btnSwitchRegister);

            btnSwitchLogin.addActionListener(e -> setAuthMode(true));
            btnSwitchRegister.addActionListener(e -> setAuthMode(false));

            tabStrip.add(btnSwitchLogin);
            tabStrip.add(btnSwitchRegister);
            centerWrap.add(tabStrip, BorderLayout.NORTH);

            authCardLayout = new CardLayout();
            formContainer = new JPanel(authCardLayout);
            formContainer.setBackground(COL_SURFACE);
            formContainer.add(buildLoginForm(), "LOGIN");
            formContainer.add(buildRegisterForm(), "REGISTER");

            centerWrap.add(formContainer, BorderLayout.CENTER);
            card.add(centerWrap, BorderLayout.CENTER);
            root.add(card, BorderLayout.CENTER);

            setAuthMode(true);
            setContentPane(root);
        }

        private void setupTabButton(JButton btn) {
            btn.setFont(FONT_UI_BOLD);
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(true);
            btn.setOpaque(true);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(180, 38));
        }

        private void setAuthMode(boolean loginActive) {
            authCardLayout.show(formContainer, loginActive ? "LOGIN" : "REGISTER");
            if (loginActive) {
                btnSwitchLogin.setBackground(COL_SURFACE);
                btnSwitchLogin.setForeground(COL_PRIMARY);
                btnSwitchLogin.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, COL_PRIMARY));
                btnSwitchRegister.setBackground(COL_SECONDARY_BG);
                btnSwitchRegister.setForeground(COL_TEXT_MUTED);
                btnSwitchRegister.setBorder(null);
            } else {
                btnSwitchRegister.setBackground(COL_SURFACE);
                btnSwitchRegister.setForeground(COL_PRIMARY);
                btnSwitchRegister.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, COL_PRIMARY));
                btnSwitchLogin.setBackground(COL_SECONDARY_BG);
                btnSwitchLogin.setForeground(COL_TEXT_MUTED);
                btnSwitchLogin.setBorder(null);
            }
        }

        private JPanel buildLoginForm() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(COL_SURFACE);
            p.setBorder(new EmptyBorder(18, 28, 20, 28));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 4, 6, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;

            JTextField txtEmail = new JTextField("");
            JPasswordField txtPass = new JPasswordField("");
            styleTextField(txtEmail);
            styleTextField(txtPass);

            JLabel l1 = new JLabel("Email (vd: alice@gmail.com):");
            l1.setFont(FONT_UI_BOLD);
            gbc.gridx = 0;
            gbc.gridy = 0;
            p.add(l1, gbc);

            gbc.gridy = 1;
            p.add(txtEmail, gbc);

            JLabel l2 = new JLabel("Mật khẩu:");
            l2.setFont(FONT_UI_BOLD);
            gbc.gridy = 2;
            p.add(l2, gbc);

            gbc.gridy = 3;
            p.add(txtPass, gbc);

            JButton btnLogin = createFlatButton("Đăng nhập", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true, 10);
            btnLogin.setPreferredSize(new Dimension(280, 40));
            gbc.gridy = 4;
            gbc.insets = new Insets(14, 4, 4, 4);
            p.add(btnLogin, gbc);

            btnLogin.addActionListener(e -> {
                String email = FileManager.normalizeEmail(txtEmail.getText());
                String pass = new String(txtPass.getPassword()).trim();
                if (!FileManager.isValidEmailSyntax(email)) {
                    JOptionPane.showMessageDialog(this, "Vui lòng nhập đúng định dạng địa chỉ Email!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                try {
                    synchronized (FileManager.class) {
                        Account acc = fm.loadAccount(email);
                        if (acc != null && acc.getPassword().equals(pass)) {
                            acc.setLastLogin(FileManager.getCurrentTime());
                            fm.saveAccount(acc);
                            authenticatedAccount = acc;
                            dispose();
                        } else {
                            JOptionPane.showMessageDialog(this, "Sai địa chỉ Email hoặc Mật khẩu!", "Lỗi xác thực", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi hệ thống: " + ex.getMessage());
                }
            });

            return p;
        }

        private JPanel buildRegisterForm() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(COL_SURFACE);
            p.setBorder(new EmptyBorder(18, 28, 20, 28));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 4, 6, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;

            JTextField txtEmail = new JTextField("");
            JPasswordField txtPass = new JPasswordField("");
            styleTextField(txtEmail);
            styleTextField(txtPass);

            JLabel l1 = new JLabel("Địa chỉ Email mới:");
            l1.setFont(FONT_UI_BOLD);
            gbc.gridx = 0;
            gbc.gridy = 0;
            p.add(l1, gbc);

            gbc.gridy = 1;
            p.add(txtEmail, gbc);

            JLabel l2 = new JLabel("Mật khẩu (tối thiểu 8 ký tự):");
            l2.setFont(FONT_UI_BOLD);
            gbc.gridy = 2;
            p.add(l2, gbc);

            gbc.gridy = 3;
            p.add(txtPass, gbc);

            JButton btnReg = createFlatButton("Tạo tài khoản mới", COL_SUCCESS, Color.WHITE, COL_SUCCESS_HOVER, true, 10);
            btnReg.setPreferredSize(new Dimension(280, 40));
            gbc.gridy = 4;
            gbc.insets = new Insets(14, 4, 4, 4);
            p.add(btnReg, gbc);

            btnReg.addActionListener(e -> {
                String email = FileManager.normalizeEmail(txtEmail.getText());
                String pass = new String(txtPass.getPassword()).trim();
                if (!FileManager.isValidEmailSyntax(email)) {
                    JOptionPane.showMessageDialog(this, "Địa chỉ Email không đúng cú pháp!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (!FileManager.isValidNewPassword(pass)) {
                    JOptionPane.showMessageDialog(this, "Mật khẩu phải có ít nhất 8 ký tự!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                try {
                    if (fm.createAccount(email, pass)) {
                        authenticatedAccount = fm.loadAccount(email);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(this, "Tài khoản Email này đã tồn tại!", "Trùng tài khoản", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
                }
            });

            return p;
        }

        public Account getAuthenticatedAccount() {
            return authenticatedAccount;
        }
    }

    public static void launch(FileManager fileManager) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            AuthDialog auth = new AuthDialog(fileManager);
            auth.setVisible(true);
            if (auth.getAuthenticatedAccount() != null) {
                MailClientGUI gui = new MailClientGUI(fileManager, auth.getAuthenticatedAccount());
                gui.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}