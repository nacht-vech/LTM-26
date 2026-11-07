import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
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
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ĐỀ TÀI NHÓM 1: CHƯƠNG TRÌNH TRÌNH DUYỆT WEB ĐƠN GIẢN (SIMPLE WEB BROWSER)
 * Giao diện đã được thiết kế lại hiện đại, đồng bộ màu sắc (tiệp màu),
 * khắc phục triệt để lỗi chữ trắng trên nền nút sáng của Windows và thay thế
 * JTabbedPane rối mắt bằng thanh điều hướng liền mạch (CardLayout).
 */
public class BrowserMain extends JFrame {

    // =========================================================================
    // BẢNG MÀU & FONT CHỮ ĐỒNG BỘ (TIỆP MÀU, DỄ NHÌN TRÊN WINDOWS)
    // =========================================================================
    public static final Color COL_BG_APP = new Color(241, 245, 249);       // Nền xám xanh dịu mắt
    public static final Color COL_SURFACE = Color.WHITE;                   // Nền khung nội dung
    public static final Color COL_BORDER = new Color(203, 213, 225);       // Viền mảnh tinh tế
    public static final Color COL_HEADER = new Color(15, 23, 42);          // Xanh đen đậm cho Header
    public static final Color COL_NAV_BG = new Color(226, 232, 240);       // Nền thanh chuyển Tab
    public static final Color COL_PRIMARY = new Color(2, 132, 199);        // Xanh dương chủ đạo
    public static final Color COL_PRIMARY_HOVER = new Color(3, 105, 161);
    public static final Color COL_SUCCESS = new Color(22, 163, 74);        // Xanh lá nút tích cực
    public static final Color COL_SUCCESS_HOVER = new Color(21, 128, 61);
    public static final Color COL_SECONDARY_BG = new Color(241, 245, 249); // Nút phụ sáng màu
    public static final Color COL_SECONDARY_FG = new Color(30, 41, 59);    // Chữ nút phụ đậm rõ
    public static final Color COL_DANGER = new Color(220, 38, 38);         // Đỏ cảnh báo/xóa
    public static final Color COL_TEXT_MAIN = new Color(15, 23, 42);
    public static final Color COL_TEXT_MUTED = new Color(100, 116, 139);

    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_UI = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_UI_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 13);

    private static final String STORAGE_DIR = ".";
    private static final String DEMO_URL = "http://localhost:8088/vku-demo?course=network_programming&group=1#section1";

    private final BrowserFileStore fileStore;
    private BrowserFileStore.AccountRecord currentUser;

    private final List<String> backForwardUrls = new ArrayList<>();
    private int historyIndex = -1;

    private JLabel lblUserStatus;
    private JButton btnBack;
    private JButton btnForward;
    private JComboBox<String> cbMethod;
    private JTextField txtUrl;
    private JPanel postConfigPanel;
    private JTextField txtPostData;
    private JLabel lblAttachedFile;
    private File selectedPostFile;

    private JLabel lblStatStatus;
    private JLabel lblStatLength;
    private JLabel lblStatP;
    private JLabel lblStatDiv;
    private JLabel lblStatSpan;
    private JLabel lblStatImg;

    // Điều hướng CardLayout thay cho JTabbedPane
    private CardLayout contentCardLayout;
    private JPanel contentCardPanel;
    private final List<JButton> navTabButtons = new ArrayList<>();
    private static final String[] CARD_KEYS = {"RENDER", "SOURCE", "HEADERS", "URL_INET", "STORAGE"};

    private JEditorPane htmlRenderPane;
    private JTextArea txtHtmlSource;
    private DefaultTableModel modelHeaders;
    private DefaultTableModel modelExtractedLinks;
    private DefaultTableModel modelUrlProps;
    private DefaultTableModel modelInetIps;
    private JTextArea txtUrlInetReport;
    private DefaultTableModel modelBookmarks;
    private DefaultTableModel modelHistory;
    private JTextArea txtRawDatabaseView;

    private String lastHtmlContent = "";

    public BrowserMain(BrowserFileStore fileStore, BrowserFileStore.AccountRecord user) {
        this.fileStore = fileStore;
        this.currentUser = user;

        HttpProcessor.startLocalDemoServer(8088);

        setTitle("VKU Simple Web Browser - Nhóm 1 | Tài khoản: " + user.username
                + " (" + user.accountFile.getName() + ")");
        setSize(1280, 820);
        setMinimumSize(new Dimension(1020, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        refreshBookmarksAndHistoryViews();

        txtUrl.setText(DEMO_URL);
        performHttpRequest(DEMO_URL, "GET", false);
    }

    // =========================================================================
    // COMPONENT TÙY CHỈNH: NÚT BẤM VẼ TRỰC TIẾP (KHÔNG BỊ WINDOWS ĐÈ MÀU TRẮNG)
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
                    g2.setColor(new Color(226, 232, 240));
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
        btn.setMargin(new Insets(6, 14, 6, 14));
        return btn;
    }

    public static void styleTextField(JTextField tf) {
        tf.setFont(FONT_UI);
        tf.setForeground(COL_TEXT_MAIN);
        tf.setBackground(Color.WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COL_BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
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

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(8, 10, 10, 10));
        root.setBackground(COL_BG_APP);

        // =====================================================================
        // KHỐI TRÊN CÙNG: HEADER TÀI KHOẢN + THANH ĐỊA CHỈ URL + THANH FORM POST
        // =====================================================================
        JPanel topContainer = new JPanel(new BorderLayout(0, 6));
        topContainer.setOpaque(false);

        // 1. Thanh thông tin tài khoản (Header Bar)
        JPanel accountBar = new JPanel(new BorderLayout(10, 0));
        accountBar.setBackground(COL_HEADER);
        accountBar.setBorder(new EmptyBorder(8, 14, 8, 14));

        String localHostInfo = "127.0.0.1";
        try {
            InetAddress local = InetAddress.getLocalHost();
            localHostInfo = local.getHostName() + " (" + local.getHostAddress() + ")";
        } catch (Exception ignored) {
        }

        lblUserStatus = new JLabel(String.format(
                "Tài khoản: %s (%s)   |   File riêng: %s   |   Tạo lúc: %s   |   Đăng nhập: %s (Lần %d)   |   Máy: %s",
                currentUser.username, currentUser.fullName, currentUser.accountFile.getName(),
                currentUser.createdAt, currentUser.lastLoginAt, currentUser.loginCount, localHostInfo
        ));
        lblUserStatus.setForeground(new Color(226, 232, 240));
        lblUserStatus.setFont(FONT_SMALL);
        accountBar.add(lblUserStatus, BorderLayout.CENTER);

        JPanel accountBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountBtnPanel.setOpaque(false);
        JButton btnChangePass = createFlatButton("Đổi mật khẩu", new Color(51, 65, 85), Color.WHITE, new Color(71, 85, 105), false);
        btnChangePass.setFont(FONT_SMALL);
        btnChangePass.addActionListener(e -> showChangePasswordDialog());

        JButton btnLogout = createFlatButton("Đăng xuất", COL_DANGER, Color.WHITE, new Color(185, 28, 28), true);
        btnLogout.setFont(FONT_SMALL);
        btnLogout.addActionListener(e -> handleLogout());

        accountBtnPanel.add(btnChangePass);
        accountBtnPanel.add(btnLogout);
        accountBar.add(accountBtnPanel, BorderLayout.EAST);
        topContainer.add(accountBar, BorderLayout.NORTH);

        // 2. Thanh điều hướng URL & Chọn phương thức GET / POST / HEAD
        JPanel navBar = new JPanel(new BorderLayout(8, 0));
        navBar.setBackground(COL_SURFACE);
        navBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COL_BORDER, 1, true),
                new EmptyBorder(7, 10, 7, 10)
        ));

        JPanel navLeftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        navLeftButtons.setOpaque(false);

        btnBack = createFlatButton("◄", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, true);
        btnBack.setToolTipText("Quay lại trang trước");
        btnBack.setEnabled(false);
        btnBack.addActionListener(e -> navigateBack());

        btnForward = createFlatButton("►", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, true);
        btnForward.setToolTipText("Tiến tới trang sau");
        btnForward.setEnabled(false);
        btnForward.addActionListener(e -> navigateForward());

        JButton btnReload = createFlatButton("Tải lại", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, false);
        btnReload.addActionListener(e -> performHttpRequest(txtUrl.getText(), (String) cbMethod.getSelectedItem(), false));

        JButton btnHomeVku = createFlatButton("VKU", COL_SECONDARY_BG, COL_PRIMARY, COL_NAV_BG, true);
        btnHomeVku.setToolTipText("Mở http://vku.udn.vn");
        btnHomeVku.addActionListener(e -> {
            txtUrl.setText("http://vku.udn.vn");
            performHttpRequest("http://vku.udn.vn", (String) cbMethod.getSelectedItem(), false);
        });

        JButton btnLocalDemo = createFlatButton("Trang Demo (GET/POST/HEAD)", new Color(224, 242, 254), new Color(3, 105, 161), new Color(186, 230, 253), true);
        btnLocalDemo.addActionListener(e -> {
            txtUrl.setText(DEMO_URL);
            performHttpRequest(DEMO_URL, (String) cbMethod.getSelectedItem(), false);
        });

        cbMethod = new JComboBox<>(new String[]{"GET", "POST", "HEAD"});
        cbMethod.setFont(FONT_UI_BOLD);
        cbMethod.setPreferredSize(new Dimension(85, 32));
        cbMethod.addActionListener(e -> {
            String m = (String) cbMethod.getSelectedItem();
            postConfigPanel.setVisible("POST".equalsIgnoreCase(m));
            topContainer.revalidate();
            topContainer.repaint();
        });

        navLeftButtons.add(btnBack);
        navLeftButtons.add(btnForward);
        navLeftButtons.add(btnReload);
        navLeftButtons.add(btnHomeVku);
        navLeftButtons.add(btnLocalDemo);
        JLabel lblMethod = new JLabel("  Method:");
        lblMethod.setFont(FONT_UI_BOLD);
        navLeftButtons.add(lblMethod);
        navLeftButtons.add(cbMethod);

        txtUrl = new JTextField();
        styleTextField(txtUrl);
        txtUrl.addActionListener(e -> performHttpRequest(txtUrl.getText(), (String) cbMethod.getSelectedItem(), false));

        JPanel navRightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        navRightButtons.setOpaque(false);

        JButton btnSend = createFlatButton("Gửi Request", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true);
        btnSend.addActionListener(e -> performHttpRequest(txtUrl.getText(), (String) cbMethod.getSelectedItem(), false));

        JButton btnBookmark = createFlatButton("+ Bookmark", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, false);
        btnBookmark.addActionListener(e -> saveCurrentBookmark());

        JButton btnSaveHtml = createFlatButton("Lưu HTML", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, false);
        btnSaveHtml.addActionListener(e -> saveHtmlToFile());

        navRightButtons.add(btnSend);
        navRightButtons.add(btnBookmark);
        navRightButtons.add(btnSaveHtml);

        navBar.add(navLeftButtons, BorderLayout.WEST);
        navBar.add(txtUrl, BorderLayout.CENTER);
        navBar.add(navRightButtons, BorderLayout.EAST);
        topContainer.add(navBar, BorderLayout.CENTER);

        // 3. Thanh cấu hình dữ liệu POST (Chỉ hiện khi chọn POST)
        postConfigPanel = new JPanel(new BorderLayout(8, 0));
        postConfigPanel.setBackground(new Color(254, 252, 232));
        postConfigPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(250, 204, 21), 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        JLabel lblPostTitle = new JLabel("Dữ liệu Body (POST Form): ");
        lblPostTitle.setFont(FONT_UI_BOLD);
        postConfigPanel.add(lblPostTitle, BorderLayout.WEST);

        txtPostData = new JTextField("student_name=NguyenVanA&class=22IT&school=VKU_DaNang");
        styleTextField(txtPostData);
        postConfigPanel.add(txtPostData, BorderLayout.CENTER);

        JPanel postFileBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        postFileBox.setOpaque(false);
        lblAttachedFile = new JLabel("Chưa đính kèm file");
        lblAttachedFile.setFont(FONT_SMALL);
        JButton btnChooseFile = createFlatButton("Chọn File gửi kèm", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, false);
        btnChooseFile.addActionListener(e -> choosePostAttachment());
        JButton btnClearFile = createFlatButton("Bỏ chọn", COL_SECONDARY_BG, COL_DANGER, COL_NAV_BG, false);
        btnClearFile.addActionListener(e -> {
            selectedPostFile = null;
            lblAttachedFile.setText("Chưa đính kèm file");
        });
        postFileBox.add(lblAttachedFile);
        postFileBox.add(btnChooseFile);
        postFileBox.add(btnClearFile);
        postConfigPanel.add(postFileBox, BorderLayout.EAST);
        postConfigPanel.setVisible(false);

        topContainer.add(postConfigPanel, BorderLayout.SOUTH);
        root.add(topContainer, BorderLayout.NORTH);

        // =====================================================================
        // KHỐI GIỮA: DẢI THỐNG KÊ GỌN GÀNG + THANH CHUYỂN TAB TIỆP MÀU + NỘI DUNG
        // =====================================================================
        JPanel centerContainer = new JPanel(new BorderLayout(0, 8));
        centerContainer.setOpaque(false);

        // 1. Dải thống kê thẻ HTML nằm trên 1 thanh ngang đồng nhất
        JPanel statsStrip = new JPanel(new GridLayout(1, 6, 8, 0));
        statsStrip.setOpaque(false);

        lblStatStatus = createCompactStatBox("HTTP Status / Method", "Chưa gửi", COL_TEXT_MAIN, statsStrip);
        lblStatLength = createCompactStatBox("Chiều dài trang HTML", "0 ký tự", COL_PRIMARY, statsStrip);
        lblStatP = createCompactStatBox("Số lượng thẻ <p>", "0", COL_SUCCESS, statsStrip);
        lblStatDiv = createCompactStatBox("Số lượng thẻ <div>", "0", new Color(180, 83, 9), statsStrip);
        lblStatSpan = createCompactStatBox("Số lượng thẻ <span>", "0", new Color(126, 34, 206), statsStrip);
        lblStatImg = createCompactStatBox("Số lượng <img> / <imag>", "0", COL_DANGER, statsStrip);

        centerContainer.add(statsStrip, BorderLayout.NORTH);

        // 2. Khung chính: Thanh Navigation Bar tiệp màu + CardLayout
        JPanel mainCardWrapper = new JPanel(new BorderLayout(0, 0));
        mainCardWrapper.setBackground(COL_SURFACE);
        mainCardWrapper.setBorder(new LineBorder(COL_BORDER, 1, true));

        JPanel navTabStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        navTabStrip.setBackground(new Color(241, 245, 249));
        navTabStrip.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COL_BORDER));

        String[] tabTitles = {
                "1. Trang Web (HTML Render)",
                "2. Mã nguồn HTML (Source)",
                "3. Thông tin Tài nguyên (HEAD) & Links",
                "4. Phân tích URL & InetAddress",
                "5. Bookmark, Lịch sử & File .txt"
        };

        for (int i = 0; i < tabTitles.length; i++) {
            final int index = i;
            JButton tabBtn = createNavTabButton(tabTitles[i]);
            tabBtn.addActionListener(e -> switchActiveCard(index));
            navTabButtons.add(tabBtn);
            navTabStrip.add(tabBtn);
        }

        contentCardLayout = new CardLayout();
        contentCardPanel = new JPanel(contentCardLayout);
        contentCardPanel.setBackground(COL_SURFACE);
        contentCardPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        contentCardPanel.add(buildRenderCard(), CARD_KEYS[0]);
        contentCardPanel.add(buildSourceCard(), CARD_KEYS[1]);
        contentCardPanel.add(buildHeadersAndLinksCard(), CARD_KEYS[2]);
        contentCardPanel.add(buildUrlInetCard(), CARD_KEYS[3]);
        contentCardPanel.add(buildStorageCard(), CARD_KEYS[4]);

        mainCardWrapper.add(navTabStrip, BorderLayout.NORTH);
        mainCardWrapper.add(contentCardPanel, BorderLayout.CENTER);

        switchActiveCard(0);
        centerContainer.add(mainCardWrapper, BorderLayout.CENTER);
        root.add(centerContainer, BorderLayout.CENTER);

        setContentPane(root);
    }

    private JButton createNavTabButton(String title) {
        JButton btn = new JButton(title);
        btn.setFont(FONT_UI_BOLD);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(true);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(11, 18, 11, 18));
        return btn;
    }

    private void switchActiveCard(int activeIndex) {
        contentCardLayout.show(contentCardPanel, CARD_KEYS[activeIndex]);
        for (int i = 0; i < navTabButtons.size(); i++) {
            JButton b = navTabButtons.get(i);
            if (i == activeIndex) {
                b.setBackground(COL_SURFACE);
                b.setForeground(COL_PRIMARY);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(3, 0, 0, 1, COL_PRIMARY),
                        new EmptyBorder(8, 18, 11, 18)
                ));
            } else {
                b.setBackground(new Color(241, 245, 249));
                b.setForeground(COL_TEXT_MUTED);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 0, 1, COL_BORDER),
                        new EmptyBorder(11, 18, 11, 18)
                ));
            }
        }
    }

    private JLabel createCompactStatBox(String label, String value, Color valueColor, JPanel parent) {
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setBackground(COL_SURFACE);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COL_BORDER, 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        JLabel lblTitle = new JLabel(label);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblTitle.setForeground(COL_TEXT_MUTED);

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblValue.setForeground(valueColor);

        box.add(lblTitle, BorderLayout.NORTH);
        box.add(lblValue, BorderLayout.CENTER);
        parent.add(box);
        return lblValue;
    }

    private JPanel buildRenderCard() {
        htmlRenderPane = new JEditorPane();
        htmlRenderPane.setEditable(false);
        htmlRenderPane.setContentType("text/html; charset=UTF-8");
        htmlRenderPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        htmlRenderPane.setFont(FONT_UI);
        htmlRenderPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String target = e.getURL() != null ? e.getURL().toString() : e.getDescription();
                if (target != null && !target.isEmpty()) {
                    txtUrl.setText(target);
                    performHttpRequest(target, "GET", false);
                }
            }
        });

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        JLabel lblHint = new JLabel("Mẹo: Nhấp trực tiếp vào các liên kết trong trang để duyệt tiếp.");
        lblHint.setFont(FONT_SMALL);
        lblHint.setForeground(COL_TEXT_MUTED);

        JButton btnOpenBrowser = createFlatButton("Mở URL trên Trình duyệt máy tính", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, false);
        btnOpenBrowser.addActionListener(e -> openInSystemBrowser(txtUrl.getText()));

        bottomBar.add(lblHint, BorderLayout.WEST);
        bottomBar.add(btnOpenBrowser, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(htmlRenderPane);
        scroll.setBorder(null);
        return createTitledCard("Khung biểu diễn trang HTML (HTML Viewer)", scroll, bottomBar);
    }

    private JPanel buildSourceCard() {
        txtHtmlSource = new JTextArea();
        txtHtmlSource.setEditable(false);
        txtHtmlSource.setFont(FONT_MONO);
        txtHtmlSource.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(txtHtmlSource);
        scroll.setBorder(null);
        return createTitledCard("Mã nguồn HTML trả về từ Server", scroll, null);
    }

    private JPanel buildHeadersAndLinksCard() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.55);
        split.setBorder(null);

        modelHeaders = new DefaultTableModel(new String[]{"Thuộc tính Tài nguyên / HTTP Header", "Giá trị trả về từ Server"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable tblHeaders = new JTable(modelHeaders);
        styleTable(tblHeaders);
        JScrollPane scrollHeaders = new JScrollPane(tblHeaders);
        scrollHeaders.setBorder(null);

        modelExtractedLinks = new DefaultTableModel(new String[]{"Loại thẻ HTML", "Đường dẫn URL tuyệt đối (Nhấp đúp để mở)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable tblLinks = new JTable(modelExtractedLinks);
        styleTable(tblLinks);
        tblLinks.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblLinks.getSelectedRow() != -1) {
                    String linkUrl = (String) modelExtractedLinks.getValueAt(tblLinks.getSelectedRow(), 1);
                    txtUrl.setText(linkUrl);
                    performHttpRequest(linkUrl, "GET", false);
                    switchActiveCard(0);
                }
            }
        });
        JScrollPane scrollLinks = new JScrollPane(tblLinks);
        scrollLinks.setBorder(null);

        split.setTopComponent(createTitledCard("Thông tin Tài nguyên từ Server (HTTP Response Headers - HEAD / GET / POST)", scrollHeaders, null));
        split.setBottomComponent(createTitledCard("Danh sách Liên kết (<a>) & Hình ảnh (<img>/<imag>) trích xuất từ trang", scrollLinks, null));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(split, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildUrlInetCard() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        JPanel quickBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quickBar.setOpaque(false);
        JLabel lblQuick = new JLabel("Kiểm tra nhanh bài mẫu:");
        lblQuick.setFont(FONT_UI_BOLD);
        quickBar.add(lblQuick);

        JButton btnTestVku = createFlatButton("Mẫu http://vku.udn.vn (diachi & inet)", COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, false);
        btnTestVku.addActionListener(e -> runStandaloneUrlInetAnalysis("http://vku.udn.vn"));

        JButton btnTestTuoiTre = createFlatButton("Mẫu www.tuoitre.vn (getAllByName)", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, false);
        btnTestTuoiTre.addActionListener(e -> runStandaloneUrlInetAnalysis("https://www.tuoitre.vn"));

        JButton btnTestCurrent = createFlatButton("Phân tích URL đang mở trên thanh địa chỉ", COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, false);
        btnTestCurrent.addActionListener(e -> runStandaloneUrlInetAnalysis(txtUrl.getText()));

        quickBar.add(btnTestVku);
        quickBar.add(btnTestTuoiTre);
        quickBar.add(btnTestCurrent);
        panel.add(quickBar, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.52);
        split.setBorder(null);

        JPanel leftTables = new JPanel(new GridLayout(2, 1, 0, 8));
        leftTables.setOpaque(false);

        modelUrlProps = new DefaultTableModel(new String[]{"Phương thức java.net.URL", "Giá trị phân tích"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable tblUrl = new JTable(modelUrlProps);
        styleTable(tblUrl);
        JScrollPane scrollUrl = new JScrollPane(tblUrl);
        scrollUrl.setBorder(null);

        modelInetIps = new DefaultTableModel(new String[]{"Hạng mục java.net.InetAddress", "Kết quả phân giải IP / HostName"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable tblInet = new JTable(modelInetIps);
        styleTable(tblInet);
        JScrollPane scrollInet = new JScrollPane(tblInet);
        scrollInet.setBorder(null);

        leftTables.add(createTitledCard("1. Bảng thuộc tính java.net.URL (Bài mẫu diachi.java)", scrollUrl, null));
        leftTables.add(createTitledCard("2. Bảng tra cứu java.net.InetAddress (Bài mẫu inet.java)", scrollInet, null));

        txtUrlInetReport = new JTextArea();
        txtUrlInetReport.setEditable(false);
        txtUrlInetReport.setFont(FONT_MONO);
        txtUrlInetReport.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane scrollReport = new JScrollPane(txtUrlInetReport);
        scrollReport.setBorder(null);

        split.setLeftComponent(leftTables);
        split.setRightComponent(createTitledCard("Báo cáo chi tiết chuẩn theo diachi.java & inet.java", scrollReport, null));
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildStorageCard() {
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setResizeWeight(0.52);
        mainSplit.setBorder(null);

        JSplitPane topTables = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        topTables.setResizeWeight(0.38);
        topTables.setBorder(null);

        modelBookmarks = new DefaultTableModel(new String[]{"Mã", "Tài khoản", "Tiêu đề", "URL", "Thời gian lưu"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable tblBm = new JTable(modelBookmarks);
        styleTable(tblBm);
        tblBm.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblBm.getSelectedRow() != -1) {
                    String bmUrl = (String) modelBookmarks.getValueAt(tblBm.getSelectedRow(), 3);
                    txtUrl.setText(bmUrl);
                    performHttpRequest(bmUrl, "GET", false);
                    switchActiveCard(0);
                }
            }
        });
        JScrollPane scrollBm = new JScrollPane(tblBm);
        scrollBm.setBorder(null);

        JButton btnDelBm = createFlatButton("Xóa Bookmark đang chọn", COL_SECONDARY_BG, COL_DANGER, COL_NAV_BG, false);
        btnDelBm.addActionListener(e -> {
            int row = tblBm.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 Bookmark để xóa!");
                return;
            }
            String bmId = (String) modelBookmarks.getValueAt(row, 0);
            fileStore.deleteBookmark(currentUser.username, bmId);
            refreshBookmarksAndHistoryViews();
        });

        modelHistory = new DefaultTableModel(new String[]{
                "Mã", "User", "Thời gian", "Method", "URL", "Status", "Độ dài", "<p>", "<div>", "<span>", "<img>", "Dữ liệu/File POST"
        }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable tblHis = new JTable(modelHistory);
        styleTable(tblHis);
        tblHis.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblHis.getSelectedRow() != -1) {
                    String hisUrl = (String) modelHistory.getValueAt(tblHis.getSelectedRow(), 4);
                    String hisMethod = (String) modelHistory.getValueAt(tblHis.getSelectedRow(), 3);
                    cbMethod.setSelectedItem(hisMethod);
                    txtUrl.setText(hisUrl);
                    performHttpRequest(hisUrl, hisMethod, false);
                    switchActiveCard(0);
                }
            }
        });
        JScrollPane scrollHis = new JScrollPane(tblHis);
        scrollHis.setBorder(null);

        JButton btnClearHis = createFlatButton("Xóa lịch sử Request trong file " + currentUser.accountFile.getName(), COL_SECONDARY_BG, COL_DANGER, COL_NAV_BG, false);
        btnClearHis.addActionListener(e -> {
            fileStore.clearRequestHistory(currentUser.username);
            refreshBookmarksAndHistoryViews();
        });

        topTables.setLeftComponent(createTitledCard("Bookmark của '" + currentUser.username + "' (Nhấp đúp để mở)", scrollBm, btnDelBm));
        topTables.setRightComponent(createTitledCard("Lịch sử Request & File gửi/nhận của '" + currentUser.username + "'", scrollHis, btnClearHis));

        txtRawDatabaseView = new JTextArea();
        txtRawDatabaseView.setEditable(false);
        txtRawDatabaseView.setFont(FONT_MONO);
        txtRawDatabaseView.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane scrollRaw = new JScrollPane(txtRawDatabaseView);
        scrollRaw.setBorder(null);

        JButton btnRefreshRaw = createFlatButton("Làm mới nội dung file " + currentUser.accountFile.getName(), COL_SECONDARY_BG, COL_SECONDARY_FG, COL_NAV_BG, false);
        btnRefreshRaw.addActionListener(e -> refreshBookmarksAndHistoryViews());

        mainSplit.setTopComponent(topTables);
        mainSplit.setBottomComponent(createTitledCard(
                "Nội dung thực tế File .txt riêng của tài khoản (" + currentUser.accountFile.getName() + ")",
                scrollRaw, btnRefreshRaw
        ));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(mainSplit, BorderLayout.CENTER);
        return wrapper;
    }

    private void performHttpRequest(String rawUrl, String method, boolean fromBackForward) {
        final String targetUrl = UrlInetHelper.normalizeUrl(rawUrl);
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ URL!");
            return;
        }
        txtUrl.setText(targetUrl);
        lblStatStatus.setText("Đang gửi " + method + "...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        final String postData = txtPostData.getText();
        final File postFile = selectedPostFile;

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            HttpProcessor.HttpResponseData httpRes;
            UrlInetHelper.UrlAnalysisResult urlRes;
            UrlInetHelper.InetAnalysisResult inetRes;

            @Override
            protected Void doInBackground() {
                urlRes = UrlInetHelper.analyzeUrl(targetUrl);
                inetRes = UrlInetHelper.analyzeInet(targetUrl);
                httpRes = HttpProcessor.executeRequest(targetUrl, method, postData, postFile);
                return null;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                updateUiWithResponse(targetUrl, method, httpRes, urlRes, inetRes, fromBackForward);
            }
        };
        worker.execute();
    }

    private void updateUiWithResponse(String requestedUrl, String method,
                                      HttpProcessor.HttpResponseData httpRes,
                                      UrlInetHelper.UrlAnalysisResult urlRes,
                                      UrlInetHelper.InetAnalysisResult inetRes,
                                      boolean fromBackForward) {
        populateUrlInetTables(urlRes, inetRes);

        if (httpRes.errorMessage != null) {
            lblStatStatus.setText("Lỗi kết nối (" + method + ")");
            lblStatLength.setText("0 ký tự");
            lblStatP.setText("0");
            lblStatDiv.setText("0");
            lblStatSpan.setText("0");
            lblStatImg.setText("0");

            String errHtml = "<html><body style='font-family:Segoe UI,sans-serif; padding:20px; color:#b91c1c;'>"
                    + "<h2>Không thể tải URL: " + requestedUrl + "</h2>"
                    + "<p><b>Chi tiết lỗi:</b> " + httpRes.errorMessage + "</p>"
                    + "<p>Gợi ý: Bạn có thể bấm nút <b>Trang Demo (GET/POST/HEAD)</b> trên thanh công cụ để kiểm thử ngay với Server nội bộ.</p>"
                    + "</body></html>";
            htmlRenderPane.setText(errHtml);
            txtHtmlSource.setText(errHtml);
            fileStore.logRequestAndFile(currentUser.username, method, requestedUrl, -1, 0, 0, 0, 0, 0,
                    "Loi: " + httpRes.errorMessage);
            refreshBookmarksAndHistoryViews();
            return;
        }

        if (!fromBackForward) {
            while (backForwardUrls.size() > historyIndex + 1) {
                backForwardUrls.remove(backForwardUrls.size() - 1);
            }
            backForwardUrls.add(httpRes.finalUrl);
            historyIndex = backForwardUrls.size() - 1;
        }
        btnBack.setEnabled(historyIndex > 0);
        btnForward.setEnabled(historyIndex < backForwardUrls.size() - 1);

        lblStatStatus.setText(method + " | HTTP " + httpRes.statusCode + " (" + httpRes.elapsedMs + " ms)");

        if ("HEAD".equalsIgnoreCase(method)) {
            String lenInfo = httpRes.contentLengthHeader >= 0
                    ? httpRes.contentLengthHeader + " bytes (Header)"
                    : "HEAD (Chỉ lấy Header)";
            lblStatLength.setText(lenInfo);
            lblStatP.setText("N/A (HEAD)");
            lblStatDiv.setText("N/A (HEAD)");
            lblStatSpan.setText("N/A (HEAD)");
            lblStatImg.setText("N/A (HEAD)");
        } else {
            HttpProcessor.HtmlTagStats st = httpRes.tagStats;
            lblStatLength.setText(st.charLength + " ký tự (" + st.byteLength + " B)");
            lblStatP.setText(String.valueOf(st.pCount));
            lblStatDiv.setText(String.valueOf(st.divCount));
            lblStatSpan.setText(String.valueOf(st.spanCount));
            lblStatImg.setText(st.imgCount + " (<img>:" + (st.imgCount - st.imagLiteralCount) + ", <imag>:" + st.imagLiteralCount + ")");
        }

        lastHtmlContent = httpRes.htmlBody;
        txtHtmlSource.setText(httpRes.htmlBody);
        txtHtmlSource.setCaretPosition(0);

        try {
            htmlRenderPane.setContentType("text/html; charset=UTF-8");
            htmlRenderPane.setText(sanitizeHtmlForSwing(httpRes.htmlBody));
            htmlRenderPane.setCaretPosition(0);
        } catch (Exception ex) {
            htmlRenderPane.setText("<html><body><pre>" + httpRes.htmlBody + "</pre></body></html>");
        }

        modelHeaders.setRowCount(0);
        modelHeaders.addRow(new Object[]{"URL Cuối cùng (Final URL)", httpRes.finalUrl});
        modelHeaders.addRow(new Object[]{"Phương thức (Request Method)", httpRes.requestMethod});
        modelHeaders.addRow(new Object[]{"Mã phản hồi (Status Code)", httpRes.statusCode + " " + httpRes.statusMessage});
        modelHeaders.addRow(new Object[]{"Loại tài nguyên (Content-Type)", httpRes.contentType});
        modelHeaders.addRow(new Object[]{"Độ dài Header (Content-Length)", httpRes.contentLengthHeader});
        modelHeaders.addRow(new Object[]{"Server", httpRes.serverName});
        modelHeaders.addRow(new Object[]{"Last-Modified", httpRes.lastModified});
        modelHeaders.addRow(new Object[]{"Thời gian phản hồi", httpRes.elapsedMs + " ms"});
        for (Map.Entry<String, String> h : httpRes.headers.entrySet()) {
            modelHeaders.addRow(new Object[]{h.getKey(), h.getValue()});
        }

        modelExtractedLinks.setRowCount(0);
        for (String link : httpRes.tagStats.extractedLinks) {
            modelExtractedLinks.addRow(new Object[]{"Liên kết <a href>", link});
        }
        for (String img : httpRes.tagStats.extractedImages) {
            modelExtractedLinks.addRow(new Object[]{"Hình ảnh <img src>", img});
        }

        String postSummary = "Khong co";
        if ("POST".equalsIgnoreCase(method)) {
            postSummary = "Form: " + txtPostData.getText();
            if (selectedPostFile != null) {
                postSummary += " | File: " + selectedPostFile.getName() + " (" + selectedPostFile.length() + "B)";
            }
        }

        HttpProcessor.HtmlTagStats st = httpRes.tagStats;
        fileStore.logRequestAndFile(
                currentUser.username, method, httpRes.finalUrl, httpRes.statusCode,
                st.charLength, st.pCount, st.divCount, st.spanCount, st.imgCount, postSummary
        );
        refreshBookmarksAndHistoryViews();
    }

    private String sanitizeHtmlForSwing(String rawHtml) {
        if (rawHtml == null) {
            return "";
        }
        return rawHtml
                .replaceAll("(?is)<script.*?>.*?</script>", "")
                .replaceAll("(?is)<noscript.*?>.*?</noscript>", "")
                .replaceAll("(?is)<svg.*?>.*?</svg>", "");
    }

    private void runStandaloneUrlInetAnalysis(String inputUrl) {
        UrlInetHelper.UrlAnalysisResult urlRes = UrlInetHelper.analyzeUrl(inputUrl);
        UrlInetHelper.InetAnalysisResult inetRes = UrlInetHelper.analyzeInet(inputUrl);
        populateUrlInetTables(urlRes, inetRes);
        switchActiveCard(3);
    }

    private void populateUrlInetTables(UrlInetHelper.UrlAnalysisResult urlRes,
                                       UrlInetHelper.InetAnalysisResult inetRes) {
        modelUrlProps.setRowCount(0);
        if (urlRes.valid) {
            for (Map.Entry<String, String> e : urlRes.properties.entrySet()) {
                modelUrlProps.addRow(new Object[]{e.getKey(), e.getValue()});
            }
        } else {
            modelUrlProps.addRow(new Object[]{"Lỗi phân tích URL", urlRes.errorMessage});
        }

        modelInetIps.setRowCount(0);
        modelInetIps.addRow(new Object[]{"LocalHost Name (getLocalHost)", inetRes.localHostName});
        modelInetIps.addRow(new Object[]{"LocalHost IP (getLocalHost)", inetRes.localHostIp});
        if (inetRes.errorMessage == null) {
            modelInetIps.addRow(new Object[]{"Target HostName (getByName)", inetRes.targetHostName});
            modelInetIps.addRow(new Object[]{"Target IP (getByName)", inetRes.targetHostIp});
            modelInetIps.addRow(new Object[]{"Canonical HostName", inetRes.canonicalHostName});
            for (String ipItem : inetRes.allAddresses) {
                modelInetIps.addRow(new Object[]{"getAllByName(\"" + inetRes.targetHostInput + "\")", ipItem});
            }
        } else {
            modelInetIps.addRow(new Object[]{"Lỗi phân giải DNS", inetRes.errorMessage});
        }

        txtUrlInetReport.setText(UrlInetHelper.formatCombinedReport(urlRes, inetRes));
        txtUrlInetReport.setCaretPosition(0);
    }

    private void refreshBookmarksAndHistoryViews() {
        modelBookmarks.setRowCount(0);
        for (BrowserFileStore.BookmarkRecord bm : fileStore.getBookmarks(currentUser.username)) {
            modelBookmarks.addRow(new Object[]{bm.id, bm.username, bm.title, bm.url, bm.createdAt});
        }

        modelHistory.setRowCount(0);
        List<BrowserFileStore.RequestLogRecord> logs = fileStore.getRequestLogs(currentUser.username);
        for (int i = logs.size() - 1; i >= 0; i--) {
            BrowserFileStore.RequestLogRecord r = logs.get(i);
            modelHistory.addRow(new Object[]{
                    r.id, r.username, r.timestamp, r.method, r.url, r.statusCode,
                    r.htmlLength, r.pCount, r.divCount, r.spanCount, r.imgCount, r.attachedFileOrData
            });
        }

        txtRawDatabaseView.setText(fileStore.readRawAccountFileText(currentUser.username));
        txtRawDatabaseView.setCaretPosition(0);
    }

    private void navigateBack() {
        if (historyIndex > 0) {
            historyIndex--;
            String url = backForwardUrls.get(historyIndex);
            txtUrl.setText(url);
            performHttpRequest(url, "GET", true);
        }
    }

    private void navigateForward() {
        if (historyIndex < backForwardUrls.size() - 1) {
            historyIndex++;
            String url = backForwardUrls.get(historyIndex);
            txtUrl.setText(url);
            performHttpRequest(url, "GET", true);
        }
    }

    private void choosePostAttachment() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file đính kèm để gửi qua POST Request");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedPostFile = chooser.getSelectedFile();
            lblAttachedFile.setText(selectedPostFile.getName() + " (" + selectedPostFile.length() + " B)");
        }
    }

    private void saveCurrentBookmark() {
        String currentUrl = txtUrl.getText().trim();
        if (currentUrl.isEmpty()) {
            return;
        }
        String title = JOptionPane.showInputDialog(this, "Nhập tên gợi nhớ cho Bookmark:", currentUrl);
        if (title != null && !title.trim().isEmpty()) {
            fileStore.addOrUpdateBookmark(currentUser.username, title.trim(), currentUrl);
            refreshBookmarksAndHistoryViews();
            JOptionPane.showMessageDialog(this,
                    "Đã lưu Bookmark và cập nhật trực tiếp vào file riêng: " + currentUser.accountFile.getName());
        }
    }

    private void saveHtmlToFile() {
        if (lastHtmlContent == null || lastHtmlContent.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có nội dung HTML để lưu!");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("downloaded_page.html"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = chooser.getSelectedFile();
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(out), StandardCharsets.UTF_8)) {
                writer.write(lastHtmlContent);
                fileStore.logRequestAndFile(currentUser.username, "SAVE_FILE", txtUrl.getText(),
                        200, lastHtmlContent.length(), 0, 0, 0, 0,
                        "Da luu trang HTML ra file: " + out.getName());
                refreshBookmarksAndHistoryViews();
                JOptionPane.showMessageDialog(this, "Đã lưu file HTML tại:\n" + out.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi lưu file: " + ex.getMessage());
            }
        }
    }

    private void openInSystemBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(UrlInetHelper.normalizeUrl(url)));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở trình duyệt hệ thống: " + ex.getMessage());
        }
    }

    private void showChangePasswordDialog() {
        JPasswordField txtNewPass = new JPasswordField(16);
        JTextField txtNewName = new JTextField(currentUser.fullName, 16);
        styleTextField(txtNewPass);
        styleTextField(txtNewName);

        JPanel p = new JPanel(new GridLayout(2, 2, 8, 8));
        p.add(new JLabel("Họ và tên hiển thị:"));
        p.add(txtNewName);
        p.add(new JLabel("Mật khẩu mới:"));
        p.add(txtNewPass);

        if (JOptionPane.showConfirmDialog(this, p, "Cập nhật thông tin tài khoản",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            String newPass = new String(txtNewPass.getPassword()).trim();
            String newName = txtNewName.getText().trim();
            if (fileStore.updateAccountPassword(currentUser.username, newPass, newName)) {
                currentUser.fullName = newName.isEmpty() ? currentUser.fullName : newName;
                refreshBookmarksAndHistoryViews();
                JOptionPane.showMessageDialog(this,
                        "Đã cập nhật trực tiếp thông tin tài khoản trong file riêng: " + currentUser.accountFile.getName());
            }
        }
    }

    private void handleLogout() {
        dispose();
        showLoginAndLaunch(fileStore);
    }

    // =========================================================================
    // HỘP THOẠI ĐĂNG NHẬP & ĐĂNG KÝ ĐÃ THIẾT KẾ LẠI (KHÔNG DÙNG JTABBEDPANE RỐI MẮT)
    // =========================================================================
    public static class AuthDialog extends JDialog {
        private final BrowserFileStore store;
        private BrowserFileStore.AccountRecord authenticatedUser;
        private CardLayout authCardLayout;
        private JPanel formContainer;
        private JButton btnSwitchLogin;
        private JButton btnSwitchRegister;

        public AuthDialog(BrowserFileStore store) {
            super((JFrame) null, "Đăng nhập / Đăng ký - VKU Simple Web Browser (Nhóm 1)", true);
            this.store = store;
            setSize(560, 480);
            setResizable(false);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            buildAuthUi();
        }

        private void buildAuthUi() {
            JPanel root = new JPanel(new BorderLayout(0, 0));
            root.setBackground(COL_BG_APP);

            // Banner tiêu đề trên cùng
            JPanel topBanner = new JPanel(new BorderLayout(0, 4));
            topBanner.setBackground(COL_HEADER);
            topBanner.setBorder(new EmptyBorder(18, 24, 18, 24));

            JLabel lblTitle = new JLabel("TRÌNH DUYỆT WEB ĐƠN GIẢN - NHÓM 1 (VKU)", SwingConstants.CENTER);
            lblTitle.setFont(FONT_HEADER);
            lblTitle.setForeground(Color.WHITE);

            JLabel lblSub = new JLabel("Hỗ trợ HTTP GET, POST, HEAD | Phân tích HTML, URL & InetAddress", SwingConstants.CENTER);
            lblSub.setFont(FONT_SMALL);
            lblSub.setForeground(new Color(148, 163, 184));

            topBanner.add(lblTitle, BorderLayout.NORTH);
            topBanner.add(lblSub, BorderLayout.SOUTH);
            root.add(topBanner, BorderLayout.NORTH);

            // Khối Card chính ở giữa
            JPanel centerOuter = new JPanel(new BorderLayout());
            centerOuter.setOpaque(false);
            centerOuter.setBorder(new EmptyBorder(16, 28, 12, 28));

            JPanel cardBox = new JPanel(new BorderLayout(0, 0));
            cardBox.setBackground(COL_SURFACE);
            cardBox.setBorder(new LineBorder(COL_BORDER, 1, true));

            // Thanh chuyển đổi Đăng nhập / Đăng ký liền mạch (Segmented Bar)
            JPanel modeBar = new JPanel(new GridLayout(1, 2, 0, 0));
            modeBar.setBackground(new Color(241, 245, 249));
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

            // Footer gọn gàng, không bị cắt chữ
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            footer.setBorder(new EmptyBorder(0, 28, 14, 28));
            JLabel lblFooter = new JLabel(
                    "Lưu trữ: Mỗi tài khoản có 1 file user_<username>.txt riêng chứa toàn bộ dữ liệu.",
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

                btnSwitchRegister.setBackground(new Color(241, 245, 249));
                btnSwitchRegister.setForeground(COL_TEXT_MUTED);
                btnSwitchRegister.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, COL_BORDER));
            } else {
                btnSwitchRegister.setBackground(COL_SURFACE);
                btnSwitchRegister.setForeground(COL_SUCCESS);
                btnSwitchRegister.setBorder(BorderFactory.createMatteBorder(3, 1, 0, 0, COL_SUCCESS));

                btnSwitchLogin.setBackground(new Color(241, 245, 249));
                btnSwitchLogin.setForeground(COL_TEXT_MUTED);
                btnSwitchLogin.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, COL_BORDER));
            }
        }

        private JPanel buildLoginForm() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(COL_SURFACE);
            p.setBorder(new EmptyBorder(20, 32, 20, 32));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(7, 6, 7, 6);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JTextField txtLoginUser = new JTextField("admin");
            JPasswordField txtLoginPass = new JPasswordField("123456");
            styleTextField(txtLoginUser);
            styleTextField(txtLoginPass);
            txtLoginUser.setPreferredSize(new Dimension(260, 36));
            txtLoginPass.setPreferredSize(new Dimension(260, 36));

            JLabel lblU = new JLabel("Tên đăng nhập:");
            lblU.setFont(FONT_UI_BOLD);
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
            p.add(lblU, gbc);

            gbc.gridx = 1; gbc.weightx = 0.7;
            p.add(txtLoginUser, gbc);

            JLabel lblP = new JLabel("Mật khẩu:");
            lblP.setFont(FONT_UI_BOLD);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
            p.add(lblP, gbc);

            gbc.gridx = 1; gbc.weightx = 0.7;
            p.add(txtLoginPass, gbc);

            JButton btnLogin = createFlatButton(
                    "Đăng nhập (Nạp file user_<username>.txt)",
                    COL_PRIMARY, Color.WHITE, COL_PRIMARY_HOVER, true
            );
            btnLogin.setPreferredSize(new Dimension(320, 40));
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
            gbc.insets = new Insets(14, 6, 8, 6);
            p.add(btnLogin, gbc);

            JLabel lblHint = new JLabel(
                    "<html><div style='text-align:center; color:#475569;'>"
                            + "Tài khoản mẫu: <b>admin / 123456</b> (<code>user_admin.txt</code>)<br>"
                            + "hoặc <b>sinhvien / 123456</b> (<code>user_sinhvien.txt</code>)</div></html>",
                    SwingConstants.CENTER
            );
            lblHint.setFont(FONT_SMALL);
            gbc.gridy = 3;
            gbc.insets = new Insets(4, 6, 0, 6);
            p.add(lblHint, gbc);

            btnLogin.addActionListener(e -> {
                String u = txtLoginUser.getText().trim();
                String pw = new String(txtLoginPass.getPassword()).trim();
                BrowserFileStore.AccountRecord acc = store.loginAndUpdateTimestamp(u, pw);
                if (acc != null) {
                    authenticatedUser = acc;
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Sai tên đăng nhập hoặc mật khẩu!", "Lỗi xác thực", JOptionPane.ERROR_MESSAGE);
                }
            });

            return p;
        }

        private JPanel buildRegisterForm() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(COL_SURFACE);
            p.setBorder(new EmptyBorder(16, 32, 16, 32));
            GridBagConstraints rg = new GridBagConstraints();
            rg.insets = new Insets(6, 6, 6, 6);
            rg.fill = GridBagConstraints.HORIZONTAL;

            JTextField txtRegUser = new JTextField();
            JPasswordField txtRegPass = new JPasswordField();
            JTextField txtRegName = new JTextField();
            styleTextField(txtRegUser);
            styleTextField(txtRegPass);
            styleTextField(txtRegName);
            txtRegUser.setPreferredSize(new Dimension(260, 34));
            txtRegPass.setPreferredSize(new Dimension(260, 34));
            txtRegName.setPreferredSize(new Dimension(260, 34));

            JLabel l1 = new JLabel("Tên tài khoản mới:");
            l1.setFont(FONT_UI_BOLD);
            rg.gridx = 0; rg.gridy = 0; rg.weightx = 0.35;
            p.add(l1, rg);
            rg.gridx = 1; rg.weightx = 0.65;
            p.add(txtRegUser, rg);

            JLabel l2 = new JLabel("Mật khẩu:");
            l2.setFont(FONT_UI_BOLD);
            rg.gridx = 0; rg.gridy = 1; rg.weightx = 0.35;
            p.add(l2, rg);
            rg.gridx = 1; rg.weightx = 0.65;
            p.add(txtRegPass, rg);

            JLabel l3 = new JLabel("Họ và tên sinh viên:");
            l3.setFont(FONT_UI_BOLD);
            rg.gridx = 0; rg.gridy = 2; rg.weightx = 0.35;
            p.add(l3, rg);
            rg.gridx = 1; rg.weightx = 0.65;
            p.add(txtRegName, rg);

            JButton btnRegister = createFlatButton(
                    "Tạo tài khoản mới (Khởi tạo file user_<username>.txt)",
                    COL_SUCCESS, Color.WHITE, COL_SUCCESS_HOVER, true
            );
            btnRegister.setPreferredSize(new Dimension(320, 40));
            rg.gridx = 0; rg.gridy = 3; rg.gridwidth = 2; rg.weightx = 1.0;
            rg.insets = new Insets(14, 6, 4, 6);
            p.add(btnRegister, rg);

            btnRegister.addActionListener(e -> {
                String u = txtRegUser.getText().trim();
                String pw = new String(txtRegPass.getPassword()).trim();
                String n = txtRegName.getText().trim();
                String res = store.registerAccount(u, pw, n);
                if ("OK".equals(res)) {
                    File f = store.getAccountFile(u);
                    JOptionPane.showMessageDialog(this,
                            "Đăng ký thành công! Đã tạo file riêng: " + f.getName()
                                    + "\nHệ thống sẽ tự động đăng nhập.");
                    authenticatedUser = store.loginAndUpdateTimestamp(u, pw);
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, res, "Lỗi đăng ký", JOptionPane.WARNING_MESSAGE);
                }
            });

            return p;
        }
    }

    private static void showLoginAndLaunch(BrowserFileStore store) {
        AuthDialog dialog = new AuthDialog(store);
        dialog.setVisible(true);
        if (dialog.authenticatedUser != null) {
            BrowserMain frame = new BrowserMain(store, dialog.authenticatedUser);
            frame.setVisible(true);
        } else {
            System.exit(0);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            BrowserFileStore store = new BrowserFileStore(STORAGE_DIR);
            showLoginAndLaunch(store);
        });
    }
}