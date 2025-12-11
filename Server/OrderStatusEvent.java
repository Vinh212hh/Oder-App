package BaiKhoiNghiepNhom4;

public class OrderStatusEvent {
    private long orderId;
    private String productId;
    private int newStock;
    private OrderStatus status;
    private String message;

    public OrderStatusEvent() {
    }

    public OrderStatusEvent(long orderId, String productId,
                            int newStock, OrderStatus status,
                            String message) {
        this.orderId = orderId;
        this.productId = productId;
        this.newStock = newStock;
        this.status = status;
        this.message = message;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getNewStock() {
        return newStock;
    }

    public void setNewStock(int newStock) {
        this.newStock = newStock;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
