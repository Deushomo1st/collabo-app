package com.collabo.backend.dto;

import java.util.List;

/** One page of a table, read-only. Secret columns arrive masked; a null cell is an SQL null. */
public record TablePage(List<String> columns, List<List<String>> rows, long total) {
}
