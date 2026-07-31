package com.shopmanagement.crmservice.meter;

/** Increments and reads monthly API call counters (DB or Redis). */
public interface ApiCallCounter {

  long increment(String tenantId, String periodKey);

  long get(String tenantId, String periodKey);
}
