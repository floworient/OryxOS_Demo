package io.oryxos.storage.repository;

import io.oryxos.storage.entity.ScheduledTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, String> {

    Optional<ScheduledTask> findByScheduleIdAndRetiredFalse(String scheduleId);

    boolean existsByScheduleIdAndRetiredFalseAndEnabledTrue(String scheduleId);

    List<ScheduledTask> findAllByRetiredFalseOrderByProfileNameAscScheduleKeyAsc();

    @Modifying
    @Query(value = """
            INSERT INTO scheduled_tasks (schedule_id, profile_name, schedule_key, display_name,
                cron, zone, message, updated_at)
            VALUES (:id, :profile, :key, :key, :cron, :zone, :message, :now)
            ON CONFLICT(profile_name, schedule_key) DO UPDATE SET
                display_name = excluded.display_name, cron = excluded.cron, zone = excluded.zone,
                message = excluded.message, retired = FALSE, updated_at = excluded.updated_at
            """, nativeQuery = true)
    void reconcile(String id, String profile, String key, String cron, String zone, String message, Instant now);

    @Modifying
    @Query("""
            update ScheduledTask t set t.retired = true, t.nextRunAt = null, t.updatedAt = :now
            where t.scheduleId = :id and t.retired = false
            """)
    void retire(String id, Instant now);

    @Modifying
    @Query("""
            update ScheduledTask t set t.enabled = :enabled, t.updatedAt = :now
            where t.scheduleId = :id and t.retired = false
            """)
    int setEnabled(String id, boolean enabled, Instant now);

    @Modifying
    @Query("""
            update ScheduledTask t set t.nextRunAt = :next, t.updatedAt = :now
            where t.scheduleId = :id and t.retired = false
            """)
    void updateNextRun(String id, Instant next, Instant now);

    @Modifying
    @Query("""
            update ScheduledTask t set t.runCount = t.runCount + 1, t.lastRunAt = :started,
                t.lastStatus = :status, t.updatedAt = :now where t.scheduleId = :id
            """)
    int complete(String id, Instant started, String status, Instant now);
}
