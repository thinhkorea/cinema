package com.example.cinema.config;

import com.example.cinema.domain.*;
import com.example.cinema.repository.*;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Configuration
public class DataSeeder {

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Bean
    CommandLineRunner initData(
            UserRepository userRepo,
            CustomerRepository customerRepo,
            StaffRepository staffRepo,
            MovieRepository movieRepo,
            RoomRepository roomRepo,
            SeatRepository seatRepo,
            ShowtimeRepository showtimeRepo,
            BookingRepository bookingRepo) {
        return args -> {

            if (!seedEnabled) {
                System.out.println("DataSeeder skipped (app.seed.enabled=false)");
                return;
            }
            // Xóa dữ liệu cũ
            bookingRepo.deleteAll();
            showtimeRepo.deleteAll();
            seatRepo.deleteAll();
            roomRepo.deleteAll();
            movieRepo.deleteAll();
            staffRepo.deleteAll();
            customerRepo.deleteAll();
            userRepo.deleteAll();

            // Users
            User admin = new User();
            admin.setUsername("admin01");
            admin.setPassword("$2a$10$bWK86rfYrdLDLtYh6lZ66eCUBo9G1.kpw7DUPTLP.KYav0Q4gONvC"); // 123456
            admin.setFullName("Admin Manager");
            admin.setRole(User.Role.ADMIN);

            User staff = new User();
            staff.setUsername("staff01");
            staff.setPassword("$2a$10$bWK86rfYrdLDLtYh6lZ66eCUBo9G1.kpw7DUPTLP.KYav0Q4gONvC");
            staff.setFullName("Nguyen Van Staff");
            staff.setRole(User.Role.STAFF);

            User cus1 = new User();
            cus1.setUsername("customer01");
            cus1.setPassword("$2a$10$bWK86rfYrdLDLtYh6lZ66eCUBo9G1.kpw7DUPTLP.KYav0Q4gONvC");
            cus1.setFullName("Le Thi Customer");
            cus1.setRole(User.Role.CUSTOMER);

            User cus2 = new User();
            cus2.setUsername("customer02");
            cus2.setPassword("$2a$10$bWK86rfYrdLDLtYh6lZ66eCUBo9G1.kpw7DUPTLP.KYav0Q4gONvC");
            cus2.setFullName("Tran Minh User");
            cus2.setRole(User.Role.CUSTOMER);

            userRepo.saveAll(List.of(admin, staff, cus1, cus2));

            // Customers
            Customer c1 = new Customer();
            c1.setUser(cus1);
            c1.setGender(Customer.Gender.MALE);
            c1.setPhone("0901112233");

            Customer c2 = new Customer();
            c2.setUser(cus2);
            c2.setGender(Customer.Gender.FEMALE);
            c2.setPhone("0908881122");

            customerRepo.saveAll(List.of(c1, c2));

            // Staff
            Staff s1 = new Staff();
            s1.setUser(staff);
            s1.setCccd("123456789012");
            s1.setPhone("0912345678");
            s1.setEmail("staff01@cinema.com");
            s1.setPosition("Cashier");
            s1.setSalary(8000000.0);
            s1.setHireDate(new java.sql.Date(System.currentTimeMillis()));
            s1.setStatus(Staff.Status.ACTIVE);
            s1.setGender(Staff.Gender.MALE);
            staffRepo.save(s1);

            // Movies
            Movie m1 = new Movie();
            m1.setTitle("Avengers: Endgame");
            m1.setDuration(180);
            m1.setGenre("Action, Adventure, Sci-Fi");
            m1.setDescription(
                    "The Avengers unite for one final stand against Thanos to restore balance to the universe.");
            m1.setPosterUrl("https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg");
            m1.setTrailerUrl("https://www.youtube.com/watch?v=TcMBFSGVi1c");
            m1.setStatus(Movie.MovieStatus.NOW_SHOWING); // Gán trạng thái Đang chiếu

            Movie m2 = new Movie();
            m2.setTitle("Inside Out 2");
            m2.setDuration(100);
            m2.setGenre("Animation, Family, Comedy");
            m2.setDescription(
                    "Riley faces new emotions and challenges as she becomes a teenager in this heartwarming Pixar sequel.");
            m2.setPosterUrl("https://upload.wikimedia.org/wikipedia/vi/a/a3/Inside_Out_2_VN_poster.jpg");
            m2.setTrailerUrl("https://www.youtube.com/watch?v=LEjhY15eCx0");
            m2.setStatus(Movie.MovieStatus.COMING_SOON); // Gán trạng thái Sắp chiếu

            Movie m3 = new Movie();
            m3.setTitle("Doraemon: Nobita’s Sky Utopia");
            m3.setDuration(95);
            m3.setGenre("Adventure, Animation, Fantasy");
            m3.setDescription("Doraemon and friends travel to a utopian land in the sky in search of an ideal world.");
            m3.setPosterUrl("https://upload.wikimedia.org/wikipedia/vi/2/27/Doraemon-_Nobita%27s_Sky_Utopia.jpg");
            m3.setTrailerUrl("https://www.youtube.com/watch?v=Ol6Z_QR-OH8");
            m3.setStatus(Movie.MovieStatus.SPECIAL_RELEASE); // Gán trạng thái Đặc biệt

            movieRepo.saveAll(List.of(m1, m2, m3));
            // Rooms
            Room r1 = new Room();
            r1.setRoomName("Room 1");
            r1.setCapacity(50);
            r1.setRoomType("2D");

            Room r2 = new Room();
            r2.setRoomName("Room 2");
            r2.setCapacity(50);
            r2.setRoomType("3D");

            Room r3 = new Room();
            r3.setRoomName("Room 3");
            r3.setCapacity(50);
            r3.setRoomType("2D");

            Room r4 = new Room();
            r4.setRoomName("Room 4");
            r4.setCapacity(60);
            r4.setRoomType("IMAX");

            roomRepo.saveAll(List.of(r1, r2, r3, r4));

            // Ghế
            generateSeats(r1, seatRepo, false);
            generateSeats(r2, seatRepo, false);
            generateSeats(r3, seatRepo, false);
            generateSeats(r4, seatRepo, true);

            // Suất chiếu — tự động cộng ngày tương lai
            // Luôn sử dụng múi giờ Việt Nam để đảm bảo tính nhất quán
            ZonedDateTime nowInVietnam = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            final int BUFFER_MINUTES = 30; // Thời gian nghỉ giữa các suất

            Showtime s01 = new Showtime();
            s01.setMovie(m1);
            s01.setRoom(r1);
            s01.setStartTime(nowInVietnam.plusDays(1).withHour(18).withMinute(0).withSecond(0).toLocalDateTime());
            s01.setEndTime(s01.getStartTime().plusMinutes(m1.getDuration() + BUFFER_MINUTES));
            s01.setPrice(90000.0);

            Showtime s02 = new Showtime();
            s02.setMovie(m2);
            s02.setRoom(r2);
            s02.setStartTime(nowInVietnam.plusDays(2).withHour(20).withMinute(0).withSecond(0).toLocalDateTime());
            s02.setEndTime(s02.getStartTime().plusMinutes(m2.getDuration() + BUFFER_MINUTES));
            s02.setPrice(80000.0);

            Showtime s03 = new Showtime();
            s03.setMovie(m3);
            s03.setRoom(r3);
            s03.setStartTime(nowInVietnam.plusDays(3).withHour(9).withMinute(0).withSecond(0).toLocalDateTime());
            s03.setEndTime(s03.getStartTime().plusMinutes(m3.getDuration() + BUFFER_MINUTES));
            s03.setPrice(75000.0);

            Showtime s04 = new Showtime();
            s04.setMovie(m1);
            s04.setRoom(r4);
            s04.setStartTime(nowInVietnam.plusDays(4).withHour(19).withMinute(0).withSecond(0).toLocalDateTime());
            s04.setEndTime(s04.getStartTime().plusMinutes(m1.getDuration() + BUFFER_MINUTES));
            s04.setPrice(120000.0);

            showtimeRepo.saveAll(List.of(s01, s02, s03, s04));
            System.out.println("Database seeded successfully!");
        };
    }

    // Ghế tự động (NORMAL, VIP, SWEETBOX)
    private void generateSeats(Room room, SeatRepository seatRepo, boolean includeSweetbox) {
        String[] rows = { "A", "B", "C", "D", "E" };

        for (String row : rows) {
            for (int col = 1; col <= 10; col++) {
                Seat seat = new Seat();
                seat.setSeatNumber(row + col);
                seat.setBooking(false);
                seat.setRoom(room);

                if (row.equals("C"))
                    seat.setSeatType(Seat.SeatType.VIP);
                else
                    seat.setSeatType(Seat.SeatType.NORMAL);

                seatRepo.save(seat);
            }
        }

        if (includeSweetbox) {
            for (int col = 1; col <= 10; col += 2) {
                Seat seat = new Seat();
                seat.setSeatNumber("F" + col + "-" + (col + 1));
                seat.setBooking(false);
                seat.setRoom(room);
                seat.setSeatType(Seat.SeatType.SWEETBOX);
                seatRepo.save(seat);
            }
        }
    }
}
