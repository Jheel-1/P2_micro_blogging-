package uga.menik.csx370.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uga.menik.csx370.models.Notification;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.NotificationService;
import uga.menik.csx370.services.UserService;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @Autowired
    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    } // NotificationController

    @GetMapping
    public String getNotificationsPage(Model model) {
        User loggedInUser = userService.getLoggedInUser();
        if (loggedInUser == null) {
            return "redirect:/login";
        } //if

        long userId = Long.parseLong(loggedInUser.getUserId());
        List<Notification> notifications = notificationService.getNotificationsForUser(userId);
        notificationService.markAllAsRead(userId);

        model.addAttribute("notifications", notifications);
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("loggedUser", loggedInUser);
        model.addAttribute("unreadNotificationCount", 0L);
        model.addAttribute("hasUnreadNotifications", false);

        return "notifications_page";
    } // getNotificationsPage
} // NotificationController
