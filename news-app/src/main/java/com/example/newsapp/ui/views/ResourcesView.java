package com.example.newsapp.ui.views;

import com.example.newsapp.domain.ResourceItem;
import com.example.newsapp.repositories.ResourceItemRepository;
import com.example.newsapp.ui.layout.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@PermitAll
@PageTitle("Ресурсы")
@Route(value = "resources", layout = MainLayout.class)
public class ResourcesView extends VerticalLayout {
    private final ResourceItemRepository resourceRepository;

    private final Grid<ResourceItem> grid = new Grid<>(ResourceItem.class, false);
    private final TextField name = new TextField("Название");
    private final TextField url = new TextField("URL");
    private final TextArea description = new TextArea("Описание");
    private final Checkbox pollingEnabled = new Checkbox("Опрашивать по расписанию");
    private final IntegerField pollingIntervalMinutes = new IntegerField("Интервал (мин)");
    private final Button save = new Button("Сохранить");
    private final Button delete = new Button("Удалить");
    private final Button cancel = new Button("Отмена");

    private final BeanValidationBinder<ResourceItem> binder = new BeanValidationBinder<>(ResourceItem.class);
    private ResourceItem current;

    public ResourcesView(ResourceItemRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
        setSizeFull();

        grid.addColumn(ResourceItem::getId).setHeader("ID").setAutoWidth(true);
        grid.addColumn(ResourceItem::getName).setHeader("Название").setAutoWidth(true);
        grid.addColumn(ResourceItem::getUrl).setHeader("URL").setAutoWidth(true);
        grid.addColumn(r -> r.getCreatedAt() != null ? r.getCreatedAt().toString() : "").setHeader("Создан").setAutoWidth(true);
        grid.addColumn(r -> r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : "").setHeader("Обновлено").setAutoWidth(true);
        grid.addColumn(r -> r.getLastProcessedAt() != null ? r.getLastProcessedAt().toString() : "").setHeader("Последняя обработка").setAutoWidth(true);
        grid.addColumn(r -> r.isPollingEnabled() ? "Да" : "Нет").setHeader("Опрашивать").setAutoWidth(true);
        grid.addColumn(r -> r.getPollingIntervalMinutes() != null ? r.getPollingIntervalMinutes() : null).setHeader("Интервал (мин)").setAutoWidth(true);
        grid.addColumn(ResourceItem::getLastPollStatus).setHeader("HTTP статус").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> edit(e.getValue()));

        // Form
        final FormLayout form = new FormLayout();
        name.setWidthFull();
        url.setWidthFull();
        description.setWidthFull();
        description.setMaxLength(1024);
        pollingIntervalMinutes.setMin(1);
        pollingIntervalMinutes.setStepButtonsVisible(true);
        form.add(name, url, description, pollingEnabled, pollingIntervalMinutes);

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        actions.setSpacing(true);

        binder.bindInstanceFields(this);

        save.addClickListener(e -> save());
        delete.addClickListener(e -> delete());
        cancel.addClickListener(e -> clearForm());

        VerticalLayout formWrapper = new VerticalLayout(form, actions);
        formWrapper.setWidth("520px");
        formWrapper.setPadding(false);

        SplitLayout split = new SplitLayout(grid, formWrapper);
        split.setSizeFull();
        split.setSplitterPosition(68);

        add(split);
        refreshGrid();
        clearForm();
    }

    private void refreshGrid() {
        List<ResourceItem> items = resourceRepository.findAll();
        grid.setItems(items);
    }

    private void clearForm() {
        current = new ResourceItem();
        binder.readBean(current);
        grid.deselectAll();
    }

    private void edit(ResourceItem item) {
        current = item != null ? item : new ResourceItem();
        binder.readBean(current);
    }

    @Transactional
    private void save() {
        try {
            binder.writeBean(current);
            resourceRepository.save(current);
            Notification.show("Сохранено");
            refreshGrid();
            clearForm();
        } catch (ValidationException ex) {
            Notification.show("Ошибка валидации: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }

    @Transactional
    private void delete() {
        if (current != null && current.getId() != null) {
            try {
                resourceRepository.deleteById(current.getId());
                Notification.show("Удалено");
                refreshGrid();
                clearForm();
            } catch (Exception ex) {
                Notification.show("Не удалось удалить: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        }
    }
}

