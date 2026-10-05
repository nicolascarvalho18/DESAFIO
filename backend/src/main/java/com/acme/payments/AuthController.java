package com.acme.payments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager; private final JwtTokenService tokens;
    public AuthController(AuthenticationManager authenticationManager,JwtTokenService tokens){this.authenticationManager=authenticationManager;this.tokens=tokens;}
    @PostMapping("/login") public TokenResponse login(@Valid @RequestBody LoginRequest body){var auth=authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.email(),body.password()));return new TokenResponse(tokens.issue(auth),"Bearer",3600);}
    public record LoginRequest(@Email @NotBlank String email,@NotBlank @Size(max=200) String password){}
    public record TokenResponse(String accessToken,String tokenType,long expiresIn){}
}
