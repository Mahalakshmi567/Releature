package com.example.flags;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "target_rules")
public class TargetRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "environment_value_id", nullable = false, updatable = false)
    private UUID environmentValueId;

    @Column(nullable = false, length = 50)
    private String attribute;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "target_rule_values", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "match_value", nullable = false, length = 100)
    private List<String> matchValues = new ArrayList<>();

    protected TargetRule() {}

    public TargetRule(UUID environmentValueId, String attribute, List<String> matchValues) {
        this.environmentValueId = environmentValueId;
        this.attribute = attribute;
        this.matchValues = new ArrayList<>(matchValues);
    }

    public UUID getId() { return id; }
    public UUID getEnvironmentValueId() { return environmentValueId; }
    public String getAttribute() { return attribute; }
    public List<String> getMatchValues() { return matchValues; }
}
