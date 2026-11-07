import java.awt.BorderLayout;
import java.awt.Font;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class ExchangeRateTable extends JFrame {
    private JTextArea displayArea;
    private JButton toggleBtn;
    private boolean isRunning = false;
    private Thread clientThread;

    private static final int SERVER_PORT = 2345;
    private static final String SERVER_IP = "192.168.107.210";

    public ExchangeRateTable() {
        setTitle("Exchange Rate Client");
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(displayArea);

        toggleBtn = new JButton("Bắt đầu cập nhật");
        toggleBtn.addActionListener(e -> toggleUpdate());

        JPanel topPanel = new JPanel();
        topPanel.add(toggleBtn);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void appendData(String message) {
        SwingUtilities.invokeLater(() -> {
            displayArea.append(message + "\n");
            displayArea.setCaretPosition(displayArea.getDocument().getLength());
        });
    }

    private void toggleUpdate() {
        if (!isRunning) {
            isRunning = true;
            toggleBtn.setText("Dừng cập nhật");
            startClientThread();
        } else {
            isRunning = false;
            toggleBtn.setText("Bắt đầu cập nhật");
            if (clientThread != null) {
                clientThread.interrupt();
            }
            appendData("--- Đã dừng cập nhật ---");
        }
    }

    private void startClientThread() {
        clientThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress serverAddress = InetAddress.getByName(SERVER_IP);
                String requestStr = "GET";
                byte[] sendData = requestStr.getBytes();

                appendData("--- Bắt đầu lấy dữ liệu mỗi giây ---");

                while (isRunning) {
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                    socket.send(sendPacket);

                    byte[] receiveData = new byte[1024];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    appendData(response);

                    Thread.sleep(1000); 
                }
            } catch (InterruptedException ie) {
            } catch (Exception e) {
                appendData("Lỗi kết nối: " + e.getMessage());
            }
        });
        clientThread.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ExchangeRateTable().setVisible(true);
        });
    }
}