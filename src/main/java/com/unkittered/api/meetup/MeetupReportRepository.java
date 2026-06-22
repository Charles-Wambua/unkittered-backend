package com.unkittered.api.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MeetupReportRepository extends JpaRepository<MeetupReport, UUID> {
}
