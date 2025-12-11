import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public class OrderProducer {

    private final ObjectMapper mapper;

    public OrderProducer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Gửi 1 đơn hàng (Order) lên RabbitMQ, để server xử lý.
     */
    public void sendOrderCreated(Order order) throws Exception {
        // dùng factory() để bảo đảm host/user/pass đúng
        ConnectionFactory factory = RabbitMQConfig.factory();

        try (Connection conn = factory.newConnection();
             Channel ch = conn.createChannel()) {

            // đảm bảo queue tồn tại
            ch.queueDeclare(
                    RabbitMQConfig.ORDER_QUEUE,
                    true,   // durable
                    false,  // exclusive
                    false,  // autoDelete
                    null
            );

            // serialize Order -> JSON
            String json = mapper.writeValueAsString(order);

            // publish message
            ch.basicPublish(
                    "",
                    RabbitMQConfig.ORDER_QUEUE,
                    null,
                    json.getBytes(StandardCharsets.UTF_8)
            );

            System.out.println("[Producer] Sent OrderCreated for orderId=" + order.getId());
        }
    }
}
