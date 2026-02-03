package com.example.newsapp.repositories;

import com.example.newsapp.domain.MessageEntity;
import com.example.newsapp.domain.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    Page<MessageEntity> findByStatusOrderByCreatedAtAsc(MessageStatus status, Pageable pageable);

    @Query("select m from MessageEntity m " +
           "left join fetch m.resource " +
           "order by m.createdAt desc")
    List<MessageEntity> findAllWithRelations();

    Optional<MessageEntity> findFirstByContent(String content);
}

