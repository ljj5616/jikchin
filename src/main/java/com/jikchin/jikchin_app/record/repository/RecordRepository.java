package com.jikchin.jikchin_app.record.repository;

import com.jikchin.jikchin_app.record.domain.Record;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordRepository extends JpaRepository<Record, Long> {
    void deleteByUser_Id(Long userId);
}
