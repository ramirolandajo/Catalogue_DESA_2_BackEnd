-- Datos iniciales para pruebas locales en MySQL/H2
-- NOTA: se usa INSERT IGNORE para evitar fallas por duplicados en reinicios

-- Marcas (incluye brand_code)
INSERT IGNORE INTO brand (brand_id, brand_code, name, active) VALUES
  (1, 1001, 'Acme', true),
  (2, 1002, 'Globex', true),
  (3, 1003, 'Samsung', true),
  (4, 1004, 'Apple', true),
  (5, 1005, 'Sony', true),
  (6, 1006, 'Lenovo', true),
  (7, 1007, 'HP', true),
  (8, 1008, 'Asus', true),
  (9, 1009, 'Dell', true),
  (10, 1010, 'Xiaomi', true),
  (11, 1011, 'LG', true),
  (12, 1012, 'Huawei', true);

-- Categorias (incluye category_code)
INSERT IGNORE INTO category (category_id, category_code, name, active) VALUES
  (1, 2001, 'Electronica', true),
  (2, 2002, 'Hogar', true),
  (3, 2003, 'Smartphones', true),
  (4, 2004, 'Laptops', true),
  (5, 2005, 'Tablets', true),
  (6, 2006, 'Smartwatches', true),
  (7, 2007, 'Accesorios', true),
  (8, 2008, 'TV', true),
  (9, 2009, 'Audio', true),
  (10, 2010, 'Hogar Inteligente', true),
  (11, 2011, 'Gaming', true),
  (12, 2012, 'Componentes', true);
