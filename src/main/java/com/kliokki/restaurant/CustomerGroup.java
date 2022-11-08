package com.kliokki.restaurant;

import java.util.UUID;

public record CustomerGroup(String id, int size) {
    public CustomerGroup(int size) {
        this(UUID.randomUUID().toString(), size);
    }
}
