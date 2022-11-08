package com.kliokki.restaurant;

public interface SeatingManager {
    void arrives(CustomerGroup group);
    void leaves(CustomerGroup group);
    Table locate(CustomerGroup group);
}
