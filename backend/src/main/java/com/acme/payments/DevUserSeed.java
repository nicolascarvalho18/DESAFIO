package com.acme.payments;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration @Profile("dev")
public class DevUserSeed {
    @Bean CommandLineRunner seedDemoUser(JdbcTemplate jdbc,PasswordEncoder encoder){return args->{Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,"demo@example.com");if(count==null||count==0)jdbc.update("INSERT INTO users(email,password_hash,role) VALUES (?,?,?)","demo@example.com",encoder.encode("Demo123!"),"USER");};}
}
