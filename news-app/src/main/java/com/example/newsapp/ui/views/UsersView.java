package com.example.newsapp.ui.views;

import com.example.newsapp.domain.AppUser;
import com.example.newsapp.repositories.AppUserRepository;
import com.example.newsapp.ui.layout.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@PermitAll
@PageTitle("Пользователи")
@Route(value = "", layout = MainLayout.class)
public class UsersView extends VerticalLayout {
    private final AppUserRepository userRepository;

    private final Grid<AppUser> grid = new Grid<>(AppUser.class, false);
    private final TextField username = new TextField("Логин");
    private final EmailField email = new EmailField("Email");
    private final Button save = new Button("Сохранить");
    private final Button delete = new Button("Удалить");
    private final Button cancel = new Button("Отмена");

    private final BeanValidationBinder<AppUser> binder = new BeanValidationBinder<>(AppUser.class);
    private AppUser current;

    public UsersView(AppUserRepository userRepository) {
        this.userRepository = userRepository;
        setSizeFull();

        grid.addColumn(AppUser::getId).setHeader("ID").setAutoWidth(true);
        grid.addColumn(AppUser::getUsername).setHeader("Логин").setAutoWidth(true);
        grid.addColumn(AppUser::getEmail).setHeader("Email").setAutoWidth(true);
        grid.addColumn(u -> u.getCreatedAt() != null ? u.getCreatedAt().toString() : "").setHeader("Создан").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> edit(e.getValue()));

        // Form
        final FormLayout form = new FormLayout();
        username.setWidthFull();
        email.setWidthFull();
        form.add(username, email);
        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        actions.setSpacing(true);

        binder.bindInstanceFields(this);

        save.addClickListener(e -> save());
        delete.addClickListener(e -> delete());
        cancel.addClickListener(e -> clearForm());

        VerticalLayout formWrapper = new VerticalLayout(form, actions);
        formWrapper.setWidth("420px");
        formWrapper.setPadding(false);

        SplitLayout split = new SplitLayout(grid, formWrapper);
        split.setSizeFull();
        split.setSplitterPosition(70);

        add(split);
        refreshGrid();
        clearForm();
    }

    private void refreshGrid() {
        List<AppUser> users = userRepository.findAll();
        grid.setItems(users);
    }

    private void clearForm() {
        current = new AppUser();
        binder.readBean(current);
        grid.deselectAll();
    }

    private void edit(AppUser user) {
        current = user != null ? user : new AppUser();
        binder.readBean(current);
    }

    @Transactional
    private void save() {
        try {
            binder.writeBean(current);
            userRepository.save(current);
            Notification.show("Сохранено");
            refreshGrid();
            clearForm();
        } catch (ValidationException ex) {
            Notification.show("Ошибка валидации: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
        } catch (DataIntegrityViolationException ex) {
            Notification.show("Логин/Email уже заняты", 3000, Notification.Position.MIDDLE);
        }
    }

    @Transactional
    private void delete() {
        if (current != null && current.getId() != null) {
            try {
                userRepository.deleteById(current.getId());
                Notification.show("Удалено");
                refreshGrid();
                clearForm();
            } catch (Exception ex) {
                Notification.show("Не удалось удалить: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        }
    }
}

