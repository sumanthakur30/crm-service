package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmJobEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmJobRepository;

/** Records the capture step. Pending rows have no handler until a later connector. */
@Service
public class CaptureJobService {

  static final String WEB_CAPTURE = "WEB_CAPTURE";

  private final CrmJobRepository jobRepository;

  public CaptureJobService(CrmJobRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  @Transactional
  public void done(String tenantId, Long eventId) {
    CrmJobEntity job = new CrmJobEntity();
    job.setTenantId(tenantId);
    job.setJobType(WEB_CAPTURE);
    job.setRefId(eventId);
    job.setStatus("DONE");
    job.setRunAt(Instant.now());
    job.setAttempts(1);
    jobRepository.save(job);
  }

  @Transactional
  public int processDue() {
    List<CrmJobEntity> due =
        jobRepository.findByStatusAndRunAtLessThanEqual("PENDING", Instant.now(), PageRequest.of(0, 20));
    for (CrmJobEntity job : due) {
      if ("META_LEAD_FETCH".equals(job.getJobType())) {
        continue;
      }
      job.setAttempts(job.getAttempts() + 1);
      job.setStatus("FAILED");
      job.setLockedAt(null);
      job.setLastError("No handler is registered for " + job.getJobType());
      jobRepository.save(job);
    }
    return due.size();
  }
}
