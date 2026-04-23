package com.budget.tracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "labels")
@Getter
@Setter
public class Label extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default")
    private boolean isDefault;

    @Column(name = "user_id")
    private java.util.UUID userId;
}
