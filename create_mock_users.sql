START TRANSACTION;

-- Create users du01 to du10 if they don't exist
INSERT INTO users (username, email, password, role, full_name, created_at)
VALUES 
('du01', 'du01@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 01', NOW()),
('du02', 'du02@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 02', NOW()),
('du03', 'du03@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 03', NOW()),
('du04', 'du04@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 04', NOW()),
('du05', 'du05@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 05', NOW()),
('du06', 'du06@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 06', NOW()),
('du07', 'du07@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 07', NOW()),
('du08', 'du08@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 08', NOW()),
('du09', 'du09@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 09', NOW()),
('du10', 'du10@example.com', '$2a$10$x.B8YI./hWvP1YwQW8vJieH5XfX8U.yX7fX8U.yX7fX8U.yX7', 'DATA_USE', 'Doctor User 10', NOW())
ON DUPLICATE KEY UPDATE username=username;

COMMIT;
