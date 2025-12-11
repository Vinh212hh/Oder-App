package BaiKhoiNghiepNhom4;

public class OrderCreatedEvent {
    private Long orderId;
    private String customerName;
    private String productId;
    private int quantity;
    private double totalPrice;

    public OrderCreatedEvent() {}

    public OrderCreatedEvent(Long orderId, String customerName,
                             String productId, int quantity, double totalPrice) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.productId = productId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }
}
