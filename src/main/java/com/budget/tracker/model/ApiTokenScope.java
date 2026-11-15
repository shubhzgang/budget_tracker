package com.budget.tracker.model;

public enum ApiTokenScope {
    READ_ONLY("bt_ro_"),
    FULL_ACCESS("bt_rw_");

    private final String prefix;

    ApiTokenScope(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
