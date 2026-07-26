package com.togudv.sylphy.controller;

import com.togudv.sylphy.model.Reminder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reminder")
public class ReminderController {

    @GetMapping("/ ")
    public String name() {
        return "";
    }
}
