package com.example.flags;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "environments")
public class Environment {

    @Id
    private UUID id;

    @Column(name = "env_key", nullable = false, unique = true, length = 30)
    private String key;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() { return id; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
}
