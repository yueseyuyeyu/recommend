package com.deng.recommend.tool;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 演示数据生成器。
 *
 * <p>固定随机种子 {@code 20261027}，保证任何人、任何时间生成的数据完全一致，
 * 便于算法评测结果复现。口令通过 BCrypt 编码后写入，绝不写明文哈希。
 *
 * <p>用法：
 * <pre>
 * cd recommend-server
 * set DB_PASSWORD=你的口令            （PowerShell: $env:DB_PASSWORD='...'）
 * mvnw exec:java -Dexec.args="--reset"
 * </pre>
 *
 * <p>参数（均可选）：{@code --reset}、{@code --users=200}、{@code --products=500}、
 * {@code --behaviors=5000}、{@code --orders=800}、{@code --reviews=600}。
 */
public final class SeedDataGenerator {

    private static final long SEED = 20261027L;

    private static final String[] ROOTS = {
            "手机数码", "电脑办公", "家用电器", "服饰鞋包", "美妆个护",
            "食品生鲜", "运动户外", "图书文娱", "家居日用", "母婴玩具"
    };

    private static final String[] SUB_NAMES = {"旗舰", "热销", "新品", "精选", "入门"};

    private static final String[] BEHAVIOR_TYPES = {"VIEW", "CLICK", "CART", "PURCHASE", "RATE"};

    private static final double[] BEHAVIOR_WEIGHTS = {1.0, 2.0, 3.0, 5.0, 4.0};

    /** 行为类型抽样权重：浏览最多，购买最少 */
    private static final int[] BEHAVIOR_WEIGHTED_CHOICES =
            {0, 0, 0, 0, 0, 0, 1, 1, 2, 3, 4};

    private SeedDataGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3306");
        String database = env("DB_NAME", "recommend_db");
        String username = env("DB_USERNAME", "root");
        String password = env("DB_PASSWORD", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";

        System.out.println("连接数据库: " + url);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            if (options.reset) {
                truncateAll(connection);
            } else if (countRows(connection, "`user`") > 0) {
                System.out.println("检测到已有数据。若要清空重建，请加 --reset 参数。");
                return;
            }
            int categories = seedCategories(connection);
            List<Long> productIds = seedProducts(connection, options.products);
            seedUsers(connection, options.users);
            seedBehaviors(connection, options.behaviors, options.users, productIds);
            seedOrders(connection, options.orders, options.users, productIds);
            seedReviews(connection, options.reviews, options.users, productIds);
            connection.commit();
            System.out.printf("完成：类目 %d、商品 %d、用户 %d、行为 %d、订单 %d、评价 %d%n",
                    categories, options.products, options.users, options.behaviors,
                    options.orders, options.reviews);
            System.out.println("演示账号：admin / admin123（管理员），user001 / 123456（普通用户）");
        }
    }

    private static int seedCategories(Connection connection) throws SQLException {
        String sql = "INSERT INTO `category` (name, parent_id, sort_order) VALUES (?, ?, ?)";
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < ROOTS.length; i++) {
                statement.setString(1, ROOTS[i]);
                statement.setLong(2, 0L);
                statement.setInt(3, i + 1);
                statement.addBatch();
                count++;
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < ROOTS.length; i++) {
                for (int j = 0; j < 3; j++) {
                    statement.setString(1, ROOTS[i] + "-" + SUB_NAMES[j]);
                    statement.setLong(2, i + 1L);
                    statement.setInt(3, j + 1);
                    statement.addBatch();
                    count++;
                }
            }
            statement.executeBatch();
        }
        return count;
    }

    private static List<Long> seedProducts(Connection connection, int total) throws SQLException {
        Random random = new Random(SEED);
        String sql = "INSERT INTO `product` "
                + "(category_id, name, subtitle, main_image, price, original_price, stock, sales, status, description) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        List<Long> ids = new ArrayList<>(total);
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= total; i++) {
                BigDecimal price = BigDecimal.valueOf(9.9 + random.nextDouble() * 4990)
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal original = price.multiply(BigDecimal.valueOf(1.2))
                        .setScale(2, RoundingMode.HALF_UP);
                long categoryId = 11 + (i % (ROOTS.length * 3));
                statement.setLong(1, categoryId);
                statement.setString(2, "演示商品 " + i);
                statement.setString(3, ROOTS[(int) ((categoryId - 11) / 3)] + " · " + SUB_NAMES[(int) ((categoryId - 11) % 3)]);
                statement.setString(4, "https://picsum.photos/seed/product" + i + "/400/400");
                statement.setBigDecimal(5, price);
                statement.setBigDecimal(6, original);
                statement.setInt(7, 50 + random.nextInt(950));
                statement.setInt(8, random.nextInt(500));
                statement.setInt(9, 1);
                statement.setString(10, "用于演示推荐效果的种子商品，编号 " + i);
                statement.addBatch();
                if (i % 100 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
        try (Statement query = connection.createStatement();
             ResultSet resultSet = query.executeQuery("SELECT id FROM `product` ORDER BY id")) {
            while (resultSet.next()) {
                ids.add(resultSet.getLong(1));
            }
        }
        return ids;
    }

    private static void seedUsers(Connection connection, int total) throws SQLException {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String userHash = encoder.encode("123456");
        String adminHash = encoder.encode("admin123");
        String sql = "INSERT INTO `user` (username, password_hash, nickname, email, role, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "admin");
            statement.setString(2, adminHash);
            statement.setString(3, "管理员");
            statement.setString(4, "admin@example.com");
            statement.setInt(5, 2);
            statement.setInt(6, 1);
            statement.addBatch();
            for (int i = 1; i <= total; i++) {
                String name = String.format("user%03d", i);
                statement.setString(1, name);
                statement.setString(2, userHash);
                statement.setString(3, name);
                statement.setString(4, name + "@example.com");
                statement.setInt(5, 1);
                statement.setInt(6, 1);
                statement.addBatch();
                if (i % 100 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void seedBehaviors(Connection connection, int total, int users, List<Long> productIds)
            throws SQLException {
        Random random = new Random(SEED + 1);
        LocalDateTime now = LocalDateTime.now();
        String sql = "INSERT INTO `behavior` (user_id, product_id, behavior_type, weight, rating, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < total; i++) {
                long userId = 2 + random.nextInt(users);           // 跳过 admin(id=1)
                long productId = pickProduct(random, productIds, users, userId);
                int typeIndex = BEHAVIOR_WEIGHTED_CHOICES[random.nextInt(BEHAVIOR_WEIGHTED_CHOICES.length)];
                String type = BEHAVIOR_TYPES[typeIndex];
                Integer rating = "RATE".equals(type) ? 1 + random.nextInt(5) : null;
                LocalDateTime occurredAt = now.minusDays(random.nextInt(30))
                        .minusSeconds(random.nextInt(86400));

                statement.setLong(1, userId);
                statement.setLong(2, productId);
                statement.setString(3, type);
                statement.setDouble(4, BEHAVIOR_WEIGHTS[typeIndex]);
                if (rating == null) {
                    statement.setNull(5, java.sql.Types.TINYINT);
                } else {
                    statement.setInt(5, rating);
                }
                statement.setTimestamp(6, Timestamp.valueOf(occurredAt));
                statement.addBatch();
                if (i % 500 == 499) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    /**
     * 70% 的行为落在用户偏好的两个类目里，30% 随机，保证既有共现又有一定探索度。
     */
    private static long pickProduct(Random random, List<Long> productIds, int users, long userId) {
        int rootA = (int) ((userId - 2) % ROOTS.length);
        int rootB = (rootA + 3) % ROOTS.length;
        if (random.nextDouble() < 0.7) {
            int root = random.nextBoolean() ? rootA : rootB;
            // 每个一级类目对应 3 个二级类目，每个二级类目下商品均匀分布
            int offset = random.nextInt(3);
            int bucket = root * 3 + offset;
            int index = bucket;
            while (index < productIds.size()) {
                if ((index - bucket) % (ROOTS.length * 3) == 0) {
                    return productIds.get(index);
                }
                index++;
            }
            // 回退：按类目直接取
            return productIds.get(Math.min(productIds.size() - 1, bucket));
        }
        return productIds.get(random.nextInt(productIds.size()));
    }

    private static void seedOrders(Connection connection, int total, int users, List<Long> productIds)
            throws SQLException {
        Random random = new Random(SEED + 2);
        LocalDateTime now = LocalDateTime.now();
        String orderSql = "INSERT INTO `orders` "
                + "(order_no, user_id, total_amount, status, receiver_name, receiver_phone, receiver_addr, paid_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String itemSql = "INSERT INTO `order_item` "
                + "(order_no, product_id, product_name, price, quantity, subtotal) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement orderStatement = connection.prepareStatement(orderSql);
             PreparedStatement itemStatement = connection.prepareStatement(itemSql)) {
            for (int i = 1; i <= total; i++) {
                String orderNo = "S" + (20261028000000L + i);
                long userId = 2 + random.nextInt(users);
                int itemCount = 1 + random.nextInt(3);
                BigDecimal totalAmount = BigDecimal.ZERO;
                for (int j = 0; j < itemCount; j++) {
                    long productId = productIds.get(random.nextInt(productIds.size()));
                    BigDecimal price = BigDecimal.valueOf(9.9 + random.nextDouble() * 4990)
                            .setScale(2, RoundingMode.HALF_UP);
                    int quantity = 1 + random.nextInt(2);
                    BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
                    totalAmount = totalAmount.add(subtotal);
                    itemStatement.setString(1, orderNo);
                    itemStatement.setLong(2, productId);
                    itemStatement.setString(3, "演示商品 " + productId);
                    itemStatement.setBigDecimal(4, price);
                    itemStatement.setInt(5, quantity);
                    itemStatement.setBigDecimal(6, subtotal);
                    itemStatement.addBatch();
                }

                int status = 1 + random.nextInt(3);
                LocalDateTime created = now.minusDays(random.nextInt(60));
                orderStatement.setString(1, orderNo);
                orderStatement.setLong(2, userId);
                orderStatement.setBigDecimal(3, totalAmount);
                orderStatement.setInt(4, status);
                orderStatement.setString(5, "收货人" + userId);
                orderStatement.setString(6, "1380000" + String.format("%04d", userId));
                orderStatement.setString(7, "演示地址 " + userId + " 号");
                orderStatement.setTimestamp(8, Timestamp.valueOf(created.plusMinutes(5)));
                orderStatement.addBatch();
                if (i % 200 == 0) {
                    orderStatement.executeBatch();
                    itemStatement.executeBatch();
                }
            }
            orderStatement.executeBatch();
            itemStatement.executeBatch();
        }
    }

    private static void seedReviews(Connection connection, int total, int users, List<Long> productIds)
            throws SQLException {
        Random random = new Random(SEED + 3);
        String sql = "INSERT INTO `review` (user_id, product_id, rating, content) VALUES (?, ?, ?, ?)";
        Set<String> used = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int inserted = 0;
            while (inserted < total) {
                long userId = 2 + random.nextInt(users);
                long productId = productIds.get(random.nextInt(productIds.size()));
                if (!used.add(userId + ":" + productId)) {
                    continue;
                }
                statement.setLong(1, userId);
                statement.setLong(2, productId);
                statement.setInt(3, 1 + random.nextInt(5));
                statement.setString(4, "演示评价：" + (inserted + 1));
                statement.addBatch();
                inserted++;
                if (inserted % 200 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void truncateAll(Connection connection) throws SQLException {
        String[] tables = {"review", "order_item", "orders", "cart", "behavior", "product", "category", "user"};
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                statement.execute("TRUNCATE TABLE `" + table + "`");
            }
        }
        System.out.println("已清空 8 张业务表，开始生成演示数据");
    }

    private static long countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 命令行参数。
     */
    private static final class Options {
        private boolean reset;
        private int users = 200;
        private int products = 500;
        private int behaviors = 5000;
        private int orders = 800;
        private int reviews = 600;

        private static Options parse(String[] args) {
            Options options = new Options();
            for (String arg : args) {
                if ("--reset".equals(arg)) {
                    options.reset = true;
                } else if (arg.startsWith("--users=")) {
                    options.users = Integer.parseInt(arg.substring("--users=".length()));
                } else if (arg.startsWith("--products=")) {
                    options.products = Integer.parseInt(arg.substring("--products=".length()));
                } else if (arg.startsWith("--behaviors=")) {
                    options.behaviors = Integer.parseInt(arg.substring("--behaviors=".length()));
                } else if (arg.startsWith("--orders=")) {
                    options.orders = Integer.parseInt(arg.substring("--orders=".length()));
                } else if (arg.startsWith("--reviews=")) {
                    options.reviews = Integer.parseInt(arg.substring("--reviews=".length()));
                }
            }
            return options;
        }
    }
}
