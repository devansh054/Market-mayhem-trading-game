package com.example.marketmayhem.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketmayhem.dto.CancelOrderMessage;
import com.example.marketmayhem.dto.PlaceOrderMessage;
import com.example.marketmayhem.service.OrderService;

@Controller
public class WebSocketController {

    private final OrderService orderService;

    public WebSocketController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Matches client send to: /app/room/{roomId}/order.place
    @MessageMapping("/room/{roomId}/order.place")
    @Transactional
    public void placeOrder(@DestinationVariable("roomId") String roomId, PlaceOrderMessage msg) {
        orderService.placeOrder(msg, roomId);
    }

    // Matches client send to: /app/room/{roomId}/order.cancel
    @MessageMapping("/room/{roomId}/order.cancel")
    @Transactional
    public void cancelOrder(@DestinationVariable("roomId") String roomId, CancelOrderMessage msg) {
        orderService.cancelOrder(msg.clOrdId(), msg.player(), roomId);
    }
}
