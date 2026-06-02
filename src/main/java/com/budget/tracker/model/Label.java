package com.budget.tracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "labels")
@Getter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Label extends BaseEntity {

    @Column(nullable = false)
    private String name;

    public void setName(String name) {
        if (name != null && name.contains("|")) {
            throw new IllegalArgumentException("Label name cannot contain the pipe character '|'");
        }
        this.name = name;
    }

    @Column(name = "is_default")
    @Setter
    @com.fasterxml.jackson.annotation.JsonProperty("isDefault")
    private boolean isDefault;

    @Column(name = "user_id")
    @Setter
    private java.util.UUID userId;
}
