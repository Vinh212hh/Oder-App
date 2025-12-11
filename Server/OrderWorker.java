package BaiKhoiNghiepNhom4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;

public class OrderWorker implements Runnable {

    private final OrderRepository orderRepo;
    private final InventoryService inventoryService;
    private final ObjectMapper mapper = new ObjectMapper();

    private Connection connection;
    private Channel channel;

    public OrderWorker(OrderRepository orderRepo, InventoryService inventoryService) {
        this.orderRepo = orderRepo;
        this.inventoryService = inventoryService;
        mapper.registerModule(new JavaTimeModule());
    }

    private void setup() throws Exception {
        ConnectionFactory factory = RabbitMQConfig.factory();
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();
        channel.queueDeclare(RabbitMQConfig.ORDER_QUEUE, true, false, false, null);
        System.out.println("[Worker] Waiting for messages...");
    }

    @Override
    public void run() {
        try {
            setup();

            DeliverCallback cb = (tag, delivery) -> {
                String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                try {
                    Order order = mapper.readValue(json, Order.class);
                    System.out.println("[Worker] Received order #" + order.getId()
                            + " SP=" + order.getProductId()
                            + ", qty=" + order.getQuantity());

                    // load lại file trước khi sửa
                    orderRepo.reloadFromFile();

                    // chuyển sang PROCESSING và lưu
                    order.setStatus(OrderStatus.PROCESSING);
                    orderRepo.save(order);

                    // 1. gửi email (fake)
                    order.setEmailSent(true);

                    // 2. giảm tồn kho – HÀM NÀY TRẢ VỀ TỒN KHO MỚI, HOẶC -1 NẾU KHÔNG ĐỦ
                    int newStock = inventoryService.decreaseStock(
                            order.getProductId(), order.getQuantity());

                    if (newStock < 0) {
                        // KHÔNG đủ hàng → FAILED
                        order.setStatus(OrderStatus.FAILED);
                        orderRepo.save(order);

                        String msg = "Đơn #" + order.getId()
                                + " bị từ chối: không đủ tồn kho.";


                        publishOrderStatus(order.getId(),
                                order.getProductId(),
                                inventoryService.getStockOfProduct(order.getProductId()),
                                OrderStatus.FAILED,
                                msg);

                        System.out.println("[Worker] Reject order #" + order.getId()
                                + " (not enough stock)");
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        return;
                    }

                    // đủ hàng
                    order.setInventoryUpdated(true);
                    order.setLogWritten(true);
                    order.setStatus(OrderStatus.COMPLETED);
                    orderRepo.save(order);

                    String msg = "Đơn #" + order.getId()
                            + " đã xử lý xong, tồn kho SP "
                            + order.getProductId() + " = " + newStock;


                    publishOrderStatus(order.getId(),
                            order.getProductId(),
                            newStock,
                            OrderStatus.COMPLETED,
                            msg);

                    System.out.println("[Worker] Finished processing order " + order.getId());

                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    e.printStackTrace();
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(),
                            false, false);
                }
            };

            channel.basicConsume(RabbitMQConfig.ORDER_QUEUE, false, cb, tag -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void publishOrderStatus(long orderId,
                                    String productId,
                                    int newStock,
                                    OrderStatus status,
                                    String message) {
        try (Connection conn = RabbitMQConfig.factory().newConnection();
             Channel ch = conn.createChannel()) {

            ch.queueDeclare(RabbitMQConfig.ORDER_STATUS_QUEUE, true, false, false, null);

            ObjectMapper om = new ObjectMapper();
            om.registerModule(new JavaTimeModule());

            OrderStatusEvent evt = new OrderStatusEvent(orderId, productId,
                    newStock, status, message);

            String json = om.writeValueAsString(evt);
            ch.basicPublish("", RabbitMQConfig.ORDER_STATUS_QUEUE, null,
                    json.getBytes(StandardCharsets.UTF_8));

            OrderGuiApp.appendSystemLog("[SERVER] Gửi trạng thái đơn #" + orderId
                    + " (" + status + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
