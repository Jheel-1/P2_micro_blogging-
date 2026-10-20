package uga.menik.csx370.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uga.menik.csx370.services.NotificationService;
import uga.menik.csx370.services.UserService;

@Controller
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping
    public String getNotificationsPage(Model model,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(required = false) String error) {
        model.addAttribute("notifications", List.of());
        model.addAttribute("unreadOnly", unread);
        model.addAttribute("notificationsLoaded", false);
        model.addAttribute("errorMessage", error);
        model.addAttribute("typeOptions", NotificationService.TYPES.stream()
                .map(value -> Map.of("value", value, "selected", value.equals(type))).toList());
        try {
            model.addAttribute("notifications",
                    notificationService.getNotificationsForUser(viewerId(), unread, type));
            model.addAttribute("notificationsLoaded", true);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException e) {
            model.addAttribute("errorMessage", "Unable to load notifications. Please try again.");
        }
        return "notifications_page";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable long id) {
        try {
            notificationService.markAsRead(viewerId(), id);
            return "redirect:/notifications";
        } catch (IllegalStateException e) {
            return failedUpdate();
        }
    }

    @PostMapping("/read-all")
    public String markAllRead() {
        try {
            notificationService.markAllAsRead(viewerId());
            return "redirect:/notifications";
        } catch (IllegalStateException e) {
            return failedUpdate();
        }
    }

    private long viewerId() {
        return Long.parseLong(userService.getLoggedInUser().getUserId());
    }

    private String failedUpdate() {
        return "redirect:/notifications?error=" + URLEncoder.encode(
                "Unable to update notifications. Please try again.", StandardCharsets.UTF_8);
    }
}
