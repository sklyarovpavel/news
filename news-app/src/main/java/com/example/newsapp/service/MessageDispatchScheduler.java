package com.example.newsapp.service;

import com.example.newsapp.domain.MessageEntity;
import com.example.newsapp.domain.MessageStatus;
import com.example.newsapp.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MessageDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(MessageDispatchScheduler.class);

    private final MessageRepository messageRepository;
    private final TelegramSenderService telegramSenderService;

    @Value("${app.dispatch.enabled:true}")
    private boolean enabled;

    @Value("${app.dispatch.batchSize:20}")
    private int batchSize;

    public MessageDispatchScheduler(MessageRepository messageRepository,
                                    TelegramSenderService telegramSenderService) {
        this.messageRepository = messageRepository;
        this.telegramSenderService = telegramSenderService;
    }

    @Scheduled(fixedDelayString = "${app.dispatch.fixedDelayMs:10000}")
    @Transactional
    public void dispatchNotSentMessages() {
        if (!enabled) {
            return;
        }
        if (!telegramSenderService.isConfigured()) {
            log.debug("Telegram not configured; dispatcher idle");
            return;
        }
        Page<MessageEntity> page = messageRepository.findByStatusOrderByCreatedAtAsc(
                MessageStatus.NOT_SENT, PageRequest.of(0, Math.max(1, batchSize)));
        if (page.isEmpty()) {
            return;
        }
        List<MessageEntity> batch = page.getContent();

        for (MessageEntity message : batch) {
            boolean allSent = sendEachLine(message.getContent());
            if (allSent) {
                message.setStatus(MessageStatus.SENT);
            } else {
                // оставляем NOT_SENT для повторной попытки
                log.warn("Сообщение id={} отправлено не полностью, повторим позже", message.getId());
            }
        }
        // Flush via transaction commit
    }

    private boolean sendEachLine(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        List<String> lines = Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        boolean allOk = true;
        for (String line : lines) {
            boolean ok = telegramSenderService.sendText(line);
            allOk = allOk && ok;
        }
        return allOk;
    }
}

