package com.collabo.backend.service;

import com.collabo.backend.exception.InvalidProfileException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Hashtags on a post: lower case, no leading hash sign, letters/digits/underscore, 2 to 30 long, at most 10, no repeats. */
final class Hashtags {
    static final int MAX = 10;
    private static final Pattern OK = Pattern.compile("[\\p{L}\\p{N}_]{2,30}");
    private Hashtags() {}

    static List<String> clean(List<String> raw) {
        if (raw == null) return List.of();
        Set<String> out = new LinkedHashSet<>();
        for (String r : raw) {
            String t = r == null ? "" : r.trim().replaceFirst("^#+", "").toLowerCase();
            if (t.isEmpty()) continue;
            if (!OK.matcher(t).matches()) throw new InvalidProfileException("Hashtags are 2 to 30 letters, numbers or underscores: #" + t);
            out.add(t);
        }
        if (out.size() > MAX) throw new InvalidProfileException("Use at most " + MAX + " hashtags.");
        return List.copyOf(out);
    }
}
