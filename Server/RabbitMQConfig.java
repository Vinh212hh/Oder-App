package BaiKhoiNghiepNhom4;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitMQConfig {

    public static final String HOST = "localhost";

    public static final String USERNAME = "nhom4";
    public static final String PASSWORD = "1";

    public static final String ORDER_QUEUE = "order_queue";
    public static final String ORDER_STATUS_QUEUE = "order_status_queue";

    public static final String PRODUCT_SYNC_REQUEST_QUEUE = "product_sync_request_queue";
    public static final String PRODUCT_SYNC_QUEUE = "product_sync_queue";
    public static final String PRODUCT_UPDATE_QUEUE = "product_update_queue";
    public static final String PRODUCT_DELETE_QUEUE = "product_delete_queue";

    public static ConnectionFactory factory() {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost(HOST);
        f.setUsername(USERNAME);
        f.setPassword(PASSWORD);
        return f;
    }
}
