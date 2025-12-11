package BaiKhoiNghiepNhom4;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryService {

    private final Path storageFile = Paths.get("products.json");
    private final ObjectMapper mapper;
    private final Map<String, Product> store = new ConcurrentHashMap<>();

    public InventoryService() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadFromFile();
    }

    // ===================== FILE I/O =====================

    private synchronized void loadFromFile() {
        store.clear();

        if (!Files.exists(storageFile)) {
            System.out.println("[Inventory] products.json chưa tồn tại, tạo mới.");
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(storageFile);
            if (bytes.length == 0) {
                System.out.println("[Inventory] File trống.");
                return;
            }

            List<Product> list = mapper.readValue(bytes, new TypeReference<List<Product>>() {});
            for (Product p : list) {
                store.put(p.getId(), p);
            }

            System.out.println("[Inventory] Loaded " + list.size() + " products.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveToFile() {
        try {
            List<Product> list = new ArrayList<>(store.values());
            mapper.writeValue(storageFile.toFile(), list);
            System.out.println("[Inventory] Saved " + list.size() + " products.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===================== API =====================

    /** Trả về toàn bộ sản phẩm */
    public synchronized List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    /** Lấy sản phẩm theo ID */
    public synchronized Product findById(String id) {
        return store.get(id);
    }

    /** Thêm hoặc cập nhật sản phẩm */
    public synchronized void upsert(Product p) {
        store.put(p.getId(), p);
        saveToFile();
    }

    /** Xóa sản phẩm */
    public synchronized void delete(String id) {
        store.remove(id);
        saveToFile();
    }

    /** Trừ tồn kho và trả về tồn mới — nếu thiếu trả về -1 */
    public synchronized int decreaseStock(String productId, int qty) {
        Product p = store.get(productId);
        if (p == null) return -1;

        int current = p.getStock();

        if (current < qty)
            return -1; // không đủ hàng

        p.setStock(current - qty);
        saveToFile();
        return p.getStock();
    }

    /** Lấy số lượng tồn kho */
    public synchronized int getStockOfProduct(String productId) {
        Product p = store.get(productId);
        return (p == null ? -1 : p.getStock());
    }
}
