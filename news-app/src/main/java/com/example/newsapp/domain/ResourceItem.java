package com.example.newsapp.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "resource_items")
public class ResourceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 2, max = 128)
    @Column(nullable = false, length = 128)
    private String name;

    @NotBlank
    @Size(max = 512)
    @Column(nullable = false, length = 512)
    private String url;

    @Size(max = 1024)
    @Column(length = 1024)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_processed_at", nullable = false)
    private Instant lastProcessedAt = Instant.now().minus(5, ChronoUnit.DAYS);

    @Column(nullable = false)
    private boolean pollingEnabled = false;

    @Column(nullable = false)
    private Integer pollingIntervalMinutes = 60;

    @Column
    private Integer lastPollStatus; // HTTP status code

    @Column(length = 512)
    private String lastPollError;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (lastProcessedAt == null) {
            lastProcessedAt = now.minus(5, ChronoUnit.DAYS);
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastProcessedAt() {
        return lastProcessedAt;
    }

    public void setLastProcessedAt(Instant lastProcessedAt) {
        this.lastProcessedAt = lastProcessedAt;
    }

    public boolean isPollingEnabled() {
        return pollingEnabled;
    }

    public void setPollingEnabled(boolean pollingEnabled) {
        this.pollingEnabled = pollingEnabled;
    }

    public Integer getPollingIntervalMinutes() {
        return pollingIntervalMinutes;
    }

    public void setPollingIntervalMinutes(Integer pollingIntervalMinutes) {
        this.pollingIntervalMinutes = pollingIntervalMinutes;
    }

    public Integer getLastPollStatus() {
        return lastPollStatus;
    }

    public void setLastPollStatus(Integer lastPollStatus) {
        this.lastPollStatus = lastPollStatus;
    }

    public String getLastPollError() {
        return lastPollError;
    }

    public void setLastPollError(String lastPollError) {
        this.lastPollError = lastPollError;
    }
}

