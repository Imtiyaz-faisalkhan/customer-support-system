package com.customersupport.backend.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * On Render (prod profile) the React build is served by this app from {@code static/} (see the Dockerfile). The
 * client-side routes have no file behind them, so opening or refreshing e.g. /dashboard/tickets/3 would 404;
 * forward them to index.html and let React Router render the page. Locally Vite serves the UI instead. "/" is here
 * too (on the deploy branch PingController no longer maps it to a placeholder text).
 */
@Controller
@Profile("prod")
public class SpaController {

    @GetMapping({"/", "/login", "/dashboard", "/dashboard/**"})
    public String app() {
        return "forward:/index.html";
    }
}
