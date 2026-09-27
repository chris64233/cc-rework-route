package com.chris64233.reworkroute;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 每个测试方法前清空所有业务表并重置 IDENTITY，保证测试间互不干扰
 * （所有 @SpringBootTest 类默认共享同一个应用上下文与 H2 实例）。
 * 并发测试不能放在单一事务里回滚，因此采用显式清库。
 */
public class DatabaseCleaner implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        try (var conn = dataSource.getConnection();
             var rs = conn.createStatement().executeQuery(
                     "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
                for (String table : tables) {
                    // TRUNCATE 同时重置 H2 的 IDENTITY 计数器。
                    stmt.execute("TRUNCATE TABLE \"" + table + "\"");
                }
                stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
        }
    }
}
