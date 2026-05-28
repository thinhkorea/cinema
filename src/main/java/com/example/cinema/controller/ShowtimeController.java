package com.example.cinema.controller;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Room;
import com.example.cinema.domain.Showtime;
import com.example.cinema.dto.BulkShowtimeRequestDTO;
import com.example.cinema.dto.BulkShowtimeResultDTO;
import com.example.cinema.dto.ShowtimeRequestDTO;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.RoomRepository;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

    private static final double WEEKDAY_PRICE = 65000.0;
    private static final double WEEKEND_PRICE = 80000.0;
    private static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
    private static final LocalTime LAST_SHOWTIME_START = LocalTime.of(23, 59);
    private static final int ROOM_TURNAROUND_MINUTES = 10;
    private static final int MIN_START_TIME_GAP_MINUTES = 15;
    private static final int AUTO_SLOT_STEP_MINUTES = 15;
    private static final int MAX_SKIPPED_MESSAGES = 20;
    private static final int MAX_PREVIEW_CREATED_SHOWTIMES = 20;
    private static final int MAX_CANDIDATE_SLOTS = 48;
    private static final int MAX_NEIGHBOR_EXPANSION_STEPS = 8;
    private static final DateTimeFormatter SHOWTIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> SESSION_ORDER = List.of("morning", "afternoon", "evening", "late");

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SeatService seatService;

    public ShowtimeController(ShowtimeRepository showtimeRepository,
                              MovieRepository movieRepository,
                              RoomRepository roomRepository,
                              SeatService seatService) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.seatService = seatService;
    }

    @GetMapping
    public ResponseEntity<List<Showtime>> getAllShowtimes() {
        return ResponseEntity.ok(showtimeRepository.findAllWithActiveRoom());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getShowtimeById(@PathVariable Long id) {
        return showtimeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<Showtime>> getShowtimesByMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(showtimeRepository.findByMovie_MovieId(movieId));
    }

    @PostMapping
    public ResponseEntity<?> createShowtime(@RequestBody ShowtimeRequestDTO req) {
        String validationError = validateShowtimeTime(req);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        Movie movie = movieRepository.findById(req.getMovieId())
                .orElseThrow(() -> new IllegalArgumentException("Movie not found"));
        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        if (Boolean.FALSE.equals(room.getActive())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phong chieu da bi vo hieu hoa."));
        }

        validationError = validateRoomAvailability(req, null);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        validationError = validateShowtimeStartTimeUniqueness(req, null);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        validationError = validateShowtimeStartTimeGap(req, null);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        seatService.initializeSeatsForRoom(room);
        return ResponseEntity.ok(persistShowtime(movie, room, req));
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> createBulkShowtimes(@RequestBody BulkShowtimeRequestDTO req) {
        try {
            return ResponseEntity.ok(processBulkShowtimes(req, true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/bulk/preview")
    public ResponseEntity<?> previewBulkShowtimes(@RequestBody BulkShowtimeRequestDTO req) {
        try {
            return ResponseEntity.ok(processBulkShowtimes(req, false));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateShowtime(@PathVariable Long id, @RequestBody ShowtimeRequestDTO req) {
        String validationError = validateShowtimeTime(req);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        validationError = validateRoomAvailability(req, id);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        validationError = validateShowtimeStartTimeUniqueness(req, id);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        validationError = validateShowtimeStartTimeGap(req, id);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        return showtimeRepository.findById(id)
                .map(existing -> {
                    Movie movie = movieRepository.findById(req.getMovieId())
                            .orElseThrow(() -> new IllegalArgumentException("Movie not found"));
                    Room room = roomRepository.findById(req.getRoomId())
                            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
                    if (Boolean.FALSE.equals(room.getActive())) {
                        throw new IllegalArgumentException("Phong chieu da bi vo hieu hoa.");
                    }

                    existing.setMovie(movie);
                    existing.setRoom(room);
                    existing.setStartTime(req.getStartTime());
                    existing.setEndTime(req.getEndTime());
                    existing.setPrice(resolvePrice(req));
                    return ResponseEntity.ok(showtimeRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShowtime(@PathVariable Long id) {
        if (!showtimeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        showtimeRepository.deleteById(id);
        return ResponseEntity.ok("Showtime deleted");
    }

    private Showtime persistShowtime(Movie movie, Room room, ShowtimeRequestDTO req) {
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setStartTime(req.getStartTime());
        showtime.setEndTime(req.getEndTime());
        showtime.setPrice(resolvePrice(req));
        return showtimeRepository.save(showtime);
    }

    private Showtime buildPreviewShowtime(Movie movie, Room room, ShowtimeRequestDTO req) {
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setStartTime(req.getStartTime());
        showtime.setEndTime(req.getEndTime());
        showtime.setPrice(resolvePrice(req));
        return showtime;
    }

    private BulkShowtimeResultDTO processBulkShowtimes(BulkShowtimeRequestDTO req, boolean persist) {
        String validationError = validateBulkRequest(req);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        Movie movie = movieRepository.findById(req.getMovieId())
                .orElseThrow(() -> new IllegalArgumentException("Movie not found"));

        List<Long> requestedRoomIds = resolveRoomIds(req);
        List<Room> targetRooms = roomRepository.findAllById(requestedRoomIds);
        if (targetRooms.size() != requestedRoomIds.size()) {
            throw new IllegalArgumentException("Co phong chieu khong ton tai trong danh sach da chon.");
        }
        if (targetRooms.stream().anyMatch(room -> Boolean.FALSE.equals(room.getActive()))) {
            throw new IllegalArgumentException("Danh sach co phong chieu da bi vo hieu hoa.");
        }

        if (persist) {
            for (Room room : targetRooms) {
                seatService.initializeSeatsForRoom(room);
            }
        }

        BulkShowtimeResultDTO result = BulkShowtimeResultDTO.builder()
                .limitApplied(req.getMaxCreatedCount())
                .build();

        Integer maxCreatedCount = req.getMaxCreatedCount();
        boolean hasLimit = maxCreatedCount != null && maxCreatedCount > 0;
        int targetCreatedCount = resolveTargetCreatedCount(req, targetRooms.size(), hasLimit, persist);
        List<String> candidateTimeSlots = buildCandidateTimeSlots(
                req.getSessions(),
                req.getTimeSlots(),
                targetCreatedCount);

        LocalDate showDate = req.getShowDate();
        Set<LocalDateTime> acceptedStartTimesInBatch = new HashSet<>();
        List<ShowtimeRequestDTO> acceptedRequestsInBatch = new ArrayList<>();

        for (String timeSlot : candidateTimeSlots) {
            if (hasLimit && result.getCreatedCount() >= maxCreatedCount) {
                result.setLimitReached(true);
                break;
            }

            LocalTime slotTime;
            try {
                slotTime = LocalTime.parse(timeSlot, TIME_SLOT_FORMAT);
            } catch (DateTimeParseException e) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                addSkippedMessage(result, "Khung gio khong hop le: " + timeSlot);
                continue;
            }

            LocalDateTime startTime = LocalDateTime.of(showDate, slotTime);
            LocalDateTime endTime = startTime.plusMinutes(movie.getDuration());
            if (acceptedStartTimesInBatch.contains(startTime)) {
                continue;
            }

            Room selectedRoom = null;
            ShowtimeRequestDTO selectedRequest = null;
            String lastFailure = null;

            for (Room room : targetRooms) {
                ShowtimeRequestDTO showtimeRequest = ShowtimeRequestDTO.builder()
                        .movieId(req.getMovieId())
                        .roomId(room.getRoomId())
                        .startTime(startTime)
                        .endTime(endTime)
                        .price(req.getPrice())
                        .build();

                validationError = validateShowtimeTime(showtimeRequest);
                if (validationError != null) {
                    lastFailure = buildSkippedMessage(room.getRoomName(), startTime, validationError);
                    break;
                }

                validationError = validateShowtimeStartTimeUniqueness(showtimeRequest, null);
                if (validationError != null) {
                    lastFailure = buildSkippedMessage(room.getRoomName(), startTime, validationError);
                    break;
                }

                validationError = validateShowtimeStartTimeGap(showtimeRequest, null);
                if (validationError != null) {
                    lastFailure = buildSkippedMessage(room.getRoomName(), startTime, validationError);
                    break;
                }

                validationError = validateBatchRoomAvailability(showtimeRequest, room, acceptedRequestsInBatch);
                if (validationError != null) {
                    lastFailure = buildSkippedMessage(room.getRoomName(), startTime, validationError);
                    continue;
                }

                validationError = validateRoomAvailability(showtimeRequest, null);
                if (validationError != null) {
                    lastFailure = buildSkippedMessage(room.getRoomName(), startTime, validationError);
                    continue;
                }

                selectedRoom = room;
                selectedRequest = showtimeRequest;
                break;
            }

            if (selectedRoom == null || selectedRequest == null) {
                if (lastFailure != null) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    addSkippedMessage(result, lastFailure);
                }
                continue;
            }

            Showtime processedShowtime = persist
                    ? persistShowtime(movie, selectedRoom, selectedRequest)
                    : buildPreviewShowtime(movie, selectedRoom, selectedRequest);

            acceptedRequestsInBatch.add(selectedRequest);
            acceptedStartTimesInBatch.add(startTime);
            result.setCreatedCount(result.getCreatedCount() + 1);
            result.getCreatedShowtimes().add(ShowtimeRequestDTO.builder()
                    .movieId(movie.getMovieId())
                    .roomId(selectedRoom.getRoomId())
                    .startTime(processedShowtime.getStartTime())
                    .endTime(processedShowtime.getEndTime())
                    .price(processedShowtime.getPrice())
                    .build());
        }

        return result;
    }

    private int resolveTargetCreatedCount(BulkShowtimeRequestDTO req, int roomCount, boolean hasLimit, boolean persist) {
        if (hasLimit && req.getMaxCreatedCount() != null) {
            return req.getMaxCreatedCount();
        }

        int preferredSlotCount = req.getTimeSlots() != null ? req.getTimeSlots().size() : 0;
        int selectedSessionCount = req.getSessions() != null ? req.getSessions().size() : 0;
        int basePreferenceCount = Math.max(preferredSlotCount, selectedSessionCount * 2);
        int defaultTarget = Math.max(basePreferenceCount, Math.max(roomCount, 1) * 3);
        if (!persist) {
            defaultTarget = Math.min(defaultTarget, MAX_PREVIEW_CREATED_SHOWTIMES);
        }
        return Math.max(defaultTarget, 1);
    }

    private List<String> buildCandidateTimeSlots(
            List<String> sessions,
            List<String> preferredTimeSlots,
            int targetCreatedCount) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        List<String> orderedPreferredSlots = buildPreferredTimeSlots(sessions, preferredTimeSlots);
        for (String slot : orderedPreferredSlots) {
            if (slot != null && !slot.isBlank()) {
                candidates.add(slot.trim());
            }
        }

        int preferredCount = candidates.size();
        int candidateLimit = Math.min(
                MAX_CANDIDATE_SLOTS,
                Math.max(Math.max(targetCreatedCount, preferredCount) * 4, preferredCount + 8));

        addNeighborCandidateTimeSlots(candidates, orderedPreferredSlots, candidateLimit);

        LocalTime cursor = OPENING_TIME;
        List<String> generatedSlots = new ArrayList<>(candidateLimit);
        while (!cursor.isAfter(LAST_SHOWTIME_START) && generatedSlots.size() < candidateLimit) {
            generatedSlots.add(cursor.format(TIME_SLOT_FORMAT));
            cursor = cursor.plusMinutes(AUTO_SLOT_STEP_MINUTES);
        }

        for (String slot : orderBulkTimeSlots(generatedSlots)) {
            candidates.add(slot);
            if (candidates.size() >= candidateLimit) {
                break;
            }
        }

        return new ArrayList<>(candidates);
    }

    private List<String> buildPreferredTimeSlots(List<String> sessions, List<String> preferredTimeSlots) {
        LinkedHashSet<String> preferredSlots = new LinkedHashSet<>();
        for (String session : orderSessions(sessions)) {
            preferredSlots.addAll(buildTimeSlotsForSession(session));
        }

        if (preferredSlots.isEmpty()) {
            preferredSlots.addAll(orderBulkTimeSlots(preferredTimeSlots));
        }

        return new ArrayList<>(preferredSlots);
    }

    private List<String> orderSessions(List<String> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        List<String> normalizedSessions = new ArrayList<>();
        for (String session : sessions) {
            String normalized = normalizeSession(session);
            if (normalized != null && !normalizedSessions.contains(normalized)) {
                normalizedSessions.add(normalized);
            }
        }

        normalizedSessions.sort(Comparator.comparingInt(SESSION_ORDER::indexOf));
        return normalizedSessions;
    }

    private List<String> buildTimeSlotsForSession(String session) {
        LocalTime start;
        LocalTime end;
        switch (session) {
            case "morning":
                start = OPENING_TIME;
                end = LocalTime.of(11, 59);
                break;
            case "afternoon":
                start = LocalTime.of(12, 0);
                end = LocalTime.of(17, 59);
                break;
            case "evening":
                start = LocalTime.of(18, 0);
                end = LocalTime.of(22, 59);
                break;
            case "late":
                start = LocalTime.of(23, 0);
                end = LocalTime.of(23, 59);
                break;
            default:
                return List.of();
        }

        List<String> slots = new ArrayList<>();
        LocalTime cursor = start;
        while (!cursor.isAfter(end)) {
            slots.add(cursor.format(TIME_SLOT_FORMAT));
            cursor = cursor.plusMinutes(AUTO_SLOT_STEP_MINUTES);
        }
        return orderBulkTimeSlots(slots);
    }

    private String normalizeSession(String session) {
        if (session == null || session.isBlank()) {
            return null;
        }

        String normalized = session.trim().toLowerCase(Locale.ROOT);
        if ("sang".equals(normalized)) {
            return "morning";
        }
        if ("chieu".equals(normalized)) {
            return "afternoon";
        }
        if ("toi".equals(normalized)) {
            return "evening";
        }
        if ("suat muon".equals(normalized) || "suatmuon".equals(normalized) || "muon".equals(normalized)) {
            return "late";
        }
        return SESSION_ORDER.contains(normalized) ? normalized : null;
    }

    private void addNeighborCandidateTimeSlots(Set<String> candidates, List<String> preferredTimeSlots, int candidateLimit) {
        if (preferredTimeSlots == null || preferredTimeSlots.isEmpty() || candidates.size() >= candidateLimit) {
            return;
        }

        for (int step = 1; step <= MAX_NEIGHBOR_EXPANSION_STEPS && candidates.size() < candidateLimit; step++) {
            int minuteOffset = step * AUTO_SLOT_STEP_MINUTES;
            for (String preferredSlot : preferredTimeSlots) {
                LocalTime baseTime;
                try {
                    baseTime = LocalTime.parse(preferredSlot, TIME_SLOT_FORMAT);
                } catch (DateTimeParseException e) {
                    continue;
                }

                addCandidateTime(candidates, baseTime.plusMinutes(minuteOffset), candidateLimit);
                if (candidates.size() >= candidateLimit) {
                    break;
                }

                addCandidateTime(candidates, baseTime.minusMinutes(minuteOffset), candidateLimit);
                if (candidates.size() >= candidateLimit) {
                    break;
                }
            }
        }
    }

    private void addCandidateTime(Set<String> candidates, LocalTime slotTime, int candidateLimit) {
        if (candidates.size() >= candidateLimit) {
            return;
        }
        if (slotTime.isBefore(OPENING_TIME) || slotTime.isAfter(LAST_SHOWTIME_START)) {
            return;
        }
        candidates.add(slotTime.format(TIME_SLOT_FORMAT));
    }

    private String validateBatchRoomAvailability(ShowtimeRequestDTO req, Room room, List<ShowtimeRequestDTO> acceptedRequestsInBatch) {
        for (ShowtimeRequestDTO accepted : acceptedRequestsInBatch) {
            if (!room.getRoomId().equals(accepted.getRoomId())) {
                continue;
            }

            LocalDateTime guardedStart = req.getStartTime().minusMinutes(ROOM_TURNAROUND_MINUTES);
            LocalDateTime guardedEnd = req.getEndTime().plusMinutes(ROOM_TURNAROUND_MINUTES);
            boolean overlaps = accepted.getStartTime().isBefore(guardedEnd)
                    && accepted.getEndTime().isAfter(guardedStart);
            if (overlaps) {
                return "Phong can nghi it nhat " + ROOM_TURNAROUND_MINUTES + " phut giua hai suat trong cung dot tao.";
            }
        }
        return null;
    }

    private String validateBulkRequest(BulkShowtimeRequestDTO req) {
        if (req == null) {
            return "Thieu du lieu tao hang loat suat chieu.";
        }
        if (req.getMovieId() == null) {
            return "Vui long chon phim.";
        }
        if (resolveRoomIds(req).isEmpty()) {
            return "Vui long chon it nhat mot phong chieu.";
        }
        if (req.getShowDate() == null) {
            return "Vui long chon ngay chieu.";
        }
        if (req.getShowDate().isBefore(LocalDate.now())) {
            return "Ngay chieu khong duoc nho hon ngay hien tai.";
        }
        boolean hasSessions = req.getSessions() != null && !req.getSessions().isEmpty();
        boolean hasTimeSlots = req.getTimeSlots() != null && !req.getTimeSlots().isEmpty();
        if (!hasSessions && !hasTimeSlots) {
            return "Vui long chon it nhat mot buoi chieu.";
        }
        if (req.getMaxCreatedCount() != null && req.getMaxCreatedCount() <= 0) {
            return "Gioi han so suat phai lon hon 0 neu co nhap.";
        }

        if (hasSessions) {
            List<String> invalidSessions = new ArrayList<>();
            for (String session : req.getSessions()) {
                if (normalizeSession(session) == null) {
                    invalidSessions.add(String.valueOf(session));
                }
            }
            if (!invalidSessions.isEmpty()) {
                return "Buoi chieu khong hop le: " + String.join(", ", invalidSessions);
            }
        }

        if (!hasTimeSlots) {
            return null;
        }

        List<String> invalidSlots = new ArrayList<>();
        for (String slot : req.getTimeSlots()) {
            if (slot == null || slot.isBlank()) {
                invalidSlots.add("(trong)");
                continue;
            }
            try {
                LocalTime.parse(slot.trim(), TIME_SLOT_FORMAT);
            } catch (DateTimeParseException e) {
                invalidSlots.add(slot);
            }
        }

        if (!invalidSlots.isEmpty()) {
            return "Khung gio khong hop le: " + String.join(", ", invalidSlots);
        }
        return null;
    }

    private List<Long> resolveRoomIds(BulkShowtimeRequestDTO req) {
        Set<Long> uniqueRoomIds = new LinkedHashSet<>();
        if (req.getRoomIds() != null) {
            for (Long roomId : req.getRoomIds()) {
                if (roomId != null) {
                    uniqueRoomIds.add(roomId);
                }
            }
        }
        if (req.getRoomId() != null) {
            uniqueRoomIds.add(req.getRoomId());
        }
        return new ArrayList<>(uniqueRoomIds);
    }

    private String buildSkippedMessage(String roomName, LocalDateTime startTime, String reason) {
        return "[" + roomName + "] " + startTime.format(SHOWTIME_FORMAT) + " - " + reason;
    }

    private void addSkippedMessage(BulkShowtimeResultDTO result, String message) {
        if (result.getSkippedMessages().size() >= MAX_SKIPPED_MESSAGES) {
            return;
        }
        result.getSkippedMessages().add(message);
    }

    private Double resolvePrice(ShowtimeRequestDTO req) {
        if (req.getPrice() != null && req.getPrice() > 0) {
            return req.getPrice();
        }
        if (req.getStartTime() == null) {
            return WEEKDAY_PRICE;
        }

        DayOfWeek day = req.getStartTime().getDayOfWeek();
        boolean isWeekday = day.getValue() >= DayOfWeek.MONDAY.getValue()
                && day.getValue() <= DayOfWeek.THURSDAY.getValue();
        return isWeekday ? WEEKDAY_PRICE : WEEKEND_PRICE;
    }

    private String validateShowtimeTime(ShowtimeRequestDTO req) {
        if (req == null || req.getStartTime() == null || req.getEndTime() == null) {
            return "Vui long chon day du gio bat dau va gio ket thuc suat chieu.";
        }

        LocalDateTime startTime = req.getStartTime();
        LocalDateTime endTime = req.getEndTime();
        if (startTime.isBefore(LocalDateTime.now())) {
            return "Khong the tao suat chieu co gio bat dau nho hon thoi diem hien tai.";
        }
        if (!endTime.isAfter(startTime)) {
            return "Gio ket thuc phai sau gio bat dau.";
        }

        LocalTime start = startTime.toLocalTime();
        if (start.isBefore(OPENING_TIME) || start.isAfter(LAST_SHOWTIME_START)) {
            return "Suat chieu chi duoc bat dau trong khung gio hoat dong cua rap: 08:30 - 23:59.";
        }
        if (start.getMinute() % 5 != 0) {
            return "Phut bat dau suat chieu phai chia het cho 5.";
        }
        return null;
    }

    private String validateRoomAvailability(ShowtimeRequestDTO req, Long excludeShowtimeId) {
        if (req.getRoomId() == null) {
            return "Vui long chon phong chieu.";
        }

        List<Showtime> conflicts = showtimeRepository.findOverlappingShowtimes(
                req.getRoomId(),
                req.getStartTime().minusMinutes(ROOM_TURNAROUND_MINUTES),
                req.getEndTime().plusMinutes(ROOM_TURNAROUND_MINUTES),
                excludeShowtimeId);
        if (conflicts.isEmpty()) {
            return null;
        }

        Showtime conflict = conflicts.get(0);
        String movieTitle = conflict.getMovie() != null ? conflict.getMovie().getTitle() : "phim khac";
        String roomName = conflict.getRoom() != null ? conflict.getRoom().getRoomName() : "phong nay";
        return "Phong " + roomName + " da co suat chieu " + movieTitle
                + " tu " + conflict.getStartTime().format(SHOWTIME_FORMAT)
                + " den " + conflict.getEndTime().format(SHOWTIME_FORMAT)
                + ". Phong can nghi it nhat " + ROOM_TURNAROUND_MINUTES
                + " phut giua hai suat. Vui long chon phong hoac thoi gian khac.";
    }

    private List<String> orderBulkTimeSlots(List<String> timeSlots) {
        if (timeSlots == null || timeSlots.isEmpty()) {
            return List.of();
        }

        List<String> orderedSlots = new ArrayList<>(timeSlots);
        orderedSlots.sort(String::compareTo);
        return orderedSlots;
    }

    private String validateShowtimeStartTimeUniqueness(ShowtimeRequestDTO req, Long excludeShowtimeId) {
        if (req.getStartTime() == null) {
            return null;
        }

        List<Showtime> conflicts = showtimeRepository.findSameStartTime(req.getStartTime(), excludeShowtimeId);
        if (conflicts.isEmpty()) {
            return null;
        }

        Showtime conflict = conflicts.get(0);
        String movieTitle = conflict.getMovie() != null ? conflict.getMovie().getTitle() : "mot phim khac";
        String roomName = conflict.getRoom() != null ? conflict.getRoom().getRoomName() : "phong khac";
        return "Da co suat chieu cua " + movieTitle + " bat dau luc "
                + conflict.getStartTime().format(SHOWTIME_FORMAT)
                + " tai " + roomName
                + ". Khong the de nhieu phong co cung mot gio bat dau suat chieu.";
    }

    private String validateShowtimeStartTimeGap(ShowtimeRequestDTO req, Long excludeShowtimeId) {
        if (req.getStartTime() == null) {
            return null;
        }

        LocalDateTime windowStart = req.getStartTime().minusMinutes(MIN_START_TIME_GAP_MINUTES - 1L);
        LocalDateTime windowEnd = req.getStartTime().plusMinutes(MIN_START_TIME_GAP_MINUTES - 1L);
        List<Showtime> conflicts = showtimeRepository.findNearbyStartTimes(windowStart, windowEnd, excludeShowtimeId);
        if (conflicts.isEmpty()) {
            return null;
        }

        Showtime conflict = conflicts.get(0);
        long minuteGap = Math.abs(java.time.Duration.between(conflict.getStartTime(), req.getStartTime()).toMinutes());
        String movieTitle = conflict.getMovie() != null ? conflict.getMovie().getTitle() : "mot phim khac";
        String roomName = conflict.getRoom() != null ? conflict.getRoom().getRoomName() : "phong khac";
        return "Gio bat dau phai cach it nhat " + MIN_START_TIME_GAP_MINUTES
                + " phut so voi suat da co. Hien da co suat cua " + movieTitle
                + " tai " + roomName
                + " bat dau luc " + conflict.getStartTime().format(SHOWTIME_FORMAT)
                + " (cach " + minuteGap + " phut).";
    }
}
