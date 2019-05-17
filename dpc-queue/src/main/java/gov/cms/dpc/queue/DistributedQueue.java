package gov.cms.dpc.queue;

import gov.cms.dpc.queue.annotations.HealthCheckQuery;
import gov.cms.dpc.queue.exceptions.JobQueueFailure;
import gov.cms.dpc.queue.exceptions.JobQueueUnhealthy;
import gov.cms.dpc.queue.models.JobResult;
import gov.cms.dpc.queue.models.JobModel;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Implements a distributed {@link JobQueue} using Redis and Postgres
 */
public class DistributedQueue implements JobQueue {

    private static final Logger logger = LoggerFactory.getLogger(DistributedQueue.class);
    public static final String CLUSTER_NOT_RESPONDING = "Redis cluster is not responding to pings.";
    public static final String REDIS_UNHEALTHY = "Redis cluster is unhealthy";
    public static final String DB_UNHEALTHY = "Database cluster is not responding";

    private final RedissonClient client;
    private final Queue<UUID> queue;
    private final SessionFactory factory;
    private final String healthQuery;

    @Inject
    DistributedQueue(RedissonClient client, SessionFactory factory, @HealthCheckQuery String healthQuery) {
        this.client = client;
        this.queue = client.getQueue("jobqueue");
        this.factory = factory;
        this.healthQuery = healthQuery;
    }

    @Override
    public void submitJob(UUID jobID, JobModel data) {
        assert (jobID.equals(data.getJobID()) && data.getStatus() == JobStatus.QUEUED);
        final OffsetDateTime submitTime = OffsetDateTime.now(ZoneOffset.UTC);
        logger.debug("Adding jobID {} to the queue at {} with for provider {}.",
                jobID, submitTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                data.getProviderID());
        data.setSubmitTime(submitTime);
        // Persist the job in postgres
        try (final Session session = this.factory.openSession()) {
            final Transaction tx = session.beginTransaction();
            try {
                session.persist(data);
                tx.commit();
            } catch (Exception e) {
                logger.error("Cannot add job to database", e);
                tx.rollback();
                throw new JobQueueFailure(jobID, e);
            }
        }
        // Add to the redis queue
        // Offer?
        boolean added;
        try {
            added = this.queue.add(jobID);
        } catch (RuntimeException e) {
            throw new JobQueueFailure(jobID, e);
        }

        if (!added) {
            logger.error("Job {} not submitted to queue.", jobID);
            throw new JobQueueFailure(jobID, "Unable to add to queue.");
        }
    }

    @Override
    public Optional<JobModel> getJob(UUID jobID) {
        // Get from Postgres
        try (final Session session = this.factory.openSession()) {

            final Transaction tx = session.beginTransaction();
            try {
                final JobModel jobModel = session.get(JobModel.class, jobID);
                if (jobModel == null) {
                    return Optional.empty();
                }
                session.refresh(jobModel);
                return Optional.ofNullable(jobModel);
            } finally {
                tx.commit();
            }
        }
    }

    @Override
    public Optional<Pair<UUID, JobModel>> workJob() {
        final UUID jobID = this.queue.poll();
        if (jobID == null) {
            return Optional.empty();
        }
        final OffsetDateTime startTime = OffsetDateTime.now(ZoneOffset.UTC);
        final JobModel updatedJob = updateModel(jobID, (JobModel job) -> {
            // Verify that the job is in progress, otherwise fail
            if (job.getStatus() != JobStatus.QUEUED) {
                throw new JobQueueFailure(jobID, String.format("Cannot work job in state: %s", job.getStatus()));
            }
            // Update the status and start time
            job.setStatus(JobStatus.RUNNING);
            job.setStartTime(startTime);
        });
        //noinspection OptionalGetWithoutIsPresent
        final var delay = Duration.between(updatedJob.getSubmitTime().get(), updatedJob.getStartTime().get());
        logger.debug("Started job {} at {}, waited in queue for {} seconds",
                jobID,
                startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                delay.toSeconds());

        return Optional.of(new Pair<>(jobID, updatedJob));
    }

    @Override
    public void completeJob(UUID jobID, JobStatus status, List<JobResult> jobResults) {
        assert (status == JobStatus.COMPLETED || status == JobStatus.FAILED);
        final OffsetDateTime completionTime = OffsetDateTime.now(ZoneOffset.UTC);
        final JobModel updatedJob = updateModel(jobID, (JobModel job) -> {
            // Verify that the job is running
            if (job.getStatus() != JobStatus.RUNNING) {
                throw new JobQueueFailure(jobID, String.format("Cannot complete job in state: %s", job.getStatus()));
            }
            // Set the status and the completion time
            job.setStatus(status);
            job.setCompleteTime(completionTime);
            job.setJobResults(jobResults);
        });
        final Duration workDuration = Duration.between(updatedJob.getStartTime().get(), updatedJob.getCompleteTime().get());
        logger.debug("Completed job {} at {} with status {} and duration {} seconds",
                jobID,
                completionTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                status,
                workDuration.toSeconds());
    }

    @Override
    public long queueSize() {
        return this.queue.size();
    }

    @Override
    public String queueType() {
        return "Redis Queue";
    }

    @Override
    public void assertHealthy() {
        // Redisson first

        try {
            if (!this.client.getNodesGroup().pingAll()) {
                throw new JobQueueUnhealthy(CLUSTER_NOT_RESPONDING);
            }
        } catch (Exception e) {
            throw new JobQueueUnhealthy(REDIS_UNHEALTHY, e);
        }

        // Now the DB
        try (final Session session = this.factory.openSession()) {

            try {
                @SuppressWarnings("rawtypes") final Query healthCheck = session.createSQLQuery(healthQuery);
                healthCheck.getFirstResult();
            } catch (Exception e) {
                throw new JobQueueUnhealthy(DB_UNHEALTHY, e);
            }
        }
    }

    /**
     * Fetch the job from the database, call the mutator function to update the job, and save the update in the database.
     *
     * @param jobID   - The jobID to fetch from the database.
     * @param mutator - Function called to update the job. If the mutator throws, rollback the transaction.
     * @return the {@link JobModel} after the a successful
     */
    private JobModel updateModel(UUID jobID, Consumer<JobModel> mutator) {
        try (final Session session = this.factory.openSession()) {

            final Transaction tx = session.beginTransaction();
            try {
                final JobModel jobModel = session.get(JobModel.class, jobID);
                if (jobModel == null) {
                    throw new JobQueueFailure(jobID, "Unable to fetch job from database");
                }
                if (!jobModel.isValid()) {
                    throw new JobQueueFailure(jobID, "Job fetched with an invalid values");
                }

                // Mutate the model
                mutator.accept(jobModel);
                // There seems to be an issue with cascading the updates to the JobResult entity
                // Clearing the session cache seems to resolve the issue, otherwise we get an EntityExistsException
                // Not entirely sure why this is happening, but this fix should be simple and performant
                session.clear();
                session.update(jobModel);
                tx.commit();
                return jobModel;
            } catch (Exception e) {
                tx.rollback();
                logger.error("Unable to update job model", e);
                throw new JobQueueFailure(jobID, e);
            }
        }
    }
}