package com.example.newsapp.repositories;

import com.example.newsapp.domain.ResourceItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceItemRepository extends JpaRepository<ResourceItem, Long> {
}

