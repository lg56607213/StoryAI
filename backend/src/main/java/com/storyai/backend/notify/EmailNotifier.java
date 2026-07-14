package com.storyai.backend.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 완성본 전송 알림.
 * 지금은 실제 발송 대신 로그만 남기는 스텁이다. 실제 이메일 발송을 붙이려면:
 *  1) build.gradle 에 spring-boot-starter-mail 추가
 *  2) application.yml 에 spring.mail.* (SMTP 호스트/계정/비밀번호) 설정
 *  3) 아래 send()에서 JavaMailSender 로 첨부(PDF) 메일 전송으로 교체
 * (카카오 알림톡은 사업자 채널·심사가 필요해 이후 별도 연동.)
 */
@Slf4j
@Component
public class EmailNotifier {

    /** 완성본 다운로드 안내를 보낸다(현재 스텁: 로그만). */
    public void sendBookReady(String toEmail, String title, String downloadUrl) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("완성본 준비됨(제목={}), 수신 이메일 없음 → 발송 생략", title);
            return;
        }
        // TODO: 실제 SMTP 발송으로 교체 (계정 설정 후).
        log.info("[이메일 발송 예정 - 스텁] to={}, 제목='{}', 다운로드={}", toEmail, title, downloadUrl);
    }
}
