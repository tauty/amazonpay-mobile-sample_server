package com.amazon.pay.sample.server.utils;

import com.amazon.pay.sample.server.storage.CacheMock;

import java.util.UUID;

/**
 * 受注Objectアクセス用のtokenを生成し、受注ObjectのIDである受注IDを管理するUtility.
 *
 * <pre>
 *     [受註IDをそのまま使わず、tokenを発行して管理する理由]
 *     一般に受注IDは重複しないことが主な要件であるため、「日付＋シーケンス番号」のような、
 *     採番された値を推測できる実装であることが多い.
 *     本サンプルでは受注を特定するためにtokenをURLパラメタやCookie, Intentのパラメタ等で
 *     指定しており、もしこれを推測可能な受注IDでやってしまうと、推測した値を埋め込んでアクセス
 *     すれば他人の受注情報を見たり更新したりできるセキュリティバグになってしまう.
 *     よって、本サンプルでは受注情報を充分に推測困難なtokenをキーとしてCacheに登録することで、
 *     外部から直接受注IDを指定できなくして、セキュリティを担保している.
 * </pre>
 */
public class TokenUtil {

    /**
     * tokenを生成し、パラメタの受注IDと紐付けてから返却する.
     * @param orderId 受注ID
     * @return token
     */
    public static String storeByToken(String orderId) {
        String token = createToken();
        CacheMock.put(token, orderId);
        return token;
    }

    /**
     * パラメタのtokenに紐付けられた受注IDを返却する.
     * @param token 受注Objectアクセス用のtoken
     * @return 受注ID
     */
    public static String get(String token) {
        return CacheMock.get(token);
    }

    /**
     * パラメタのtokenに紐付けられた受注IDが存在するか判定する.
     * @param token 受注Objectアクセス用のtoken
     * @return 受注IDが存在するときtrue, 削除されていればfalse
     */
    public static boolean exists(String token) {
        return CacheMock.get(token) != null;
    }

    /**
     * 受注Objectアクセス用のtokenを生成する.
     * tokenは推測困難であることが求められるので、下記の要件を満たす必要がある.
     * <ul>
     *     <li>暗号論的擬似乱数生成器(CSPRNG)により生成された乱数部分を含んでいる</li>
     *     <li>上記乱数部分の桁数が十分な長さである.(※一般的なSessionID・tokenの実装で128bit前後)</li>
     * </ul>
     * ここで採用しているUUID v4のJDKによる実装は上記を満たしている.
     * もし別の実装を採用する場合には、上記を満たしているか確認すること.<br/>
     * Note: UUID v4の乱数部分の仕様では、生成にCSPRNGを使うことを規定していない. つまり実装によっては
     * 推測可能な乱数生成方法を採用している可能性もある. よって、JDK以外のUUID v4の実装の採用を検討する
     * 場合には必ずその乱数生成方法がCSPRNGかを確認すること.
     * @return 受注Objectアクセス用のtoken
     */
    private static String createToken() {
        return UUID.randomUUID().toString();
    }
}
