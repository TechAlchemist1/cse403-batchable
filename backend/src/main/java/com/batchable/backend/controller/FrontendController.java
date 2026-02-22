package com.batchable.backend.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

// To avoid errors when user refreshes on a non-home url
@Controller
public class FrontendController {

    @RequestMapping(value = {
        "/",
        "/{x:[\\w\\-]+}",
        "/{x:[\\w\\-]+}/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}