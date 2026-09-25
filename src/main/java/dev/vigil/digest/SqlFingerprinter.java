package dev.vigil.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes SQL into a literal-free fingerprint so "WHERE id = 42" and "WHERE id = 7" group together.
 * Invariant: Stateless and thread-safe normalization ensuring queries of identical syntactic shape yield identical fingerprint IDs.
 */
public final class SqlFingerprinter {

  public enum Dialect { MYSQL, POSTGRES }
  public record Fingerprint(String id, String text) {}

  private static final Pattern IN_OR_VALUES =
      Pattern.compile("\\b(in|values)\\s*\\(\\s*\\?(?:\\s*,\\s*\\?)*\\s*\\)");
  private static final Pattern EXTRA_ROWS =
      Pattern.compile("(values \\(\\?\\+\\))(?:\\s*,\\s*\\(\\s*\\?(?:\\s*,\\s*\\?)*\\s*\\))+");

  private final Dialect dialect;

  public SqlFingerprinter(Dialect dialect) { this.dialect = dialect; }

  public Fingerprint fingerprint(String sql) {
    String text = normalize(sql);
    return new Fingerprint(sha16(text), text);
  }

  public String normalize(String sql) {
    int n = sql.length();
    StringBuilder out = new StringBuilder(n);
    int i = 0;
    while (i < n) {
      char c = sql.charAt(i);
      char next = i + 1 < n ? sql.charAt(i + 1) : '\0';

      if (c == '/' && next == '*') { i = skipBlockComment(sql, i); space(out); continue; }
      if (c == '-' && next == '-') { i = skipLine(sql, i); space(out); continue; }
      if (c == '#' && dialect == Dialect.MYSQL) { i = skipLine(sql, i); space(out); continue; }

      if (c == '\'') { i = skipQuoted(sql, i, '\'', dialect == Dialect.MYSQL); out.append('?'); continue; }
      if (c == '"') {
        if (dialect == Dialect.MYSQL) { i = skipQuoted(sql, i, '"', true); out.append('?'); }
        else { int end = skipQuoted(sql, i, '"', false); out.append(sql, i, end); i = end; }  // PG identifier
        continue;
      }
      if (c == '`') { int end = skipQuoted(sql, i, '`', false); out.append(sql, i, end); i = end; continue; }

      if (c == '$' && Character.isDigit(next)) {            // PG positional parameter $1
        i++; while (i < n && Character.isDigit(sql.charAt(i))) i++;
        out.append('?'); continue;
      }

      if (Character.isDigit(c) || (c == '.' && Character.isDigit(next) && !identTail(out))) {
        i = skipNumber(sql, i); out.append('?'); continue;
      }

      if (Character.isWhitespace(c)) { space(out); i++; continue; }

      if (Character.isLetter(c) || c == '_') {
        int j = i + 1;
        while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_' || sql.charAt(j) == '$')) j++;
        out.append(sql.substring(i, j).toLowerCase(Locale.ROOT));
        i = j; continue;
      }

      out.append(c); i++;
    }
    String s = out.toString().trim();
    s = IN_OR_VALUES.matcher(s).replaceAll("$1 (?+)");
    s = EXTRA_ROWS.matcher(s).replaceAll("$1");
    return s;
  }

  private static boolean identTail(StringBuilder sb) {
    if (sb.isEmpty()) return false;
    char last = sb.charAt(sb.length() - 1);
    return Character.isLetterOrDigit(last) || last == '_' || last == '`' || last == '"';
  }

  private static void space(StringBuilder sb) {
    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
  }

  private static int skipBlockComment(String s, int i) {
    int end = s.indexOf("*/", i + 2);
    return end < 0 ? s.length() : end + 2;
  }

  private static int skipLine(String s, int i) {
    int end = s.indexOf('\n', i);
    return end < 0 ? s.length() : end + 1;
  }

  /** Returns index after the closing quote. Handles doubled quotes and optional backslash escapes. */
  private static int skipQuoted(String s, int start, char quote, boolean backslashEscapes) {
    int n = s.length(), i = start + 1;
    while (i < n) {
      char c = s.charAt(i);
      if (backslashEscapes && c == '\\') { i += 2; continue; }
      if (c == quote) {
        if (i + 1 < n && s.charAt(i + 1) == quote) { i += 2; continue; }
        return i + 1;
      }
      i++;
    }
    return n;
  }

  private static int skipNumber(String s, int i) {
    int n = s.length();
    if (s.charAt(i) == '0' && i + 1 < n && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
      i += 2;
      while (i < n && Character.digit(s.charAt(i), 16) >= 0) i++;
      return i;
    }
    while (i < n && Character.isDigit(s.charAt(i))) i++;
    if (i < n && s.charAt(i) == '.') { i++; while (i < n && Character.isDigit(s.charAt(i))) i++; }
    if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      int j = i + 1;
      if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
      if (j < n && Character.isDigit(s.charAt(j))) { while (j < n && Character.isDigit(s.charAt(j))) j++; i = j; }
    }
    return i;
  }

  private static String sha16(String text) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d, 0, 8);
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
