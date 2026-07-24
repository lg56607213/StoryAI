package com.storyai.backend.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 키움페이 통합결제 LINK(웹표준, V3.1 보안) + 통합취소 API 클라이언트.
 *
 * 결제: 1단계 /pay/hash 로 KIWOOM_ENC(해시)를 받고, 2단계에서 브라우저가 /pay/linkEnc 로 결제창을 연다.
 * 취소: /pay/ready → RETURNURL 2단계 호출.
 *
 * CPID·연동키는 환경변수로 주입한다. 미설정이면 isConfigured()=false(결제 비활성).
 */
@Slf4j
@Component
public class KiwoomPayClient {

    private final String provider;
    private final String cpid;
    private final String cancelAuthKey;
    private final String cardMethod;
    private final String apiBase;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public KiwoomPayClient(
            @Value("${storyai.payment.provider:none}") String provider,
            @Value("${storyai.payment.kiwoom.cpid:}") String cpid,
            @Value("${storyai.payment.kiwoom.cancel-auth-key:}") String cancelAuthKey,
            @Value("${storyai.payment.kiwoom.card-method:CARD}") String cardMethod,
            @Value("${storyai.payment.kiwoom.api-base:https://apitest.kiwoompay.co.kr}") String apiBase,
            ObjectMapper mapper) {
        this.provider = provider;
        this.cpid = cpid;
        this.cancelAuthKey = cancelAuthKey;
        this.cardMethod = cardMethod;
        this.apiBase = apiBase.replaceAll("/+$", "");
        this.mapper = mapper;
    }

    @PostConstruct
    void logConfig() {
        log.info("KiwoomPayClient 설정: provider={}, configured={}, cardMethod={}, apiBase={}",
                provider, isConfigured(), cardMethod, apiBase);
    }

    /** 결제 UI(결제 흐름)를 노출할지. provider=kiwoom이면 true (CPID 없어도 심사용 결제화면 표시 가능). */
    public boolean uiEnabled() {
        return "kiwoom".equalsIgnoreCase(provider);
    }

    /** 실제 결제 요청이 가능한지(CPID까지 있음). */
    public boolean isConfigured() {
        return uiEnabled() && cpid != null && !cpid.isBlank();
    }

    public String cpid() {
        return cpid;
    }

    public String cardMethod() {
        return cardMethod;
    }

    public String linkEncUrl() {
        return apiBase + "/pay/linkEnc";
    }

    /**
     * 1단계: 결제 해시(KIWOOM_ENC)를 받는다.
     * 필수: PAYMETHOD, TYPE(P/M/W), CPID, ORDERNO, AMOUNT.
     */
    public String requestHash(String payMethod, String type, String orderNo, int amount) {
        requireConfigured();
        ObjectNode body = mapper.createObjectNode();
        body.put("PAYMETHOD", payMethod);
        body.put("TYPE", type);
        body.put("CPID", cpid);
        body.put("ORDERNO", orderNo);
        body.put("AMOUNT", String.valueOf(amount)); // 모든 값 문자열
        JsonNode res = postJson(apiBase + "/pay/hash", body, StandardCharsets.UTF_8, null);
        String code = res.path("RESULTCODE").asText("");
        if (!"0000".equals(code)) {
            throw new IllegalStateException("키움페이 해시 요청 실패 [" + code + "] "
                    + res.path("ERRORMESSAGE").asText(""));
        }
        String enc = res.path("KIWOOM_ENC").asText("");
        if (enc.isBlank()) {
            throw new IllegalStateException("키움페이 해시 응답에 KIWOOM_ENC 없음");
        }
        return enc;
    }

    /**
     * 결제 취소(부분취소 가능). 성공 시 취소금액 반환.
     * @param payMethod 취소결제수단(예: CARD/CARDK)
     * @param trxId 승인거래번호(DAOUTRX)
     * @param amount 취소금액
     */
    public void cancel(String payMethod, String trxId, int amount, String reason) {
        requireConfigured();
        // 1단계: ready → RETURNURL + TOKEN
        ObjectNode ready = mapper.createObjectNode();
        ready.put("CPID", cpid);
        ready.put("PAYMETHOD", payMethod);
        ready.put("CANCELREQ", "Y");
        JsonNode readyRes = postJson(apiBase + "/pay/ready", ready,
                Charset.forName("EUC-KR"), cancelAuthKey);
        String returnUrl = readyRes.path("RETURNURL").asText("");
        String token = readyRes.path("TOKEN").asText("");
        if (returnUrl.isBlank() || token.isBlank()) {
            throw new IllegalStateException("키움페이 취소 ready 실패: " + snippet(readyRes.toString()));
        }

        // 2단계: RETURNURL 로 실제 취소 요청
        ObjectNode cancel = mapper.createObjectNode();
        cancel.put("CPID", cpid);
        cancel.put("TRXID", trxId);
        cancel.put("AMOUNT", String.valueOf(amount));
        cancel.put("CANCELREASON", reason == null || reason.isBlank() ? "고객 요청" : reason);
        JsonNode res = postJson(returnUrl, cancel, Charset.forName("EUC-KR"), cancelAuthKey, token);
        String code = res.path("RESULTCODE").asText("");
        if (!"0000".equals(code)) {
            throw new IllegalStateException("키움페이 취소 실패 [" + code + "] "
                    + res.path("ERRORMESSAGE").asText(""));
        }
    }

    // ---------- 내부 ----------

    private JsonNode postJson(String url, JsonNode body, Charset charset, String authKey, String... token) {
        try {
            byte[] payload = mapper.writeValueAsString(body).getBytes(charset);
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json;charset=" + charset.name())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
            if (authKey != null && !authKey.isBlank()) {
                req.header("Authorization", authKey);
            }
            if (token != null && token.length > 0 && token[0] != null) {
                req.header("TOKEN", token[0]);
            }
            HttpResponse<byte[]> res = http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
            String text = new String(res.body(), charset);
            if (res.statusCode() != 200) {
                throw new IllegalStateException("키움페이 HTTP " + res.statusCode() + ": " + snippet(text));
            }
            return mapper.readTree(text);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("키움페이 호출 오류: " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("결제(키움페이)가 아직 설정되지 않았습니다. (CPID 미설정)");
        }
    }

    private String snippet(String s) {
        return s == null ? "" : s.substring(0, Math.min(300, s.length()));
    }
}
