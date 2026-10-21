package com.queueless.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Owner owner;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    /** Exponential moving average of how long one customer takes, in seconds. */
    @Column(nullable = false)
    private double avgServiceSeconds;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    @Column(nullable = false)
    private int ticketCounter;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Shop() {
    }

    public Shop(Owner owner, String name, String slug, double avgServiceSeconds) {
        this.owner = owner;
        this.name = name;
        this.slug = slug;
        this.avgServiceSeconds = avgServiceSeconds;
    }

    /** Callers must hold a write lock on this row (see ShopRepository#lockById). */
    public int nextNumber() {
        return ++ticketCounter;
    }

    public Long getId() {
        return id;
    }

    public Owner getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public double getAvgServiceSeconds() {
        return avgServiceSeconds;
    }

    public void setAvgServiceSeconds(double avgServiceSeconds) {
        this.avgServiceSeconds = avgServiceSeconds;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
}
