package com.athena.lms.collections.repository;

import com.athena.lms.collections.entity.CollectionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CollectionActionRepository extends JpaRepository<CollectionAction, UUID> {

    List<CollectionAction> findByCaseIdOrderByPerformedAtDesc(UUID caseId);
}
