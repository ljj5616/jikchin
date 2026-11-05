package com.jikchin.jikchin_app.room.repository;

import com.jikchin.jikchin_app.room.domain.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    @Override
    @EntityGraph(attributePaths = {"chatThread", "chatThread.createdBy", "homeTeam", "awayTeam"})
    Optional<Room> findById(Long id);

    @EntityGraph(attributePaths = {"chatThread", "chatThread.createdBy", "homeTeam", "awayTeam"})
    List<Room> findByGameDateGreaterThanEqualOrderByGameDateAscIdAsc(LocalDate date);

    @EntityGraph(attributePaths = {"chatThread", "chatThread.createdBy", "homeTeam", "awayTeam"})
    @Query("""
            select room from Room room
            where exists (
                select participant.id from ChatParticipant participant
                where participant.chatThread = room.chatThread and participant.user.id = :userId
            )
            order by room.createdAt desc, room.id desc
            """)
    List<Room> findParticipatingRooms(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from Room room where room.id = :roomId")
    Optional<Room> findByIdForUpdate(@Param("roomId") Long roomId);
}
