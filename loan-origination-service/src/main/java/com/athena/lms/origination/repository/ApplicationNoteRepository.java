package com.athena.lms.origination.repository;

import com.athena.lms.origination.entity.ApplicationNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, UUID> {
}
