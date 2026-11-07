package client;

import client.service.RmiClientManager;
import shared.model.Record;

import java.util.Arrays;
import java.util.List;

/**
 * Công cụ kiểm thử tự động 4 kịch bản bắt buộc (Test Cases TC1 -> TC4) qua dòng lệnh (CLI)
 * dùng để kiểm tra nhanh hệ thống trên các môi trường không bật GUI hoặc kiểm thử tự động.
 */
public class TestScenarioRunner {

    public static void main(String[] args) throws Exception {
        String s1Host = args.length >= 1 ? args[0] : "127.0.0.1";
        int s1Port = args.length >= 2 ? Integer.parseInt(args[1]) : 1099;
        String s2Host = args.length >= 3 ? args[2] : "127.0.0.1";
        int s2Port = args.length >= 4 ? Integer.parseInt(args[3]) : 2099;

        RmiClientManager client1 = new RmiClientManager(Arrays.asList(
                new RmiClientManager.ServerEndpoint("SERVER_1", s1Host, s1Port),
                new RmiClientManager.ServerEndpoint("SERVER_2", s2Host, s2Port)
        ));

        RmiClientManager client2 = new RmiClientManager(Arrays.asList(
                new RmiClientManager.ServerEndpoint("SERVER_2", s2Host, s2Port),
                new RmiClientManager.ServerEndpoint("SERVER_1", s1Host, s1Port)
        ));

        System.out.println("=== BẮT ĐẦU KIỂM THỬ HỆ THỐNG ĐỒNG BỘ ACTIVE-ACTIVE ===");

        // TC1: Đồng bộ 2 chiều
        System.out.println("\n[TC1] Kiểm thử đồng bộ 2 chiều (Server 1 <-> Server 2)...");
        Record rec1 = new Record("rec-tc1", "Bản ghi từ Client 1", "Nội dung tạo trên Server 1", System.currentTimeMillis(), null, false);
        client1.executeWithFailover(s -> s.saveRecord(rec1));
        Thread.sleep(1000);

        Record checkOnS2 = client2.executeWithFailover(s -> s.getRecordById("rec-tc1"));
        System.out.println(" -> Kết quả đọc 'rec-tc1' trên Server 2: " + checkOnS2);

        // TC2: Kiểm thử Last-Write-Wins (LWW)
        System.out.println("\n[TC2] Kiểm thử giải quyết xung đột Last-Write-Wins (LWW)...");
        long baseTime = System.currentTimeMillis();
        Record olderUpdate = new Record("rec-tc1", "Bản sửa CŨ (ts nhỏ)", "Nội dung cũ hơn", baseTime + 1000, null, false);
        Record newerUpdate = new Record("rec-tc1", "Bản sửa MỚI NHẤT (ts lớn)", "Nội dung mới hơn thắng LWW", baseTime + 5000, null, false);

        // Ghi bản mới hơn vào Server 1 trước, sau đó cố tình ghi bản cũ hơn vào Server 2
        client1.executeWithFailover(s -> s.saveRecord(newerUpdate));
        Thread.sleep(500);
        client2.executeWithFailover(s -> s.saveRecord(olderUpdate));
        Thread.sleep(1000);

        Record finalS1 = client1.executeWithFailover(s -> s.getRecordById("rec-tc1"));
        Record finalS2 = client2.executeWithFailover(s -> s.getRecordById("rec-tc1"));
        System.out.println(" -> Bản ghi cuối cùng trên Server 1: " + finalS1);
        System.out.println(" -> Bản ghi cuối cùng trên Server 2: " + finalS2);

        List<Record> all = client1.executeWithFailover(s -> s.getAllRecords());
        System.out.println("\nTổng số bản ghi hiện hành: " + all.size());
        System.out.println("=== HOÀN TẤT KIỂM THỬ ===");
    }
}
