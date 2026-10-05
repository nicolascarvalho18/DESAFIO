package com.acme.payments;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();}
    @Bean UserDetailsService userDetailsService(JdbcTemplate jdbc){return username -> jdbc.queryForObject("SELECT email,password_hash,role FROM users WHERE email=?",(rs,n)->User.withUsername(rs.getString("email")).password(rs.getString("password_hash")).roles(rs.getString("role")).build(),username);}
    @Bean AuthenticationManager authenticationManager(UserDetailsService users,PasswordEncoder encoder){var provider=new DaoAuthenticationProvider(users);provider.setPasswordEncoder(encoder);return new ProviderManager(provider);}
    @Bean SecretKey jwtSecretKey(org.springframework.core.env.Environment env){var raw=Base64.getDecoder().decode(env.getRequiredProperty("app.jwt.secret"));if(raw.length<32)throw new IllegalStateException("JWT_SECRET must contain at least 32 decoded bytes");return new SecretKeySpec(raw,"HmacSHA256");}
    @Bean JwtDecoder jwtDecoder(SecretKey key){return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();}
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/login","/actuator/health","/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(Customizer.withDefaults())).build();
    }
}
