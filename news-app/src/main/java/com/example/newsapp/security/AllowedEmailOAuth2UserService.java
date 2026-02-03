package com.example.newsapp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AllowedEmailOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private static final Logger log = LoggerFactory.getLogger(AllowedEmailOAuth2UserService.class);

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final Set<String> allowedEmailsLowerCase;

    public AllowedEmailOAuth2UserService(
            @Value("${app.security.allowedEmails:}") String allowedEmails
    ) {
        this.allowedEmailsLowerCase = Arrays.stream(allowedEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (this.allowedEmailsLowerCase.isEmpty()) {
            log.warn("ALLOWED_EMAILS is empty — allowing all Google accounts to sign in");
        } else {
            log.info("Configured {} allowed email(s) for sign-in", this.allowedEmailsLowerCase.size());
        }
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User user = delegate.loadUser(userRequest);
        String email = extractEmail(user);
        if (!isAllowed(email)) {
            log.warn("Denied sign-in for email '{}'", email);
            throw new BadCredentialsException("Email is not allowed");
        }
        log.info("Allowed sign-in for email '{}'", email);
        return user;
    }

    private String extractEmail(AuthenticatedPrincipal principal) {
        if (principal instanceof OAuth2User oAuth2User) {
            Object emailAttr = oAuth2User.getAttributes().get("email");
            if (emailAttr != null) {
                return String.valueOf(emailAttr);
            }
        }
        return null;
    }

    private boolean isAllowed(String email) {
        if (email == null) {
            return false;
        }
        if (allowedEmailsLowerCase.isEmpty()) {
            // Empty allowlist => allow all
            return true;
        }
        return allowedEmailsLowerCase.contains(email.toLowerCase(Locale.ROOT));
    }
}

