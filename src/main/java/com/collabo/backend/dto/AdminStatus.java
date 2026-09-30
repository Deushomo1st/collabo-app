package com.collabo.backend.dto;

import java.time.Instant;
import java.util.Map;

/** The console's status strip. The three flags are the ones that bite in production: mail key, secure cookies, test accounts. */
public record AdminStatus(Instant serverTime, long uptimeSeconds, String database, boolean databaseOk,
                          boolean mailConfigured, boolean secureCookies, boolean testAccounts, Map<String, Long> counts) {
}
