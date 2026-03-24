package com.example.bizinsights.traffic.store;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderIdStoreTest {

    @Test
    void pickRandomReturnsEmptyWhenStoreIsEmpty() {
        OrderIdStore store = new OrderIdStore();
        assertEquals(Optional.empty(), store.pickRandom());
    }

    @Test
    void pickRandomReturnsAddedOrderId() {
        OrderIdStore store = new OrderIdStore();
        store.add("order-1");

        Optional<String> result = store.pickRandom();
        assertTrue(result.isPresent());
        assertEquals("order-1", result.get());
    }

    @Test
    void sizeReflectsAddedItems() {
        OrderIdStore store = new OrderIdStore();
        assertEquals(0, store.size());

        store.add("order-1");
        store.add("order-2");
        assertEquals(2, store.size());
    }

    @Test
    void pickRandomReturnsOneOfAddedOrderIds() {
        OrderIdStore store = new OrderIdStore();
        store.add("order-1");
        store.add("order-2");
        store.add("order-3");

        for (int i = 0; i < 20; i++) {
            Optional<String> result = store.pickRandom();
            assertTrue(result.isPresent());
            assertTrue(result.get().startsWith("order-"));
        }
    }
}
