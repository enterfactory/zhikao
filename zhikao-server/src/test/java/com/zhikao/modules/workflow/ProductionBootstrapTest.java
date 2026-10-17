package com.zhikao.modules.workflow;

import com.zhikao.config.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.zhikao.mapper.StageAFixtureMapper;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"ZHIKAO_BOOTSTRAP_ADMIN=owner","ZHIKAO_BOOTSTRAP_PASSWORD=BootstrapTest2026"})
@ActiveProfiles("ecs")
class ProductionBootstrapTest {
    static final String SCHEMA = EcsTestSupport.schema("bootstrap");
    @org.springframework.test.context.DynamicPropertySource
    static void cloud(org.springframework.test.context.DynamicPropertyRegistry r) { EcsTestSupport.configure(r, SCHEMA); }

    @Autowired StageAFixtureMapper fixtures;
    @org.junit.jupiter.api.AfterAll static void cleanup(@Autowired StageAFixtureMapper fixtures,@Autowired org.springframework.data.redis.connection.RedisConnectionFactory redis) {
        EcsTestSupport.cleanupRedis(redis,SCHEMA);fixtures.dropTestSchema(SCHEMA);
    }
    @Autowired DataInitializer initializer;
    @Test void newInstallHasAdminAndCatalogWithoutDemoUsersAndPreservesCustomizedGrants() {
        assertEquals(1,fixtures.userCount());
        assertEquals("owner",fixtures.onlyUsername());
        assertEquals(0,fixtures.questionCount());
        assertEquals(0,fixtures.classCount());
        assertTrue(fixtures.permissionCount()>20);
        assertTrue(fixtures.dictItemCount()>10);
        fixtures.removeTeacherGrants();
        String password=fixtures.onlyPasswordHash();
        initializer.run();
        assertEquals(password,fixtures.onlyPasswordHash());
        assertEquals(0,fixtures.teacherGrantCount());
    }
}
