USE morphoaid_db; 
INSERT IGNORE INTO users (id, email, password, full_name, role, created_at, updated_at) VALUES (1, 'admin@test.com', 'pass', 'Admin', 'ADMIN', NOW(), NOW());
