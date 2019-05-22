package com.amazon.pay.sample.server.controller;

import com.amazon.pay.Client;
import com.amazon.pay.Config;
import com.amazon.pay.exceptions.AmazonServiceException;
import com.amazon.pay.impl.PayClient;
import com.amazon.pay.impl.PayConfig;
import com.amazon.pay.request.AuthorizeRequest;
import com.amazon.pay.request.ConfirmOrderReferenceRequest;
import com.amazon.pay.request.GetOrderReferenceDetailsRequest;
import com.amazon.pay.request.SetOrderReferenceDetailsRequest;
import com.amazon.pay.response.parser.AuthorizeResponseData;
import com.amazon.pay.response.parser.ConfirmOrderReferenceResponseData;
import com.amazon.pay.response.parser.GetOrderReferenceDetailsResponseData;
import com.amazon.pay.response.parser.SetOrderReferenceDetailsResponseData;
import com.amazon.pay.sample.server.storage.DatabaseMock;
import com.amazon.pay.sample.server.storage.DatabaseMock.Order;
import com.amazon.pay.sample.server.utils.TokenUtil;
import com.amazon.pay.types.CurrencyCode;
import com.amazon.pay.types.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

@Controller
public class ApayController {

    @Value("${client.id}")
    private String clientId;

    @Value("${seller.id}")
    private String sellerId;

    @Value("${access.key}")
    private String accessKey;

    @Value("${secret.key}")
    private String secretKey;

    @PostMapping("/createOrder")
    public String createOrder(@RequestParam int hd8, @RequestParam int hd10, Model model) {

        Order order = new Order();
        order.items = new ArrayList<>();
        if (hd8 != 0) {
            order.items.add(new DatabaseMock.Item("item0008", "Fire HD8", hd8, 8980));
        }
        if (hd10 != 0) {
            order.items.add(new DatabaseMock.Item("item0010", "Fire HD10", hd10, 15980));
        }
        order.price = order.items.stream().mapToLong(item -> item.summary).sum();
        order.priceTaxIncluded = (long) (1.08 * order.price);
        order.myOrderStatus = "CREATED";
        String myOrderId = DatabaseMock.storeOrder(order);
        model.addAttribute("order", order);
        model.addAttribute("token", TokenUtil.storeByToken(myOrderId));

        return "cart";
    }

    @GetMapping("/cart_pc")
    public String cart_pc() {
        return "cart_pc";
    }

    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    @GetMapping("/button")
    public String button(@RequestParam String token, HttpServletResponse response, Model model) {
        System.out.println("[button] " + token);

        Cookie cookie = new Cookie("token", token);
        cookie.setSecure(true);
        response.addCookie(cookie);

        model.addAttribute("clientId", clientId);
        model.addAttribute("sellerId", sellerId);

        return "button";
    }

    @GetMapping("/confirm_order")
    public String confirmOrder(@CookieValue(required = false) String token, HttpServletResponse response, Model model) {
        if (token == null) return "dummy"; // dealt with the request Chrome Custom Tabs sometime reload this page.
        System.out.println("[confirm_order] " + token);

        // Delete token cookie
        Cookie cookie = new Cookie("token", token);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        model.addAttribute("token", token);
        model.addAttribute("order", DatabaseMock.getOrder(TokenUtil.get(token)));
        model.addAttribute("clientId", clientId);
        model.addAttribute("sellerId", sellerId);

        return "confirm_order";
    }

    @PostMapping("/purchase")
    public String purchase(@RequestParam String token, @RequestParam String accessToken, @RequestParam String orderReferenceId, Model model) throws AmazonServiceException {
        System.out.println("[purchase] " + token + ", " + accessToken + ", " + orderReferenceId);

        Order order = DatabaseMock.getOrder(TokenUtil.get(token));
        order.orderReferenceId = orderReferenceId;

        Config config = new PayConfig()
                .withSellerId(sellerId)
                .withAccessKey(accessKey)
                .withSecretKey(secretKey)
                .withCurrencyCode(CurrencyCode.JPY)
                .withSandboxMode(true)
                .withRegion(Region.JP);

        Client client = new PayClient(config);

        GetOrderReferenceDetailsRequest request = new GetOrderReferenceDetailsRequest(orderReferenceId);
        // request.setAddressConsentToken(paramMap.get("access_token")); // Note: It's old! should be removed!
        request.setAccessToken(accessToken);
        GetOrderReferenceDetailsResponseData response = client.getOrderReferenceDetails(request);

        System.out.println("<GetOrderReferenceDetailsResponseData>");
        System.out.println(response);
        System.out.println("</GetOrderReferenceDetailsResponseData>");

        order.buyerName = emptyIfNull(response.getDetails().getBuyer().getName());
        order.buyerEmail = emptyIfNull(response.getDetails().getBuyer().getEmail());
        order.buyerPhone = emptyIfNull(response.getDetails().getBuyer().getPhone());
        order.destinationName = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getName());
        order.destinationPhone = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getPhone());
        order.destinationPostalCode = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getPostalCode());
        order.destinationStateOrRegion = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getStateOrRegion());
        order.destinationCity = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getCity());
        order.destinationAddress1 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine1());
        order.destinationAddress2 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine2());
        order.destinationAddress3 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine3());

        SetOrderReferenceDetailsRequest setOrderReferenceDetailsRequest = new SetOrderReferenceDetailsRequest(orderReferenceId, String.valueOf(order.priceTaxIncluded));

        //set optional parameters
        setOrderReferenceDetailsRequest.setOrderCurrencyCode(CurrencyCode.JPY);
        setOrderReferenceDetailsRequest.setSellerNote(String.valueOf(order.items));
        setOrderReferenceDetailsRequest.setSellerOrderId(order.myOrderId);
        setOrderReferenceDetailsRequest.setStoreName("My Sweet Shop");

        //call API
        SetOrderReferenceDetailsResponseData responseSet = client.setOrderReferenceDetails(setOrderReferenceDetailsRequest);

        System.out.println("<SetOrderReferenceDetailsResponseData>");
        System.out.println(responseSet);
        System.out.println("</SetOrderReferenceDetailsResponseData>");

        ConfirmOrderReferenceResponseData responseCon = client.confirmOrderReference(new ConfirmOrderReferenceRequest(orderReferenceId));
        // Note: it was not String, but request object!

        System.out.println("<ConfirmOrderReferenceResponseData>");
        System.out.println(responseCon);
        System.out.println("</ConfirmOrderReferenceResponseData>");

        AuthorizeRequest authorizeRequest = new AuthorizeRequest(orderReferenceId, generateId(), String.valueOf(order.priceTaxIncluded));

        //Set Optional parameters
        authorizeRequest.setAuthorizationCurrencyCode(CurrencyCode.JPY); //Overrides currency code set in Client
        authorizeRequest.setSellerAuthorizationNote("You can write something here.");
        authorizeRequest.setTransactionTimeout("0"); //Set to 0 for synchronous mode
//        authorizeRequest.setCaptureNow(true); // Set this to true if you want to capture the amount in the same API call

        //Call Authorize API
        AuthorizeResponseData authResponse = client.authorize(authorizeRequest);

        System.out.println("<AuthorizeResponseData>");
        System.out.println(authResponse);
        System.out.println("</AuthorizeResponseData>");

        order.myOrderStatus = "AUTHORIZED";
        DatabaseMock.storeOrder(order);

        model.addAttribute("token", token);

        return "purchase";
    }

    @PostMapping("/thanks")
    public String thanks(@RequestParam String token, Model model) {
        System.out.println("[thanks] " + token);
        model.addAttribute("order", DatabaseMock.getOrder(TokenUtil.remove(token)));
        return "thanks";
    }


    private String generateId() {
        return String.valueOf(Math.abs(ThreadLocalRandom.current().nextLong()));
    }

    private String emptyIfNull(String s) {
        return s == null ? "" : s;
    }
}
