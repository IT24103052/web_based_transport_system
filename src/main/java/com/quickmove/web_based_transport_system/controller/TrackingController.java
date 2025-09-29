package com.quickmove.web_based_transport_system.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class TrackingController {
    @MessageMapping("/location")
    @SendTo("/topic/tracking")
    public String updateLocation(String locationJson) {
        return locationJson;
    }
}