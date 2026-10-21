package com.queueless.web;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueless.service.QueueService;
import com.queueless.web.Dtos.JoinRequest;
import com.queueless.web.Dtos.PublicStatus;
import com.queueless.web.Dtos.TicketView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final QueueService queues;

    public PublicController(QueueService queues) {
        this.queues = queues;
    }

    @GetMapping("/shops/{slug}")
    PublicStatus status(@PathVariable String slug) {
        return queues.publicStatus(slug);
    }

    @PostMapping("/shops/{slug}/join")
    TicketView join(@PathVariable String slug, @Valid @RequestBody JoinRequest req) {
        return queues.join(slug, req.name());
    }

    @GetMapping("/tickets/{token}")
    TicketView ticket(@PathVariable String token) {
        return queues.ticket(token);
    }

    @DeleteMapping("/tickets/{token}")
    TicketView cancel(@PathVariable String token) {
        return queues.cancel(token);
    }
}
