package dev.vigil.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqlFingerprinterTest {
  private final SqlFingerprinter mysql = new SqlFingerprinter(SqlFingerprinter.Dialect.MYSQL);
  private final SqlFingerprinter pg = new SqlFingerprinter(SqlFingerprinter.Dialect.POSTGRES);

  static Stream<Arguments> mysqlCases() {
    return Stream.of(
      Arguments.of("SELECT * FROM users WHERE id = 42 AND name = 'bob'",
                   "select * from users where id = ? and name = ?"),
      Arguments.of("select * from t where id in (1,2,3)",  "select * from t where id in (?+)"),
      Arguments.of("select * from t where id in(7)",       "select * from t where id in (?+)"),
      Arguments.of("INSERT INTO t VALUES (1,'a'),(2,'b'),(3,'c')", "insert into t values (?+)"),
      Arguments.of("select 'it''s' /* hint */ , 1.5e3, 0xFF -- trailing\n from t",
                   "select ? , ?, ? from t"),
      Arguments.of("select `Order` from `Db`.`t` where a = \"x\"", "select `Order` from `Db`.`t` where a = ?"),
      Arguments.of("select   a\n\tfrom   t  # c\n where b=.5", "select a from t where b=?")
    );
  }

  @ParameterizedTest @MethodSource("mysqlCases")
  void mysqlNormalizes(String in, String expected) { assertEquals(expected, mysql.normalize(in)); }

  @Test void postgresPlaceholdersAndIdentifiers() {
    assertEquals("select \"Name\" from t where id = ? and x = ?",
                 pg.normalize("SELECT \"Name\" FROM t WHERE id = $1 AND x = 'v'"));
  }

  @Test void sameShapeSameFingerprint() {
    assertEquals(mysql.fingerprint("select * from o where id = 1").id(),
                 mysql.fingerprint("SELECT * FROM o WHERE id =   99999").id());
  }

  @Test void differentShapeDifferentFingerprint() {
    assertNotEquals(mysql.fingerprint("select * from o where id = 1").id(),
                    mysql.fingerprint("select * from o where name = 1").id());
  }
}
