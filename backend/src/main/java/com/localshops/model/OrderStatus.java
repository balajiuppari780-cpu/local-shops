package com.localshops.model;

public enum OrderStatus {
    PLACED,
    ACCEPTED,
    PACKED,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        if (this == PLACED) {
            return target == ACCEPTED || target == CANCELLED;
        }
        if (this == ACCEPTED) {
            return target == PACKED || target == CANCELLED;
        }
        if (this == PACKED) {
            return target == DELIVERED;
        }
        return false;
    }
}

