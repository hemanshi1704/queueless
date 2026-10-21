package com.queueless.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_shop_status", columnList = "shop_id,status,ticket_number")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Shop shop;

    @Column(name = "ticket_number", nullable = false)
    private int number;

    @Column(nullable = false, unique = true, length = 20)
    private String token;

    @Column(nullable = false, length = 40)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TicketStatus status = TicketStatus.WAITING;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant finishedAt;

    protected Ticket() {
    }

    public Ticket(Shop shop, int number, String token, String customerName, Instant createdAt) {
        this.shop = shop;
        this.number = number;
        this.token = token;
        this.customerName = customerName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Shop getShop() {
        return shop;
    }

    public int getNumber() {
        return number;
    }

    public String getToken() {
        return token;
    }

    public String getCustomerName() {
        return customerName;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
