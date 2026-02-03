package com.example.newsapp.ui.views;

import com.example.newsapp.domain.MessageEntity;
import com.example.newsapp.domain.ResourceItem;
import com.example.newsapp.repositories.MessageRepository;
import com.example.newsapp.repositories.ResourceItemRepository;
import com.example.newsapp.ui.layout.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
@PageTitle("Сообщения")
@Route(value = "messages", layout = MainLayout.class)
public class MessagesView extends VerticalLayout {
    private final MessageRepository messageRepository;
    private final ResourceItemRepository resourceRepository;

    private final Grid<MessageEntity> grid = new Grid<>(MessageEntity.class, false);
    private final TextArea content = new TextArea("Текст сообщения");
    private final TextField authorUsername = new TextField("Автор (имя пользователя)");
    private final ComboBox<ResourceItem> resource = new ComboBox<>("Ресурс (необязательно)");
    private final Button save = new Button("Сохранить");
    private final Button delete = new Button("Удалить");
    private final Button cancel = new Button("Отмена");

    private final BeanValidationBinder<MessageEntity> binder = new BeanValidationBinder<>(MessageEntity.class);
    private MessageEntity current;

    public MessagesView(MessageRepository messageRepository,
                        ResourceItemRepository resourceRepository) {
        this.messageRepository = messageRepository;
        this.resourceRepository = resourceRepository;
        setSizeFull();

        grid.addColumn(MessageEntity::getId).setHeader("ID").setAutoWidth(true);
        grid.addColumn(MessageEntity::getAuthorUsername)
                .setHeader("Автор").setAutoWidth(true);
        grid.addColumn(m -> m.getResource() != null ? m.getResource().getName() : "")
                .setHeader("Ресурс").setAutoWidth(true);
        grid.addColumn(m -> {
            String c = m.getContent();
            return c != null && c.length() > 80 ? c.substring(0, 80) + "…" : c;
        }).setHeader("Текст").setAutoWidth(true).setFlexGrow(2);
        grid.addColumn(m -> m.getCreatedAt() != null ? m.getCreatedAt().toString() : "")
                .setHeader("Создан").setAutoWidth(true);
        grid.addColumn(m -> m.getStatus() != null ? m.getStatus().name() : "")
                .setHeader("Статус").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> edit(e.getValue()));

        // Form
        final FormLayout form = new FormLayout();
        content.setWidthFull();
        content.setMaxLength(2048);
        content.setHeight("200px");

        authorUsername.setWidthFull();

        resource.setItems(resourceRepository.findAll());
        resource.setItemLabelGenerator(ResourceItem::getName);
        resource.setWidthFull();

        form.add(content, authorUsername, resource);

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        actions.setSpacing(true);

        binder.bindInstanceFields(this);

        save.addClickListener(e -> save());
        delete.addClickListener(e -> delete());
        cancel.addClickListener(e -> clearForm());

        VerticalLayout formWrapper = new VerticalLayout(form, actions);
        formWrapper.setWidth("640px");
        formWrapper.setPadding(false);

        SplitLayout split = new SplitLayout(grid, formWrapper);
        split.setSizeFull();
        split.setSplitterPosition(64);

        add(split);
        refreshGrid();
        clearForm();
    }

    private void refreshGrid() {
        List<MessageEntity> items = messageRepository.findAllWithRelations();
        grid.setItems(items);
    }

    private void clearForm() {
        current = new MessageEntity();
        binder.readBean(current);
        grid.deselectAll();
    }

    private void edit(MessageEntity entity) {
        current = entity != null ? entity : new MessageEntity();
        binder.readBean(current);
        // Refresh combos data
        resource.setItems(resourceRepository.findAll());
    }

    @Transactional
    private void save() {
        try {
            binder.writeBean(current);
            messageRepository.save(current);
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
                messageRepository.deleteById(current.getId());
                Notification.show("Удалено");
                refreshGrid();
                clearForm();
            } catch (Exception ex) {
                Notification.show("Не удалось удалить: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        }
    }
}

