package com.liveklass.global.config;

import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        User creator = userRepository.save(User.create("강민준", "creator@liveklass.com", UserRole.CREATOR));
        User student1 = userRepository.save(User.create("이수현", "student1@liveklass.com", UserRole.STUDENT));
        User student2 = userRepository.save(User.create("박지원", "student2@liveklass.com", UserRole.STUDENT));

        log.info("=== Mock Users Initialized ===");
        log.info("CREATOR  | id={} | {}", creator.getId(), creator.getEmail());
        log.info("STUDENT1 | id={} | {}", student1.getId(), student1.getEmail());
        log.info("STUDENT2 | id={} | {}", student2.getId(), student2.getEmail());
        log.info("==============================");
    }
}
