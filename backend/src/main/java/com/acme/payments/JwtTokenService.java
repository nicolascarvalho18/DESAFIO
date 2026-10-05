package com.acme.payments;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JwtTokenService {
    private final SecretKey key; private final ObjectMapper mapper;
    public JwtTokenService(SecretKey key,ObjectMapper mapper){this.key=key;this.mapper=mapper;}
    public String issue(org.springframework.security.core.Authentication auth){
        try {
            var now=Instant.now();var header=Map.of("alg","HS256","typ","JWT");
            var roles=auth.getAuthorities().stream().map(a->a.getAuthority()).collect(Collectors.toList());
            var claims=Map.of("iss","northstar-payments","sub",auth.getName(),"iat",now.getEpochSecond(),"exp",now.plusSeconds(3600).getEpochSecond(),"roles",roles);
            var enc=Base64.getUrlEncoder().withoutPadding();var unsigned=enc.encodeToString(mapper.writeValueAsBytes(header))+"."+enc.encodeToString(mapper.writeValueAsBytes(claims));
            var mac=Mac.getInstance("HmacSHA256");mac.init(key);return unsigned+"."+enc.encodeToString(mac.doFinal(unsigned.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch(Exception e){throw new IllegalStateException("Could not issue access token",e);}
    }
}

