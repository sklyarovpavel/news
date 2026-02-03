package com.example.newsapp.service;

import com.example.newsapp.domain.MessageEntity;
import com.example.newsapp.domain.ResourceItem;
import com.example.newsapp.repositories.MessageRepository;
import com.example.newsapp.repositories.ResourceItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class IngestPersistenceService {

    private final MessageRepository messageRepository;
    private final ResourceItemRepository resourceItemRepository;

    public IngestPersistenceService(MessageRepository messageRepository,
                                    ResourceItemRepository resourceItemRepository) {
        this.messageRepository = messageRepository;
        this.resourceItemRepository = resourceItemRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMessage(MessageEntity message) {
        messageRepository.save(message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateResourceStatus(Long resourceId, boolean success, Integer lastStatus, String lastError) {
        ResourceItem resource = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: id=" + resourceId));
        if (success) {
            resource.setLastProcessedAt(Instant.now());
        }
        resource.setLastPollStatus(lastStatus);
        resource.setLastPollError(lastError);
        resourceItemRepository.save(resource);
    }
}

