# Ứng Dụng Đồng Bộ Dữ Liệu Active-Active Qua Java RMI (MVC + File Storage + LWW)

Mã nguồn này được xây dựng bám sát 100% thiết kế trong tài liệu `RMI_Active_Active_Sync_Guide.md`.

## 1. Cấu trúc thư mục

```text
rmi-sync-system/
├── shared/                             # Module dùng chung (build ra dist/shared.jar)
│   ├── model/
│   │   ├── Record.java                 # Thực thể dữ liệu + logic so sánh Last-Write-Wins
│   │   └── SyncAction.java             # Enum: CREATE, UPDATE, DELETE
│   └── service/
│       ├── IClientService.java         # Remote interface cho Client gọi
│       └── ISyncService.java           # Remote interface cho 2 Server đồng bộ P2P
│
├── server/                             # Module Server (build ra dist/server.jar)
│   ├── config/
│   │   └── ServerConfig.java           # Đọc cấu hình từ file .properties hoặc CLI
│   ├── storage/
│   │   ├── FileStorageManager.java     # Lưu/đọc file nguyên tử (.tmp -> .dat) + Khóa luồng + LWW
│   │   └── SyncQueueManager.java       # Lưu hàng đợi ngoại tuyến (data/queue/*.sync)
│   ├── service/
│   │   ├── ClientServiceImpl.java      # Xử lý request từ Client & kích hoạt đồng bộ
│   │   └── SyncServiceImpl.java        # Nhận đồng bộ từ đối tác & chặn vòng lặp Echo Sync
│   ├── sync/
│   │   ├── PeerSyncEngine.java         # Quản lý kết nối P2P, đẩy hàng đợi & đối soát khởi động
│   │   └── HeartbeatTask.java          # Định kỳ 3s ping đối tác và flush queue
│   └── ServerApp.java                  # Điểm khởi chạy Server
│
├── client/                             # Module Client Java Swing MVC (build ra dist/client.jar)
│   ├── model/
│   │   └── ClientModel.java            # AbstractTableModel quản lý dữ liệu trên bảng
│   ├── view/
│   │   ├── MainFrame.java              # Giao diện chính Swing
│   │   └── RecordDialog.java           # Hộp thoại Thêm/Sửa (hỗ trợ chỉnh offset ms để test LWW)
│   ├── controller/
│   │   └── MainController.java         # Điều phối sự kiện bằng SwingWorker bất đồng bộ
│   ├── service/
│   │   └── RmiClientManager.java       # Quản lý kết nối RMI & tự động Failover chuyển Server
│   ├── ClientApp.java                  # Điểm khởi chạy giao diện Swing
│   └── TestScenarioRunner.java         # Công cụ chạy kiểm thử tự động bằng CLI
│
├── config/
│   ├── server1.properties              # Cấu hình Server 1
│   ├── server2.properties              # Cấu hình Server 2
│   └── client.properties               # Cấu hình Client
└── build.sh / build.bat                # Script biên dịch và đóng gói JAR
```

## 2. Hướng dẫn Biên dịch & Chạy thử

### Bước 1: Biên dịch mã nguồn
- **Trên Windows**: Chạy 
`if (Test-Path bin) { Remove-Item -Recurse -Force bin }
mkdir bin
javac -encoding UTF-8 -d bin shared\model\*.java shared\service\*.java server\config\*.java server\storage\*.java server\sync\*.java server\service\*.java server\*.java client\model\*.java client\service\*.java client\view\*.java client\controller\*.java client\*.java RunServer.java RunClient.java`

### Bước 2: chạy server/client
- sever: java -cp bin RunServer
- client: java -cp bin RunClient