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
public class AmazonPayRestController {

    /**
     * merchant.propertiesからの読み込み
     */
    @Value("${seller.id}")
    private String sellerId;

    /**
     * merchant.propertiesからの読み込み
     */
    @Value("${access.key}")
    private String accessKey;

    /**
     * merchant.propertiesからの読み込み
     */
    @Value("${secret.key}")
    private String secretKey;

    /**
     * NATIVEサンプル用Activityから非同期に呼び出されて、受注Objectを生成・保存する.
     * 既に受注Objectが生成済みの場合には、更新を行う.
     *
     * @param token 受注Objectへのアクセス用token
     * @param hd8   Kindle File HD8の購入数
     * @param hd10  Kindle File HD10の購入数
     * @return 受注Objectへのアクセス用token(※ ApayControllerと違い 、 画面生成template名ではなく直接ResponseのBodyを返却する)
     */
    @PostMapping("/registerOrder")
    public String registerOrder(@RequestParam(required = false) String token, @RequestParam int hd8, @RequestParam int hd10) {

        // 受注Objectの生成/更新
        Order order = token == null ? new Order() : DatabaseMock.getOrder(TokenUtil.get(token));
        order.items = new ArrayList<>();
        if (hd8 > 0) {
            order.items.add(new DatabaseMock.Item("item0008", "Fire HD8", hd8, 8980));
        }
        if (hd10 > 0) {
            order.items.add(new DatabaseMock.Item("item0010", "Fire HD10", hd10, 15980));
        }
        order.price = order.items.stream().mapToLong(item -> item.summary).sum();
        order.priceTaxIncluded = (long) (1.08 * order.price);
        order.myOrderStatus = "CREATED";

        // 受注Objectの保存
        String myOrderId = DatabaseMock.storeOrder(order);

        // 受注Objectへのアクセス用tokenの返却
        // Note: tokenを用いる理由については、TokenUtilのJavadoc参照.
        return token != null ? token : TokenUtil.storeByToken(myOrderId);
    }

    /**
     * 購入確定画面でアドレスWidgetで住所を選択した時にAjaxで非同期に呼び出されて、Amazon Pay APIから取得した住所情報より送料を計算する.
     *
     * @param token            受注Objectへのアクセス用token
     * @param accessToken      Amazon Pay側の情報にアクセスするためのToken. ボタンWidgetクリック時に取得する.
     * @param orderReferenceId Amazon Pay側の受注管理番号.
     * @return 計算した送料・総合計金額を含んだJSON (※ApayControllerと違い、画面生成template名ではなく直接ResponseのBodyを返却する)
     * @throws AmazonServiceException Amazon PayのAPIがthrowするエラー. 今回はサンプルなので特に何もしていないが、実際のコードでは正しく対処する.
     */
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

        //--------------------------------------------
        // Amazon Pay側のOrderReferenceの詳細情報の取得
        //--------------------------------------------
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
