package com.zhikao.modules.workflow;
import com.zhikao.cache.NamespacedKeySerializer;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class NamespacedKeySerializerTest {
    @Test void separatesEnvironmentsAndKeepsLogicalKeysStable() {
        var dev=new NamespacedKeySerializer("zhikao:dev:");
        var prod=new NamespacedKeySerializer("zhikao:prod:");
        assertEquals("zhikao:dev:satoken:login:1",new String(dev.serialize("satoken:login:1"),StandardCharsets.UTF_8));
        assertNotEquals(new String(dev.serialize("k"),StandardCharsets.UTF_8),new String(prod.serialize("k"),StandardCharsets.UTF_8));
        assertEquals("satoken:login:1",dev.deserialize(dev.serialize("satoken:login:1")));
        assertNull(dev.serialize(null));assertNull(dev.deserialize(null));
        assertThrows(IllegalArgumentException.class,()->new NamespacedKeySerializer(""));
    }
}
