package com.jikchin.jikchin_app.room.repository;
import com.jikchin.jikchin_app.room.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByChatThread_CreatedBy_IdOrderByCreatedAtDesc(Long userId);
}
