package com.example.cinema.repository;

import com.example.cinema.domain.SearchDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchDocumentRepository extends JpaRepository<SearchDocument, Long> {

    Optional<SearchDocument> findBySourceKey(String sourceKey);

    List<SearchDocument> findBySourceTypeAndActiveTrue(SearchDocument.SourceType sourceType);
}
