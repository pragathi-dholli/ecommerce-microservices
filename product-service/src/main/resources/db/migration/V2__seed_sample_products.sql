-- ============================================================
-- V2: Seed sample product data (dev / demo only)
-- ============================================================

INSERT INTO products (name, description, sku, price, stock_quantity, category, brand, image_url, status)
VALUES
    ('Wireless Noise-Cancelling Headphones',
     'Premium over-ear headphones with 30-hour battery life and active noise cancellation.',
     'ELEC-WNC-001', 299.99, 150, 'Electronics', 'SoundMax',
     'https://cdn.example.com/products/headphones-001.jpg', 'ACTIVE'),

    ('Mechanical Gaming Keyboard',
     'TKL layout, Cherry MX Blue switches, RGB backlighting, USB-C.',
     'ELEC-KBD-002', 129.99, 85, 'Electronics', 'GameGear',
     'https://cdn.example.com/products/keyboard-002.jpg', 'ACTIVE'),

    ('4K USB-C Monitor 27"',
     'IPS panel, 144Hz, HDR400, includes USB-C hub and height-adjustable stand.',
     'ELEC-MON-003', 549.99, 40, 'Electronics', 'ViewTech',
     'https://cdn.example.com/products/monitor-003.jpg', 'ACTIVE'),

    ('Ergonomic Office Chair',
     'Lumbar support, breathable mesh back, adjustable armrests. Supports up to 150kg.',
     'FURN-CHR-001', 399.99, 25, 'Furniture', 'ComfortPlus',
     'https://cdn.example.com/products/chair-001.jpg', 'ACTIVE'),

    ('Standing Desk 160x80cm',
     'Electric height-adjustable, dual motor, memory presets, cable management tray.',
     'FURN-DSK-002', 749.99, 12, 'Furniture', 'DeskPro',
     'https://cdn.example.com/products/desk-002.jpg', 'ACTIVE'),

    ('Running Shoes - Men''s',
     'Lightweight, responsive foam midsole, breathable upper. Available sizes 7-13.',
     'SPRT-SHO-001', 89.99, 200, 'Sports', 'SpeedRun',
     'https://cdn.example.com/products/shoes-001.jpg', 'ACTIVE'),

    ('Yoga Mat Premium',
     'Non-slip surface, 6mm thick, eco-friendly TPE material, includes carry strap.',
     'SPRT-YGA-002', 49.99, 175, 'Sports', 'ZenFit',
     'https://cdn.example.com/products/yoga-mat-002.jpg', 'ACTIVE'),

    ('Protein Powder - Chocolate 2kg',
     'Whey isolate, 25g protein per serving, low sugar, 60 servings.',
     'HLTH-PRT-001', 59.99, 300, 'Health', 'NutriMax',
     'https://cdn.example.com/products/protein-001.jpg', 'ACTIVE'),

    ('Smart Watch Series X',
     'Heart rate, GPS, sleep tracking, 5-day battery, water resistant 50m.',
     'ELEC-SWT-004', 199.99, 8, 'Electronics', 'TechWear',
     'https://cdn.example.com/products/smartwatch-004.jpg', 'ACTIVE'),

    ('Discontinued Bluetooth Speaker',
     'Portable waterproof speaker — discontinued model.',
     'ELEC-SPK-OLD', 79.99, 0, 'Electronics', 'SoundMax',
     'https://cdn.example.com/products/speaker-old.jpg', 'DISCONTINUED');
