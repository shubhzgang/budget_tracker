package com.budget.tracker.model;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UuidGenerationTest {

    /**
     * Dummy entity to test BaseEntity logic
     */
    private static class TestEntity extends BaseEntity {
        public void triggerOnCreate() {
            super.onCreate();
        }
    }

    @Test
    void shouldGenerateUuidV7() {
        TestEntity entity = new TestEntity();
        entity.triggerOnCreate();
        UUID id = entity.getId();

        assertNotNull(id);
        assertEquals(7, id.version(), "The generated UUID should be version 7");
    }

    @Test
    void shouldBeTimeOrdered() throws InterruptedException {
        List<UUID> uuids = new ArrayList<>();
        int count = 100;

        for (int i = 0; i < count; i++) {
            TestEntity entity = new TestEntity();
            entity.triggerOnCreate();
            uuids.add(entity.getId());
            // Small sleep is not strictly necessary for UUIDv7 monotonicity in this library,
            // but helps demonstrate time-ordering over a duration.
            if (i % 10 == 0) {
                Thread.sleep(1);
            }
        }

        for (int i = 0; i < uuids.size() - 1; i++) {
            UUID first = uuids.get(i);
            UUID second = uuids.get(i + 1);
            assertTrue(first.compareTo(second) < 0,
                    String.format("UUIDs should be time-ordered: %s should be less than %s", first, second));
        }
    }

    @Test
    void shouldNotOverwriteExistingId() {
        TestEntity entity = new TestEntity();
        UUID manualId = UUID.randomUUID();
        entity.setId(manualId);
        
        entity.triggerOnCreate();
        
        assertEquals(manualId, entity.getId(), "onCreate should not overwrite an existing ID");
    }
}
