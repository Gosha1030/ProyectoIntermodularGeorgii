package georgii.sytnik.thothtasks.domain.schedule;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public record TaskWithSource(TaskEntity task, byte[] sourceId) {
}