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

            // Movies from movie.json (updated with correct actor format)
            Movie m1 = createMovie("MAI", 131, "Tâm lý, Tình cảm", 
                "Câu chuyện về Mai, một nữ nhân viên massage, vô tình gặp Dương, một anh chàng nhạc công đào hoa.",
                "https://upload.wikimedia.org/wikipedia/vi/3/36/Mai_2024_poster.jpg",
                "https://www.youtube.com/watch?v=HXWRTGbhb4U",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.C18,
                "Phương Anh Đào, Tuấn Trần");
            
            Movie m2 = createMovie("Deadpool & Wolverine", 127, "Hành động, Hài, Siêu anh hùng",
                "Tổ chức TVA kéo Deadpool vào một nhiệm vụ mới xuyên đa vũ trụ, buộc anh phải hợp tác với một Wolverine cáu kỉnh.",
                "https://upload.wikimedia.org/wikipedia/en/4/4c/Deadpool_%26_Wolverine_poster.jpg",
                "https://www.youtube.com/watch?v=73_1biulkYk",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.C18,
                "Ryan Reynolds, Hugh Jackman");
            
            Movie m3 = createMovie("Inside Out 2 (Những Mảnh Ghép Cảm Xúc 2)", 96, "Hoạt hình, Hài, Gia đình",
                "Riley bước vào tuổi thiếu niên. Trụ sở Cảm Xúc bị xáo trộn bởi sự xuất hiện của các cảm xúc mới: Lo Âu, Ganh Tị, Xấu Hổ và Chán Nản.",
                "https://upload.wikimedia.org/wikipedia/en/f/f7/Inside_Out_2_poster.jpg",
                "https://www.youtube.com/watch?v=LEjhY15eCx0&t=2s",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.P,
                "Amy Poehler (lồng tiếng), Maya Hawke (lồng tiếng)");

            // Replace with 20 movies from movie.json
            Movie m4 = createMovie("Joker: Điên Có Đôi", 138, "Tâm lý, Tội phạm, Nhạc kịch",
                "Arthur Fleck bị giam giữ tại nhà thương điên Arkham. Tại đây, hắn gặp gỡ và nảy sinh tình cảm với Harley Quinn.",
                "https://upload.wikimedia.org/wikipedia/vi/7/79/JOKER_FOLIE_%C3%80_DEUX_-_Vietnam_poster.jpg",
                "https://www.youtube.com/watch?v=xy8aJw1vYHo",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.C18,
                "Joaquin Phoenix, Lady Gaga");

            Movie m5 = createMovie("Dune: Hành Tinh Cát - Phần Hai", 166, "Khoa học viễn tưởng, Phiêu lưu",
                "Paul Atreides hợp nhất với người Fremen để trả thù những kẻ đã hủy hoại gia đình anh.",
                "https://upload.wikimedia.org/wikipedia/vi/9/94/Dune_2_VN_poster.jpg",
                "https://www.youtube.com/watch?v=U2Qp5pL3ovA",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.C16,
                "Timothée Chalamet, Zendaya");

            Movie m6 = createMovie("Godzilla x Kong: Đế Chế Mới", 115, "Hành động, Phiêu lưu",
                "Kong và Godzilla tái hợp để chống lại một mối đe dọa khổng lồ ẩn sâu bên trong Trái Đất.",
                "https://upload.wikimedia.org/wikipedia/en/b/be/Godzilla_x_kong_the_new_empire_poster.jpg",
                "https://www.youtube.com/watch?v=qqrpMRDuPfc",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.C13,
                "Rebecca Hall, Dan Stevens");

            Movie m7 = createMovie("Lật Mặt 7: Một Điều Ước", 135, "Gia đình, Chính kịch",
                "Câu chuyện cảm động về bà Hai và 5 người con. Sau một tai nạn, bà Hai cần người chăm sóc.",
                "https://upload.wikimedia.org/wikipedia/vi/d/d4/%C3%81p_ph%C3%ADch_ch%C3%ADnh_th%E1%BB%A9c_L%E1%BA%ADt_m%E1%BA%B7t_7.jpg",
                "https://www.youtube.com/watch?v=d1ZHdosjNX8",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.P,
                "Quách Ngọc Tuyên, Nghệ sĩ Thanh Hiền");

            Movie m8 = createMovie("Vùng Đất Câm Lặng: Ngày Một", 99, "Kinh dị, Khoa học viễn tưởng",
                "Lấy bối cảnh ngày đầu tiên thế giới bị các sinh vật ngoài hành tinh săn mồi bằng âm thanh xâm lược.",
                "https://upload.wikimedia.org/wikipedia/vi/6/6f/V%C3%B9ng_%C4%91%E1%BA%A5t_c%C3%A2m_l%E1%BA%B7ng_2024.jpg",
                "https://www.youtube.com/watch?v=SQYHY5HzRL0",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.C16,
                "Lupita Nyong'o, Joseph Quinn");

            Movie m9 = createMovie("Kung Fu Panda 4", 94, "Hoạt hình, Hành động, Hài",
                "Po được chọn để trở thành Lãnh đạo Tinh thần của Thung lũng Bình Yên, nhưng cậu cần tìm một Chiến binh Rồng mới.",
                "https://upload.wikimedia.org/wikipedia/vi/7/7f/Kung_Fu_Panda_4_poster.jpg",
                "https://www.youtube.com/watch?v=_inKs4eeHiI",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.P,
                "Jack Black (lồng tiếng), Awkwafina (lồng tiếng)");

            Movie m10 = createMovie("Oppenheimer", 180, "Tiểu sử, Lịch sử, Chính kịch",
                "Câu chuyện về nhà vật lý J. Robert Oppenheimer và vai trò của ông trong việc phát triển bom nguyên tử.",
                "https://upload.wikimedia.org/wikipedia/vi/2/21/Oppenheimer_%E2%80%93_Vietnam_poster.jpg",
                "https://www.youtube.com/watch?v=bK6ldnjE3Y0",
                Movie.MovieStatus.NOW_SHOWING,
                Movie.AgeRating.C16,
                "Cillian Murphy, Emily Blunt");

            Movie m11 = createMovie("Kẻ Trộm Mặt Trăng 4", 95, "Hoạt hình, Hài, Phiêu lưu",
                "Gru và gia đình chào đón thành viên mới, Gru Jr. Nhưng họ buộc phải chạy trốn khi kẻ thù cũ, Maxime Le Mal, thoát khỏi tù.",
                "https://upload.wikimedia.org/wikipedia/vi/e/e3/K%E1%BA%BB_tr%E1%BB%99m_m%E1%BA%B7t_tr%C4%83ng_4_poster2.jpg",
                "https://www.youtube.com/watch?v=S1dnnQsY0QU",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.P,
                "Steve Carell (lồng tiếng), Kristen Wiig (lồng tiếng)");

            Movie m12 = createMovie("Gia Tài Của Ngoại", 127, "Chính kịch, Gia đình",
                "Một chàng trai trẻ bỏ học để về chăm sóc người bà đang mắc bệnh ung thư giai đoạn cuối, với hi vọng được thừa hưởng gia tài.",
                "https://upload.wikimedia.org/wikipedia/vi/3/37/HOW_TO_MAKE_MILLION_BEFORE_GRANDMA_DIES_%E2%80%93_Vietnam_poster.jpg",
                "https://www.youtube.com/watch?v=Y_qYJ6To93k",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C13,
                "Putthipong Assaratanakul, Usha Seamkhum");

            Movie m13 = createMovie("The Dark Knight (Kỵ Sĩ Bóng Đêm)", 152, "Hành động, Tội phạm, Chính kịch",
                "Batman đối mặt với kẻ thù lớn nhất của mình, Joker, một tên tội phạm hỗn loạn muốn chứng minh rằng ai cũng có thể bị tha hóa.",
                "https://upload.wikimedia.org/wikipedia/vi/2/2d/Poster_phim_K%E1%BB%B5_s%C4%A9_b%C3%B3ng_%C4%91%C3%AAm_2008.jpg",
                "https://www.youtube.com/watch?v=AW_fVi_YGhE",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C16,
                "Christian Bale, Heath Ledger");

            Movie m14 = createMovie("Inception", 148, "Hành động, Khoa học viễn tưởng, Giật gân",
                "Một tên trộm chuyên nghiệp chuyên đánh cắp thông tin bằng cách xâm nhập vào tiềm thức của người khác, được giao nhiệm vụ cấy một ý tưởng.",
                "https://upload.wikimedia.org/wikipedia/en/2/2e/Inception_%282010%29_theatrical_poster.jpg",
                "https://www.youtube.com/watch?v=YoHD9XEInc0",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C16,
                "Leonardo DiCaprio, Joseph Gordon-Levitt");

            Movie m15 = createMovie("Interstellar", 169, "Khoa học viễn tưởng, Phiêu lưu, Chính kịch",
                "Một nhóm phi hành gia du hành qua một hố đen vũ trụ để tìm kiếm một hành tinh mới có thể ở được cho nhân loại.",
                "https://upload.wikimedia.org/wikipedia/vi/4/46/Interstellar_poster.jpg",
                "https://www.youtube.com/watch?v=zSWdZVtXT7E",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C13,
                "Matthew McConaughey, Anne Hathaway");

            Movie m16 = createMovie("Parasite (Ký Sinh Trùng)", 132, "Chính kịch, Giật gân, Hài đen",
                "Một gia đình nghèo xâm nhập vào cuộc sống của một gia đình giàu có bằng cách giả mạo danh tính, dẫn đến những hậu quả bi thảm.",
                "https://upload.wikimedia.org/wikipedia/vi/c/cc/Poster_phim_Parasite_2019.jpg",
                "https://www.youtube.com/watch?v=5xH0HfJHsaY",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C18,
                "Song Kang-ho, Lee Sun-kyun");

            Movie m17 = createMovie("Spider-Man: No Way Home", 148, "Hành động, Phiêu lưu, Siêu anh hùng",
                "Danh tích của Peter Parker bị lộ, cậu tìm đến Doctor Strange để nhờ giúp đỡ. Phép thuật thất bại, kéo các kẻ thù từ đa vũ trụ đến.",
                "https://upload.wikimedia.org/wikipedia/en/0/00/Spider-Man_No_Way_Home_poster.jpg",
                "https://www.youtube.com/watch?v=JfVOs4VSpmA",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C13,
                "Tom Holland, Zendaya, Benedict Cumberbatch");

            Movie m18 = createMovie("Avengers: Endgame", 181, "Hành động, Khoa học viễn tưởng, Siêu anh hùng",
                "Sau cú búng tay của Thanos, các Avengers còn lại tập hợp để thực hiện một kế hoạch cuối cùng nhằm đảo ngược mọi thứ.",
                "https://upload.wikimedia.org/wikipedia/vi/2/2d/Avengers_Endgame_bia_teaser.jpg",
                "https://www.youtube.com/watch?v=TcMBFSGVi1c",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C13,
                "Robert Downey Jr., Chris Evans");

            Movie m19 = createMovie("The Batman", 176, "Hành động, Tội phạm, Chính kịch",
                "Batman theo đuổi Riddler, một kẻ giết người hàng loạt, và khám phá ra những bí mật đen tối về tham nhũng ở Gotham.",
                "https://upload.wikimedia.org/wikipedia/en/f/ff/The_Batman_%28film%29_poster.jpg",
                "https://www.youtube.com/watch?v=mqqft2x_Aa4",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C16,
                "Robert Pattinson, Zoë Kravitz");

            Movie m20 = createMovie("Barbie", 114, "Hài, Phiêu lưu, Giả tưởng",
                "Barbie và Ken rời khỏi Barbieland để đến thế giới thực, khám phá ra những niềm vui và hiểm họa của việc sống giữa con người.",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0b/Barbie_Logo.svg/1920px-Barbie_Logo.svg.png",
                "https://www.youtube.com/watch?v=pBk4NYhWNMM",
                Movie.MovieStatus.SPECIAL_RELEASE,
                Movie.AgeRating.C13,
                "Margot Robbie, Ryan Gosling");

            Movie m21 = createMovie("Bố Già", 128, "Gia đình, Hài, Chính kịch",
                "Phim xoay quanh cuộc sống của gia đình ông Ba Sang tại một xóm lao động, tập trung vào mâu thuẫn thế hệ giữa cha và con.",
                "https://upload.wikimedia.org/wikipedia/vi/7/72/Bo_gia_2021_ap_phich.jpg",
                "https://www.youtube.com/watch?v=jluSu8Rw6YE",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C16,
                "Trấn Thành, Tuấn Trần");

            Movie m22 = createMovie("Mắt Biếc", 117, "Lãng mạn, Chính kịch",
                "Dựa trên truyện của Nguyễn Nhật Ánh, phim là câu chuyện tình đơn phương của Ngạn dành cho Hà Lan, cô bạn có đôi mắt biếc.",
                "https://upload.wikimedia.org/wikipedia/vi/4/42/%C3%81p_ph%C3%ADch_phim_M%E1%BA%AFt_bi%E1%BA%BFc.jpg",
                "https://www.youtube.com/watch?v=MNm77lvTfi4",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C16,
                "Trần Nghĩa, Trúc Anh");

            Movie m23 = createMovie("Tiệc Trăng Máu", 118, "Hài, Chính kịch, Tâm lý",
                "Một nhóm bạn thân chơi một trò chơi nguy hiểm: công khai tất cả tin nhắn, cuộc gọi trên điện thoại của họ trong một bữa tiệc.",
                "https://upload.wikimedia.org/wikipedia/vi/c/cc/Tiec_trang_mau_poster.jpg",
                "https://www.youtube.com/watch?v=EEaeafFfVE8",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C18,
                "Thái Hòa, Thu Trang");

            Movie m24 = createMovie("Aquaman and the Lost Kingdom (Aquaman 2)", 124, "Hành động, Phiêu lưu, Giả tưởng",
                "Aquaman phải hợp tác với người em trai Orm để bảo vệ Atlantis khỏi Black Manta, kẻ đã sở hữu Cây đinh ba đen đầy quyền năng.",
                "https://upload.wikimedia.org/wikipedia/en/4/4a/Aquaman_and_the_Lost_Kingdom_poster.jpg",
                "https://www.youtube.com/watch?v=keZ70jipjXc",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C13,
                "Jason Momoa, Patrick Wilson");

            Movie m25 = createMovie("The Super Mario Bros. Movie (Phim Anh Em Super Mario)", 92, "Hoạt hình, Phiêu lưu, Hài",
                "Hai anh em thợ sửa ống nước Mario và Luigi bị lạc vào một thế giới phép thuật và phải giải cứu Công chúa Peach khỏi Bowser.",
                "https://upload.wikimedia.org/wikipedia/en/4/44/The_Super_Mario_Bros._Movie_poster.jpg",
                "https://www.youtube.com/watch?v=TnGl01FkMMo",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.P,
                "Chris Pratt (lồng tiếng), Anya Taylor-Joy (lồng tiếng)");

            Movie m26 = createMovie("Fast X (Fast & Furious 10)", 141, "Hành động, Tội phạm, Phiêu lưu",
                "Dom Toretto và gia đình phải đối mặt với Dante Reyes, con trai của trùm ma túy Hernan Reyes, kẻ muốn trả thù cho cái chết của cha mình.",
                "https://upload.wikimedia.org/wikipedia/vi/2/22/Fast_X_VN_poster.jpg",
                "https://www.youtube.com/watch?v=JSE9vhCuxs8",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C16,
                "Vin Diesel, Jason Momoa");

            Movie m27 = createMovie("Avatar: The Way of Water (Avatar: Dòng Chảy Của Nước)", 192, "Khoa học viễn tưởng, Hành động, Phiêu lưu",
                "Jake Sully và Neytiri cùng gia đình phải rời bỏ ngôi nhà của mình và tìm nơi ẩn náu tại các rạn san hô khi một mối đe dọa cũ quay trở lại.",
                "https://upload.wikimedia.org/wikipedia/vi/e/e0/Avatar_D%C3%B2ng_ch%E1%BA%A3y_c%E1%BB%A7a_n%C6%B0%E1%BB%9Bc_-_Poster_ch%C3%ADnh_th%E1%BB%A9c.jpg",
                "https://www.youtube.com/watch?v=d9MyW72ELq0",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C13,
                "Sam Worthington, Zoe Saldaña");

            Movie m28 = createMovie("Black Panther: Wakanda Forever", 161, "Hành động, Phiêu lưu, Siêu anh hùng",
                "Wakanda thương tiếc Vua T'Challa. Nữ hoàng Ramonda và Shuri phải chiến đấu để bảo vệ quốc gia của mình khỏi các thế lực xâm lược.",
                "https://upload.wikimedia.org/wikipedia/vi/3/3b/Black_Panther_Wakanda_Forever_poster.jpg",
                "https://www.youtube.com/watch?v=sKX4zA52B9c",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C13,
                "Letitia Wright, Lupita Nyong'o");

            Movie m29 = createMovie("Tenet", 150, "Hành động, Khoa học viễn tưởng, Giật gân",
                "Một đặc vụ bí mật phải tìm cách ngăn chặn Thế chiến III thông qua công nghệ 'nghịch đảo thời gian' phức tạp.",
                "https://upload.wikimedia.org/wikipedia/vi/1/18/Tenet_poster_VN.jpeg",
                "https://www.youtube.com/watch?v=AZGcmvrTX9M",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C16,
                "John David Washington, Robert Pattinson");

            Movie m30 = createMovie("Đào, Phở và Piano", 100, "Lịch sử, Chiến tranh, Lãng mạn",
                "Lấy bối cảnh trận chiết 60 ngày đêm cuối năm 1946, phim kể về mối tình của một anh tự vệ và một cô tiểu thư Hà Nội.",
                "https://upload.wikimedia.org/wikipedia/vi/2/29/%C3%81p_ph%C3%ADch_%C4%90%C3%A0o%2C_ph%E1%BB%9F_v%C3%A0_piano.jpg",
                "https://www.youtube.com/watch?v=qn1t_biQigc",
                Movie.MovieStatus.COMING_SOON,
                Movie.AgeRating.C13,
                "Doãn Quốc Đam, Cao Thùy Linh");

            movieRepo.saveAll(List.of(m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15, m16, m17, m18, m19, m20, m21, m22, m23, m24, m25, m26, m27, m28, m29, m30));
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

    private Movie createMovie(String title, Integer duration, String genre, String description, 
                             String posterUrl, String trailerUrl, Movie.MovieStatus status,
                             Movie.AgeRating ageRating, String actors) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setDuration(duration);
        movie.setGenre(genre);
        movie.setDescription(description);
        movie.setPosterUrl(posterUrl);
        movie.setTrailerUrl(trailerUrl);
        movie.setStatus(status);
        movie.setAgeRating(ageRating);
        movie.setActors(actors);
        return movie;
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

