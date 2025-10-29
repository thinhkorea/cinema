package com.example.cinema.controller;

import com.example.cinema.domain.Staff;
import com.example.cinema.domain.User;
import com.example.cinema.repository.StaffRepository;
import com.example.cinema.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staffs")
@CrossOrigin(origins = "*")
public class StaffController {

    private final StaffRepository staffRepo;
    private final UserRepository userRepo;

    public StaffController(StaffRepository staffRepo, UserRepository userRepo) {
        this.staffRepo = staffRepo;
        this.userRepo = userRepo;
    }

    // Lấy danh sách toàn bộ nhân viên
    @GetMapping
    public ResponseEntity<List<Staff>> getAllStaff() {
        return ResponseEntity.ok(staffRepo.findAll());
    }

    // Xem chi tiết 1 nhân viên
    @GetMapping("/{id}")
    public ResponseEntity<?> getStaffById(@PathVariable Long id) {
        return staffRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Tạo nhân viên mới (liên kết user sẵn có)
    @PostMapping
    public ResponseEntity<?> createStaff(@RequestBody Staff staff) {
        if (staff.getUser() != null && staff.getUser().getUserId() != null) {
            User user = userRepo.findById(staff.getUser().getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            staff.setUser(user);
        }
        return ResponseEntity.ok(staffRepo.save(staff));
    }

    // Cập nhật nhân viên
    @PutMapping("/{id}")
    public ResponseEntity<?> updateStaff(@PathVariable Long id, @RequestBody Staff updated) {
        return staffRepo.findById(id)
                .map(s -> {
                    s.setPhone(updated.getPhone());
                    s.setEmail(updated.getEmail());
                    s.setPosition(updated.getPosition());
                    s.setSalary(updated.getSalary());
                    s.setStatus(updated.getStatus());
                    s.setGender(updated.getGender());
                    return ResponseEntity.ok(staffRepo.save(s));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Xóa nhân viên
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStaff(@PathVariable Long id) {
        staffRepo.deleteById(id);
        return ResponseEntity.ok().body("Staff deleted successfully");
    }
}
