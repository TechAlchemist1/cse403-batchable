package com.batchable.backend.db.dao;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.models.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RestaurantDAOTest extends PostgresTestBase {

  private TestDataSource ds;
  private RestaurantDAO restaurantDAO;

  @BeforeEach
  void setUp() throws Exception {
    ds = new TestDataSource(conn);
    restaurantDAO = new RestaurantDAO(ds);
    cleanDb();
  }

  private static void cleanDb() throws Exception {
    try (Statement st = conn.createStatement()) {
      // Truncate dependent tables first (FKs), then Restaurant
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    }
  }

  @Test
  void createRestaurant_returnsId_andGetRestaurantWorks() throws Exception {
    long id = restaurantDAO.createRestaurant("R1", "Seattle");
    assertTrue(id > 0);

    Optional<Restaurant> got = restaurantDAO.getRestaurant(id);
    assertTrue(got.isPresent());

    Restaurant r = got.get();
    assertEquals(id, r.id);
    assertEquals("R1", r.name);
    assertEquals("Seattle", r.location);
  }

  @Test
  void getRestaurant_missing_returnsEmpty() throws Exception {
    assertTrue(restaurantDAO.getRestaurant(999999).isEmpty());
  }

  @Test
  void listRestaurants_returnsAll_inIdOrder() throws Exception {
    long a = restaurantDAO.createRestaurant("A", "Seattle");
    long b = restaurantDAO.createRestaurant("B", "Bellevue");
    long c = restaurantDAO.createRestaurant("C", "Redmond");

    List<Restaurant> all = restaurantDAO.listRestaurants();
    assertEquals(3, all.size());

    assertEquals(List.of(a, b, c), all.stream().map(r -> r.id).toList());
    assertEquals(List.of("A", "B", "C"), all.stream().map(r -> r.name).toList());
  }

  @Test
  void updateRestaurant_updatesExactlyOneRow() throws Exception {
    long id = restaurantDAO.createRestaurant("R1", "Seattle");

    assertTrue(restaurantDAO.updateRestaurant(id, "R1-new", "Tacoma"));

    Restaurant r = restaurantDAO.getRestaurant(id).orElseThrow();
    assertEquals("R1-new", r.name);
    assertEquals("Tacoma", r.location);
  }

  @Test
  void updateRestaurant_missing_returnsFalse() throws Exception {
    assertFalse(restaurantDAO.updateRestaurant(123456, "X", "Y"));
  }

  @Test
  void deleteRestaurant_deletes_andReturnsTrue_thenFalseIfRepeated() throws Exception {
    long id = restaurantDAO.createRestaurant("R1", "Seattle");

    assertTrue(restaurantDAO.deleteRestaurant(id));
    assertTrue(restaurantDAO.getRestaurant(id).isEmpty());

    assertFalse(restaurantDAO.deleteRestaurant(id));
  }

  @Test
  void restaurantExists_trueWhenPresent_falseWhenMissing() throws Exception {
    long id = restaurantDAO.createRestaurant("R1", "Seattle");

    assertTrue(restaurantDAO.restaurantExists(id));
    assertFalse(restaurantDAO.restaurantExists(999999));
  }

  @Test
  void restaurantExistsByName_trueOnlyForExactMatch_caseSensitiveByDefault() throws Exception {
    restaurantDAO.createRestaurant("Chipotle", "Seattle");

    assertTrue(restaurantDAO.restaurantExistsByName("Chipotle"));
    assertFalse(restaurantDAO.restaurantExistsByName("chipotle")); // case-sensitive by default
    assertFalse(restaurantDAO.restaurantExistsByName("NotChipotle"));
  }

  @Test
  void restaurantExistsByNameExcludingId_trueWhenAnotherRowHasName_falseWhenOnlySelfHasName()
      throws Exception {
    long id1 = restaurantDAO.createRestaurant("R1", "Seattle");
    long id2 = restaurantDAO.createRestaurant("R2", "Bellevue");

    // another row has name "R2" (excluding id1) => true
    assertTrue(restaurantDAO.restaurantExistsByNameExcludingId(id1, "R2"));

    // only self has name "R2" (excluding id2) => false
    assertFalse(restaurantDAO.restaurantExistsByNameExcludingId(id2, "R2"));

    // name doesn't exist anywhere => false
    assertFalse(restaurantDAO.restaurantExistsByNameExcludingId(id1, "DOES_NOT_EXIST"));

    // excluding one id should not hide another with same name if you allow duplicates
    // (your schema does not enforce uniqueness, so this is good coverage)
    long id3 = restaurantDAO.createRestaurant("R2", "Redmond");
    assertTrue(restaurantDAO.restaurantExistsByNameExcludingId(id2, "R2")); // id3 still matches
  }
}
