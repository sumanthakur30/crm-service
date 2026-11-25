package com.shopmanagement.crmservice.integration.meta;

import java.util.List;
import java.util.Map;

public interface MetaGraphClient {

  record Token(String accessToken, Long expiresInSeconds) {}

  record Named(String id, String name) {}

  record Page(String id, String name, String accessToken) {}

  record LeadField(String name, List<String> values) {}

  record LeadDetails(
      String id,
      String formId,
      String campaignId,
      String campaignName,
      String adId,
      String adName,
      String adsetId,
      String adsetName,
      String pageId,
      String instagramActorId,
      List<LeadField> fieldData) {}

  Token exchangeCode(String code, String redirectUri);

  Token exchangeLongLived(String shortToken);

  List<Named> businesses(String userToken);

  List<Page> pages(String userToken);

  List<Named> forms(String pageId, String pageToken);

  void subscribeLeadgen(String pageId, String pageToken);

  LeadDetails fetchLead(String leadgenId, String pageToken, boolean compact);

  boolean tokenWorks(String pageToken);
}
