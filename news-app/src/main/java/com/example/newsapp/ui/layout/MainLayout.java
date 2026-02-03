package com.example.newsapp.ui.layout;

import com.example.newsapp.ui.views.MessagesView;
import com.example.newsapp.ui.views.ResourcesView;
import com.example.newsapp.ui.views.UsersView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;

public class MainLayout extends AppLayout {

    private final Tabs menu;

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        addToNavbar(new DrawerToggle(), new H1("News Admin"));
        this.menu = createMenu();
        Scroller scroller = new Scroller(menu);
        scroller.setClassName("app-nav-scroller");
        addToDrawer(scroller);
    }

    private Tabs createMenu() {
        final Tabs tabs = new Tabs();
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        tabs.add(
                createTab("Пользователи", UsersView.class),
                createTab("Ресурсы", ResourcesView.class),
                createTab("Сообщения", MessagesView.class)
        );
        return tabs;
    }

    private Tab createTab(String text, Class<? extends com.vaadin.flow.component.Component> navigationTarget) {
        final RouterLink link = new RouterLink(text, navigationTarget);
        link.setTabIndex(-1);
        final Tab tab = new Tab(link);
        return tab;
    }
}

