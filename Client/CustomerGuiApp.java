import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CustomerGuiApp {

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        OrderProducer producer = new OrderProducer(mapper);

        SwingUtilities.invokeLater(() -> createUi(mapper, producer));
    }

    private static void createUi(ObjectMapper mapper, OrderProducer producer) {
        JFrame frame = new JFrame("CLIENT - Đặt hàng (RabbitMQ Async)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 580);
        frame.setLocationRelativeTo(null);

        AtomicLong idGen = new AtomicLong(1);

        // Lưu các đơn do client này gửi + trạng thái mới nhất
        Set<Long> myOrders = ConcurrentHashMap.newKeySet();
        Map<Long, OrderStatusEvent> statusCache = new ConcurrentHashMap<>();

        // ===== BẢNG SẢN PHẨM =====
        String[] cols = {"Mã SP", "Tên", "Giá", "Tồn kho"};
        DefaultTableModel modelProduct = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tableProducts = new JTable(modelProduct);
        JScrollPane spProducts = new JScrollPane(tableProducts);

        // ===== LOG =====
        JTextArea txtLog = new JTextArea();
        txtLog.setEditable(false);
        JScrollPane spLog = new JScrollPane(txtLog);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spProducts, spLog);
        split.setDividerLocation(260);

        // ===== FORM ĐẶT HÀNG + XEM TRẠNG THÁI =====
        JTextField txtCustomer = new JTextField(12);
        JTextField txtQty      = new JTextField("1", 4);
        JTextField txtRepeat   = new JTextField("1", 3); // số lần đặt

        JButton btnOrder   = new JButton("Đặt hàng");
        JButton btnRefresh = new JButton("Refresh sản phẩm");

        JTextField txtCheckOrderId = new JTextField(5);
        JButton btnCheckStatus = new JButton("Xem trạng thái đơn");

        JPanel panelTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelTop.add(new JLabel("Khách:"));
        panelTop.add(txtCustomer);
        panelTop.add(new JLabel("SL:"));
        panelTop.add(txtQty);
        panelTop.add(new JLabel("Số lần:"));
        panelTop.add(txtRepeat);
        panelTop.add(btnOrder);
        panelTop.add(btnRefresh);

        JPanel panelRightTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelRightTop.add(new JLabel("OrderId:"));
        panelRightTop.add(txtCheckOrderId);
        panelRightTop.add(btnCheckStatus);

        JPanel panelNorth = new JPanel(new BorderLayout());
        panelNorth.add(panelTop, BorderLayout.WEST);
        panelNorth.add(panelRightTop, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout());
        frame.add(panelNorth, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);

        // ===== NÚT REFRESH (gửi yêu cầu SYNC FULL) =====
        btnRefresh.addActionListener(e -> requestFullSync(mapper, modelProduct, txtLog));

        // ===== NÚT ĐẶT HÀNG =====
        btnOrder.addActionListener(e -> {
            try {
                int row = tableProducts.getSelectedRow();
                if (row < 0) {
                    JOptionPane.showMessageDialog(frame,
                            "Vui lòng chọn 1 sản phẩm.",
                            "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                String customer = txtCustomer.getText().trim();
                if (customer.isEmpty()) {
                    JOptionPane.showMessageDialog(frame,
                            "Vui lòng nhập tên khách.",
                            "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                int qty;
                try {
                    qty = Integer.parseInt(txtQty.getText().trim());
                    if (qty <= 0) {
                        JOptionPane.showMessageDialog(frame,
                                "Số lượng phải > 0.",
                                "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Số lượng không hợp lệ.",
                            "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                int repeat;
                try {
                    repeat = Integer.parseInt(txtRepeat.getText().trim());
                    if (repeat <= 0) repeat = 1;
                } catch (NumberFormatException ex) {
                    repeat = 1;
                }

                String productId   = modelProduct.getValueAt(row, 0).toString();
                String productName = modelProduct.getValueAt(row, 1).toString();
                double price       = Double.parseDouble(modelProduct.getValueAt(row, 2).toString());
                int stock          = Integer.parseInt(modelProduct.getValueAt(row, 3).toString());

                // Check tồn kho (cho 1 đơn). Server vẫn kiểm tra lại lần nữa.
                if (qty > stock) {
                    JOptionPane.showMessageDialog(frame,
                            "Không đủ tồn kho.\nTồn hiện tại: " + stock + ", SL yêu cầu: " + qty,
                            "Không đủ hàng", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                double total = price * qty;

                // ===== POPUP XÁC NHẬN TRƯỚC KHI GỬI =====
                int choice = JOptionPane.showConfirmDialog(
                        frame,
                        "Xác nhận đặt hàng?\n\n" +
                                "Khách: " + customer + "\n" +
                                "Sản phẩm: " + productId + " - " + productName + "\n" +
                                "Số lượng mỗi đơn: " + qty + "\n" +
                                "Tổng mỗi đơn: " + total + "\n" +
                                "Số lần đặt: " + repeat,
                        "Xác nhận đặt hàng",
                        JOptionPane.OK_CANCEL_OPTION
                );
                if (choice != JOptionPane.OK_OPTION) {
                    return; // khách bấm Cancel
                }

                // ===== GỬI NHIỀU ĐƠN NẾU repeat > 1 =====
                for (int i = 0; i < repeat; i++) {
                    long id = idGen.getAndIncrement();

                    Order order = new Order();
                    order.setId(id);
                    order.setCustomerName(customer);
                    order.setProductId(productId);
                    order.setQuantity(qty);
                    order.setTotalPrice(total);

                    producer.sendOrderCreated(order);
                    myOrders.add(id);   // đánh dấu đơn thuộc client này

                    txtLog.append("Khách " + customer + " đặt hàng #" + id
                            + ": SP=" + productId + ", SL=" + qty + ", tổng=" + total + "\n");
                }
                txtLog.setCaretPosition(txtLog.getDocument().getLength());

                // Thông báo nhẹ sau khi gửi batch
                JOptionPane.showMessageDialog(
                        frame,
                        "Đã gửi " + (repeat) + " đơn lên server.\n" +
                                "Server sẽ xử lý và cập nhật trạng thái.",
                        "Đặt hàng thành công",
                        JOptionPane.INFORMATION_MESSAGE
                );

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame,
                        "Lỗi khi đặt hàng: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        // ===== NÚT XEM TRẠNG THÁI ĐƠN =====
        btnCheckStatus.addActionListener(e -> {
            try {
                long id = Long.parseLong(txtCheckOrderId.getText().trim());
                OrderStatusEvent ev = statusCache.get(id);
                if (ev == null) {
                    JOptionPane.showMessageDialog(frame,
                            "Chưa có thông tin trạng thái cho đơn #" + id
                                    + " (server chưa xử lý hoặc đơn không tồn tại).",
                            "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(frame,
                            "Đơn #" + ev.getOrderId()
                                    + " | Trạng thái: " + ev.getStatus()
                                    + "\nSP: " + ev.getProductId()
                                    + " | Tồn kho mới: " + ev.getNewStock(),
                            "Trạng thái đơn",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame,
                        "OrderId không hợp lệ.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        // LISTENER: trạng thái đơn hàng
        startOrderStatusListener(frame, mapper, modelProduct, txtLog,
                myOrders, statusCache);

        // LISTENER: sync full
        startProductSyncListener(mapper, modelProduct, txtLog);

        // LISTENER: cập nhật 1 SP
        startProductUpdateListener(mapper, modelProduct, txtLog);

        // LISTENER: xóa SP
        startProductDeleteListener(mapper, modelProduct, txtLog);

        frame.setVisible(true);

        // mở GUI tự sync 1 lần
        requestFullSync(mapper, modelProduct, txtLog);
    }

    // ======= GỬI YÊU CẦU SYNC FULL (request queue) =======
    private static void requestFullSync(ObjectMapper mapper,
                                        DefaultTableModel modelProduct,
                                        JTextArea txtLog) {
        try (Connection conn = RabbitMQConfig.factory().newConnection();
             Channel ch = conn.createChannel()) {

            ch.queueDeclare(RabbitMQConfig.PRODUCT_SYNC_REQUEST_QUEUE, true, false, false, null);
            String msg = "SYNC";
            ch.basicPublish("", RabbitMQConfig.PRODUCT_SYNC_REQUEST_QUEUE, null,
                    msg.getBytes(StandardCharsets.UTF_8));

            txtLog.append("Đã gửi yêu cầu sync sản phẩm.\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        } catch (Exception e) {
            e.printStackTrace();
            txtLog.append("Lỗi gửi yêu cầu sync: " + e.getMessage() + "\n");
        }
    }

    // ======= NHẬN TRẠNG THÁI ĐƠN HÀNG =======
    private static void startOrderStatusListener(JFrame frame,
                                                 ObjectMapper mapper,
                                                 DefaultTableModel modelProduct,
                                                 JTextArea txtLog,
                                                 Set<Long> myOrders,
                                                 Map<Long, OrderStatusEvent> statusCache) {
        new Thread(() -> {
            try {
                Connection conn = RabbitMQConfig.factory().newConnection();
                Channel ch = conn.createChannel();
                ch.queueDeclare(RabbitMQConfig.ORDER_STATUS_QUEUE, true, false, false, null);

                DeliverCallback cb = (tag, delivery) -> {
                    String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    try {
                        OrderStatusEvent ev = mapper.readValue(json, OrderStatusEvent.class);
                        statusCache.put(ev.getOrderId(), ev); // cache cho nút "xem trạng thái"

                        SwingUtilities.invokeLater(() -> {
                            // luôn update tồn kho trên bảng
                            String pid = ev.getProductId();
                            int newStock = ev.getNewStock();
                            if (newStock >= 0) {
                                for (int i = 0; i < modelProduct.getRowCount(); i++) {
                                    if (modelProduct.getValueAt(i, 0).equals(pid)) {
                                        modelProduct.setValueAt(newStock, i, 3);
                                        break;
                                    }
                                }
                            }

                            // Chỉ log nếu đơn thuộc client này (tránh spam từ client khác)
                            if (!myOrders.contains(ev.getOrderId())) {
                                return;
                            }

                            txtLog.append("Đơn #" + ev.getOrderId()
                                    + " => " + ev.getStatus()
                                    + " | " + ev.getMessage() + "\n");
                            txtLog.setCaretPosition(txtLog.getDocument().getLength());
                            // ❌ KHÔNG popup tự động ở đây nữa
                        });

                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                };

                ch.basicConsume(RabbitMQConfig.ORDER_STATUS_QUEUE, true, cb, tag -> {});
            } catch (Exception e) {
                e.printStackTrace();
                txtLog.append("Lỗi lắng nghe order status: " + e.getMessage() + "\n");
            }
        }, "order-status-listener").start();
    }

    // ======= NHẬN FULL LIST SẢN PHẨM =======
    private static void startProductSyncListener(ObjectMapper mapper,
                                                 DefaultTableModel modelProduct,
                                                 JTextArea txtLog) {
        new Thread(() -> {
            try {
                Connection conn = RabbitMQConfig.factory().newConnection();
                Channel ch = conn.createChannel();
                ch.queueDeclare(RabbitMQConfig.PRODUCT_SYNC_QUEUE, true, false, false, null);

                DeliverCallback cb = (tag, delivery) -> {
                    String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    try {
                        List<Product> list = mapper.readValue(
                                json, new TypeReference<List<Product>>() {});

                        SwingUtilities.invokeLater(() -> {
                            modelProduct.setRowCount(0);
                            for (Product p : list) {
                                modelProduct.addRow(new Object[]{
                                        p.getId(), p.getName(), p.getPrice(), p.getStock()
                                });
                            }
                            txtLog.append("Đã nhận danh sách " + list.size() + " sản phẩm.\n");
                            txtLog.setCaretPosition(txtLog.getDocument().getLength());
                        });
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                };

                ch.basicConsume(RabbitMQConfig.PRODUCT_SYNC_QUEUE, true, cb, tag -> {});
            } catch (Exception e) {
                e.printStackTrace();
                txtLog.append("Lỗi lắng nghe product sync: " + e.getMessage() + "\n");
            }
        }, "product-sync-listener").start();
    }

    // ======= NHẬN UPDATE 1 SẢN PHẨM =======
    private static void startProductUpdateListener(ObjectMapper mapper,
                                                   DefaultTableModel modelProduct,
                                                   JTextArea txtLog) {
        new Thread(() -> {
            try {
                Connection conn = RabbitMQConfig.factory().newConnection();
                Channel ch = conn.createChannel();
                ch.queueDeclare(RabbitMQConfig.PRODUCT_UPDATE_QUEUE, true, false, false, null);

                DeliverCallback cb = (tag, delivery) -> {
                    String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    try {
                        Product p = mapper.readValue(json, Product.class);

                        SwingUtilities.invokeLater(() -> {
                            boolean found = false;
                            for (int i = 0; i < modelProduct.getRowCount(); i++) {
                                if (modelProduct.getValueAt(i, 0).equals(p.getId())) {
                                    modelProduct.setValueAt(p.getName(), i, 1);
                                    modelProduct.setValueAt(p.getPrice(), i, 2);
                                    modelProduct.setValueAt(p.getStock(), i, 3);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                modelProduct.addRow(new Object[]{
                                        p.getId(), p.getName(), p.getPrice(), p.getStock()
                                });
                            }
                            txtLog.append("SP " + p.getId() + " được cập nhật.\n");
                            txtLog.setCaretPosition(txtLog.getDocument().getLength());
                        });
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                };

                ch.basicConsume(RabbitMQConfig.PRODUCT_UPDATE_QUEUE, true, cb, tag -> {});
            } catch (Exception e) {
                e.printStackTrace();
                txtLog.append("Lỗi lắng nghe product update: " + e.getMessage() + "\n");
            }
        }, "product-update-listener").start();
    }

    // ======= NHẬN SỰ KIỆN XÓA SẢN PHẨM =======
    private static void startProductDeleteListener(ObjectMapper mapper,
                                                   DefaultTableModel modelProduct,
                                                   JTextArea txtLog) {
        new Thread(() -> {
            try {
                Connection conn = RabbitMQConfig.factory().newConnection();
                Channel ch = conn.createChannel();
                ch.queueDeclare(RabbitMQConfig.PRODUCT_DELETE_QUEUE, true, false, false, null);

                DeliverCallback cb = (tag, delivery) -> {
                    String productId = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    SwingUtilities.invokeLater(() -> {
                        for (int i = 0; i < modelProduct.getRowCount(); i++) {
                            if (modelProduct.getValueAt(i, 0).equals(productId)) {
                                modelProduct.removeRow(i);
                                break;
                            }
                        }
                        txtLog.append("SP " + productId + " đã bị xóa bởi server.\n");
                        txtLog.setCaretPosition(txtLog.getDocument().getLength());
                    });
                };

                ch.basicConsume(RabbitMQConfig.PRODUCT_DELETE_QUEUE, true, cb, tag -> {});
            } catch (Exception e) {
                e.printStackTrace();
                txtLog.append("Lỗi lắng nghe product delete: " + e.getMessage() + "\n");
            }
        }, "product-delete-listener").start();
    }
}
