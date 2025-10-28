-- ==========================================================
-- RESET DATABASE
-- ==========================================================
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE tickets;
TRUNCATE TABLE bookings;
TRUNCATE TABLE showtimes;
TRUNCATE TABLE seats;
TRUNCATE TABLE rooms;
TRUNCATE TABLE movies;
TRUNCATE TABLE staffs;
TRUNCATE TABLE customers;
TRUNCATE TABLE users;

SET FOREIGN_KEY_CHECKS = 1;

-- ==========================================================
-- USERS (password: 123456)
-- ==========================================================
INSERT INTO users (user_id, username, password, full_name, role)
VALUES
(1, 'admin01', '$2a$10$bWK86rfYrdLDLtYh6lZ66eCUBo9G1.kpw7DUPTLP.KYav0Q4gONvC', 'Admin Manager', 'ADMIN'),
(2, 'staff01', '$2a$10$bWK86rfYrdLDLtYh6lZ66eCUBo9G1.kpw7DUPTLP.KYav0Q4gONvC', 'Nguyen Van Staff', 'STAFF'),
(3, 'customer01', '$2a$10$bWK86rfYrdLDLtYh6lZ66eCUBo9G1.kpw7DUPTLP.KYav0Q4gONvC', 'Le Thi Customer', 'CUSTOMER'),
(4, 'customer02', '$2a$10$bWK86rfYrdLDLtYh6lZ66eCUBo9G1.kpw7DUPTLP.KYav0Q4gONvC', 'Tran Minh User', 'CUSTOMER');

-- ==========================================================
-- CUSTOMERS
-- ==========================================================
INSERT INTO customers (customer_id, user_id, gender, phone)
VALUES
(1, 3, 'MALE', '0901112233'),
(2, 4, 'FEMALE', '0908881122');

-- ==========================================================
-- STAFFS
-- ==========================================================
INSERT INTO staffs (staff_id, user_id, cccd, phone, email, position, salary, hire_date, status, gender)
VALUES
(1, 2, '123456789012', '0912345678', 'staff01@cinema.com', 'Cashier', 8000000, '2023-08-01', 'ACTIVE', 'MALE');

-- ==========================================================
-- MOVIES
-- ==========================================================
INSERT INTO movies (movie_id, title, duration, genre, description, poster_url, trailer_url)
VALUES
(1, 'Avengers: Endgame', 180, 'Action', 'The Avengers take on Thanos.', NULL, NULL),
(2, 'Inside Out 2', 100, 'Animation', 'Journey into Riley’s mind again.', NULL, NULL),
(3, 'Doraemon: Nobita’s Sky Utopia', 95, 'Adventure', 'Doraemon and friends discover a utopian world.', NULL, NULL);

-- ==========================================================
-- ROOMS
-- ==========================================================
INSERT INTO rooms (room_id, room_name, capacity, room_type)
VALUES
(1, 'Room 1', 20, '2D'),
(2, 'Room 2', 30, '3D'),
(3, 'Room 3', 40, '2D'),
(4, 'Room 4', 60, 'IMAX');

-- ==========================================================
-- SEATS (sử dụng alias an toàn cho MySQL)
-- ==========================================================

-- 🪑 Room 1
INSERT INTO seats (seat_number, booking, room_id, seat_type)
SELECT CONCAT(r.row_letter, c.col_number), false, 1, 'NORMAL'
FROM (SELECT 'A' AS row_letter UNION SELECT 'B') AS r,
     (SELECT 1 AS col_number UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
             SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS c;

-- 🪑 Room 2
INSERT INTO seats (seat_number, booking, room_id, seat_type)
SELECT CONCAT(r.row_letter, c.col_number), false, 2, 'NORMAL'
FROM (SELECT 'A' AS row_letter UNION SELECT 'B' UNION SELECT 'C') AS r,
     (SELECT 1 AS col_number UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
             SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS c;

-- 🪑 Room 3
INSERT INTO seats (seat_number, booking, room_id, seat_type)
SELECT CONCAT(r.row_letter, c.col_number), false, 3, 'NORMAL'
FROM (SELECT 'A' AS row_letter UNION SELECT 'B' UNION SELECT 'C' UNION SELECT 'D') AS r,
     (SELECT 1 AS col_number UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
             SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS c;

-- 🪑 Room 4 (Normal A–E)
INSERT INTO seats (seat_number, booking, room_id, seat_type)
SELECT CONCAT(r.row_letter, c.col_number), false, 4, 'NORMAL'
FROM (SELECT 'A' AS row_letter UNION SELECT 'B' UNION SELECT 'C' UNION SELECT 'D' UNION SELECT 'E') AS r,
     (SELECT 1 AS col_number UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
             SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS c;

-- 🪑 Room 4 (Sweetbox F1–F5)
INSERT INTO seats (seat_number, booking, room_id, seat_type)
SELECT CONCAT('F', c.col_number), false, 4, 'SWEETBOX'
FROM (SELECT 1 AS col_number UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) AS c;

-- ==========================================================
-- SHOWTIMES
-- ==========================================================
INSERT INTO showtimes (showtime_id, movie_id, room_id, start_time, end_time, price)
VALUES
(1, 1, 1, '2025-10-16T18:00:00', '2025-10-16T21:00:00', 90000),
(2, 2, 2, '2025-10-16T20:00:00', '2025-10-16T21:40:00', 80000),
(3, 3, 3, '2025-10-17T09:00:00', '2025-10-17T10:40:00', 75000),
(4, 1, 4, '2025-10-17T19:00:00', '2025-10-17T21:50:00', 120000);

-- ==========================================================
-- BOOKINGS (Dùng subquery để tránh lỗi FK)
-- ==========================================================
INSERT INTO bookings (booking_id, customer_id, showtime_id, seat_id, status, created_at)
VALUES
(1, 1, 1, (SELECT seat_id FROM seats WHERE seat_number = 'A1' AND room_id = 1 LIMIT 1), 'PAID', CURRENT_TIMESTAMP),
(2, 2, 2, (SELECT seat_id FROM seats WHERE seat_number = 'B3' AND room_id = 2 LIMIT 1), 'PENDING', CURRENT_TIMESTAMP),
(3, 1, 4, (SELECT seat_id FROM seats WHERE seat_number = 'F5' AND room_id = 4 LIMIT 1), 'PAID', CURRENT_TIMESTAMP);

-- ==========================================================
-- TICKETS
-- ==========================================================
INSERT INTO tickets (ticket_id, showtime_id, seat_number, price, sold_by, sold_at)
VALUES
(1, 1, 'A2', 90000, 1, CURRENT_TIMESTAMP),
(2, 3, 'B2', 75000, 1, CURRENT_TIMESTAMP),
(3, 4, 'F4', 120000, 1, CURRENT_TIMESTAMP);
