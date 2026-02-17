BEGIN;

-- Restaurant deletion cascades to drivers, menu items, and orders.
-- Driver deletion cascades to batches.
-- Batch deletion should NOT delete orders; it should null out batch_id.

-- -------- Driver -> Restaurant (driver.restaurant_id)
DO $$
BEGIN
  IF to_regclass('public.driver') IS NOT NULL THEN
    ALTER TABLE driver
      DROP CONSTRAINT IF EXISTS driver_restaurant_id_fkey;
    ALTER TABLE driver
      ADD CONSTRAINT driver_restaurant_id_fkey
      FOREIGN KEY (restaurant_id) REFERENCES restaurant(id)
      ON DELETE CASCADE;
  END IF;
END $$;

-- -------- Batch -> Driver (batch.driver_id)
DO $$
BEGIN
  IF to_regclass('public.batch') IS NOT NULL THEN
    ALTER TABLE batch
      DROP CONSTRAINT IF EXISTS batch_driver_id_fkey;
    ALTER TABLE batch
      ADD CONSTRAINT batch_driver_id_fkey
      FOREIGN KEY (driver_id) REFERENCES driver(id)
      ON DELETE CASCADE;
  END IF;
END $$;

-- -------- Order -> Restaurant ("Order".restaurant_id)
DO $$
BEGIN
  IF to_regclass('public."Order"') IS NOT NULL THEN
    ALTER TABLE "Order"
      DROP CONSTRAINT IF EXISTS "Order_restaurant_id_fkey";
    ALTER TABLE "Order"
      ADD CONSTRAINT "Order_restaurant_id_fkey"
      FOREIGN KEY (restaurant_id) REFERENCES restaurant(id)
      ON DELETE CASCADE;
  END IF;
END $$;

-- -------- Order -> Batch ("Order".batch_id)  (recommended: SET NULL)
DO $$
BEGIN
  IF to_regclass('public."Order"') IS NOT NULL THEN
    ALTER TABLE "Order"
      DROP CONSTRAINT IF EXISTS "Order_batch_id_fkey";
    ALTER TABLE "Order"
      ADD CONSTRAINT "Order_batch_id_fkey"
      FOREIGN KEY (batch_id) REFERENCES batch(id)
      ON DELETE SET NULL;
  END IF;
END $$;

-- -------- Menu items -> Restaurant (handle both menu_item and "Menu_Item")
DO $$
BEGIN
  IF to_regclass('public.menu_item') IS NOT NULL THEN
    ALTER TABLE menu_item
      DROP CONSTRAINT IF EXISTS menu_item_restaurant_id_fkey;
    ALTER TABLE menu_item
      ADD CONSTRAINT menu_item_restaurant_id_fkey
      FOREIGN KEY (restaurant_id) REFERENCES restaurant(id)
      ON DELETE CASCADE;

  ELSIF to_regclass('public."Menu_Item"') IS NOT NULL THEN
    ALTER TABLE "Menu_Item"
      DROP CONSTRAINT IF EXISTS "Menu_Item_restaurant_id_fkey";
    ALTER TABLE "Menu_Item"
      ADD CONSTRAINT "Menu_Item_restaurant_id_fkey"
      FOREIGN KEY (restaurant_id) REFERENCES restaurant(id)
      ON DELETE CASCADE;
  END IF;
END $$;

COMMIT;
