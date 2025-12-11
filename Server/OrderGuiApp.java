package BaiKhoiNghiepNhom4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class OrderGuiApp {

    // Chỉ còn 1 log hệ thống (bên phải)
    private static JTextArea txtSystemLog;

    public static void appendSystemLog(String msg) {
        if (txtSystemLog == null) return;
        SwingUtilities.invokeLater(() -> {
            txtSystemLog.append(msg + "\n");
            txtSystemLog.setCaretPosition(txtSystemLog.getDocument().getLength());
        });
    }

    public static void main(String[] args) throws Exception {
        InventoryService inventoryService = new InventoryService();
        OrderRepository orderRepository = new OrderRepository();

        // Worker xử lý ORDER_QUEUE
        OrderWorker worker = new OrderWorker(orderRepository, inventoryService);
        Thread workerThread = new Thread(worker, "order-worker");
        workerThread.setDaemon(true);
        workerThread.start();

        // Listener xử lý yêu cầu SYNC sản phẩm
        startProductSyncRequestListener(inventoryService);

        // Mở GUI
        SwingUtilities.invokeLater(() -> createUi(inventoryService, orderRepository));
    }

    // =================== GUI ADMIN =====================
    private static void createUi(InventoryService inventoryService,
                                 OrderRepository orderRepository) {

        JFrame frame = new JFrame("SERVER ADMIN - Quản lý sản phẩm & đơn hàng (RabbitMQ)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 650);
        frame.setLocationRelativeTo(null);

        // ====== BẢNG SẢN PHẨM (TRÊN) ======
        String[] colProduct = {"Mã SP", "Tên SP", "Giá", "Tồn kho"};
        DefaultTableModel modelProduct = new DefaultTableModel(colProduct, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tblProducts = new JTable(modelProduct);
        JScrollPane spProducts = new JScrollPane(tblProducts);

        Runnable reloadProducts = () -> {
            modelProduct.setRowCount(0);
            List<Product> list = inventoryService.findAll();
            for (Product p : list) {
                modelProduct.addRow(new Object[]{
                        p.getId(), p.getName(), p.getPrice(), p.getStock()
                });
            }
        };
        reloadProducts.run();

        // ====== BẢNG ĐƠN HÀNG (SẼ ĐƯA XUỐNG DƯỚI TRÁI) ======
        String[] colOrder = {"OrderId", "Khách", "SP", "SL", "Tổng tiền", "Trạng thái"};
        DefaultTableModel modelOrder = new DefaultTableModel(colOrder, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tblOrders = new JTable(modelOrder);
        JScrollPane spOrders = new JScrollPane(tblOrders);

        Runnable reloadOrders = () -> {
            modelOrder.setRowCount(0);
            List<Order> list = orderRepository.findAll();
            for (Order o : list) {
                modelOrder.addRow(new Object[]{
                        o.getId(),
                        o.getCustomerName(),
                        o.getProductId(),
                        o.getQuantity(),
                        o.getTotalPrice(),
                        o.getStatus()
                });
            }
        };
        reloadOrders.run();

        // Auto reload đơn mỗi 2s
        new javax.swing.Timer(2000, e -> reloadOrders.run()).start();

        // ====== FORM THÊM/SỬA/XÓA SẢN PHẨM + XUẤT BÁO CÁO ======
        JTextField txtId = new JTextField(6);
        JTextField txtName = new JTextField(10);
        JTextField txtPrice = new JTextField(8);
        JTextField txtStock = new JTextField(5);

        JButton btnSave   = new JButton("Thêm / Cập nhật");
        JButton btnDelete = new JButton("Xóa");
        JButton btnReload = new JButton("Reload");
        JButton btnExport = new JButton("Xuất báo cáo");

        JPanel panelForm = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelForm.add(new JLabel("Mã SP:"));   panelForm.add(txtId);
        panelForm.add(new JLabel("Tên:"));     panelForm.add(txtName);
        panelForm.add(new JLabel("Giá:"));     panelForm.add(txtPrice);
        panelForm.add(new JLabel("Tồn kho:")); panelForm.add(txtStock);
        panelForm.add(btnSave);
        panelForm.add(btnDelete);
        panelForm.add(btnReload);
        panelForm.add(btnExport);

        // Click bảng SP -> đổ lên form
        tblProducts.getSelectionModel().addListSelectionListener(e -> {
            int row = tblProducts.getSelectedRow();
            if (row >= 0) {
                txtId.setText(modelProduct.getValueAt(row, 0).toString());
                txtName.setText(modelProduct.getValueAt(row, 1).toString());
                txtPrice.setText(modelProduct.getValueAt(row, 2).toString());
                txtStock.setText(modelProduct.getValueAt(row, 3).toString());
            }
        });

        // SAVE
        btnSave.addActionListener(e -> {
            try {
                String id = txtId.getText().trim();
                String name = txtName.getText().trim();
                double price = Double.parseDouble(txtPrice.getText().trim());
                int stock = Integer.parseInt(txtStock.getText().trim());

                if (id.isEmpty() || name.isEmpty()) {
                    JOptionPane.showMessageDialog(frame,
                            "Mã SP và Tên không được để trống!",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                Product p = new Product(id, name, price, stock);
                inventoryService.upsert(p);

                reloadProducts.run();
                publishProductUpdate(p);

                JOptionPane.showMessageDialog(frame,
                        "Đã lưu sản phẩm " + id,
                        "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame,
                        "Lỗi nhập dữ liệu sản phẩm!",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        // DELETE
        btnDelete.addActionListener(e -> {
            String id = txtId.getText().trim();
            if (id.isEmpty()) {
                JOptionPane.showMessageDialog(frame,
                        "Nhập Mã SP cần xóa!",
                        "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            inventoryService.delete(id);
            reloadProducts.run();
            publishProductDelete(id);
        });

        // RELOAD
        btnReload.addActionListener(e -> {
            reloadProducts.run();
            reloadOrders.run();
        });

        // ====== NÚT XUẤT BÁO CÁO ĐƠN HÀNG ======
        btnExport.addActionListener(e -> {
            try {
                List<Order> list = orderRepository.findAll();
                Path path = Paths.get("orders_report.txt");
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                StringBuilder sb = new StringBuilder();
                sb.append("BÁO CÁO ĐƠN HÀNG\n");
                sb.append("=================\n\n");
                for (Order o : list) {
                    sb.append("OrderId: ").append(o.getId())
                            .append(" | Khách: ").append(o.getCustomerName())
                            .append(" | SP: ").append(o.getProductId())
                            .append(" | SL: ").append(o.getQuantity())
                            .append(" | Tổng: ").append(o.getTotalPrice())
                            .append(" | Trạng thái: ").append(o.getStatus());
                    if (o.getCreatedAt() != null) {
                        sb.append(" | Tạo lúc: ").append(o.getCreatedAt().format(fmt));
                    }
                    sb.append("\n");
                }

                Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
                JOptionPane.showMessageDialog(frame,
                        "Đã xuất báo cáo ra file: " + path.toAbsolutePath(),
                        "Xuất báo cáo",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame,
                        "Lỗi xuất báo cáo: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        // ====== CHỈ CÒN 1 TEXT AREA LOG BÊN PHẢI ======
        txtSystemLog = new JTextArea();
        txtSystemLog.setEditable(false);
        JScrollPane spSystemLog = new JScrollPane(txtSystemLog);

        // BOTTOM: trái = bảng đơn hàng, phải = log hệ thống
        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                spOrders, spSystemLog);
        bottomSplit.setDividerLocation(600);

        // TOP/BOTTOM: trên = bảng sản phẩm, dưới = (đơn hàng + log)
        JSplitPane mainCenter = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                spProducts, bottomSplit);
        mainCenter.setDividerLocation(260);

        frame.setLayout(new BorderLayout());
        frame.add(panelForm, BorderLayout.NORTH);
        frame.add(mainCenter, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    // =================== LISTENER SYNC REQUEST từ CLIENT =====================
    private static void startProductSyncRequestListener(InventoryService inventoryService) {
        new Thread(() -> {
            try (Connection conn = RabbitMQConfig.factory().newConnection();
                 Channel ch = conn.createChannel()) {

                ch.queueDeclare(RabbitMQConfig.PRODUCT_SYNC_REQUEST_QUEUE, true, false, false, null);
                ch.queueDeclare(RabbitMQConfig.PRODUCT_SYNC_QUEUE, true, false, false, null);

                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());

                DeliverCallback cb = (tag, delivery) -> {
                    try {
                        appendSystemLog("[SERVER] Nhận yêu cầu SYNC sản phẩm từ client");

                        List<Product> products = inventoryService.findAll();
                        String json = mapper.writeValueAsString(products);

                        ch.basicPublish("",
                                RabbitMQConfig.PRODUCT_SYNC_QUEUE,
                                null,
                                json.getBytes(StandardCharsets.UTF_8));

                        appendSystemLog("[SERVER] Đã gửi " + products.size()
                                + " sản phẩm sang PRODUCT_SYNC_QUEUE");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                };

                ch.basicConsume(RabbitMQConfig.PRODUCT_SYNC_REQUEST_QUEUE,
                        true, cb, tag -> {});
                Thread.sleep(Long.MAX_VALUE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "product-sync-request-listener").start();
    }

    // =================== PUBLISH UPDATE / DELETE =====================

    private static void publishProductUpdate(Product p) {
        try (Connection conn = RabbitMQConfig.factory().newConnection();
             Channel ch = conn.createChannel()) {

            ch.queueDeclare(RabbitMQConfig.PRODUCT_UPDATE_QUEUE, true, false, false, null);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String json = mapper.writeValueAsString(p);

            ch.basicPublish("",
                    RabbitMQConfig.PRODUCT_UPDATE_QUEUE,
                    null,
                    json.getBytes(StandardCharsets.UTF_8));

            appendSystemLog("[SERVER] Gửi cập nhật SP " + p.getId()
                    + " sang PRODUCT_UPDATE_QUEUE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void publishProductDelete(String productId) {
        try (Connection conn = RabbitMQConfig.factory().newConnection();
             Channel ch = conn.createChannel()) {

            ch.queueDeclare(RabbitMQConfig.PRODUCT_DELETE_QUEUE, true, false, false, null);

            ch.basicPublish("",
                    RabbitMQConfig.PRODUCT_DELETE_QUEUE,
                    null,
                    productId.getBytes(StandardCharsets.UTF_8));

            appendSystemLog("[SERVER] Gửi xóa SP " + productId
                    + " sang PRODUCT_DELETE_QUEUE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
