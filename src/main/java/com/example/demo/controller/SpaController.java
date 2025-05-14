package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    /**
     * This method serves the Vue.js application for any path except API endpoints and static resources.
     * It allows Vue to handle client-side routing
     */
    @GetMapping(value = {
            "/",
            "/login",
            "/register",
            "/dashboard",
            "/courses/**",
            "/exams/**",
            "/profile"
    })
    public String forward() {
        return "forward:/index.html";
    }
}