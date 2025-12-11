import com.rabbitmq.client.ConnectionFactory;

public class RabbitMQConfig {

    // 👉 Đặt IP máy SERVER (máy chạy RabbitMQ + OrderGuiApp)
    // public static final String HOST = "192.168.1.244"; IP cũ khi không chạy lan
    public static final String HOST = "26.30.11.136";

    // 👉 User/password bạn đã tạo bằng rabbitmqctl
    public static final String USERNAME = "nhom4";
    public static final String PASSWORD = "1";

    // Tên queue dùng chung
    public static final String ORDER_QUEUE = "order_queue";
    public static final String ORDER_STATUS_QUEUE = "order_status_queue";

    public static final String PRODUCT_SYNC_REQUEST_QUEUE = "product_sync_request_queue";
    public static final String PRODUCT_SYNC_QUEUE = "product_sync_queue";
    public static final String PRODUCT_UPDATE_QUEUE = "product_update_queue";
    public static final String PRODUCT_DELETE_QUEUE = "product_delete_queue";

    public static ConnectionFactory factory() {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost(HOST);
        f.setPort(5672);                      // đảm bảo dùng đúng port
        f.setUsername(USERNAME);
        f.setPassword(PASSWORD);

        f.setAutomaticRecoveryEnabled(true);  // nếu mất kết nối sẽ tự reconnect
        f.setNetworkRecoveryInterval(3000);

        return f;
    }

}
