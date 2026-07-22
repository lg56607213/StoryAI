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
 * - SMTP가 설정되면(JavaMailSender 빈 존재) 실제로 발송한다.
 * - 설정 전(키 없음)에는 발송을 건너뛰고 로그만 남긴다 → 앱은 정상 부팅.
 *
 * 활성화 방법(Railway 환경변수):
 *   SPRING_MAIL_HOST=smtp.gmail.com
 *   SPRING_MAIL_USERNAME=보내는주소@gmail.com
 *   SPRING_MAIL_PASSWORD=<Gmail 앱 비밀번호>
 * (포트/STARTTLS 기본값은 application.yml 에 설정됨)
 */
@Slf4j
@Component
public class EmailNotifier {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** 보내는 사람 주소(기본: SMTP 계정). */
    @Value("${spring.mail.username:}")
    private String from;

    public EmailNotifier(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /** SMTP가 설정되어 실제 발송이 가능한 상태인지. */
    public boolean isConfigured() {
        return mailSenderProvider.getIfAvailable() != null;
    }

    /** 테스트용 간단 발송. 실패하면 예외를 던져 원인을 즉시 알 수 있게 한다(로그 삼키지 않음). */
    public void sendSimple(String to, String subject, String body) throws Exception {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP 미설정: SPRING_MAIL_HOST/USERNAME/PASSWORD 환경변수를 확인하세요.");
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo(to);
        if (from != null && !from.isBlank()) {
            helper.setFrom(from);
        }
        helper.setSubject(subject);
        helper.setText(body, false);
        sender.send(message);
    }

    /** 완성본 PDF를 이메일로 발송한다. pdfBytes 가 있으면 첨부한다. */
    public void sendBookReady(String toEmail, String title, byte[] pdfBytes, String downloadUrl) {
        String safeTitle = (title == null || title.isBlank()) ? "동화책" : title;
        if (toEmail == null || toEmail.isBlank()) {
            log.info("완성본 준비됨(제목={}), 수신 이메일 없음 → 발송 생략", safeTitle);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            // SMTP 미설정: 발송은 건너뛰되, 완성본은 결과 화면 다운로드로 받을 수 있음.
            log.info("[이메일 미설정 → 발송 생략] to={}, 제목='{}', 다운로드={}", toEmail, safeTitle, downloadUrl);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setSubject("[투데이히어로] '" + safeTitle + "' 동화책이 완성되었어요 📖");
            helper.setText(
                    safeTitle + " 동화책이 완성되었어요!\n\n"
                            + "첨부된 PDF 파일에서 우리 아이 동화책을 확인하실 수 있어요.\n"
                            + "소중한 순간을 담아드릴 수 있어 기뻐요. 감사합니다.\n\n"
                            + "— 투데이히어로 (todayhero.co.kr)",
                    false);
            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment(safeTitle + ".pdf", new ByteArrayResource(pdfBytes), "application/pdf");
            }
            sender.send(message);
            log.info("완성본 이메일 발송 완료: to={}, 제목='{}'", toEmail, safeTitle);
        } catch (Exception e) {
            // 발송 실패해도 작업 자체는 성공 처리(다운로드로 수령 가능).
            log.warn("이메일 발송 실패: to={}, 원인={}", toEmail, e.getMessage());
        }
    }
}
