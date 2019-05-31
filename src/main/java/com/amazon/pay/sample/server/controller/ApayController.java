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

    /**
     * merchant.propertiesからの読み込み
     */
    @Value("${client.id}")
    private String clientId;

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
     * order.htmlから呼び出されて、受注Objectを生成・保存する.
     *
     * @param hd8   Kindle File HD8の購入数
     * @param hd10  Kindle File HD10の購入数
     * @param model 画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @PostMapping("/createOrder")
    public String createOrder(@RequestParam int hd8, @RequestParam int hd10, Model model) {

        // 受注Objectの生成
        Order order = new Order();
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

        // 受注Objectの保存と受注Objectへのアクセス用tokenの生成
        // Note: tokenを用いる理由については、TokenUtilのJavadoc参照.
        String myOrderId = DatabaseMock.storeOrder(order);
        String token = TokenUtil.storeByToken(myOrderId);

        // 画面生成templateへの値の受け渡し
        model.addAttribute("order", order);
        model.addAttribute("token", token);

        return "cart";
    }

    /**
     * Chrome Custom Tabsの起動時に呼び出されて、ボタンWidget表示画面を表示する.
     * Note: ボタンWidget表示画面は見た目上ではLoading画像のみが表示されており、次の購入確定画面に自動的に遷移する.
     * 裏では非表示のボタンWidgetを読み込まれており、読み込みが完了すると自動的にJavaScriptでボタンWidgetがクリックされる.
     *
     * @param token    受注Objectへのアクセス用token
     * @param response responseオブジェクト
     * @param model    画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/button")
    public String button(@RequestParam String token, HttpServletResponse response, Model model) {
        System.out.println("[button] " + token);

        // tokenが削除済みの場合(購入処理後、「戻る」で戻ってきてAmazonPayボタンがクリックされた場合)、エラーとする.
        if(!TokenUtil.exists(token)) return "error";

        Cookie cookie = new Cookie("token", token);
        cookie.setSecure(true);
        response.addCookie(cookie);

        model.addAttribute("clientId", clientId);
        model.addAttribute("sellerId", sellerId);

        return "button";
    }

    /**
     * ボタンWidget表示画面から呼び出されて、アドレスWidget・支払いWidgetのある購入確定画面を表示する.
     *
     * @param token    受注Objectへのアクセス用token
     * @param response responseオブジェクト
     * @param model    画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
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

    /**
     * 購入確定画面から呼び出されて、購入処理を実行してThanks画面Activityを呼び出すIntentを送信する.
     *
     * @param token            受注Objectへのアクセス用token
     * @param accessToken      Amazon Pay側の情報にアクセスするためのToken. ボタンWidgetクリック時に取得する.
     * @param orderReferenceId Amazon Pay側の受注管理番号.
     * @param model            画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     * @throws AmazonServiceException Amazon PayのAPIがthrowするエラー. 今回はサンプルなので特に何もしていないが、実際のコードでは正しく対処する.
     */
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

        //--------------------------------------------
        // Amazon Pay側のOrderReferenceの詳細情報の取得
        //--------------------------------------------
        GetOrderReferenceDetailsRequest request = new GetOrderReferenceDetailsRequest(orderReferenceId);
        // request.setAddressConsentToken(paramMap.get("access_token")); // Note: It's old! should be removed!
        request.setAccessToken(accessToken);
        GetOrderReferenceDetailsResponseData response = client.getOrderReferenceDetails(request);

        System.out.println("<GetOrderReferenceDetailsResponseData>");
        System.out.println(response);
        System.out.println("</GetOrderReferenceDetailsResponseData>");

        // Amazon Pay側の受注詳細情報を、受注Objectに反映
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

        //--------------------------------
        // OrderReferenceの詳細情報の設定
        //--------------------------------
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

        //--------------------------------
        // OrderReferenceの確認
        //--------------------------------
        ConfirmOrderReferenceResponseData responseCon = client.confirmOrderReference(new ConfirmOrderReferenceRequest(orderReferenceId));
        // Note: it was not String, but request object!

        System.out.println("<ConfirmOrderReferenceResponseData>");
        System.out.println(responseCon);
        System.out.println("</ConfirmOrderReferenceResponseData>");

        //----------------------------------
        // Authorize(オーソリ, 与信枠確保)処理
        //----------------------------------
        AuthorizeRequest authorizeRequest = new AuthorizeRequest(orderReferenceId, generateId(), String.valueOf(order.totalPrice));

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

        // 受注Objectのステータスをオーソリ完了に設定して保存
        order.myOrderStatus = "AUTHORIZED";
        DatabaseMock.storeOrder(order);

        model.addAttribute("token", token);

        return "purchase";
    }

    /**
     * Thanks画面Activity内のWebViewから呼び出されて、受注Objectの詳細情報を表示する.
     *
     * @param token 受注Objectへのアクセス用token
     * @param model 画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @PostMapping("/thanks")
    public String thanks(@RequestParam String token, Model model) {
        System.out.println("[thanks] " + token);
        model.addAttribute("order", DatabaseMock.getOrder(TokenUtil.remove(token)));
        return "thanks";
    }

    /**
     * テスト用URL. 通常のPCのブラウザからアクセスできる.
     *
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/cart_pc")
    public String cart_pc() {
        return "cart_pc";
    }

    private String generateId() {
        return String.valueOf(Math.abs(ThreadLocalRandom.current().nextLong()));
    }

    private String emptyIfNull(String s) {
        return s == null ? "" : s;
    }
}
