package com.example.newsapp.config;

import com.example.newsapp.security.AllowedEmailOAuth2UserService;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class SecurityConfig extends VaadinWebSecurity {

    private final AllowedEmailOAuth2UserService allowedEmailOAuth2UserService;

    public SecurityConfig(AllowedEmailOAuth2UserService allowedEmailOAuth2UserService) {
        this.allowedEmailOAuth2UserService = allowedEmailOAuth2UserService;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Let Vaadin configure its internal security (static resources, internal endpoints)
        super.configure(http);

        http
            .oauth2Login(oauth -> oauth
                .loginPage("/oauth2/authorization/google")
                .userInfoEndpoint(u -> u.userService(allowedEmailOAuth2UserService))
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            );

        // Use a Vaadin login view that redirects to Google OAuth
        setLoginView(http, com.example.newsapp.ui.views.LoginView.class);
    }
}

