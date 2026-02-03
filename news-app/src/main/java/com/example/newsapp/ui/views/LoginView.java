package com.example.newsapp.ui.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        Div title = new Div();
        title.setText("Вход в News App");
        Button btn = new Button("Войти через Google", e -> goToGoogle());
        add(title, btn);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Auto-redirect to Google OAuth2 login
        goToGoogle();
    }

    private void goToGoogle() {
        UI.getCurrent().getPage().setLocation("/oauth2/authorization/google");
    }
}

