package com.morphoaid.backend.config;

import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DevDataSeeder implements CommandLineRunner {

        private final UserRepository userRepository;
        private final CaseRepository caseRepository;
        private final AIResultRepository aiResultRepository;
        private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
        private final com.morphoaid.backend.repository.InvitationTokenRepository invitationTokenRepository;

        @Autowired
        public DevDataSeeder(UserRepository userRepository, CaseRepository caseRepository,
                        AIResultRepository aiResultRepository,
                        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                        com.morphoaid.backend.repository.InvitationTokenRepository invitationTokenRepository) {
                this.userRepository = userRepository;
                this.caseRepository = caseRepository;
                this.aiResultRepository = aiResultRepository;
                this.passwordEncoder = passwordEncoder;
                this.invitationTokenRepository = invitationTokenRepository;
        }

        @Override
        public void run(String... args) throws Exception {
                String adminEmail = "admin@test.com";
                String dataUseEmail = "demo@morphoaid.com";

                // 1. Seed User
                User savedUser;
                if (userRepository.findByEmail(adminEmail).isEmpty()) {
                        User adminUser = User.builder()
                                        .email(adminEmail)
                                        .password(passwordEncoder.encode("Admin123!"))
                                        .role(Role.ADMIN)
                                        .fullName("MorphoAid Admin")
                                        .organization("MorphoAid")
                                        .build();

                        savedUser = userRepository.save(adminUser);

                        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DevDataSeeder.class);
                        logger.info("Seeded admin: {} (password: Admin123!)", adminEmail);
                } else {
                        savedUser = userRepository.findByEmail(adminEmail).get();
                        // Fix for 401: ensure password is correct in dev
                        savedUser.setPassword(passwordEncoder.encode("Admin123!"));
                        userRepository.save(savedUser);
                        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DevDataSeeder.class);
                        logger.info("Updated existing admin: {} (password: Admin123!)", adminEmail);
                }

                if (userRepository.findByEmail(dataUseEmail).isEmpty()) {
                        User dataUseUser = User.builder()
                                        .email(dataUseEmail)
                                        .password(passwordEncoder.encode("demopass"))
                                        .role(Role.DATA_USE)
                                        .fullName("Demo User")
                                        .organization("MorphoAid Demo")
                                        .build();

                        userRepository.save(dataUseUser);
                }

                if (invitationTokenRepository.count() == 0) {
                        com.morphoaid.backend.entity.InvitationToken token = com.morphoaid.backend.entity.InvitationToken
                                        .builder()
                                        .token("DEMO-DATAPREP-TOKEN-123")
                                        .role(Role.DATA_PREP)
                                        .expiresAt(java.time.LocalDateTime.now().plusDays(30))
                                        .build();
                        invitationTokenRepository.save(token);
                        System.out.println("====== SEEDED DATAPREP TOKEN: DEMO-DATAPREP-TOKEN-123 ======");
                }

                // 2. Seed Cases if empty
                if (caseRepository.count() == 0) {

                        Case case1 = Case.builder()
                                        .patientCode("PT-1001")
                                        .technicianId("TECH-A1")
                                        .location("Zone 1 Rural Clinic")
                                        .status(CaseStatus.ANALYZED)
                                        .imagePath("/storage/dummy/sample1.png")
                                        .uploadedBy(savedUser)
                                        .build();

                        Case case2 = Case.builder()
                                        .patientCode("PT-1002")
                                        .technicianId("TECH-B2")
                                        .location("City Hospital")
                                        .status(CaseStatus.PENDING)
                                        .imagePath("/storage/dummy/sample2.png")
                                        .uploadedBy(savedUser)
                                        .build();

                        Case case3 = Case.builder()
                                        .patientCode("PT-1003")
                                        .technicianId("TECH-A1")
                                        .location("Zone 1 Rural Clinic")
                                        .status(CaseStatus.REVIEWED)
                                        .imagePath("/storage/dummy/sample3.png")
                                        .uploadedBy(savedUser)
                                        .build();

                        Case case4 = Case.builder()
                                        .patientCode("PT-2044")
                                        .technicianId("TECH-C3")
                                        .location("Mobile Unit North")
                                        .status(CaseStatus.PENDING)
                                        .imagePath("/storage/dummy/sample4.png")
                                        .uploadedBy(savedUser)
                                        .build();

                        Case case5 = Case.builder()
                                        .patientCode("PT-3099")
                                        .technicianId("TECH-D4")
                                        .location("Central Lab")
                                        .status(CaseStatus.PENDING)
                                        .imagePath("/storage/dummy/sample5.png")
                                        .uploadedBy(savedUser)
                                        .build();

                        List<Case> savedCases = caseRepository
                                        .saveAll(Arrays.asList(case1, case2, case3, case4, case5));

                        // Seed 1 AI Result for Case 1
                        AIResult ai1 = AIResult.builder()
                                        .caseEntity(savedCases.get(0))
                                        .parasiteStage("P. falciparum ring")
                                        .drugExposure(false)
                                        .confidence(0.98)
                                        .rawResponseJson("{\"type\": \"falciparum\", \"stage\": \"ring\"}")
                                        .build();

                        aiResultRepository.save(ai1);
                }
        }
}
