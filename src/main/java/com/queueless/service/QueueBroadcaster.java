package com.queueless.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class QueueBroadcaster {

    private final SimpMessagingTemplate template;

    public QueueBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onQueueChanged(QueueChanged event) {
        template.convertAndSend("/topic/shops/" + event.slug(), event.status());
    }
}
