package com.batchable.backend.db.dao;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DAOConfig {

  @Bean
  public RestaurantDAO restaurantDAO(DataSource ds) {
    return new RestaurantDAO(ds);
  }

  @Bean
  public DriverDAO driverDAO(DataSource ds) {
    return new DriverDAO(ds);
  }

  @Bean
  public OrderDAO orderDAO(DataSource ds) {
    return new OrderDAO(ds);
  }

  @Bean
  public BatchDAO batchDAO(DataSource ds) {
    return new BatchDAO(ds);
  }

  @Bean
  public MenuItemDAO menuItemDAO(DataSource ds) {
    return new MenuItemDAO(ds);
  }
}
