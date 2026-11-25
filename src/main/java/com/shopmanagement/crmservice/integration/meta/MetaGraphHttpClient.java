package com.shopmanagement.crmservice.integration.meta;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.shopmanagement.crmservice.config.CrmMetaProperties;

/** Official Graph calls. The access token is a query parameter and is never logged. */
@Component
public class MetaGraphHttpClient implements MetaGraphClient {

  private static final String FULL_FIELDS =
      "created_time,id,ad_id,form_id,field_data,campaign_id,campaign_name,ad_name,adset_id,adset_name";
  private static final String COMPACT_FIELDS = "created_time,id,form_id,field_data";

  private final CrmMetaProperties properties;
  private final RestClient http = RestClient.create();

  public MetaGraphHttpClient(CrmMetaProperties properties) {
    this.properties = properties;
  }

  @Override
  public Token exchangeCode(String code, String redirectUri) {
    Map<String, Object> body =
        get(
            UriComponentsBuilder.fromUriString(graph("/oauth/access_token"))
                .queryParam("client_id", properties.getAppId())
                .queryParam("client_secret", properties.getAppSecret())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code", code)
                .encode()
                .build()
                .toUriString());
    return token(body);
  }

  @Override
  public Token exchangeLongLived(String shortToken) {
    Map<String, Object> body =
        get(
            UriComponentsBuilder.fromUriString(graph("/oauth/access_token"))
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", properties.getAppId())
                .queryParam("client_secret", properties.getAppSecret())
                .queryParam("fb_exchange_token", shortToken)
                .encode()
                .build()
                .toUriString());
    return token(body);
  }

  @Override
  public List<Named> businesses(String userToken) {
    return namedList(get(graph("/me/businesses") + "?access_token=" + encode(userToken)));
  }

  @Override
  public List<Page> pages(String userToken) {
    Map<String, Object> body =
        get(graph("/me/accounts") + "?fields=id,name,access_token&access_token=" + encode(userToken));
    List<Page> pages = new ArrayList<>();
    for (Map<String, Object> row : data(body)) {
      pages.add(new Page(text(row.get("id")), text(row.get("name")), text(row.get("access_token"))));
    }
    return pages;
  }

  @Override
  public List<Named> forms(String pageId, String pageToken) {
    return namedList(
        get(graph("/" + pageId + "/leadgen_forms") + "?fields=id,name&access_token=" + encode(pageToken)));
  }

  @Override
  public void subscribeLeadgen(String pageId, String pageToken) {
    post(
        graph("/" + pageId + "/subscribed_apps")
            + "?subscribed_fields=leadgen&access_token="
            + encode(pageToken));
  }

  @Override
  public LeadDetails fetchLead(String leadgenId, String pageToken, boolean compact) {
    String fields = compact ? COMPACT_FIELDS : FULL_FIELDS;
    Map<String, Object> body =
        get(graph("/" + leadgenId) + "?fields=" + fields + "&access_token=" + encode(pageToken));
    return new LeadDetails(
        text(body.get("id")),
        text(body.get("form_id")),
        text(body.get("campaign_id")),
        text(body.get("campaign_name")),
        text(body.get("ad_id")),
        text(body.get("ad_name")),
        text(body.get("adset_id")),
        text(body.get("adset_name")),
        text(body.get("page_id")),
        firstText(body, "instagram_actor_id", "instagram_user_id"),
        fields(body.get("field_data")));
  }

  @Override
  public boolean tokenWorks(String pageToken) {
    try {
      get(graph("/me") + "?fields=id&access_token=" + encode(pageToken));
      return true;
    } catch (MetaGraphException ex) {
      return false;
    }
  }

  private String graph(String path) {
    String version = properties.getGraphVersion() == null ? "v21.0" : properties.getGraphVersion().trim();
    return "https://graph.facebook.com/" + version + path;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> get(String uri) {
    try {
      return http.get().uri(URI.create(uri)).retrieve().body(Map.class);
    } catch (RestClientResponseException ex) {
      throw new MetaGraphException(ex.getStatusCode().value(), clip(ex.getResponseBodyAsString()));
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> post(String uri) {
    try {
      return http.post().uri(URI.create(uri)).retrieve().body(Map.class);
    } catch (RestClientResponseException ex) {
      throw new MetaGraphException(ex.getStatusCode().value(), clip(ex.getResponseBodyAsString()));
    }
  }

  private static Token token(Map<String, Object> body) {
    if (body == null || text(body.get("access_token")) == null) {
      throw new MetaGraphException(502, "Facebook did not return a token");
    }
    Object expires = body.get("expires_in");
    Long seconds = expires instanceof Number number ? number.longValue() : null;
    return new Token(text(body.get("access_token")), seconds);
  }

  private static List<Named> namedList(Map<String, Object> body) {
    List<Named> rows = new ArrayList<>();
    for (Map<String, Object> row : data(body)) {
      rows.add(new Named(text(row.get("id")), text(row.get("name"))));
    }
    return rows;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> data(Map<String, Object> body) {
    if (body == null || !(body.get("data") instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        rows.add((Map<String, Object>) map);
      }
    }
    return rows;
  }

  @SuppressWarnings("unchecked")
  private static List<LeadField> fields(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<LeadField> fields = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      List<String> values = new ArrayList<>();
      Object rawValues = map.get("values");
      if (rawValues instanceof List<?> valueList) {
        for (Object value : valueList) {
          if (value != null && !String.valueOf(value).isBlank()) {
            values.add(String.valueOf(value).trim());
          }
        }
      }
      fields.add(new LeadField(text(map.get("name")), values));
    }
    return fields;
  }

  private static String firstText(Map<String, Object> body, String... keys) {
    for (String key : keys) {
      String value = text(body.get(key));
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static String encode(String token) {
    return java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String clip(String body) {
    if (body == null || body.isBlank()) {
      return "Facebook request failed";
    }
    String text = body.replaceAll("access_token=[^&\\s]+", "access_token=redacted");
    return text.length() <= 300 ? text : text.substring(0, 300);
  }
}
