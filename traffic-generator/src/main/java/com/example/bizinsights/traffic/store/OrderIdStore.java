package com.example.bizinsights.traffic.store;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class OrderIdStore {

    private final List<String> successfulOrderIds = new CopyOnWriteArrayList<>();

    public void add(String orderId) {
        successfulOrderIds.add(orderId);
    }

    public Optional<String> pickRandom() {
        if (successfulOrderIds.isEmpty()) {
            return Optional.empty();
        }
        int idx = ThreadLocalRandom.current().nextInt(successfulOrderIds.size());
        return Optional.of(successfulOrderIds.get(idx));
    }

    public int size() {
        return successfulOrderIds.size();
    }
}
