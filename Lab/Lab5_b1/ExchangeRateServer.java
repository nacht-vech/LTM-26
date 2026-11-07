import java.awt.BorderLayout;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class ExchangeRateServer extends JFrame {
    private JTextArea logArea;
    private static final int PORT = 2345;

    public ExchangeRateServer() {
        setTitle("Exchange Rate Server");
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        add(new JLabel("  Nhật ký hoạt động của Server:", SwingConstants.LEFT), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        startServerThread();
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startServerThread() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(PORT)) {
                appendLog("Server đang chạy và lắng nghe tại cổng " + PORT + "...");
                byte[] receiveData = new byte[1024];
                Random random = new Random();
                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    double tokyoRate = 130.0 + random.nextDouble() * 5.0;
                    double newYorkRate = 1.0 + random.nextDouble() * 0.1;
                    double hongKongRate = 7.8 + random.nextDouble() * 0.2;
                    String currentTime = formatter.format(new Date());

                    String responseStr = String.format("[%s] Tokyo: %.2f | NewYork: %.2f | HongKong: %.2f",
                            currentTime, tokyoRate, newYorkRate, hongKongRate);
                    
                    byte[] sendData = responseStr.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, 
                            receivePacket.getAddress(), receivePacket.getPort());
                    
                    socket.send(sendPacket);
                    
                    appendLog("Client: " + receivePacket.getAddress().getHostAddress() + " - Gửi: " + responseStr);
                }
            } catch (Exception e) {
                appendLog("Lỗi Server: " + e.getMessage());
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ExchangeRateServer().setVisible(true);
        });
    }
}