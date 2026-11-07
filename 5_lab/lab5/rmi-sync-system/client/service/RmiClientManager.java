package client.service;

import shared.service.IClientService;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý kết nối RMI từ Client (trên máy ảo VM) tới 2 Server vật lý.
 * Hỗ trợ cơ chế tự động chuyển đổi dự phòng (Failover) khi Server đang kết nối bị sập.
 */
public class RmiClientManager {

    public static class ServerEndpoint {
        private final String name;
        private final String host;
        private final int port;

        public ServerEndpoint(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Override
        public String toString() {
            return String.format("%s (%s:%d)", name, host, port);
        }
    }

    @FunctionalInterface
    public interface RemoteCall<T> {
        T execute(IClientService service) throws RemoteException;
    }

    private final List<ServerEndpoint> endpoints = new ArrayList<>();
    private int currentIndex = 0;
    private IClientService cachedService;

    public RmiClientManager(List<ServerEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("Danh sách địa chỉ Server không được rỗng.");
        }
        this.endpoints.addAll(endpoints);
    }

    /**
     * Thực thi một lời gọi RMI với cơ chế tự động Failover sang Server còn lại nếu gặp lỗi kết nối.
     */
    public synchronized <T> T executeWithFailover(RemoteCall<T> call) throws RemoteException {
        int attempts = 0;
        RemoteException lastException = null;

        while (attempts < endpoints.size()) {
            ServerEndpoint currentEndpoint = endpoints.get(currentIndex);
            try {
                IClientService service = getOrConnect(currentEndpoint);
                return call.execute(service);
            } catch (RemoteException ex) {
                lastException = ex;
                cachedService = null;
                System.err.printf("[CLIENT-FAILOVER] Lỗi kết nối tới %s: %s. Đang chuyển sang Server dự phòng...%n",
                        currentEndpoint, ex.getMessage());
                switchToNextServer();
                attempts++;
            }
        }

        throw new RemoteException("Cả 2 Server hiện đều không phản hồi. Vui lòng kiểm tra kết nối mạng/Server.", lastException);
    }

    /**
     * Chủ động chuyển đổi sang Server mong muốn từ giao diện người dùng (để test Active-Active).
     */
    public synchronized void selectServer(int index) {
        if (index >= 0 && index < endpoints.size()) {
            this.currentIndex = index;
            this.cachedService = null;
        }
    }

    public synchronized void switchToNextServer() {
        currentIndex = (currentIndex + 1) % endpoints.size();
        cachedService = null;
    }

    private IClientService getOrConnect(ServerEndpoint endpoint) throws RemoteException {
        if (cachedService != null) {
            return cachedService;
        }
        try {
            Registry registry = LocateRegistry.getRegistry(endpoint.getHost(), endpoint.getPort());
            cachedService = (IClientService) registry.lookup("ClientService");
            return cachedService;
        } catch (Exception e) {
            throw new RemoteException("Không thể lookup ClientService tại " + endpoint, e);
        }
    }

    public synchronized ServerEndpoint getCurrentEndpoint() {
        return endpoints.get(currentIndex);
    }

    public synchronized int getCurrentIndex() {
        return currentIndex;
    }

    public List<ServerEndpoint> getEndpoints() {
        return new ArrayList<>(endpoints);
    }
}
