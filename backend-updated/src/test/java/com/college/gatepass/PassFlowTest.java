package com.college.gatepass;

import com.college.gatepass.dto.Dtos;
import com.college.gatepass.entity.PassType;
import com.college.gatepass.entity.Role;
import com.college.gatepass.entity.User;
import com.college.gatepass.repository.UserRepository;
import com.college.gatepass.service.AuthService;
import com.college.gatepass.service.GatePassService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:gp;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "spring.data.redis.repositories.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "spring.cache.type=none"
        }
)
class PassFlowTest {

    @Autowired AuthService auth;
    @Autowired GatePassService passes;
    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    @Test void fullFlow() {
        // 1. Register student – ab MessageResponse aayega
        var msg = auth.register(new Dtos.RegisterRequest(
                "s@x.com", "password1", "Stu Dent",
                "1234567890",           // phone
                "E1",                   // enrollmentNo
                "Block-A",              // hostel
                "101",                  // roomNo
                "Computer Science",     // department
                "BCA",                  // course
                5,                      // semester
                "9876543210",           // studentMobile
                "9999988888",           // parentMobile
                Set.of("STUDENT")));
        assertThat(msg.message()).contains("OTP has been sent");

        // 2. Email verify karna (test ke liye directly DB se user ko verified kar do)
        User student = userRepo.findByEmail("s@x.com").orElseThrow();
        // Simulate OTP verification (bypass karo – production mein real flow test karna ho to verify API call karo)
        student.setEmailVerified(true);
        student.setEmailOtp(null);
        student.setOtpExpiry(null);
        userRepo.save(student);

        // 3. Warden directly DB mein
        User wardenUser = User.builder()
                .email("w@x.com")
                .passwordHash(encoder.encode("password1"))
                .fullName("War Den")
                .active(true)
                .roles(Set.of(Role.WARDEN))
                .build();
        userRepo.save(wardenUser);
        Long wardenId = wardenUser.getId();

        // 4. Security guard directly DB mein
        User securityUser = User.builder()
                .email("g@x.com")
                .passwordHash(encoder.encode("password1"))
                .fullName("Sec Guard")
                .active(true)
                .roles(Set.of(Role.SECURITY))
                .build();
        userRepo.save(securityUser);
        Long securityId = securityUser.getId();

        // 5. Pass create
        var p = passes.create(student.getId(), new Dtos.CreatePassRequest(
                "Family visit",                 // reason
                "Home - Jaipur",                // destination
                PassType.HOME,                  // passType
                Instant.now().plus(1, ChronoUnit.HOURS),   // leaveAt
                "Computer Science",             // department
                "BCA",                          // course
                5,                              // semester
                "9876543210",                   // studentMobile
                "9999988888",                   // parentMobile
                Instant.now().plus(8, ChronoUnit.HOURS)    // returnBy
        ));
        assertThat(p.status().name()).isEqualTo("PENDING");

        var approved = passes.approve(p.id(), wardenId, "ok", "127.0.0.1");
        assertThat(approved.status().name()).isEqualTo("APPROVED");
        assertThat(approved.qrToken()).isNotBlank();

        var used = passes.markUsed(approved.qrToken(), securityId, "127.0.0.1");
        assertThat(used.status().name()).isEqualTo("USED");

        var ret = passes.markUsed(approved.qrToken(), securityId, "127.0.0.1");
        assertThat(ret.status().name()).isEqualTo("RETURNED");
    }
}