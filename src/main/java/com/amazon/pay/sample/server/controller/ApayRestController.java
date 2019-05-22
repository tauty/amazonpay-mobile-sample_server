package com.amazon.pay.sample.server.controller;

import com.amazon.pay.Client;
import com.amazon.pay.Config;
import com.amazon.pay.exceptions.AmazonServiceException;
import com.amazon.pay.impl.PayClient;
import com.amazon.pay.impl.PayConfig;
import com.amazon.pay.request.GetOrderReferenceDetailsRequest;
import com.amazon.pay.response.parser.GetOrderReferenceDetailsResponseData;
import com.amazon.pay.sample.server.storage.DatabaseMock;
import com.amazon.pay.sample.server.storage.DatabaseMock.Order;
import com.amazon.pay.sample.server.utils.TokenUtil;
import com.amazon.pay.types.CurrencyCode;
import com.amazon.pay.types.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
public class ApayRestController {

    @Value("${seller.id}")
    private String sellerId;

    @Value("${access.key}")
    private String accessKey;

    @Value("${secret.key}")
    private String secretKey;

    @PostMapping("/registerOrder")
    public String registerOrder(@RequestParam(required = false) String token, @RequestParam int hd8, @RequestParam int hd10) {

        Order order = token == null ? new Order() : DatabaseMock.getOrder(TokenUtil.get(token));
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

        return token != null ? token : TokenUtil.storeByToken(myOrderId);
    }

    @PostMapping("/calc_postage")
    public Map<String, String> calcPostage(@RequestParam String token, @RequestParam String orderReferenceId
            , @RequestParam String accessToken) throws AmazonServiceException {
        System.out.println("[calc_postage]: " + token + ", " + orderReferenceId + ", " + accessToken);

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

        Order order = DatabaseMock.getOrder(TokenUtil.get(token));
        order.postage = calcPostage(response);
        order.totalPrice = order.priceTaxIncluded + order.postage;
        DatabaseMock.storeOrder(order);

        Map<String, String> map = new HashMap<>();
        map.put("postage", comma(order.postage));
        map.put("totalPrice", comma(order.totalPrice));
        return map;
    }

    private long calcPostage(GetOrderReferenceDetailsResponseData response) {
        String stateOrRegion = response.getDetails().getDestination().getPhysicalDestination().getStateOrRegion();
        if (stateOrRegion.equals("沖縄県") || stateOrRegion.equals("北海道")) {
            return 1080;
        } else {
            return 540;
        }
    }

    private String comma(long num) {
        return comma(String.valueOf(num));
    }

    private String comma(String num) {
        int index = num.length() - 3;
        if (index <= 0) return num;
        return comma(num.substring(0, index)) + "," + num.substring(index);
    }

}
