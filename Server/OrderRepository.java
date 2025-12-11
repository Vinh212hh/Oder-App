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

public class OrderRepository {

    private final Path storageFile = Paths.get("orders.json");
    private final ObjectMapper mapper;
    private final Map<Long, Order> store = new ConcurrentHashMap<>();

    public OrderRepository() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadFromFile();
    }

    // ====== ĐỌC / GHI FILE ======
    private synchronized void loadFromFile() {
        store.clear();
        if (!Files.exists(storageFile)) {
            System.out.println("[OrderRepo] orders.json chưa tồn tại, bắt đầu rỗng.");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(storageFile);
            if (bytes.length == 0) {
                System.out.println("[OrderRepo] orders.json rỗng.");
                return;
            }

            List<Order> list = mapper.readValue(
                    bytes,
                    new TypeReference<List<Order>>() {}
            );
            for (Order o : list) {
                store.put(o.getId(), o);
            }
            System.out.println("[OrderRepo] Loaded " + list.size() + " orders from file.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveToFile() {
        try {
            List<Order> list = new ArrayList<>(store.values());
            mapper.writeValue(storageFile.toFile(), list);
            System.out.println("[OrderRepo] Saved " + list.size() + " orders to file.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ====== API ======

    /** Lưu hoặc cập nhật đơn hàng. */
    public synchronized void save(Order order) {
        if (order == null || order.getId() == null) return;
        store.put(order.getId(), order);
        saveToFile();
    }

    /** Lấy toàn bộ đơn hàng. */
    public synchronized List<Order> findAll() {
        return new ArrayList<>(store.values());
    }

    /** Tìm đơn theo ID. */
    public synchronized Optional<Order> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    /** Cho phép các class khác (OrderWorker, GUI) reload lại dữ liệu từ file. */
    public synchronized void reloadFromFile() {
        loadFromFile();
    }
}
