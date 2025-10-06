-- ==========================================================
-- 👤 USERS
-- ==========================================================
-- Password bcrypt: "admin123"
INSERT INTO users (username, password, full_name, role) VALUES
('admin10', '$2a$10$GZ9yG9l4kWzbnql5H3yI8OT9I4vZlXEmjZW2FjW3A/E7TrlUswO8S', 'Administrator', 'ADMIN'),
('staff01', '$2a$10$GZ9yG9l4kWzbnql5H3yI8OT9I4vZlXEmjZW2FjW3A/E7TrlUswO8S', 'Staff One', 'STAFF'),
('minh', '$2a$10$GZ9yG9l4kWzbnql5H3yI8OT9I4vZlXEmjZW2FjW3A/E7TrlUswO8S', 'Minh Nguyen', 'CUSTOMER');

-- ==========================================================
-- 🎥 MOVIES
-- ==========================================================
INSERT INTO movies (title, genre, duration, description, release_date, end_date, poster_url, trailer_url) VALUES
('Avengers: Endgame', 'Action', 185, 'Final battle against Thanos.', '2025-01-01', '2025-03-31',
'https://image.tmdb.org/t/p/original/Avengers-Endgame.jpg',
'https://www.youtube.com/watch?v=TcMBFSGVi1c'),
('Venom: The Last Dance', 'Sci-Fi', 110, 'Venom faces his greatest challenge.', '2025-02-01', '2025-04-01',
'https://image.tmdb.org/t/p/original/Venom-LastDance.jpg',
'https://www.youtube.com/watch?v=V3qVw1K7mY8'),
('Aquaman 2', 'Adventure', 140, 'Atlantis in danger once again.', '2025-03-01', '2025-05-01',
'https://image.tmdb.org/t/p/original/Aquaman2.jpg',
'https://www.youtube.com/watch?v=UGc5ACvXy9I');

-- ==========================================================
-- 🏠 ROOMS
-- ==========================================================
INSERT INTO rooms (room_name, capacity, room_type) VALUES
('Room 1', 50, '2D'),
('Room 2', 40, '3D'),
('IMAX Hall', 100, 'IMAX');

-- ==========================================================
-- 🪑 SEATS
-- ==========================================================
-- 10 ghế mỗi phòng (A1–A10)
INSERT INTO seats (room_id, seat_number, booking)
SELECT r.room_id, CONCAT('A', n), 0
FROM rooms r
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) nums;

-- ==========================================================
-- ⏰ SHOWTIMES
-- ==========================================================
INSERT INTO showtimes (movie_id, room_id, start_time, end_time, price) VALUES
(1, 1, '2025-10-10 18:00:00', '2025-10-10 21:05:00', 90000),
(1, 2, '2025-10-11 20:00:00', '2025-10-11 23:00:00', 95000),
(2, 1, '2025-10-12 19:00:00', '2025-10-12 20:50:00', 85000),
(3, 2, '2025-10-13 21:00:00', '2025-10-13 23:20:00', 100000);

-- ==========================================================
-- 🎫 BOOKINGS
-- ==========================================================
INSERT INTO bookings (user_id, showtime_id, seat_id, status, created_at) VALUES
(3, 1, 1, 'PAID', NOW()),
(3, 2, 3, 'CANCELLED', NOW()),
(3, 3, 5, 'PENDING', NOW());

-- ==========================================================
-- 🎟️ TICKETS
-- ==========================================================
INSERT INTO tickets (showtime_id, seat_number, price, sold_by, sold_at) VALUES
(1, 'A1', 90000, 2, NOW()),
(2, 'A3', 95000, 2, NOW());
