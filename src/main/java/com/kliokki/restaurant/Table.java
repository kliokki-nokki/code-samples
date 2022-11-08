package com.kliokki.restaurant;

import java.util.UUID;

public record Table(String id, int size) {
    public Table(int size) {
        this(UUID.randomUUID().toString(), size);
    }
}
