package com.shopmanagement.crmservice.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.AssignRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.ImportResult;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;

@Service
public class LeadImportService {

  private static final int MAX_ROWS = 5000;

  private final CrmLeadService leadService;
  private final AssignmentService assignmentService;
  private final CrmEntitlementGuard entitlementGuard;

  public LeadImportService(
      CrmLeadService leadService,
      AssignmentService assignmentService,
      CrmEntitlementGuard entitlementGuard) {
    this.leadService = leadService;
    this.assignmentService = assignmentService;
    this.entitlementGuard = entitlementGuard;
  }

  @Transactional
  public ImportResult importFile(MultipartFile file, boolean assignRoundRobin, String teamId) {
    entitlementGuard.requireCrmAccess();
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
    }
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    try {
      List<Map<String, String>> rows;
      if (name.endsWith(".xlsx")) {
        rows = parseXlsx(file.getInputStream());
      } else if (name.endsWith(".csv") || name.endsWith(".txt") || name.isBlank()) {
        rows = parseCsv(file.getInputStream());
      } else {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Unsupported file type. Use .csv or .xlsx");
      }
      return importRows(rows, assignRoundRobin, teamId);
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse file: " + ex.getMessage());
    }
  }

  private ImportResult importRows(List<Map<String, String>> rows, boolean assignRoundRobin, String teamId) {
    if (rows.size() > MAX_ROWS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max " + MAX_ROWS + " rows per import");
    }
    int created = 0;
    int skipped = 0;
    List<String> errors = new ArrayList<>();

    for (int i = 0; i < rows.size(); i++) {
      Map<String, String> row = rows.get(i);
      String title = first(row, "title", "lead_title", "name", "lead name");
      if (title == null || title.isBlank()) {
        skipped++;
        errors.add("row " + (i + 2) + ": missing title/name");
        continue;
      }
      try {
        LeadUpsert upsert =
            new LeadUpsert(
                title,
                first(row, "display_name", "contact", "contact_name"),
                first(row, "company_name", "company", "organization"),
                first(row, "email", "email_id"),
                first(row, "phone", "mobile", "mobile_number"),
                first(row, "source_code", "source", "lead_source"),
                first(row, "status"),
                first(row, "priority"),
                parseScore(first(row, "score")),
                first(row, "owner_user_id", "owner"),
                teamId != null && !teamId.isBlank() ? teamId : first(row, "team_id", "team"),
                parseAmount(first(row, "amount", "value", "deal_value")),
                first(row, "currency"),
                null,
                null,
                extraAttributes(row),
                Map.of(),
                first(row, "form_key"));
        LeadResponse lead = leadService.create(upsert);
        if (assignRoundRobin) {
          assignmentService.assign(
              lead.id(), new AssignRequest("ROUND_ROBIN", null, blankOr(teamId, lead.teamId())));
        }
        created++;
      } catch (Exception ex) {
        skipped++;
        errors.add("row " + (i + 2) + ": " + ex.getMessage());
      }
    }
    return new ImportResult(rows.size(), created, skipped, errors.stream().limit(50).toList());
  }

  private static String blankOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static Map<String, Object> extraAttributes(Map<String, String> row) {
    Map<String, Object> attrs = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : row.entrySet()) {
      String key = e.getKey().toLowerCase(Locale.ROOT).trim();
      if (isKnown(key) || e.getValue() == null || e.getValue().isBlank()) {
        continue;
      }
      attrs.put(e.getKey().trim(), e.getValue().trim());
    }
    return attrs;
  }

  private static boolean isKnown(String key) {
    return switch (key) {
      case "title",
          "lead_title",
          "name",
          "lead name",
          "display_name",
          "contact",
          "contact_name",
          "company_name",
          "company",
          "organization",
          "email",
          "email_id",
          "phone",
          "mobile",
          "mobile_number",
          "source_code",
          "source",
          "lead_source",
          "status",
          "priority",
          "score",
          "owner_user_id",
          "owner",
          "team_id",
          "team",
          "amount",
          "value",
          "deal_value",
          "currency",
          "form_key" -> true;
      default -> false;
    };
  }

  private static String first(Map<String, String> row, String... keys) {
    for (String key : keys) {
      for (Map.Entry<String, String> e : row.entrySet()) {
        if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(key)) {
          return e.getValue();
        }
      }
    }
    return null;
  }

  private static Integer parseScore(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static BigDecimal parseAmount(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(raw.trim().replace(",", ""));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static List<Map<String, String>> parseCsv(InputStream in) throws Exception {
    List<Map<String, String>> rows = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(reader)) {
      for (CSVRecord record : parser) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String header : parser.getHeaderNames()) {
          map.put(header, record.isMapped(header) ? record.get(header) : null);
        }
        rows.add(map);
      }
    }
    return rows;
  }

  private static List<Map<String, String>> parseXlsx(InputStream in) throws Exception {
    List<Map<String, String>> rows = new ArrayList<>();
    DataFormatter formatter = new DataFormatter();
    try (Workbook workbook = new XSSFWorkbook(in)) {
      Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
      if (sheet == null) {
        return rows;
      }
      Iterator<Row> it = sheet.iterator();
      if (!it.hasNext()) {
        return rows;
      }
      Row headerRow = it.next();
      List<String> headers = new ArrayList<>();
      for (Cell cell : headerRow) {
        headers.add(formatter.formatCellValue(cell).trim());
      }
      while (it.hasNext()) {
        Row row = it.next();
        Map<String, String> map = new LinkedHashMap<>();
        boolean any = false;
        for (int c = 0; c < headers.size(); c++) {
          Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
          String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
          if (!value.isEmpty()) {
            any = true;
          }
          map.put(headers.get(c), value);
        }
        if (any) {
          rows.add(map);
        }
      }
    }
    return rows;
  }
}
