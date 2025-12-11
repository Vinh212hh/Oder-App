package BaiKhoiNghiepNhom4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public class OrderProducer implements AutoCloseable {

    private final Connection connection;
    private final Channel channel;

    // ObjectMapper hỗ trợ LocalDateTime
    private final ObjectMapper objectMapper;

    public OrderProducer() throws Exception {

        // Khởi tạo ObjectMapper + JSR310
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Tạo kết nối RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);

        this.connection = factory.newConnection();
        this.channel = connection.createChannel();

        // Hàng đợi client → server
        channel.queueDeclare(RabbitMQConfig.ORDER_QUEUE, true, false, false, null);

        System.out.println("[Producer] Ready to send messages.");
    }

    /**
     * Gửi full Order lên server để xử lý nền.
     */
    public void sendOrderCreated(Order order) throws Exception {
        String json = objectMapper.writeValueAsString(order);

        channel.basicPublish(
                "",
                RabbitMQConfig.ORDER_QUEUE,
                null,
                json.getBytes(StandardCharsets.UTF_8)
        );

        System.out.println("[Producer] Sent OrderCreated: orderId=" + order.getId());
    }

    @Override
    public void close() throws Exception {
        channel.close();
        connection.close();
    }
}
