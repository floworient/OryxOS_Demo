package io.oryxos.storage.repository;

import io.oryxos.storage.entity.TaskExecution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    List<TaskExecution> findAllByScheduleIdOrderByStartedAtDescIdDesc(String scheduleId);

    List<TaskExecution> findAllByScheduleIdOrderByIdAsc(String scheduleId);
}
