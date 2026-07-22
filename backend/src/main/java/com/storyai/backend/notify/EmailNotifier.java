package com.storyai.backend.notify;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 완성본 이메일 발송(PDF 첨부).
 * 발송 수단 우선순위: ① Resend(HTTPS API) → ② SMTP(JavaMailSender) → ③ 미설정 시 로그만.
 * - Railway는 SMTP 포트를 막으므로 운영에서는 Resend를 사용한다.
 * - 어떤 수단도 없으면 발송을 건너뛰고 앱은 정상 동작(고객은 다운로드로 수령).
 */
@Slf4j
@Component
public class EmailNotifier {

    private final ResendMailer resend;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** SMTP 발신 주소(SMTP 폴백 사용 시). */
    @Value("${spring.mail.username:}")
    private String smtpFrom;

    public EmailNotifier(ResendMailer resend, ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.resend = resend;
        this.mailSenderProvider = mailSenderProvider;
    }

    /** 실제 발송이 가능한 상태인지(Resend 또는 SMTP 중 하나라도 설정됨). */
    public boolean isConfigured() {
        return resend.isConfigured() || mailSenderProvider.getIfAvailable() != null;
    }

    /** 테스트용 간단 발송. 실패 시 예외를 던져 원인을 즉시 알 수 있게 한다. */
    public void sendSimple(String to, String subject, String body) throws Exception {
        if (resend.isConfigured()) {
            resend.send(to, subject, body, null, null);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("발송 수단 미설정: RESEND_API_KEY 또는 SPRING_MAIL_* 환경변수를 확인하세요.");
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo(to);
        if (smtpFrom != null && !smtpFrom.isBlank()) {
            helper.setFrom(smtpFrom);
        }
        helper.setSubject(subject);
        helper.setText(body, false);
        sender.send(message);
    }

    /** 완성본 PDF를 이메일로 발송한다. 성공하면 true. 실패해도 예외 없이 false 반환(작업은 성공 처리). */
    public boolean sendBookReady(String toEmail, String title, byte[] pdfBytes, String downloadUrl) {
        String safeTitle = (title == null || title.isBlank()) ? "동화책" : title;
        if (toEmail == null || toEmail.isBlank()) {
            log.info("완성본 준비됨(제목={}), 수신 이메일 없음 → 발송 생략", safeTitle);
            return false;
        }
        String subject = "[투데이히어로] '" + safeTitle + "' 동화책이 완성되었어요 📖";
        String body = safeTitle + " 동화책이 완성되었어요!\n\n"
                + "첨부된 PDF 파일에서 우리 아이 동화책을 확인하실 수 있어요.\n"
                + "소중한 순간을 담아드릴 수 있어 기뻐요. 감사합니다.\n\n"
                + "— 투데이히어로 (todayhero.co.kr)";
        String fileName = safeTitle + ".pdf";

        try {
            if (resend.isConfigured()) {
                resend.send(toEmail, subject, body, pdfBytes, fileName);
                log.info("완성본 이메일 발송 완료(Resend): to={}", toEmail);
                return true;
            }
            JavaMailSender sender = mailSenderProvider.getIfAvailable();
            if (sender == null) {
                log.info("[발송 수단 미설정 → 생략] to={}, 제목='{}', 다운로드={}", toEmail, safeTitle, downloadUrl);
                return false;
            }
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            if (smtpFrom != null && !smtpFrom.isBlank()) {
                helper.setFrom(smtpFrom);
            }
            helper.setSubject(subject);
            helper.setText(body, false);
            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment(fileName, new ByteArrayResource(pdfBytes), "application/pdf");
            }
            sender.send(message);
            log.info("완성본 이메일 발송 완료(SMTP): to={}", toEmail);
            return true;
        } catch (Exception e) {
            log.warn("이메일 발송 실패: to={}, 원인={}", toEmail, e.getMessage());
            return false;
        }
    }
}
