package com.queueless.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueless.service.QueueService;
import com.queueless.web.Dtos.CreateShopRequest;
import com.queueless.web.Dtos.OwnerSnapshot;
import com.queueless.web.Dtos.ShopSummary;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/owner/shops")
public class OwnerController {

    private final QueueService queues;

    public OwnerController(QueueService queues) {
        this.queues = queues;
    }

    @GetMapping
    List<ShopSummary> list(@AuthenticationPrincipal Long ownerId) {
        return queues.listShops(ownerId);
    }

    @PostMapping
    ShopSummary create(@AuthenticationPrincipal Long ownerId, @Valid @RequestBody CreateShopRequest req) {
        return queues.createShop(ownerId, req);
    }

    @GetMapping("/{id}/queue")
    OwnerSnapshot snapshot(@AuthenticationPrincipal Long ownerId, @PathVariable Long id) {
        return queues.snapshot(ownerId, id);
    }

    @PostMapping("/{id}/next")
    OwnerSnapshot next(@AuthenticationPrincipal Long ownerId, @PathVariable Long id) {
        return queues.callNext(ownerId, id);
    }

    @PostMapping("/{id}/skip")
    OwnerSnapshot skip(@AuthenticationPrincipal Long ownerId, @PathVariable Long id) {
        return queues.skipCurrent(ownerId, id);
    }

    @PostMapping("/{id}/toggle-open")
    OwnerSnapshot toggleOpen(@AuthenticationPrincipal Long ownerId, @PathVariable Long id) {
        return queues.toggleOpen(ownerId, id);
    }
}
