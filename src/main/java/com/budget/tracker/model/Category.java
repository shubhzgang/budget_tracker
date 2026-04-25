package com.budget.tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String icon;

    @Column(name = "is_default")
    private boolean isDefault;

    @Column(name = "user_id")
    private java.util.UUID userId;
}