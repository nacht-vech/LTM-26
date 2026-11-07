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
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import model.Account;
import model.Email;
import protocol.Pop3Client;
import protocol.SmtpClient;
import storage.FileManager;

/**
 * FILE GIAO DIỆN ĐỒ HỌA RIÊNG BIỆT (JAVA SWING UI) - MailClientGUI.java
 * Đã điều chỉnh toàn diện:
 * 1. Khung cấu hình SMTP / Email Thật được thiết kế thành 3 hàng dọc rõ ràng trong GridBagLayout:
 *    - Hàng 1: SMTP Host + Port
 *    - Hàng 2: SMTP Email (Gmail xác thực)
 *    - Hàng 3: App Pass (Mật khẩu ứng dụng 16 ký tự) + nút tích "Hiện mã" để dễ dàng nhập/kiểm tra,
 *      tuyệt đối không bị tràn lề hay che khuất ở bất kỳ kích thước cửa sổ nào.
 * 2. Gửi Email qua luồng ngầm (Background Thread) giúp giao diện không bị đơ khi đính kèm ảnh nặng (>1MB).
 */
public class MailClientGUI extends JFrame {

    // =========================================================================
    // 1. CẤU HÌNH BẢNG MÀU & FONT CHỮ TẬP TRUNG
    // =========================================================================
    public static final int WINDOW_WIDTH = 1360;
    public static final int WINDOW_HEIGHT = 830;

    public static final Color COL_BG_MAIN = new Color(241, 245, 249);
    public static final Color COL_SURFACE = Color.WHITE;
    public static final Color COL_BORDER = new Color(203, 213, 225);
    public static final Color COL_HEADER_BG = new Color(15, 23, 42);
    public static final Color COL_TAB_STRIP = new Color(241, 245, 249);
    public static final Color COL_SIDEBAR_BG = new Color(248, 250, 252);
    public static final Color COL_FOLDER_ACTIVE_BG = new Color(219, 234, 254);
    public static final Color COL_FOLDER_ACTIVE_FG = new Color(3, 105, 161);
    public static final Color COL_PRIMARY = new Color(2, 132, 199);
    public static final Color COL_PRIMARY_HOVER = new Color(3, 105, 161);
    public static final Color COL_SUCCESS = new Color(22, 163, 74);
    public static final Color COL_SUCCESS_HOVER = new Color(21, 128, 61);
    public static final Color COL_SECONDARY_BG = new Color(241, 245, 249);
    public static final Color COL_SECONDARY_FG = new Color(30, 41, 59);
    public static final Color COL_DANGER = new Color(220, 38, 38);
    public static final Color COL_CONSOLE_BG = new Color(15, 23, 42);
    public static final Color COL_CONSOLE_TEXT = new Color(56, 189, 248);
    public static final Color COL_TEXT_MAIN = new Color(15, 23, 42);
    public static final Color COL_TEXT_MUTED = new Color(100, 116, 139);

    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_UI = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_UI_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 13);

    // =========================================================================
    // 2. CÁC ĐỐI TƯỢNG XỬ LÝ NGHIỆP VỤ (MODEL / PROTOCOL / STORAGE)
    // =========================================================================
    private final FileManager fileManager;
    private Account currentUser;
    private final SmtpClient smtpClient;
    private final Pop3Client pop3Client;
    private Timer autoSyncTimer;
    private long lastKnownFileModified = 0L;

    // =========================================================================
    // 3. CÁC COMPONENT GIAO DIỆN SWING (3 TAB CHÍNH, TAB 1 LÀ GMAIL WORKSPACE)
    // =========================================================================
    private JLabel lblTopAccountBanner;
    private CardLayout mainCardLayout;
    private JPanel mainCardContainer;
    private final List<JButton> navButtons = new ArrayList<>();
    private static final String[] CARD_KEYS = {"GMAIL_WORKSPACE", "URL_INET", "ACCOUNT"};

    private JTextArea txtProtocolConsole;

    // --- Các thành phần trong Tab 1: Giao diện Gộp kiểu Google Mail (Inbox + Sent + Compose) ---
    private boolean viewingInboxFolder = true;
    private JButton btnFolderInbox;
    private JButton btnFolderSent;
    private JLabel lblMailboxFolderHeader;
    private JButton btnReplyCurrentMail;
    private DefaultTableModel modelMailList;
    private JTable tblMailList;
    private JTextArea txtMailDetail;

    // Khung Soạn thư kiểu Gmail nằm ngay trong Tab 1
    private JSplitPane mailAndComposeSplit;
    private JPanel composeDrawerPanel;
    private JComboBox<String> cbSendMode;
    private JPanel pnlExternalSmtpConfig;
    private JTextField txtSmtpHost;
    private JTextField txtSmtpPort;
    private JTextField txtSmtpAuthUser;
    private JPasswordField txtSmtpAuthPass;
    private JLabel lblSenderEmailDisplay;
    private JTextField txtReceiverEmail;
    private JTextField txtSubject;
    private JTextArea txtBody;
    private JTextField txtAttachment;
    private JButton btnSendSmtp;

    // --- Tab 2: Phân tích URL (diachi.java) & InetAddress (inet.java) ---
    private JTextField txtNetInput;
    private DefaultTableModel modelUrlProps;
    private DefaultTableModel modelInetProps;
    private JTextArea txtNetConsole;

    // --- Tab 3: Thông tin Tài khoản & Xem trực tiếp File .txt ---
    private JLabel lblAccUser;
    private JLabel lblAccPass;
    private JLabel lblAccCreated;
    private JLabel lblAccLastLogin;
    private JLabel lblAccFilePath;
    private JLabel lblAccInboxCount;
    private JLabel lblAccSentCount;
    private JTextArea txtRawAccountFile;

    public MailClientGUI(FileManager fileManager, Account loggedInUser) {
        this.fileManager = fileManager;
        this.currentUser = loggedInUser;
        this.smtpClient = new SmtpClient();
        this.pop3Client = new Pop3Client();

        setTitle("Hệ Thống Mail Client (SMTP & POP3) - Tài khoản: " + currentUser.getUsername()
                + " | File riêng: data/" + currentUser.getUsername() + ".txt");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setMinimumSize(new Dimension(1100, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initMainLayout();
        refreshAllMailAndFileViews();
        runPop3FetchOnGui();
        startAutoSyncTimer();
    }

    private void startAutoSyncTimer() {
        File userFile = fileManager.getAccountFile(currentUser.getUsername());
        if (userFile.exists()) {
            lastKnownFileModified = userFile.lastModified();
        }
        autoSyncTimer = new Timer(2000, e -> {
            File f = fileManager.getAccountFile(currentUser.getUsername());
            if (f.exists() && f.lastModified() != lastKnownFileModified) {
                lastKnownFileModified = f.lastModified();
                int prevInboxCount = currentUser.getInbox().size();
                refreshAllMailAndFileViews();
                if (currentUser.getInbox().size() > prevInboxCount) {
                    currentUser = pop3Client.fetchEmails(currentUser);
                    appendProtocolLog(pop3Client.getLastProtocolLog());
                }
            }
        });
        autoSyncTimer.start();
    }

    // =========================================================================
    // CÁC HÀM DỰNG COMPONENT ĐỒNG BỘ (KHÔNG BỊ LỖI MÀU TRÊN WINDOWS)
    // =========================================================================
    public static JButton createFlatButton(String text, Color bgColor, Color fgColor, Color hoverColor, boolean bold) {
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
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                if (getBackground().equals(COL_SECONDARY_BG) || getBackground().equals(Color.WHITE)) {
                    g2.setColor(COL_BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
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
        btn.setMargin(new Insets(6, 12, 6, 12));
        return btn;
    }

    public static void styleTextField(JTextField tf) {
        tf.setFont(FONT_UI);
        tf.setForeground(COL_TEXT_MAIN);
        tf.setBackground(Color.WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COL_BORDER, 1, true),
                new EmptyBorder(5, 8, 5, 8)
        ));
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_UI);
        table.setRowHeight(28);
        table.setGridColor(new Color(226, 232, 240));
        table.setSelectionBackground(new Color(224, 242, 254));
        table.setSelectionForeground(COL_TEXT_MAIN);
        table.setShowVerticalLines(true);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_UI_BOLD);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(COL_TEXT_MAIN);
        header.setPreferredSize(new Dimension(header.getWidth(), 32));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
    }

    public static JPanel createTitledCard(String title, Component centerComp, Component bottomComp) {
        JPanel card = new JPanel(new BorderLayout(0, 0));
        card.setBackground(COL_SURFACE);
        card.setBorder(new LineBorder(COL_BORDER, 1, true));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(248, 250, 252));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER),
                new EmptyBorder(8, 12, 8, 12)
        ));
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(FONT_SECTION);
        lblTitle.setForeground(COL_TEXT_MAIN);
        header.add(lblTitle, BorderLayout.WEST);

        card.add(header, BorderLayout.NORTH);
        card.add(centerComp, BorderLayout.CENTER);

        if (bottomComp != null) {
            JPanel footer = new JPanel(new BorderLayout());
            footer.setBackground(new Color(248, 250, 252));
            footer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, COL_BORDER),
                    new EmptyBorder(6, 10, 6, 10)
            ));
            footer.add(bottomComp, BorderLayout.CENTER);
            card.add(footer, BorderLayout.SOUTH);
        }
        return card;
    }

    private void initMainLayout() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(8, 10, 10, 10));
        root.setBackground(COL_BG_MAIN);

        root.add(createHeaderPanel(), BorderLayout.NORTH);

        JSplitPane mainHorizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainHorizontalSplit.setResizeWeight(0.74);
        mainHorizontalSplit.setBorder(null);

        JPanel leftWorkspace = new JPanel(new BorderLayout(0, 0));
        leftWorkspace.setBackground(COL_SURFACE);
        leftWorkspace.setBorder(new LineBorder(COL_BORDER, 1, true));

        JPanel navStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        navStrip.setBackground(COL_TAB_STRIP);
        navStrip.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER));

        String[] tabNames = {
                "1. Hộp thư Điện tử (Giao diện Google Mail - POP3 & SMTP)",
                "2. Phân tích URL & InetAddress (diachi & inet)",
                "3. Hồ sơ & File data/" + currentUser.getUsername() + ".txt"
        };

        for (int i = 0; i < tabNames.length; i++) {
            final int idx = i;
            JButton btn = new JButton(tabNames[i]);
            btn.setFont(FONT_UI_BOLD);
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(true);
            btn.setOpaque(true);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> switchActiveTab(idx));
            navButtons.add(btn);
            navStrip.add(btn);
        }

        mainCardLayout = new CardLayout();
        mainCardContainer = new JPanel(mainCardLayout);
        mainCardContainer.setBackground(COL_SURFACE);
        mainCardContainer.setBorder(new EmptyBorder(8, 8, 8, 8));

        mainCardContainer.add(createUnifiedGmailTab(), CARD_KEYS[0]);
        mainCardContainer.add(createUrlInetTab(), CARD_KEYS[1]);
        mainCardContainer.add(createAccountDetailsTab(), CARD_KEYS[2]);

        leftWorkspace.add(navStrip, BorderLayout.NORTH);
        leftWorkspace.add(mainCardContainer, BorderLayout.CENTER);
        switchActiveTab(0);

        mainHorizontalSplit.setLeftComponent(leftWorkspace);
        mainHorizontalSplit.setRightComponent(createProtocolLogPanel());

        root.add(mainHorizontalSplit, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void switchActiveTab(int activeIdx) {
        mainCardLayout.show(mainCardContainer, CARD_KEYS[activeIdx]);
        for (int i = 0; i < navButtons.size(); i++) {
            JButton b = navButtons.get(i);
            if (i == activeIdx) {
                b.setBackground(COL_SURFACE);
                b.setForeground(COL_PRIMARY);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(3, 0, 0, 1, COL_PRIMARY),
                        new EmptyBorder(8, 18, 11, 18)
                ));
            } else {
                b.setBackground(COL_TAB_STRIP);
                b.setForeground(COL_TEXT_MUTED);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 0, 1, COL_BORDER),
                        new EmptyBorder(11, 18, 11, 18)
                ));
            }
        }
    }

    private JPanel createHeaderPanel() {
        JPanel topBanner = new JPanel(new BorderLayout(10, 0));
        topBanner.setBackground(COL_HEADER_BG);
        topBanner.setBorder(new EmptyBorder(8, 14, 8, 14));

        lblTopAccountBanner = new JLabel();
        lblTopAccountBanner.setForeground(new Color(226, 232, 240));
        lblTopAccountBanner.setFont(FONT_SMALL);
        updateTopBannerText();
        topBanner.add(lblTopAccountBanner, BorderLayout.CENTER);

        JPanel topRightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topRightButtons.setOpaque(false);

        JButton btnChangePass = createFlatButton("Đổi mật khẩu", new Color(51, 65, 85), Color.WHITE, new Color(71, 85, 105), false);
        btnChangePass.setFont(FONT_SMALL);
        btnChangePass.addActionListener(e -> showChangePasswordDialog());

        JButton btnSwitchUser = createFlatButton("Đăng xuất / Đổi tài khoản", COL_DANGER, Color.WHITE, new Color(185, 28, 28), true);
        btnSwitchUser.setFont(FONT_SMALL);
        btnSwitchUser.addActionListener(e -> handleLogout());

        topRightButtons.add(btnChangePass);
        topRightButtons.add(btnSwitchUser);
        topBanner.add(topRightButtons, BorderLayout.EAST);

        return topBanner;
    }

    private JPanel createProtocolLogPanel() {
        txtProtocolConsole = new JTextArea();
        txtProtocolConsole.setEditable(false);
        txtProtocolConsole.setBackground(COL_CONSOLE_BG);
        txtProtocolConsole.setForeground(COL_CONSOLE_TEXT);
        txtProtocolConsole.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtProtocolConsole.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(txtProtocolConsole);
        scroll.setBorder(null);

        JPanel protoBottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        protoBottomBar.setOpaque(false);
        JButton btnClearLog = createFlatButton("Xóa màn hình Log", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_TAB_STRIP, false);
        btnClearLog.addActionListener(e -> txtProtocolConsole.setText(""));
        protoBottomBar.add(btnClearLog);

        return createTitledCard("Nhật ký Giao thức SMTP & POP3 (C->S / S->C trực tiếp)", scroll, protoBottomBar);
    }

    private void updateTopBannerText() {
        String localIp = "127.0.0.1";
        try {
            InetAddress local = InetAddress.getLocalHost();
            localIp = local.getHostName() + " (" + local.getHostAddress() + ")";
        } catch (Exception ignored) {
        }
        lblTopAccountBanner.setText(String.format(
                "Tài khoản Email: %s   |   Tạo lúc: %s   |   Đăng nhập: %s   |   File riêng: data/%s.txt   |   Máy: %s",
                currentUser.getUsername(), currentUser.getCreatedAt(),
                currentUser.getLastLogin(), currentUser.getUsername(), localIp
        ));
    }

    // =========================================================================
    // TAB 1 HỢP NHẤT KIỂU GOOGLE MAIL (GỘP HỘP THƯ ĐẾN + ĐÃ GỬI + SOẠN THƯ)
    // =========================================================================
    private JPanel createUnifiedGmailTab() {
        JPanel gmailRoot = new JPanel(new BorderLayout(8, 0));
        gmailRoot.setOpaque(false);

        JPanel leftSidebar = new JPanel();
        leftSidebar.setLayout(new BoxLayout(leftSidebar, BoxLayout.Y_AXIS));
        leftSidebar.setBackground(COL_SIDEBAR_BG);
        leftSidebar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COL_BORDER, 1, true),
                new EmptyBorder(12, 10, 12, 10)
        ));
        leftSidebar.setPreferredSize(new Dimension(205, 100));

        JButton btnComposeNew = createFlatButton("+ Soạn thư mới", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true);
        btnComposeNew.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnComposeNew.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnComposeNew.addActionListener(e -> openComposeDrawerForNewMail());

        leftSidebar.add(btnComposeNew);
        leftSidebar.add(Box.createVerticalStrut(14));

        JLabel lblFolderTitle = new JLabel("DANH MỤC HỘP THƯ");
        lblFolderTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblFolderTitle.setForeground(COL_TEXT_MUTED);
        lblFolderTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftSidebar.add(lblFolderTitle);
        leftSidebar.add(Box.createVerticalStrut(6));

        btnFolderInbox = createSidebarFolderButton("Hộp thư đến (0)", true);
        btnFolderInbox.addActionListener(e -> switchMailboxFolder(true));

        btnFolderSent = createSidebarFolderButton("Thư đã gửi (0)", false);
        btnFolderSent.addActionListener(e -> switchMailboxFolder(false));

        leftSidebar.add(btnFolderInbox);
        leftSidebar.add(Box.createVerticalStrut(4));
        leftSidebar.add(btnFolderSent);
        leftSidebar.add(Box.createVerticalStrut(18));

        JLabel lblToolsTitle = new JLabel("TIỆN ÍCH GIAO THỨC");
        lblToolsTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblToolsTitle.setForeground(COL_TEXT_MUTED);
        lblToolsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftSidebar.add(lblToolsTitle);
        leftSidebar.add(Box.createVerticalStrut(6));

        JButton btnSyncPop3 = createFlatButton("Nhận thư mới (POP3)", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_TAB_STRIP, false);
        btnSyncPop3.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSyncPop3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btnSyncPop3.addActionListener(e -> runPop3FetchOnGui());

        JButton btnSocketPop3 = createFlatButton("Test POP3 Socket", COL_SECONDARY_BG, COL_PRIMARY, COL_TAB_STRIP, false);
        btnSocketPop3.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSocketPop3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btnSocketPop3.addActionListener(e -> showExternalPop3Dialog());

        JButton btnQuickUser = createFlatButton("+ Tạo TK Email mới", COL_SECONDARY_BG, COL_SUCCESS, COL_TAB_STRIP, true);
        btnQuickUser.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnQuickUser.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btnQuickUser.addActionListener(e -> quickCreateRecipientAccount());

        leftSidebar.add(btnSyncPop3);
        leftSidebar.add(Box.createVerticalStrut(6));
        leftSidebar.add(btnSocketPop3);
        leftSidebar.add(Box.createVerticalStrut(6));
        leftSidebar.add(btnQuickUser);
        leftSidebar.add(Box.createVerticalGlue());

        gmailRoot.add(leftSidebar, BorderLayout.WEST);

        mailAndComposeSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mailAndComposeSplit.setResizeWeight(0.50);
        mailAndComposeSplit.setBorder(null);

        mailAndComposeSplit.setLeftComponent(buildMailboxListAndReaderPanel());
        composeDrawerPanel = buildGmailComposePanel();
        mailAndComposeSplit.setRightComponent(composeDrawerPanel);

        gmailRoot.add(mailAndComposeSplit, BorderLayout.CENTER);
        return gmailRoot;
    }

    private JButton createSidebarFolderButton(String text, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_UI_BOLD);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(true);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        styleSidebarFolderButton(btn, active);
        return btn;
    }

    private void styleSidebarFolderButton(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(COL_FOLDER_ACTIVE_BG);
            btn.setForeground(COL_FOLDER_ACTIVE_FG);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, COL_PRIMARY),
                    new EmptyBorder(8, 10, 8, 10)
            ));
        } else {
            btn.setBackground(COL_SIDEBAR_BG);
            btn.setForeground(COL_TEXT_MAIN);
            btn.setBorder(new EmptyBorder(8, 14, 8, 10));
        }
    }

    private JPanel buildMailboxListAndReaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JPanel topToolbar = new JPanel(new BorderLayout(8, 0));
        topToolbar.setBackground(COL_SURFACE);
        topToolbar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COL_BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        lblMailboxFolderHeader = new JLabel("Hộp thư đến (POP3 Inbox)");
        lblMailboxFolderHeader.setFont(FONT_SECTION);
        lblMailboxFolderHeader.setForeground(COL_PRIMARY);
        topToolbar.add(lblMailboxFolderHeader, BorderLayout.WEST);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionBtns.setOpaque(false);

        JButton btnRefreshFolder = createFlatButton("Làm mới", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_TAB_STRIP, false);
        btnRefreshFolder.addActionListener(e -> {
            if (viewingInboxFolder) {
                runPop3FetchOnGui();
            } else {
                currentUser = pop3Client.viewSentEmails(currentUser);
                appendProtocolLog(pop3Client.getLastProtocolLog());
                refreshAllMailAndFileViews();
            }
        });

        btnReplyCurrentMail = createFlatButton("Trả lời", COL_SECONDARY_BG, COL_PRIMARY, COL_TAB_STRIP, true);
        btnReplyCurrentMail.addActionListener(e -> replySelectedEmail());

        JButton btnDeleteMail = createFlatButton("Xóa thư", COL_SECONDARY_BG, COL_DANGER, COL_TAB_STRIP, false);
        btnDeleteMail.addActionListener(e -> deleteSelectedEmailInCurrentFolder());

        JButton btnOpenAttachment = createFlatButton("Mở / Lưu Đính kèm", COL_SECONDARY_BG, COL_SUCCESS, COL_TAB_STRIP, true);
        btnOpenAttachment.addActionListener(e -> openOrSaveSelectedAttachment());

        actionBtns.add(btnRefreshFolder);
        actionBtns.add(btnReplyCurrentMail);
        actionBtns.add(btnOpenAttachment);
        actionBtns.add(btnDeleteMail);
        topToolbar.add(actionBtns, BorderLayout.EAST);

        panel.add(topToolbar, BorderLayout.NORTH);

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setResizeWeight(0.46);
        verticalSplit.setBorder(null);

        modelMailList = new DefaultTableModel(
                new String[]{"STT", "Thời gian", "Người gửi (From)", "Người nhận (To)", "Tiêu đề (Subject)", "Đính kèm"}, 0
        ) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tblMailList = new JTable(modelMailList);
        styleTable(tblMailList);
        tblMailList.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedEmailInReadingPane();
            }
        });
        JScrollPane scrollTable = new JScrollPane(tblMailList);
        scrollTable.setBorder(new LineBorder(COL_BORDER, 1, true));

        txtMailDetail = new JTextArea("Chọn một thư trong danh sách phía trên để đọc nội dung chi tiết...");
        txtMailDetail.setEditable(false);
        txtMailDetail.setFont(FONT_MONO);
        txtMailDetail.setLineWrap(true);
        txtMailDetail.setWrapStyleWord(true);
        txtMailDetail.setMargin(new Insets(10, 12, 10, 12));
        JScrollPane scrollDetail = new JScrollPane(txtMailDetail);
        scrollDetail.setBorder(null);

        verticalSplit.setTopComponent(scrollTable);
        verticalSplit.setBottomComponent(createTitledCard("Nội dung chi tiết Thư đang chọn", scrollDetail, null));

        panel.add(verticalSplit, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Khung Soạn thư kiểu Google Mail ("Thư mới - Gửi SMTP"):
     * Khung vàng cấu hình SMTP (pnlExternalSmtpConfig) được chia thành 3 dòng dọc rõ ràng
     * để các ô Host, Port, SMTP Email, và App Pass (16 ký tự) luôn rộng rãi, dễ bấm và dễ nhập.
     */
    private JPanel buildGmailComposePanel() {
        JPanel formPanel = new JPanel(new BorderLayout(0, 6));
        formPanel.setBackground(COL_SURFACE);

        JPanel fieldsBox = new JPanel(new GridBagLayout());
        fieldsBox.setBackground(COL_SURFACE);
        fieldsBox.setBorder(new EmptyBorder(8, 10, 6, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        cbSendMode = new JComboBox<>(new String[]{
                "Localhost (Lưu trực tiếp data/<email>.txt)",
                "Socket Server (Kết nối SMTP Host/Port 2525)",
                "Gửi Email Thật (Gmail SMTP TLS 587 + Lưu data/)"
        });
        cbSendMode.setFont(FONT_UI);

        // Khung vàng cấu hình SMTP xếp thành 3 hàng dọc rộng rãi, không bao giờ bị khuất ô App Pass
        pnlExternalSmtpConfig = new JPanel(new GridBagLayout());
        pnlExternalSmtpConfig.setBackground(new Color(254, 252, 232));
        pnlExternalSmtpConfig.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(250, 204, 21), 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        txtSmtpHost = new JTextField("127.0.0.1");
        txtSmtpPort = new JTextField("2525");
        txtSmtpAuthUser = new JTextField("");
        txtSmtpAuthPass = new JPasswordField("");
        styleTextField(txtSmtpHost);
        styleTextField(txtSmtpPort);
        styleTextField(txtSmtpAuthUser);
        styleTextField(txtSmtpAuthPass);

        txtSmtpAuthUser.setToolTipText("Nhập địa chỉ Gmail thật dùng để gửi (VD: ninhleduy192@gmail.com)");
        txtSmtpAuthPass.setToolTipText("Nhập Mật khẩu ứng dụng 16 ký tự của Google (App Password)");

        final char defaultEchoChar = txtSmtpAuthPass.getEchoChar();
        JCheckBox chkShowAppPass = new JCheckBox("Hiện mã");
        chkShowAppPass.setOpaque(false);
        chkShowAppPass.setFont(FONT_SMALL);
        chkShowAppPass.addActionListener(e -> {
            txtSmtpAuthPass.setEchoChar(chkShowAppPass.isSelected() ? (char) 0 : defaultEchoChar);
        });

        GridBagConstraints sg = new GridBagConstraints();
        sg.insets = new Insets(3, 4, 3, 4);
        sg.fill = GridBagConstraints.HORIZONTAL;

        // Hàng 1: Host + Port
        JLabel lblHost = new JLabel("SMTP Host:");
        lblHost.setFont(FONT_UI_BOLD);
        sg.gridx = 0; sg.gridy = 0; sg.weightx = 0;
        pnlExternalSmtpConfig.add(lblHost, sg);

        sg.gridx = 1; sg.gridy = 0; sg.weightx = 0.7;
        pnlExternalSmtpConfig.add(txtSmtpHost, sg);

        JLabel lblPort = new JLabel("Port:");
        lblPort.setFont(FONT_UI_BOLD);
        sg.gridx = 2; sg.gridy = 0; sg.weightx = 0;
        pnlExternalSmtpConfig.add(lblPort, sg);

        sg.gridx = 3; sg.gridy = 0; sg.weightx = 0.3;
        pnlExternalSmtpConfig.add(txtSmtpPort, sg);

        // Hàng 2: SMTP Email (Gmail thật dùng xác thực)
        JLabel lblAuthU = new JLabel("SMTP Email:");
        lblAuthU.setFont(FONT_UI_BOLD);
        sg.gridx = 0; sg.gridy = 1; sg.weightx = 0; sg.gridwidth = 1;
        pnlExternalSmtpConfig.add(lblAuthU, sg);

        sg.gridx = 1; sg.gridy = 1; sg.weightx = 1.0; sg.gridwidth = 3;
        pnlExternalSmtpConfig.add(txtSmtpAuthUser, sg);

        // Hàng 3: App Pass (Mật khẩu ứng dụng 16 ký tự) + nút tích "Hiện mã"
        JLabel lblAuthP = new JLabel("App Pass:");
        lblAuthP.setFont(FONT_UI_BOLD);
        lblAuthP.setForeground(COL_DANGER);
        sg.gridx = 0; sg.gridy = 2; sg.weightx = 0; sg.gridwidth = 1;
        pnlExternalSmtpConfig.add(lblAuthP, sg);

        sg.gridx = 1; sg.gridy = 2; sg.weightx = 1.0; sg.gridwidth = 2;
        pnlExternalSmtpConfig.add(txtSmtpAuthPass, sg);

        sg.gridx = 3; sg.gridy = 2; sg.weightx = 0; sg.gridwidth = 1;
        pnlExternalSmtpConfig.add(chkShowAppPass, sg);

        pnlExternalSmtpConfig.setVisible(false);

        cbSendMode.addActionListener(e -> {
            int mode = cbSendMode.getSelectedIndex();
            pnlExternalSmtpConfig.setVisible(mode == 1 || mode == 2);
            if (mode == 1) {
                txtSmtpHost.setText("127.0.0.1");
                txtSmtpPort.setText("2525");
            } else if (mode == 2) {
                txtSmtpHost.setText("smtp.gmail.com");
                txtSmtpPort.setText("587");
                if (txtSmtpAuthUser.getText().trim().isEmpty() && currentUser != null) {
                    txtSmtpAuthUser.setText(currentUser.getUsername());
                }
                SwingUtilities.invokeLater(() -> txtSmtpAuthPass.requestFocusInWindow());
            }
            fieldsBox.revalidate();
            fieldsBox.repaint();
        });

        lblSenderEmailDisplay = new JLabel(currentUser.getUsername());
        lblSenderEmailDisplay.setFont(FONT_UI_BOLD);
        lblSenderEmailDisplay.setForeground(COL_PRIMARY);

        txtReceiverEmail = new JTextField("");
        styleTextField(txtReceiverEmail);
        txtReceiverEmail.setToolTipText("Nhập địa chỉ Email người nhận (ví dụ: bob@hotmail.com, alice@gmail.com...)");

        txtSubject = new JTextField("");
        txtAttachment = new JTextField("");
        styleTextField(txtSubject);
        styleTextField(txtAttachment);

        JButton btnBrowseFile = createFlatButton("Đính kèm...", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_TAB_STRIP, false);
        btnBrowseFile.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Chọn tệp đính kèm để gửi qua SMTP");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                txtAttachment.setText(f.getName() + " (" + f.length() + " bytes - " + f.getAbsolutePath() + ")");
            }
        });

        JLabel lMode = new JLabel("Chế độ SMTP:");
        lMode.setFont(FONT_UI_BOLD);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        fieldsBox.add(lMode, gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0;
        fieldsBox.add(cbSendMode, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3;
        fieldsBox.add(pnlExternalSmtpConfig, gbc);

        JLabel lFrom = new JLabel("Từ (From):");
        lFrom.setFont(FONT_UI_BOLD);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
        fieldsBox.add(lFrom, gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        fieldsBox.add(lblSenderEmailDisplay, gbc);

        JLabel lTo = new JLabel("Đến (Email):");
        lTo.setFont(FONT_UI_BOLD);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0;
        fieldsBox.add(lTo, gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1.0;
        fieldsBox.add(txtReceiverEmail, gbc);

        JLabel lSub = new JLabel("Tiêu đề:");
        lSub.setFont(FONT_UI_BOLD);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1; gbc.weightx = 0;
        fieldsBox.add(lSub, gbc);
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weightx = 1.0;
        fieldsBox.add(txtSubject, gbc);

        JLabel lFile = new JLabel("Đính kèm:");
        lFile.setFont(FONT_UI_BOLD);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1; gbc.weightx = 0;
        fieldsBox.add(lFile, gbc);
        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1.0;
        fieldsBox.add(txtAttachment, gbc);
        gbc.gridx = 2; gbc.gridy = 5; gbc.weightx = 0;
        fieldsBox.add(btnBrowseFile, gbc);

        formPanel.add(fieldsBox, BorderLayout.NORTH);

        txtBody = new JTextArea("");
        txtBody.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtBody.setLineWrap(true);
        txtBody.setWrapStyleWord(true);
        txtBody.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane scrollBody = new JScrollPane(txtBody);
        scrollBody.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COL_BORDER));
        formPanel.add(scrollBody, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomBar.setOpaque(false);

        JButton btnClearCompose = createFlatButton("Xóa trắng", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_TAB_STRIP, false);
        btnClearCompose.addActionListener(e -> {
            txtReceiverEmail.setText("");
            txtSubject.setText("");
            txtBody.setText("");
            txtAttachment.setText("");
            txtReceiverEmail.requestFocusInWindow();
        });

        btnSendSmtp = createFlatButton("GỬI THƯ (SMTP)", COL_SUCCESS, Color.WHITE, COL_SUCCESS_HOVER, true);
        btnSendSmtp.addActionListener(e -> handleSendEmailOnGui());

        bottomBar.add(btnClearCompose);
        bottomBar.add(btnSendSmtp);

        return createTitledCard("Thư mới - Soạn & Gửi Email / File (SMTP)", formPanel, bottomBar);
    }

    private void switchMailboxFolder(boolean inboxSelected) {
        this.viewingInboxFolder = inboxSelected;
        styleSidebarFolderButton(btnFolderInbox, inboxSelected);
        styleSidebarFolderButton(btnFolderSent, !inboxSelected);
        btnReplyCurrentMail.setVisible(inboxSelected);

        refreshAllMailAndFileViews();
    }

    private void openComposeDrawerForNewMail() {
        switchActiveTab(0);
        if (!composeDrawerPanel.isVisible()) {
            composeDrawerPanel.setVisible(true);
            mailAndComposeSplit.setDividerLocation(0.50);
        }
        txtReceiverEmail.requestFocusInWindow();
    }

    // =========================================================================
    // TAB 2: PHÂN TÍCH URL (diachi.java) & INETADDRESS (inet.java)
    // =========================================================================
    private JPanel createUrlInetTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);
        txtNetInput = new JTextField("");
        styleTextField(txtNetInput);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnGroup.setOpaque(false);

        JButton btnAnalyze = createFlatButton("Phân tích URL & InetAddress", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true);
        btnAnalyze.addActionListener(e -> runUrlAndInetAnalysis(txtNetInput.getText()));

        JButton btnSampleVku = createFlatButton("Mẫu vku.udn.vn", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_TAB_STRIP, false);
        btnSampleVku.addActionListener(e -> {
            txtNetInput.setText("http://vku.udn.vn");
            runUrlAndInetAnalysis("http://vku.udn.vn");
        });

        JButton btnSampleTuoiTre = createFlatButton("Mẫu www.tuoitre.vn", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_TAB_STRIP, false);
        btnSampleTuoiTre.addActionListener(e -> {
            txtNetInput.setText("https://www.tuoitre.vn");
            runUrlAndInetAnalysis("https://www.tuoitre.vn");
        });

        btnGroup.add(btnAnalyze);
        btnGroup.add(btnSampleVku);
        btnGroup.add(btnSampleTuoiTre);

        JLabel lblNet = new JLabel("Nhập URL hoặc Tên miền: ");
        lblNet.setFont(FONT_UI_BOLD);
        topBar.add(lblNet, BorderLayout.WEST);
        topBar.add(txtNetInput, BorderLayout.CENTER);
        topBar.add(btnGroup, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.52);
        split.setBorder(null);

        JPanel leftTables = new JPanel(new GridLayout(2, 1, 0, 8));
        leftTables.setOpaque(false);

        modelUrlProps = new DefaultTableModel(new String[]{"Thuộc tính java.net.URL", "Giá trị trả về"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable tblUrl = new JTable(modelUrlProps);
        styleTable(tblUrl);
        JScrollPane scrollUrl = new JScrollPane(tblUrl);
        scrollUrl.setBorder(null);

        modelInetProps = new DefaultTableModel(new String[]{"Hạng mục java.net.InetAddress", "Host Name / IP Address"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable tblInet = new JTable(modelInetProps);
        styleTable(tblInet);
        JScrollPane scrollInet = new JScrollPane(tblInet);
        scrollInet.setBorder(null);

        leftTables.add(createTitledCard("1. Bảng phân tích java.net.URL (Bài mẫu diachi.java)", scrollUrl, null));
        leftTables.add(createTitledCard("2. Bảng phân giải java.net.InetAddress (Bài mẫu inet.java)", scrollInet, null));

        txtNetConsole = new JTextArea();
        txtNetConsole.setEditable(false);
        txtNetConsole.setFont(FONT_MONO);
        txtNetConsole.setMargin(new Insets(10, 12, 10, 12));
        JScrollPane scrollReport = new JScrollPane(txtNetConsole);
        scrollReport.setBorder(null);

        split.setLeftComponent(leftTables);
        split.setRightComponent(createTitledCard("Kết quả chi tiết chuẩn theo diachi.java & inet.java", scrollReport, null));
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    // =========================================================================
    // TAB 3: THÔNG TIN TÀI KHOẢN CÁ NHÂN & XEM TRỰC TIẾP FILE data/<email>.txt
    // =========================================================================
    private JPanel createAccountDetailsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel infoGrid = new JPanel(new GridLayout(4, 2, 16, 10));
        infoGrid.setBackground(COL_SURFACE);
        infoGrid.setBorder(new EmptyBorder(12, 16, 12, 16));

        lblAccUser = new JLabel();
        lblAccPass = new JLabel();
        lblAccCreated = new JLabel();
        lblAccLastLogin = new JLabel();
        lblAccFilePath = new JLabel();
        lblAccInboxCount = new JLabel();
        lblAccSentCount = new JLabel();

        lblAccUser.setFont(FONT_UI_BOLD);
        lblAccPass.setFont(FONT_UI_BOLD);
        lblAccCreated.setFont(FONT_UI_BOLD);
        lblAccLastLogin.setFont(FONT_UI_BOLD);
        lblAccFilePath.setFont(FONT_UI_BOLD);
        lblAccFilePath.setForeground(COL_PRIMARY);
        lblAccInboxCount.setFont(FONT_UI_BOLD);
        lblAccSentCount.setFont(FONT_UI_BOLD);

        infoGrid.add(lblAccUser);
        infoGrid.add(lblAccPass);
        infoGrid.add(lblAccCreated);
        infoGrid.add(lblAccLastLogin);
        infoGrid.add(lblAccFilePath);
        infoGrid.add(lblAccInboxCount);
        infoGrid.add(lblAccSentCount);

        JButton btnRefreshFile = createFlatButton("Đồng bộ & Đọc lại từ File .txt", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true);
        btnRefreshFile.addActionListener(e -> refreshAllMailAndFileViews());
        infoGrid.add(btnRefreshFile);

        panel.add(createTitledCard("Thông tin Chi tiết Tài khoản Cá nhân", infoGrid, null), BorderLayout.NORTH);

        txtRawAccountFile = new JTextArea();
        txtRawAccountFile.setEditable(false);
        txtRawAccountFile.setFont(FONT_MONO);
        txtRawAccountFile.setMargin(new Insets(10, 12, 10, 12));
        JScrollPane scrollRaw = new JScrollPane(txtRawAccountFile);
        scrollRaw.setBorder(null);

        panel.add(createTitledCard(
                "Nội dung thực tế trong File riêng của tài khoản (data/" + currentUser.getUsername() + ".txt - Lưu chung [ACCOUNT], [INBOX], [SENT])",
                scrollRaw, null
        ), BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // 4. CÁC HÀM XỬ LÝ SỰ KIỆN NÚT BẤM TRÊN GUI
    // =========================================================================
    private void runPop3FetchOnGui() {
        currentUser = pop3Client.fetchEmails(currentUser);
        appendProtocolLog(pop3Client.getLastProtocolLog());
        refreshAllMailAndFileViews();
        if (viewingInboxFolder && !currentUser.getInbox().isEmpty()) {
            tblMailList.setRowSelectionInterval(0, 0);
            displaySelectedEmailInReadingPane();
        }
    }

    private void handleSendEmailOnGui() {
        final String toEmail = FileManager.normalizeEmail(txtReceiverEmail.getText());
        String rawSub = txtSubject.getText().trim();
        final String subject = rawSub.isEmpty() ? "(Không có tiêu đề)" : rawSub;
        final String body = txtBody.getText().trim();
        final String attachment = txtAttachment.getText().trim();

        if (toEmail.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập địa chỉ Email người nhận (ví dụ: bob@hotmail.com, alice@gmail.com)!",
                    "Thiếu địa chỉ Email nhận", JOptionPane.WARNING_MESSAGE);
            txtReceiverEmail.requestFocusInWindow();
            return;
        }

        if (!FileManager.isValidEmailSyntax(toEmail)) {
            JOptionPane.showMessageDialog(this,
                    "Địa chỉ người nhận '" + toEmail + "' không đúng cú pháp Email!\n"
                            + "Vui lòng nhập đúng định dạng Email (ví dụ: user@gmail.com, user@hotmail.com...).",
                    "Sai cú pháp Email", JOptionPane.ERROR_MESSAGE);
            txtReceiverEmail.requestFocusInWindow();
            txtReceiverEmail.selectAll();
            return;
        }

        final int modeIndex = cbSendMode.getSelectedIndex();
        final String host = txtSmtpHost.getText().trim();
        int parsedPort = 2525;
        try {
            parsedPort = Integer.parseInt(txtSmtpPort.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        final int port = parsedPort;

        String rawAuthU = txtSmtpAuthUser.getText().trim();
        if (rawAuthU.isEmpty() && modeIndex != 0 && port != 2525) {
            rawAuthU = currentUser.getUsername();
            txtSmtpAuthUser.setText(rawAuthU);
        }
        final String authU = rawAuthU;
        final String authP = new String(txtSmtpAuthPass.getPassword()).trim();

        if (modeIndex != 0 && port != 2525 && authP.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Bạn đang chọn gửi tới Email Thật qua " + host + ":" + port + " nhưng chưa nhập 'App Pass'!\n"
                            + "Hãy nhập Mật khẩu ứng dụng 16 ký tự của Google vào ô 'App Pass' (màu đỏ) trong khung vàng.",
                    "Thiếu App Pass (16 ký tự)", JOptionPane.WARNING_MESSAGE);
            txtSmtpAuthPass.requestFocusInWindow();
            return;
        }

        btnSendSmtp.setEnabled(false);
        btnSendSmtp.setText("Đang gửi SMTP...");

        new Thread(() -> {
            final boolean ok;
            if (modeIndex == 0) {
                ok = smtpClient.sendEmail(currentUser, toEmail, subject, body, attachment);
            } else {
                ok = smtpClient.sendEmailViaExternalServer(
                        currentUser, host, port, authU, authP, toEmail, subject, body, attachment
                );
            }

            SwingUtilities.invokeLater(() -> {
                btnSendSmtp.setEnabled(true);
                btnSendSmtp.setText("GỬI THƯ (SMTP)");

                appendProtocolLog(smtpClient.getLastProtocolLog());
                refreshAllMailAndFileViews();

                File f = fileManager.getAccountFile(currentUser.getUsername());
                if (f.exists()) {
                    lastKnownFileModified = f.lastModified();
                }

                if (ok) {
                    JOptionPane.showMessageDialog(this,
                            smtpClient.getLastStatusMessage()
                                    + "\nĐã đồng bộ vào mục [SENT] của 'data/" + currentUser.getUsername()
                                    + ".txt' và mục [INBOX] của 'data/" + toEmail + ".txt'!",
                            "SMTP Thành công", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            smtpClient.getLastStatusMessage(),
                            "SMTP Thất bại", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void populateCurrentFolderTable() {
        int prevSelected = tblMailList.getSelectedRow();
        modelMailList.setRowCount(0);
        List<Email> list = viewingInboxFolder ? currentUser.getInbox() : currentUser.getSent();
        for (int i = 0; i < list.size(); i++) {
            Email e = list.get(i);
            modelMailList.addRow(new Object[]{
                    "#" + (i + 1),
                    e.getTime(),
                    e.getSender(),
                    e.getReceiver(),
                    e.getSubject(),
                    e.getAttachedFile()
            });
        }
        if (list.isEmpty()) {
            txtMailDetail.setText(viewingInboxFolder
                    ? "Hộp thư đến (Inbox) hiện đang trống."
                    : "Hộp thư đã gửi (Sent) hiện đang trống.");
        } else {
            int rowToSelect = (prevSelected >= 0 && prevSelected < list.size()) ? prevSelected : 0;
            tblMailList.setRowSelectionInterval(rowToSelect, rowToSelect);
            displaySelectedEmailInReadingPane();
        }
    }

    private void displaySelectedEmailInReadingPane() {
        int row = tblMailList.getSelectedRow();
        List<Email> list = viewingInboxFolder ? currentUser.getInbox() : currentUser.getSent();
        if (row < 0 || row >= list.size()) return;
        Email e = list.get(row);
        String boxTitle = viewingInboxFolder
                ? "HỘP THƯ ĐẾN (POP3 RETR #" + (row + 1) + ")"
                : "THƯ ĐÃ GỬI (SENT #" + (row + 1) + ")";
        txtMailDetail.setText(formatEmailDetailText(e, row + 1, boxTitle));
        txtMailDetail.setCaretPosition(0);
    }

    private String formatEmailDetailText(Email e, int stt, String boxTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("================ ").append(boxTitle).append(" ================\n");
        sb.append("STT           : #").append(stt).append("\n");
        sb.append("Thời gian gửi : ").append(e.getTime()).append("\n");
        sb.append("Người gửi     : ").append(e.getSender()).append("\n");
        sb.append("Người nhận    : ").append(e.getReceiver()).append("\n");
        sb.append("Tiêu đề       : ").append(e.getSubject()).append("\n");
        sb.append("Tệp đính kèm  : ").append(e.getAttachedFile()).append("\n");
        sb.append("------------------------------------------------------------\n");
        sb.append("NỘI DUNG EMAIL:\n\n").append(e.getBody()).append("\n");
        return sb.toString();
    }

    private void replySelectedEmail() {
        int row = tblMailList.getSelectedRow();
        List<Email> list = viewingInboxFolder ? currentUser.getInbox() : currentUser.getSent();
        if (row < 0 || row >= list.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 thư trong danh sách để trả lời!");
            return;
        }
        Email e = list.get(row);
        txtReceiverEmail.setText(e.getSender());
        txtSubject.setText(e.getSubject().startsWith("Re:") ? e.getSubject() : "Re: " + e.getSubject());
        txtBody.setText("\n\n--- Thư gốc từ " + e.getSender() + " lúc " + e.getTime() + " ---\n" + e.getBody());
        txtBody.requestFocusInWindow();
        txtBody.setCaretPosition(0);
    }

    private void deleteSelectedEmailInCurrentFolder() {
        int row = tblMailList.getSelectedRow();
        List<Email> list = viewingInboxFolder ? currentUser.getInbox() : currentUser.getSent();
        if (row < 0 || row >= list.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 thư trong danh sách để xóa!");
            return;
        }
        if (viewingInboxFolder) {
            if (pop3Client.deleteEmailPop3(currentUser, row)) {
                appendProtocolLog(pop3Client.getLastProtocolLog());
                refreshAllMailAndFileViews();
            }
        } else {
            try {
                synchronized (FileManager.class) {
                    Account fresh = fileManager.loadAccount(currentUser.getUsername());
                    if (fresh != null && row < fresh.getSent().size()) {
                        fresh.getSent().remove(row);
                        fileManager.saveAccount(fresh);
                        currentUser = fresh;
                    }
                }
                refreshAllMailAndFileViews();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi cập nhật file: " + ex.getMessage());
            }
        }
    }

    private void showExternalPop3Dialog() {
        JTextField txtHost = new JTextField("127.0.0.1", 14);
        JTextField txtPort = new JTextField("1110", 6);
        JTextField txtUser = new JTextField(currentUser.getUsername(), 14);
        JPasswordField txtPass = new JPasswordField(currentUser.getPassword(), 14);
        styleTextField(txtHost);
        styleTextField(txtPort);
        styleTextField(txtUser);
        styleTextField(txtPass);

        JPanel p = new JPanel(new GridLayout(4, 2, 8, 8));
        p.add(new JLabel("POP3 Server Host:"));
        p.add(txtHost);
        p.add(new JLabel("POP3 Server Port:"));
        p.add(txtPort);
        p.add(new JLabel("POP3 Email:"));
        p.add(txtUser);
        p.add(new JLabel("POP3 Password:"));
        p.add(txtPass);

        if (JOptionPane.showConfirmDialog(this, p, "Kết nối POP3 Server có sẵn qua Socket",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            int port = 1110;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
            } catch (NumberFormatException ignored) {
            }
            boolean ok = pop3Client.fetchFromExternalPop3Server(
                    currentUser, txtHost.getText().trim(), port,
                    txtUser.getText().trim(), new String(txtPass.getPassword()).trim()
            );
            appendProtocolLog(pop3Client.getLastProtocolLog());
            refreshAllMailAndFileViews();
            if (ok) {
                JOptionPane.showMessageDialog(this, "Đã thực hiện phiên POP3 qua Socket thành công! Xem chi tiết ở khung Log bên phải.");
            } else {
                JOptionPane.showMessageDialog(this, "Không thể kết nối tới POP3 Server: Xem chi tiết ở khung Log bên phải.",
                        "Lỗi kết nối POP3", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void quickCreateRecipientAccount() {
        JTextField txtNewEmail = new JTextField("", 18);
        JPasswordField txtNewP = new JPasswordField("", 18);
        styleTextField(txtNewEmail);
        styleTextField(txtNewP);

        JPanel p = new JPanel(new GridLayout(2, 2, 8, 8));
        p.add(new JLabel("Địa chỉ Email mới (VD: user@gmail.com):"));
        p.add(txtNewEmail);
        p.add(new JLabel("Mật khẩu (tối thiểu 8 ký tự):"));
        p.add(txtNewP);

        if (JOptionPane.showConfirmDialog(this, p, "Tạo tài khoản Email mới (Mật khẩu >= 8 ký tự)",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            String rawEmail = FileManager.normalizeEmail(txtNewEmail.getText());
            String pw = new String(txtNewP.getPassword()).trim();
            if (!FileManager.isValidEmailSyntax(rawEmail)) {
                JOptionPane.showMessageDialog(this,
                        "Tên tài khoản bắt buộc phải là địa chỉ Email đúng cú pháp (ví dụ: user@gmail.com, user@hotmail.com)!",
                        "Sai cú pháp Email", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!FileManager.isValidNewPassword(pw)) {
                JOptionPane.showMessageDialog(this,
                        "Mật khẩu phải có ít nhất 8 ký tự (hiện tại: " + pw.length() + " ký tự)!\n"
                                + "Không đủ điều kiện nên tài khoản không được tạo.",
                        "Mật khẩu chưa đủ 8 ký tự", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                if (fileManager.createAccount(rawEmail, pw)) {
                    txtReceiverEmail.setText(rawEmail);
                    JOptionPane.showMessageDialog(this,
                            "Đã tạo tài khoản Email '" + rawEmail + "' kèm file riêng data/" + rawEmail + ".txt!");
                } else {
                    JOptionPane.showMessageDialog(this, "Tài khoản Email '" + rawEmail + "' đã tồn tại!");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
            }
        }
    }

    private void runUrlAndInetAnalysis(String rawInput) {
        modelUrlProps.setRowCount(0);
        modelInetProps.setRowCount(0);
        StringBuilder report = new StringBuilder();

        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isEmpty()) {
            txtNetConsole.setText("Vui lòng nhập một địa chỉ URL hoặc tên miền để phân tích.");
            return;
        }
        String urlCandidate = input.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") ? input : "http://" + input;

        report.append("================ 1. PHÂN TÍCH URL (Bài mẫu diachi.java) ================\n");
        String host = input;
        try {
            URL url = new URL(urlCandidate);
            host = url.getHost();
            addUrlRow("URL (url.toString())", url.toString(), report);
            addUrlRow("protocol", url.getProtocol(), report);
            addUrlRow("authority", url.getAuthority(), report);
            addUrlRow("file name", url.getFile(), report);
            addUrlRow("host", url.getHost(), report);
            addUrlRow("path", url.getPath(), report);
            addUrlRow("port", String.valueOf(url.getPort()), report);
            addUrlRow("default port", String.valueOf(url.getDefaultPort()), report);
            addUrlRow("query", String.valueOf(url.getQuery()), report);
            addUrlRow("ref", String.valueOf(url.getRef()), report);
        } catch (Exception e) {
            modelUrlProps.addRow(new Object[]{"Lỗi phân tích URL", e.getMessage()});
            report.append("Lỗi URL: ").append(e.getMessage()).append("\n");
        }

        report.append("\n============= 2. PHÂN TÍCH INETADDRESS (Bài mẫu inet.java) =============\n");
        try {
            InetAddress local = InetAddress.getLocalHost();
            modelInetProps.addRow(new Object[]{"LocalHost Name", local.getHostName()});
            modelInetProps.addRow(new Object[]{"LocalHost IP Address", local.getHostAddress()});
            report.append("Host Name (LocalHost): ").append(local.getHostName()).append("\n");
            report.append("IP Address (LocalHost): ").append(local.getHostAddress()).append("\n\n");

            InetAddress target = InetAddress.getByName(host);
            modelInetProps.addRow(new Object[]{"Host Name (" + host + ")", target.getHostName()});
            modelInetProps.addRow(new Object[]{"IP Address (" + host + ")", target.getHostAddress()});
            report.append("Host Name: ").append(target.getHostName()).append("\n");
            report.append("IP Address: ").append(target.getHostAddress()).append("\n\n");

            report.append("All address of ").append(host).append(":\n");
            InetAddress[] sw = InetAddress.getAllByName(host);
            for (int i = 0; i < sw.length; i++) {
                modelInetProps.addRow(new Object[]{"getAllByName [" + (i + 1) + "]", sw[i].toString()});
                report.append("  ").append(sw[i].toString()).append("\n");
            }
        } catch (Exception e) {
            modelInetProps.addRow(new Object[]{"Lỗi phân giải DNS", e.getMessage()});
            report.append("Lỗi InetAddress: ").append(e.getMessage()).append("\n");
        }

        txtNetConsole.setText(report.toString());
        txtNetConsole.setCaretPosition(0);
    }

    private void addUrlRow(String key, String val, StringBuilder report) {
        modelUrlProps.addRow(new Object[]{key, val});
        report.append(String.format("%-16s : %s\n", key, val));
    }

    private void refreshAllMailAndFileViews() {
        try {
            Account latest = fileManager.loadAccount(currentUser.getUsername());
            if (latest != null) {
                currentUser = latest;
            }
        } catch (Exception ignored) {
        }

        updateTopBannerText();
        lblSenderEmailDisplay.setText(currentUser.getUsername());

        int inboxCount = currentUser.getInbox().size();
        int sentCount = currentUser.getSent().size();
        btnFolderInbox.setText("Hộp thư đến (" + inboxCount + ")");
        btnFolderSent.setText("Thư đã gửi (" + sentCount + ")");

        if (viewingInboxFolder) {
            lblMailboxFolderHeader.setText("Hộp thư đến (POP3 Inbox) - " + inboxCount + " thư");
        } else {
            lblMailboxFolderHeader.setText("Thư đã gửi (Sent Mail) - " + sentCount + " thư");
        }

        populateCurrentFolderTable();

        lblAccUser.setText("Tài khoản Email: " + currentUser.getUsername());
        lblAccPass.setText("Mật khẩu: " + currentUser.getPassword());
        lblAccCreated.setText("Thời gian tạo TK: " + currentUser.getCreatedAt());
        lblAccLastLogin.setText("Đăng nhập lần cuối: " + currentUser.getLastLogin());
        lblAccFilePath.setText("Đường dẫn File riêng: data/" + currentUser.getUsername() + ".txt");
        lblAccInboxCount.setText("Tổng thư trong Inbox: " + inboxCount + " thư");
        lblAccSentCount.setText("Tổng thư đã gửi (Sent): " + sentCount + " thư");

        txtRawAccountFile.setText(fileManager.readRawAccountFile(currentUser.getUsername()));
        txtRawAccountFile.setCaretPosition(0);
    }

    private void appendProtocolLog(String logBlock) {
        if (logBlock == null || logBlock.isEmpty()) return;
        txtProtocolConsole.append(logBlock + "\n");
        txtProtocolConsole.setCaretPosition(txtProtocolConsole.getDocument().getLength());
    }

    private void showChangePasswordDialog() {
        JPasswordField txtNewPass = new JPasswordField("", 16);
        styleTextField(txtNewPass);
        JPanel p = new JPanel(new GridLayout(1, 2, 8, 8));
        p.add(new JLabel("Mật khẩu mới (>= 8 ký tự):"));
        p.add(txtNewPass);

        if (JOptionPane.showConfirmDialog(this, p, "Đổi mật khẩu tài khoản " + currentUser.getUsername(),
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            String newPass = new String(txtNewPass.getPassword()).trim();
            if (!FileManager.isValidNewPassword(newPass)) {
                JOptionPane.showMessageDialog(this,
                        "Mật khẩu mới phải có ít nhất 8 ký tự!",
                        "Mật khẩu không hợp lệ", JOptionPane.ERROR_MESSAGE);
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
                refreshAllMailAndFileViews();
                JOptionPane.showMessageDialog(this,
                        "Đã cập nhật mật khẩu trực tiếp vào file data/" + currentUser.getUsername() + ".txt!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi lưu file: " + ex.getMessage());
            }
        }
    }

    private void handleLogout() {
        if (autoSyncTimer != null) {
            autoSyncTimer.stop();
        }
        dispose();
        launch(fileManager);
    }

    // =========================================================================
    // 5. HỘP THOẠI ĐĂNG NHẬP & ĐĂNG KÝ
    // =========================================================================
    public static class AuthDialog extends JDialog {
        private final FileManager fm;
        private Account authenticatedAccount;
        private CardLayout authCardLayout;
        private JPanel formContainer;
        private JButton btnSwitchLogin;
        private JButton btnSwitchRegister;

        public AuthDialog(FileManager fm) {
            super((JFrame) null, "Đăng nhập / Đăng ký - Hệ Thống Mail Client (Nhóm 2)", true);
            this.fm = fm;
            setSize(560, 480);
            setResizable(false);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            buildUi();
        }

        private void buildUi() {
            JPanel root = new JPanel(new BorderLayout(0, 0));
            root.setBackground(COL_BG_MAIN);

            JPanel topBanner = new JPanel(new BorderLayout(0, 4));
            topBanner.setBackground(COL_HEADER_BG);
            topBanner.setBorder(new EmptyBorder(18, 24, 18, 24));

            JLabel lblTitle = new JLabel("NHÓM 2: HỆ THỐNG MAIL CLIENT (SMTP & POP3)", SwingConstants.CENTER);
            lblTitle.setFont(FONT_HEADER);
            lblTitle.setForeground(Color.WHITE);

            JLabel lblSub = new JLabel("Tài khoản theo đúng định dạng Email (@gmail, @hotmail...) | Lưu riêng data/<email>.txt", SwingConstants.CENTER);
            lblSub.setFont(FONT_SMALL);
            lblSub.setForeground(new Color(148, 163, 184));

            topBanner.add(lblTitle, BorderLayout.NORTH);
            topBanner.add(lblSub, BorderLayout.SOUTH);
            root.add(topBanner, BorderLayout.NORTH);

            JPanel centerOuter = new JPanel(new BorderLayout());
            centerOuter.setOpaque(false);
            centerOuter.setBorder(new EmptyBorder(16, 28, 12, 28));

            JPanel cardBox = new JPanel(new BorderLayout(0, 0));
            cardBox.setBackground(COL_SURFACE);
            cardBox.setBorder(new LineBorder(COL_BORDER, 1, true));

            JPanel modeBar = new JPanel(new GridLayout(1, 2, 0, 0));
            modeBar.setBackground(COL_TAB_STRIP);
            modeBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER));

            btnSwitchLogin = new JButton("ĐĂNG NHẬP HỆ THỐNG");
            btnSwitchRegister = new JButton("ĐĂNG KÝ TÀI KHOẢN MỚI");
            setupModeButton(btnSwitchLogin);
            setupModeButton(btnSwitchRegister);

            btnSwitchLogin.addActionListener(e -> setAuthMode(true));
            btnSwitchRegister.addActionListener(e -> setAuthMode(false));

            modeBar.add(btnSwitchLogin);
            modeBar.add(btnSwitchRegister);
            cardBox.add(modeBar, BorderLayout.NORTH);

            authCardLayout = new CardLayout();
            formContainer = new JPanel(authCardLayout);
            formContainer.setBackground(COL_SURFACE);

            formContainer.add(buildLoginForm(), "LOGIN");
            formContainer.add(buildRegisterForm(), "REGISTER");

            cardBox.add(formContainer, BorderLayout.CENTER);
            centerOuter.add(cardBox, BorderLayout.CENTER);
            root.add(centerOuter, BorderLayout.CENTER);

            String localHostStr = "localhost (127.0.0.1)";
            try {
                InetAddress local = InetAddress.getLocalHost();
                localHostStr = local.getHostName() + " (" + local.getHostAddress() + ")";
            } catch (Exception ignored) {
            }
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            footer.setBorder(new EmptyBorder(0, 24, 14, 24));
            JLabel lblFooter = new JLabel(
                    "Máy trạm: " + localHostStr + " | Mỗi tài khoản Email lưu chung trong 1 file data/<email>.txt",
                    SwingConstants.CENTER
            );
            lblFooter.setFont(FONT_SMALL);
            lblFooter.setForeground(COL_TEXT_MUTED);
            footer.add(lblFooter, BorderLayout.CENTER);
            root.add(footer, BorderLayout.SOUTH);

            setAuthMode(true);
            setContentPane(root);
        }

        private void setupModeButton(JButton btn) {
            btn.setFont(FONT_UI_BOLD);
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(true);
            btn.setOpaque(true);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(200, 42));
        }

        private void setAuthMode(boolean loginActive) {
            authCardLayout.show(formContainer, loginActive ? "LOGIN" : "REGISTER");
            if (loginActive) {
                btnSwitchLogin.setBackground(COL_SURFACE);
                btnSwitchLogin.setForeground(COL_PRIMARY);
                btnSwitchLogin.setBorder(BorderFactory.createMatteBorder(3, 0, 0, 1, COL_PRIMARY));

                btnSwitchRegister.setBackground(COL_TAB_STRIP);
                btnSwitchRegister.setForeground(COL_TEXT_MUTED);
                btnSwitchRegister.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, COL_BORDER));
            } else {
                btnSwitchRegister.setBackground(COL_SURFACE);
                btnSwitchRegister.setForeground(COL_SUCCESS);
                btnSwitchRegister.setBorder(BorderFactory.createMatteBorder(3, 1, 0, 0, COL_SUCCESS));

                btnSwitchLogin.setBackground(COL_TAB_STRIP);
                btnSwitchLogin.setForeground(COL_TEXT_MUTED);
                btnSwitchLogin.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, COL_BORDER));
            }
        }

        private JPanel buildLoginForm() {
            JPanel loginPanel = new JPanel(new GridBagLayout());
            loginPanel.setBackground(COL_SURFACE);
            loginPanel.setBorder(new EmptyBorder(20, 32, 20, 32));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(7, 6, 7, 6);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JTextField txtUser = new JTextField("");
            styleTextField(txtUser);
            txtUser.setPreferredSize(new Dimension(260, 36));

            JPasswordField txtPass = new JPasswordField("");
            styleTextField(txtPass);
            txtPass.setPreferredSize(new Dimension(260, 36));

            JLabel lUser = new JLabel("Địa chỉ Email:");
            lUser.setFont(FONT_UI_BOLD);
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.32;
            loginPanel.add(lUser, gbc);
            gbc.gridx = 1; gbc.weightx = 0.68;
            loginPanel.add(txtUser, gbc);

            JLabel lPass = new JLabel("Mật khẩu:");
            lPass.setFont(FONT_UI_BOLD);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.32;
            loginPanel.add(lPass, gbc);
            gbc.gridx = 1; gbc.weightx = 0.68;
            loginPanel.add(txtPass, gbc);

            JButton btnLogin = createFlatButton(
                    "Đăng nhập Hệ thống Mail",
                    COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true
            );
            btnLogin.setPreferredSize(new Dimension(320, 40));
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
            gbc.insets = new Insets(14, 6, 8, 6);
            loginPanel.add(btnLogin, gbc);

            JLabel lblHint = new JLabel(
                    "<html><div style='text-align:center; color:#475569;'>"
                            + "Tài khoản mẫu: <b>alice@gmail.com</b> và <b>bob@hotmail.com</b> (MK: <b>12345678</b>)<br>"
                            + "Hoặc chuyển sang tab <b>ĐĂNG KÝ TÀI KHOẢN MỚI</b> để tạo email bất kỳ.</div></html>",
                    SwingConstants.CENTER
            );
            lblHint.setFont(FONT_SMALL);
            gbc.gridy = 3;
            gbc.insets = new Insets(4, 6, 0, 6);
            loginPanel.add(lblHint, gbc);

            btnLogin.addActionListener(e -> {
                String email = FileManager.normalizeEmail(txtUser.getText());
                String pass = new String(txtPass.getPassword()).trim();
                if (!FileManager.isValidEmailSyntax(email)) {
                    JOptionPane.showMessageDialog(this,
                            "Tên tài khoản bắt buộc phải là địa chỉ Email đúng cú pháp (ví dụ: user@gmail.com, user@hotmail.com)!",
                            "Sai định dạng Email", JOptionPane.WARNING_MESSAGE);
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
                            JOptionPane.showMessageDialog(this,
                                    "Đăng nhập thất bại: Sai địa chỉ Email hoặc Mật khẩu!",
                                    "Lỗi xác thực", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi hệ thống: " + ex.getMessage());
                }
            });

            return loginPanel;
        }

        private JPanel buildRegisterForm() {
            JPanel regPanel = new JPanel(new GridBagLayout());
            regPanel.setBackground(COL_SURFACE);
            regPanel.setBorder(new EmptyBorder(18, 32, 18, 32));
            GridBagConstraints rg = new GridBagConstraints();
            rg.insets = new Insets(6, 6, 6, 6);
            rg.fill = GridBagConstraints.HORIZONTAL;

            JTextField txtRegEmail = new JTextField("");
            JPasswordField txtRegPass = new JPasswordField("");
            styleTextField(txtRegEmail);
            styleTextField(txtRegPass);
            txtRegEmail.setPreferredSize(new Dimension(260, 36));
            txtRegPass.setPreferredSize(new Dimension(260, 36));

            JLabel l1 = new JLabel("Địa chỉ Email mới:");
            l1.setFont(FONT_UI_BOLD);
            rg.gridx = 0; rg.gridy = 0; rg.weightx = 0.36;
            regPanel.add(l1, rg);
            rg.gridx = 1; rg.weightx = 0.64;
            regPanel.add(txtRegEmail, rg);

            JLabel l2 = new JLabel("Mật khẩu (>= 8 ký tự):");
            l2.setFont(FONT_UI_BOLD);
            rg.gridx = 0; rg.gridy = 1; rg.weightx = 0.36;
            regPanel.add(l2, rg);
            rg.gridx = 1; rg.weightx = 0.64;
            regPanel.add(txtRegPass, rg);

            JLabel lblPassRule = new JLabel("Bắt buộc: Tên TK phải đúng cú pháp Email (@gmail, @hotmail...) & Mật khẩu >= 8 ký tự.");
            lblPassRule.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            lblPassRule.setForeground(COL_DANGER);
            rg.gridx = 0; rg.gridy = 2; rg.gridwidth = 2;
            rg.insets = new Insets(2, 6, 4, 6);
            regPanel.add(lblPassRule, rg);

            JButton btnReg = createFlatButton(
                    "Đăng ký & Khởi tạo File data/<email>.txt",
                    COL_SUCCESS, Color.WHITE, COL_SUCCESS_HOVER, true
            );
            btnReg.setPreferredSize(new Dimension(320, 40));
            rg.gridx = 0; rg.gridy = 3; rg.gridwidth = 2; rg.weightx = 1.0;
            rg.insets = new Insets(10, 6, 4, 6);
            regPanel.add(btnReg, rg);

            btnReg.addActionListener(e -> {
                String email = FileManager.normalizeEmail(txtRegEmail.getText());
                String pass = new String(txtRegPass.getPassword()).trim();
                if (email.isEmpty() || pass.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Địa chỉ Email và Mật khẩu không được để trống!");
                    return;
                }
                if (!FileManager.isValidEmailSyntax(email)) {
                    JOptionPane.showMessageDialog(this,
                            "Tên tài khoản '" + email + "' không hợp lệ!\n"
                                    + "Tên tài khoản bắt buộc phải là địa chỉ Email đúng cú pháp (ví dụ: ten@gmail.com, ten@hotmail.com...).",
                            "Sai định dạng Email", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!FileManager.isValidNewPassword(pass)) {
                    JOptionPane.showMessageDialog(this,
                            "Mật khẩu phải có ít nhất 8 ký tự (bạn mới nhập " + pass.length() + " ký tự)!\n"
                                    + "Không đủ điều kiện nên tài khoản không được tạo.",
                            "Mật khẩu chưa đủ 8 ký tự", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try {
                    if (fm.createAccount(email, pass)) {
                        JOptionPane.showMessageDialog(this,
                                "Đăng ký thành công tài khoản Email: " + email + "\n"
                                        + "Đã khởi tạo file dữ liệu riêng: data/" + email + ".txt\n"
                                        + "Hệ thống sẽ tự động đăng nhập.");
                        authenticatedAccount = fm.loadAccount(email);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Đăng ký thất bại: Tài khoản Email '" + email + "' đã tồn tại!",
                                "Trùng tài khoản", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
                }
            });

            return regPanel;
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

    private void openOrSaveSelectedAttachment() {
        int row = tblMailList.getSelectedRow();
        List<Email> list = viewingInboxFolder ? currentUser.getInbox() : currentUser.getSent();
        if (row < 0 || row >= list.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 thư có đính kèm tệp trong danh sách!");
            return;
        }
        Email email = list.get(row);
        String att = email.getAttachedFile();
        if (att == null || att.trim().isEmpty() || "None".equalsIgnoreCase(att.trim())) {
            JOptionPane.showMessageDialog(this, "Thư này không có tệp đính kèm!");
            return;
        }

        File realFile = SmtpClient.resolveAttachmentFile(att);
        if (realFile == null || !realFile.exists()) {
            String cleanName = new File(att).getName();
            File fallback = new File("attachments", cleanName);
            if (fallback.exists()) {
                realFile = fallback;
            }
        }

        if (realFile == null || !realFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy file gốc trên ổ cứng ('" + att + "')!\n"
                            + "Lưu ý: Các thư cũ gửi trước khi nâng cấp Base64 chỉ lưu mỗi tên file.",
                    "Không tìm thấy File gốc", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final File targetFile = realFile;
        String lowerName = targetFile.getName().toLowerCase();

        JComponent previewComp = null;
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif")) {
            javax.swing.ImageIcon icon = new javax.swing.ImageIcon(targetFile.getAbsolutePath());
            if (icon.getIconWidth() > 0) {
                int maxW = 480, maxH = 320;
                int w = icon.getIconWidth(), h = icon.getIconHeight();
                if (w > maxW || h > maxH) {
                    double scale = Math.min((double) maxW / w, (double) maxH / h);
                    w = (int) (w * scale);
                    h = (int) (h * scale);
                }
                java.awt.Image scaled = icon.getImage().getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
                JLabel lblImg = new JLabel(new javax.swing.ImageIcon(scaled));
                lblImg.setHorizontalAlignment(SwingConstants.CENTER);
                lblImg.setBorder(new LineBorder(COL_BORDER, 1, true));
                previewComp = lblImg;
            }
        }

        JPanel dialogPanel = new JPanel(new BorderLayout(0, 10));
        dialogPanel.add(new JLabel("<html><b>Tệp đính kèm gốc:</b> " + targetFile.getName()
                + "<br><b>Dung lượng:</b> " + targetFile.length() + " bytes"
                + "<br><b>Vị trí lưu trên máy:</b> " + targetFile.getAbsolutePath() + "</html>"), BorderLayout.NORTH);
        if (previewComp != null) {
            dialogPanel.add(previewComp, BorderLayout.CENTER);
        }

        Object[] options = {"Mở file trực tiếp", "Lưu về máy (Save As...)", "Đóng"};
        int choice = JOptionPane.showOptionDialog(this, dialogPanel,
                "Tệp đính kèm: " + targetFile.getName(),
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        try {
            if (choice == 0) {
                java.awt.Desktop.getDesktop().open(targetFile);
            } else if (choice == 1) {
                JFileChooser saver = new JFileChooser();
                saver.setSelectedFile(new File(targetFile.getName()));
                if (saver.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File dest = saver.getSelectedFile();
                    java.nio.file.Files.copy(targetFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    JOptionPane.showMessageDialog(this, "Đã lưu file thành công tại:\n" + dest.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi thao tác với file: " + ex.getMessage());
        }
    }
}