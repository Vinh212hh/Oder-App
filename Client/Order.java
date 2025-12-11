import java.time.LocalDateTime;

public class Order {

    private Long id;
    private String customerName;
    private String productId;
    private int quantity;
    private double totalPrice;
    private LocalDateTime createdAt;
    private OrderStatus status;

    private boolean emailSent;
    private boolean inventoryUpdated;
    private boolean logWritten;

    public Order() {
    }

    public Order(Long id,
                 String customerName,
                 String productId,
                 int quantity,
                 double totalPrice,
                 LocalDateTime createdAt,
                 OrderStatus status) {
        this.id = id;
        this.customerName = customerName;
        this.productId = productId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
        this.status = status;
    }

    // Getters / setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public void setEmailSent(boolean emailSent) {
        this.emailSent = emailSent;
    }

    public boolean isInventoryUpdated() {
        return inventoryUpdated;
    }

    public void setInventoryUpdated(boolean inventoryUpdated) {
        this.inventoryUpdated = inventoryUpdated;
    }

    public boolean isLogWritten() {
        return logWritten;
    }

    public void setLogWritten(boolean logWritten) {
        this.logWritten = logWritten;
    }
}
